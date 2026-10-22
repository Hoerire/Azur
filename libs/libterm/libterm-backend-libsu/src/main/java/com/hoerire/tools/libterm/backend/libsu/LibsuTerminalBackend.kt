package com.hoerire.tools.libterm.backend.libsu

import com.hoerire.tools.libterm.BackendStartResult
import com.hoerire.tools.libterm.Clock
import com.hoerire.tools.libterm.OutputChunk
import com.hoerire.tools.libterm.OutputStream
import com.hoerire.tools.libterm.SendResult
import com.hoerire.tools.libterm.TerminalBackend
import com.hoerire.tools.libterm.TerminalBytes
import com.hoerire.tools.libterm.TerminalFailure
import com.hoerire.tools.libterm.TerminalIdentity
import com.hoerire.tools.libterm.TerminalOpenOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LibsuTerminalBackend(
    override val identity: TerminalIdentity,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : TerminalBackend {
    internal constructor(
        identity: TerminalIdentity,
        clock: Clock,
        scope: CoroutineScope,
        adapterFactory: LibsuShellAdapterFactory,
    ) : this(
        identity = identity,
        clock = clock,
        scope = scope,
    ) {
        this.adapterFactory = adapterFactory
    }

    private val lifecycleMutex = Mutex()
    private var adapterFactory: LibsuShellAdapterFactory = RealLibsuShellAdapterFactory()
    private val outputChunks = MutableSharedFlow<OutputChunk>(
        replay = OUTPUT_BUFFER_CAPACITY,
        extraBufferCapacity = OUTPUT_BUFFER_CAPACITY,
    )

    private var session: LibsuShellSession? = null
    private var outputCollectionJob: Job? = null
    private var startResult: BackendStartResult? = null
    private var closed: Boolean = false

    override val output: Flow<OutputChunk> = outputChunks.asSharedFlow()

    override suspend fun start(openOptions: TerminalOpenOptions): BackendStartResult {
        return lifecycleMutex.withLock {
            startResult?.let { return@withLock it }
            if (closed) {
                return@withLock rememberStartResult(
                    BackendStartResult.Failed(
                        TerminalFailure.StartupFailed(
                            identity = identity,
                            message = "libsu backend is closed",
                        ),
                    ),
                )
            }

            try {
                val openedSession = adapterFactory.open(identity, openOptions)
                session = openedSession
                outputCollectionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    openedSession.output.collect { event ->
                        outputChunks.emit(event.toOutputChunk())
                    }
                }
                rememberStartResult(BackendStartResult.Started)
            } catch (error: LibsuInvalidOpenOptionsException) {
                rememberStartResult(
                    BackendStartResult.Failed(error.failure),
                )
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                rememberStartResult(
                    BackendStartResult.Failed(
                        TerminalFailure.StartupFailed(
                            identity = identity,
                            message = error.message,
                            cause = error,
                        ),
                    ),
                )
            }
        }
    }

    override suspend fun send(input: TerminalBytes): SendResult {
        val currentSession = lifecycleMutex.withLock {
            session ?: return@withLock null
        }
        return currentSession?.write(input) ?: SendResult.Failed(
            TerminalFailure.AlreadyClosed(
                identity = identity,
                message = "libsu backend has not been started",
            ),
        )
    }

    override suspend fun close() {
        val currentSession = lifecycleMutex.withLock {
            if (closed) {
                return
            }
            closed = true
            session
        }

        currentSession?.close()
        outputCollectionJob?.cancel()
    }

    override suspend fun awaitExit(): TerminalFailure? {
        val currentSession = lifecycleMutex.withLock {
            val failedStart = (startResult as? BackendStartResult.Failed)?.failure
            if (failedStart != null) {
                return@withLock AwaitTarget.Failure(failedStart)
            }

            session?.let { return@withLock AwaitTarget.Session(it) }

            AwaitTarget.Failure(
                TerminalFailure.StartupFailed(
                    identity = identity,
                    message = "libsu backend has not been started",
                ),
            )
        }

        return when (currentSession) {
            is AwaitTarget.Failure -> currentSession.failure
            is AwaitTarget.Session -> currentSession.session.awaitExit()
        }
    }

    private fun rememberStartResult(result: BackendStartResult): BackendStartResult {
        startResult = result
        return result
    }

    private fun LibsuOutputEvent.toOutputChunk(): OutputChunk {
        return when (this) {
            is LibsuOutputEvent.Stdout -> OutputChunk(
                stream = OutputStream.STDOUT,
                bytes = bytes,
                timestampMillis = clock.nowMillis(),
            )

            is LibsuOutputEvent.Stderr -> OutputChunk(
                stream = OutputStream.STDERR,
                bytes = bytes,
                timestampMillis = clock.nowMillis(),
            )
        }
    }

    private sealed interface AwaitTarget {
        data class Session(val session: LibsuShellSession) : AwaitTarget

        data class Failure(val failure: TerminalFailure) : AwaitTarget
    }

    private companion object {
        private const val OUTPUT_BUFFER_CAPACITY = 64
    }
}
