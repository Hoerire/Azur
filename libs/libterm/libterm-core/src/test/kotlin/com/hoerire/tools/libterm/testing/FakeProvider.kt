package com.hoerire.tools.libterm.testing

import com.hoerire.tools.libterm.BackendAvailability
import com.hoerire.tools.libterm.PrivilegeProvider
import com.hoerire.tools.libterm.TerminalFailure
import com.hoerire.tools.libterm.TerminalIdentity

class FakeProvider : PrivilegeProvider {
    private val availabilityByIdentity = mutableMapOf<TerminalIdentity, BackendAvailability>()

    override suspend fun getAvailability(identity: TerminalIdentity): BackendAvailability {
        return availabilityByIdentity[identity] ?: BackendAvailability.Available
    }

    fun setAvailable(identity: TerminalIdentity) {
        availabilityByIdentity[identity] = BackendAvailability.Available
    }

    fun setUnavailable(identity: TerminalIdentity, message: String? = null) {
        availabilityByIdentity[identity] = BackendAvailability.Unavailable(
            TerminalFailure.BackendUnavailable(identity = identity, message = message),
        )
    }

    fun setUnauthorized(identity: TerminalIdentity, message: String? = null) {
        availabilityByIdentity[identity] = BackendAvailability.Unauthorized(
            TerminalFailure.AuthorizationDenied(identity = identity, message = message),
        )
    }
}
