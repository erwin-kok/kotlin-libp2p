// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network

// in rfc3489 4 different Nat types are distinguished:
//
// Full Cone: A full cone NAT is one where all requests from the same internal IP address and port are mapped to the same external
// IP address and port. Furthermore, any external host can send a packet to the internal host, by sending a packet to the mapped
// external address.
//
// Restricted Cone: A restricted cone NAT is one where all requests from the same internal IP address and port are mapped to the same
// external IP address and port. Unlike a full cone NAT, an external host (with IP address X) can send a packet to the internal host
// only if the internal host had previously sent a packet to IP address X.
//
// Port Restricted Cone: A port restricted cone NAT is like a restricted cone NAT, but the restriction includes port numbers.
// Specifically, an external host can send a packet, with source IP address X and source port P, to the internal host only if the
// internal host had previously sent a packet to IP address X and port P.
//
// Symmetric: A symmetric NAT is one where all requests from the same internal IP address and port, to a specific destination IP
// address and port, are mapped to the same external IP address and port. If the same host sends a packet with the same source
// address and port, but to a different destination, a different mapping is used. Furthermore, only the external host that
// receives a packet can send a UDP packet back to the internal host.
//
//
// In other words, the difference between a Cone Nat and a Symmetric Nat is:
//
// Cone: A particular LocalMultiAddress is always mapped to the same observed multiaddress, regardless of its destination.
// This means that peers always see the same observed address for a particular LocalMultiAddress.
//
// Symmetric: A particular LocalMultiAddress is mapped to different observed multiaddresses, depending on its destination.
// This means that different peers see different observed addresses corresponding to the same LocalMultiAddress.
//
// In this library, we do not differentiate between Full Cone, Restricted Cone, and Port Restricted Cone. They all will be
// designated as a Cone Nat. So, in the end, three NatDeviceType are used: Unknown, Cone, and Symmetric.
//
// Note that hole punching is ONLY possible if our side and the remote side are both behind a Cone Nat.

enum class NatDeviceType {
    NatDeviceTypeUnknown,
    NatDeviceTypeCone,
    NatDeviceTypeSymmetric,
}
