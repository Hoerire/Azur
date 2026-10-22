package com.hoerire.tools.libterm.runtime

import com.hoerire.tools.libterm.AuthorizationResult
import com.hoerire.tools.libterm.BackendAvailability
import com.hoerire.tools.libterm.PrivilegeAuthorizer
import com.hoerire.tools.libterm.PrivilegeProvider
import com.hoerire.tools.libterm.TerminalFailure
import com.hoerire.tools.libterm.TerminalIdentity
import com.hoerire.tools.libterm.runtime.internal.RuntimePrivilegeAuthorizer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RuntimePrivilegeAuthorizerTest {

    @Test
    fun `root authorization denied is preserved and does not call shizuku authorizer`() = runTest {
        val suFailure = TerminalFailure.AuthorizationDenied(
            identity = TerminalIdentity.Su,
            message = "root denied",
        )
        val libsuProvider = RecordingPrivilegeProvider(
            BackendAvailability.Unauthorized(suFailure),
        )
        val shizukuAuthorizer = RecordingAuthorizer(AuthorizationResult.Granted)
        val authorizer = RuntimePrivilegeAuthorizer(
            libsuProvider = libsuProvider,
            shizukuAuthorizer = shizukuAuthorizer,
        )

        val result = authorizer.requestAuthorization(TerminalIdentity.Su)

        val denied = result as AuthorizationResult.Denied
        assertSame(suFailure, denied.failure)
        assertEquals(listOf(TerminalIdentity.Su), libsuProvider.calls)
        assertEquals(emptyList(), shizukuAuthorizer.calls)
    }

    @Test
    fun `shizuku authorization delegates to shizuku authorizer only`() = runTest {
        val shizukuFailure = TerminalFailure.AuthorizationDenied(
            identity = TerminalIdentity.Shizuku,
            message = "shizuku denied",
        )
        val shizukuResult = AuthorizationResult.Denied(shizukuFailure)
        val libsuProvider = RecordingPrivilegeProvider(BackendAvailability.Available)
        val shizukuAuthorizer = RecordingAuthorizer(shizukuResult)
        val authorizer = RuntimePrivilegeAuthorizer(
            libsuProvider = libsuProvider,
            shizukuAuthorizer = shizukuAuthorizer,
        )

        val result = authorizer.requestAuthorization(TerminalIdentity.Shizuku)

        assertSame(shizukuResult, result)
        assertEquals(emptyList(), libsuProvider.calls)
        assertEquals(listOf(TerminalIdentity.Shizuku), shizukuAuthorizer.calls)
    }

    private class RecordingPrivilegeProvider(
        private val availability: BackendAvailability,
    ) : PrivilegeProvider {
        val calls = mutableListOf<TerminalIdentity>()

        override suspend fun getAvailability(identity: TerminalIdentity): BackendAvailability {
            calls += identity
            return availability
        }
    }

    private class RecordingAuthorizer(
        private val result: AuthorizationResult,
    ) : PrivilegeAuthorizer {
        val calls = mutableListOf<TerminalIdentity>()

        override suspend fun requestAuthorization(identity: TerminalIdentity): AuthorizationResult {
            calls += identity
            return result
        }
    }
}
