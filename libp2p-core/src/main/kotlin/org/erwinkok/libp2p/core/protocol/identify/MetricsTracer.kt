// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.protocol.identify

import org.erwinkok.libp2p.core.event.EvtLocalAddressesUpdated
import org.erwinkok.libp2p.core.event.EvtLocalProtocolsUpdated

class MetricsTracer {
    fun triggeredPushes(it: EvtLocalAddressesUpdated) {
//        TODO("Not yet implemented")
    }

    fun triggeredPushes(it: EvtLocalProtocolsUpdated) {
//        TODO("Not yet implemented")
    }

    fun identifySent(push: Boolean, protocolsCount: Int, listenAddrsCount: Int) {
//        TODO("Not yet implemented")
    }

    fun identifyReceived(push: Boolean, protocolsCount: Int, listenAddrsCount: Int) {
//        TODO("Not yet implemented")
    }

    fun connectionPushSupport(identifyPushSupported: PushSupport) {
//        TODO("Not yet implemented")
    }
}
