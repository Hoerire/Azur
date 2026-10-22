package com.hoerire.tools.libterm.testing

import com.hoerire.tools.libterm.Clock

class FakeClock(initialMillis: Long = 0L) : Clock {
    private var currentMillis: Long = initialMillis

    override fun nowMillis(): Long = currentMillis

    fun advanceBy(deltaMillis: Long): Long {
        require(deltaMillis >= 0L) { "deltaMillis must be >= 0" }
        currentMillis += deltaMillis
        return currentMillis
    }
}
