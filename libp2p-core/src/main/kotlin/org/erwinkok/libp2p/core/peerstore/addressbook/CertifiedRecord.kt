// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore.addressbook

data class CertifiedRecord(
    val sequence: Long,
    val raw: ByteArray,
)
