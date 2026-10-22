package com.hoerire.tools.libterm.runtime.internal

import com.hoerire.tools.libterm.Clock

internal object RuntimeClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
