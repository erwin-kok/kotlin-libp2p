// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing.mocknet.done

import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.testing.mocknet.done.MockNet
import org.erwinkok.libp2p.testing.mocknet.done.Printer
import java.io.StringWriter

class MockPrinter(private val sw: StringWriter) : Printer {
    override fun mocknetLinks(mocknet: MockNet) {
        val links = mocknet.links()
        sw.append("Mocknet link map:\n")
        for ((p1, lm) in links) {
            sw.append("\t$p1 linked to:\n")
            for ((p2, l) in lm) {
                sw.append("\t\t$p2 (${l.size} links)\n")
            }
        }
        sw.append("\n")
    }

    override fun networkConnections(ni: Network) {
        sw.append("${ni.localPeerId} connected to:\n")
        for (c in ni.connections()) {
            sw.append("\t${c.remoteIdentity.peerId} (address: ${c.remoteAddress})\n")
        }
        sw.append("\n")
    }
}
