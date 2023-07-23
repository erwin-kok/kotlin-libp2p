// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.base

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.erwinkok.libp2p.core.util.StringUtils

abstract class AwaitableClosableBase(
    val scope: CoroutineScope,
) : AwaitableClosable {
    protected val context = Job(scope.coroutineContext[Job])

    override val jobContext: Job
        get() = context

    protected fun launch(name: String? = null, action: suspend (CoroutineScope) -> Unit): Job {
        val coroutineName = name ?: StringUtils.randomString(8)
        return scope.launch(context + CoroutineName(coroutineName)) {
            action(this)
        }
    }

    override fun close() {
        context.cancel()
    }
}
