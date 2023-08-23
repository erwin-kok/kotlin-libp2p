package org.erwinkok.libp2p.core.protocol.ping

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.host.BasicHost
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.swarm.SwarmTestBuilder
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class PingServiceTest {
    @Test
    fun testPing() = runTest {
        val h1 = BasicHost(this, SwarmTestBuilder.create(this))
        val h2 = BasicHost(this, SwarmTestBuilder.create(this))
//        h1.connect(AddressInfo.fromPeerIdAndAddresses(h2.id, h2.addresses())).expectNoErrors()
        val ps1 = PingService(this, h1)
        val ps2 = PingService(this, h2)
//        ping(ps1, h2.id)
        ps2.close()
        ps1.close()
        h2.close()
        h2.awaitClosed()
        h1.close()
        h1.awaitClosed()
    }

    private suspend fun ping(ps: PingService, peerId: PeerId) {
        val flow = ps.ping(peerId, 5.seconds)
        flow.take(5).collect {
            assertNull(it.error)
            assertNotNull(it.rtt)
            assertTrue(0 > it.rtt!!)
        }
    }
}
