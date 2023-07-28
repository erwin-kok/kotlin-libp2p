// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.transport

import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.result.Result

interface Resolver {
    suspend fun resolve(address: InetMultiaddress): Result<List<InetMultiaddress>>
}
