// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.resourcemanager

import org.erwinkok.result.Result

interface StreamScope : ResourceScope {
    fun setService(srv: String): Result<Unit>
}
