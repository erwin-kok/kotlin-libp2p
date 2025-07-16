// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.event

import org.erwinkok.libp2p.core.network.NatDeviceType
import org.erwinkok.libp2p.core.network.NetworkProtocol

data class EvtNatDeviceTypeChanged(
    val transportProtocol: NetworkProtocol,
    val natDeviceType: NatDeviceType,
) : EventType()
