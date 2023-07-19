// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network

import inet.ipaddr.HostName
import inet.ipaddr.IPAddressString
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.SocketAddress
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.multiformat.multiaddress.Multiaddress
import org.erwinkok.multiformat.multiaddress.Protocol
import org.erwinkok.multiformat.multiaddress.components.Component
import org.erwinkok.multiformat.multiaddress.components.MultihashComponent
import org.erwinkok.multiformat.multihash.Multihash
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.flatMap
import java.io.ByteArrayOutputStream

enum class NetworkProtocol {
    UNKNOWN,
    UDP,
    TCP,
}

class InetMultiaddress private constructor(val hostName: HostName?, val networkProtocol: NetworkProtocol, var multihash: Multihash?, private val components: List<Component>) {
    private val _string: String by lazy { constructString() }
    val bytes: ByteArray by lazy { constructBytes() }

    val peerId: Result<PeerId>
        get() {
            val mh = multihash ?: return Err("$this does not contain a multihash")
            return PeerId.fromMultihash(mh)
        }

    fun toSocketAddress(): Result<SocketAddress> {
        if (hostName != null) {
            return Ok(InetSocketAddress(hostName.host, hostName.port))
        }
        return Err("Could not convert InetMultiAddress $this into a socket")
    }

    val isValidTcpIp: Boolean
        get() {
            val hn = hostName ?: return false
            return hn.isValid && hn.port != null && hn.port > 0 && networkProtocol == NetworkProtocol.TCP
        }

    val isNotEmpty: Boolean
        get() = bytes.isNotEmpty()

    fun withHostName(hostName: HostName): InetMultiaddress {
        return InetMultiaddress(hostName, networkProtocol, multihash, components)
    }

    fun withNetworkProtocol(networkProtocol: NetworkProtocol): InetMultiaddress {
        return InetMultiaddress(hostName, networkProtocol, multihash, components)
    }

    fun withMultihash(multihash: Multihash): InetMultiaddress {
        return InetMultiaddress(hostName, networkProtocol, multihash, components)
    }

    fun withPeerId(peerId: PeerId): InetMultiaddress {
        return InetMultiaddress(hostName, networkProtocol, peerId.multihash, components)
    }

    fun withoutPeerId(): InetMultiaddress {
        return InetMultiaddress(hostName, networkProtocol, null, components)
    }

    override fun toString(): String {
        return _string
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is InetMultiaddress) {
            return super.equals(other)
        }
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    private fun constructBytes(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        if (hostName != null) {
            val address = hostName.address
            if (address.isIPv4) {
                Protocol.writeTo(outputStream, Protocol.IP4, address.bytes)
            } else {
                val ip6 = address.toIPv6()
                if (ip6 != null) {
                    if (ip6.hasZone()) {
                        Protocol.writeTo(outputStream, Protocol.IP6ZONE, ip6.zone.toByteArray())
                        Protocol.writeTo(outputStream, Protocol.IP6, ip6.removeZone().bytes)
                    } else {
                        Protocol.writeTo(outputStream, Protocol.IP6, address.bytes)
                    }
                } else {
                    Protocol.writeTo(outputStream, Protocol.IP4, address.bytes)
                }
            }
            if (hostName.port != null) {
                val portBytes = byteArrayOf((hostName.port shr 8).toByte(), hostName.port.toByte())
                if (networkProtocol == NetworkProtocol.TCP) {
                    Protocol.writeTo(outputStream, Protocol.TCP, portBytes)
                } else if (networkProtocol == NetworkProtocol.UDP) {
                    Protocol.writeTo(outputStream, Protocol.UDP, portBytes)
                }
            }
        }
        components.forEach {
            Protocol.writeTo(outputStream, it.protocol, it.addressBytes)
        }
        val mh = multihash
        if (mh != null) {
            Protocol.writeTo(outputStream, Protocol.P2P, mh.bytes())
        }
        return outputStream.toByteArray()
    }

    private fun constructString(): String {
        val stringBuilder = StringBuilder()
        if (hostName != null) {
            val address = hostName.address
            if (address.isIPv4) {
                Protocol.writeTo(stringBuilder, Protocol.IP4, address.toString())
            } else {
                val ip6 = address.toIPv6()
                if (ip6 != null) {
                    if (ip6.hasZone()) {
                        Protocol.writeTo(stringBuilder, Protocol.IP6ZONE, ip6.zone)
                        Protocol.writeTo(stringBuilder, Protocol.IP6, ip6.removeZone().toString())
                    } else {
                        Protocol.writeTo(stringBuilder, Protocol.IP6, address.toString())
                    }
                } else {
                    Protocol.writeTo(stringBuilder, Protocol.IP4, address.toString())
                }
            }
            if (hostName.port != null) {
                if (networkProtocol == NetworkProtocol.TCP) {
                    Protocol.writeTo(stringBuilder, Protocol.TCP, hostName.port.toString())
                } else if (networkProtocol == NetworkProtocol.UDP) {
                    Protocol.writeTo(stringBuilder, Protocol.UDP, hostName.port.toString())
                }
            }
        }
        components.forEach {
            Protocol.writeTo(stringBuilder, it.protocol, it.value)
        }
        val mh = multihash
        if (mh != null) {
            Protocol.writeTo(stringBuilder, Protocol.P2P, mh.base58())
        }
        return cleanPath(stringBuilder.toString())
    }

