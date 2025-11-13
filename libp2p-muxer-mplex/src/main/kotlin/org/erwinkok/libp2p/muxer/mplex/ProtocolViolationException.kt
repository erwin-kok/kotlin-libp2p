package org.erwinkok.libp2p.muxer.mplex

import io.ktor.util.internal.initCauseBridge
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolViolationException(val violation: String) : Exception(), CopyableThrowable<ProtocolViolationException> {
    override val message: String
        get() = "Received illegal frame: $violation"

    override fun createCopy(): ProtocolViolationException = ProtocolViolationException(violation).also {
        it.initCauseBridge(this)
    }
}
