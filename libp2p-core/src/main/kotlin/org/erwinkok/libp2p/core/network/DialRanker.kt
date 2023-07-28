// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network

typealias DialRanker = (List<InetMultiaddress>) -> List<AddressDelay>