    private fun cleanPath(str: String): String {
        val split = str.trim { it <= ' ' }.split("/")
        return "/" + split.filter { cs -> cs.isNotBlank() }.joinToString("/")
    }

    companion object {
        fun fromString(address: String): Result<InetMultiaddress> {
            return Multiaddress.fromString(address)
                .flatMap { fromMultiaddress(it) }
        }

        fun fromBytes(bytes: ByteArray): Result<InetMultiaddress> {
            return Multiaddress.fromBytes(bytes)
                .flatMap { fromMultiaddress(it) }
        }

        fun fromHostNameAndProtocol(hostName: HostName, protocol: NetworkProtocol): Result<InetMultiaddress> {
            return Ok(InetMultiaddress(hostName, protocol, null, listOf()))
        }

        fun fromSocketAndProtocol(socketAddress: SocketAddress, protocol: NetworkProtocol): Result<InetMultiaddress> {
            if (socketAddress is InetSocketAddress) {
                val hostName = HostName(java.net.InetSocketAddress(socketAddress.hostname, socketAddress.port))
                return Ok(InetMultiaddress(hostName, protocol, null, listOf()))
            }
            return Err("Could not create InetMultiaddress from socket")
        }

        private fun fromMultiaddress(multiaddress: Multiaddress): Result<InetMultiaddress> {
            return fromComponents(multiaddress.components)
        }

        private fun fromComponents(components: List<Component>): Result<InetMultiaddress> {
            val addressBuilder = StringBuilder()
            components.forEach { it.writeTo(addressBuilder) }
            val address = addressBuilder.toString()

            var multihash: Multihash? = null
            val p2pIndex = components.indexOfLast { it.protocol == Protocol.IPFS || it.protocol == Protocol.P2P }
            var endIndex = components.size
            if (p2pIndex >= 0) {
                if (p2pIndex != endIndex - 1) {
                    return Err("/p2p component must be the last component")
                }
                val multihashComponent = components[p2pIndex]
                if (multihashComponent is MultihashComponent) {
                    multihash = multihashComponent.multihash
                }
                endIndex--
            }

            var zone: String? = null
            var ip: IPAddressString? = null
            var port = 0
            var hostName: HostName? = null
            var networkProtocol = NetworkProtocol.UNKNOWN
            val restComponents = mutableListOf<Component>()
            for (i in 0 until endIndex) {
                val component = components[i]
                when (component.protocol) {
                    Protocol.IP6ZONE -> {
                        if (!zone.isNullOrBlank()) {
                            return Err("$address has multiple zones")
                        }
                        zone = component.value
                    }

                    Protocol.IP4, Protocol.DNS4 -> {
                        if (!zone.isNullOrBlank()) {
                            return Err("$address has ip4 with zone")
                        }
                        if (ip != null) {
                            return Err("$address has multiple ip addresses")
                        }
                        ip = IPAddressString(component.value)
                    }

                    Protocol.IP6, Protocol.DNS, Protocol.DNS6 -> {
                        if (ip != null) {
                            return Err("$address has multiple ip addresses")
                        }
                        ip = if (zone != null) {
                            IPAddressString("${component.value}%$zone")
                        } else {
                            IPAddressString(component.value)
                        }
                    }

                    Protocol.TCP -> {
                        if (port != 0) {
                            return Err("$address multiple ports")
                        }
                        port = try {
                            component.value.toInt()
                        } catch (_: NumberFormatException) {
                            0
                        }
                        networkProtocol = NetworkProtocol.TCP
                    }

                    Protocol.UDP -> {
                        if (port != 0) {
                            return Err("$address multiple ports")
                        }
                        port = try {
                            component.value.toInt()
                        } catch (_: NumberFormatException) {
                            0
                        }
                        networkProtocol = NetworkProtocol.UDP
                    }

                    else -> {
                        restComponents.add(component)
                    }
                }
            }
            if (ip != null) {
                hostName = if (ip.address != null) {
                    if (networkProtocol != NetworkProtocol.UNKNOWN) {
                        HostName(ip.address, port)
                    } else {
                        HostName(ip.address)
                    }
                } else {
                    if (networkProtocol != NetworkProtocol.UNKNOWN) {
                        HostName("$ip:$port")
                    } else {
                        HostName("$ip")
                    }
                }
            }
            return Ok(InetMultiaddress(hostName, networkProtocol, multihash, restComponents))
        }
    }
}
