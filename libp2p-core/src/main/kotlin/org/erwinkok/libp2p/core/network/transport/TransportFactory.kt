// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.transport

import kotlinx.coroutines.CoroutineDispatcher
import org.erwinkok.libp2p.core.network.upgrader.Upgrader
import org.erwinkok.libp2p.core.resourcemanager.ResourceManager
import org.erwinkok.result.Result

interface TransportFactory {
    fun create(upgrader: Upgrader, resourceManager: ResourceManager, dispatcher: CoroutineDispatcher): Result<Transport>
}
