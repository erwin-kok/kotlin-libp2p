// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.protocol.identify

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkConnection

class ObservedAddressManager(
    private val scope: CoroutineScope,
) : AwaitableClosable {
    private val _context = Job(scope.coroutineContext[Job])

    override val jobContext: Job get() = _context

    fun record(connection: NetworkConnection, it: InetMultiaddress) {
    }

    fun addresses(): List<InetMultiaddress> {
        return listOf()
    }

    fun addressesFor(local: InetMultiaddress): List<InetMultiaddress> {
        return listOf()
    }

    override fun close() {
        _context.complete()
    }
}
