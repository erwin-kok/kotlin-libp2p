// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.resourcemanager

class ScopeStatistic {
    var numStreamsInbound: Int = 0
    var numStreamsOutbound: Int = 0
    var numConnsInbound: Int = 0
    var numConnsOutbound: Int = 0
    var numFD: Int = 0
    var memory: Long = 0
}
