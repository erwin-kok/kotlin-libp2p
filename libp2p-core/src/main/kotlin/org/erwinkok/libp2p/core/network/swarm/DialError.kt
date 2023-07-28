// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.result.Error
import org.erwinkok.result.errorMessage

internal data class TransportError(
    val address: InetMultiaddress,
    val cause: Error,
)

internal class DialError(val peerId: PeerId, val cause: Error? = null) {
    private val dialErrors = mutableListOf<TransportError>()
    private var skipped = 0

    fun recordError(address: InetMultiaddress, error: Error) {
        if (dialErrors.size >= MaxDialDialErrors) {
            skipped++
        }
        dialErrors.add(TransportError(address, error))
    }

    fun combine(): Error {
        val sb = StringBuilder()
        sb.append("While trying to connect to $peerId, the following errors occurred: ")
        if (cause != null) {
            sb.append("  ${errorMessage(cause)}")
        }
        for (dialError in dialErrors) {
            sb.append("\n  * [${dialError.address}] ${dialError.cause}")
        }
        if (skipped > 0) {
            sb.append("\n    ... skipping $skipped errors ...")
        }
        return Error(sb.toString())
    }

    companion object {
        private const val MaxDialDialErrors = 16
    }
}
