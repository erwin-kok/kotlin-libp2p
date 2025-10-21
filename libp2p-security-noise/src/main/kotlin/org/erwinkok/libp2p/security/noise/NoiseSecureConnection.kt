// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.security.noise

import com.southernstorm.noise.protocol.CipherState
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ReaderJob
import io.ktor.utils.io.WriterJob
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readShort
import io.ktor.utils.io.reader
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeShort
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.ConnectionBase
import org.erwinkok.libp2p.core.network.securitymuxer.SecureConnection

class NoiseSecureConnection(
    scope: CoroutineScope,
    private val insecureConnection: Connection,
    private val receiverCipherState: CipherState,
    private val senderCipherState: CipherState,
    override val localIdentity: LocalIdentity,
    override val remoteIdentity: RemoteIdentity,
) : ConnectionBase(scope), AwaitableClosable, SecureConnection {
    override fun attachForReadingImpl(channel: ByteChannel): WriterJob =
        scope.writer(context + CoroutineName("noise-conn-input-loop"), channel) {
            try {
                val decryptBuffer = ByteArray(MaxPlaintextLength + senderCipherState.macLength)
                while (!this.channel.isClosedForWrite && !insecureConnection.input.isClosedForRead) {
                    val length = insecureConnection.input.readShort().toInt()
                    require(length < MaxPlaintextLength)
                    insecureConnection.input.readFully(decryptBuffer, 0, length)
                    val decryptLength = receiverCipherState.decryptWithAd(null, decryptBuffer, 0, decryptBuffer, 0, length)
                    this.channel.writeFully(decryptBuffer, 0, decryptLength)
                    this.channel.flush()
                }
            } finally {
                insecureConnection.input.cancel()
            }
        }

    override fun attachForWritingImpl(channel: ByteChannel): ReaderJob =
        scope.reader(context + CoroutineName("noise-conn-output-loop"), channel) {
            try {
                val encryptBuffer = ByteArray(MaxPlaintextLength + senderCipherState.macLength)
                while (!this.channel.isClosedForRead && !insecureConnection.output.isClosedForWrite) {
                    if (channel.availableForRead == 0) {
                        channel.awaitContent()
                        continue
                    }
                    val size = this.channel.readAvailable(encryptBuffer, 0, MaxPlaintextLength)
                    if (size > 0) {
                        val encryptLength = senderCipherState.encryptWithAd(null, encryptBuffer, 0, encryptBuffer, 0, size)
                        insecureConnection.output.writeShort(encryptLength.toShort())
                        insecureConnection.output.writeFully(encryptBuffer, 0, encryptLength)
                        insecureConnection.output.flush()
                    }
                }
            } finally {
                insecureConnection.output.flushAndClose()
            }
        }

    override fun actualClose(): Throwable? {
        return try {
            super.close()
            insecureConnection.close()
            null
        } catch (cause: Throwable) {
            cause
        }
    }

    companion object {
        private const val MaxTransportMsgLength = 0xffff
        private const val MaxPlaintextLength = MaxTransportMsgLength - 16
    }
}
