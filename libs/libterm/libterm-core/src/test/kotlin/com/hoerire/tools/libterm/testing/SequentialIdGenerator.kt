package com.hoerire.tools.libterm.testing

import com.hoerire.tools.libterm.IdGenerator

class SequentialIdGenerator(
    private val prefix: String = "session-",
    start: Long = 1L,
) : IdGenerator {
    private var nextValue: Long = start

    init {
        require(start >= 0L) { "start must be >= 0" }
    }

    override fun nextId(): String = "$prefix${nextValue++}"
}
