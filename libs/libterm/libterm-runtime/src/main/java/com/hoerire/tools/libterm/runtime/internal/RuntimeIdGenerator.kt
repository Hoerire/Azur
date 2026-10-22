package com.hoerire.tools.libterm.runtime.internal

import com.hoerire.tools.libterm.IdGenerator
import java.util.UUID

internal class RuntimeIdGenerator : IdGenerator {
    override fun nextId(): String = UUID.randomUUID().toString()
}
