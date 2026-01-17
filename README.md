<h1 align="center">
  <a href="libp2p.io"><img width="250" src="https://github.com/libp2p/libp2p/blob/master/logo/black-bg-2.png?raw=true" alt="libp2p hex logo" /></a>
</h1>

# kotlin-libp2p

A Kotlin implementation of the libp2p networking stack.

[![ci](https://github.com/erwin-kok/kotlin-libp2p/actions/workflows/ci.yaml/badge.svg)](https://github.com/erwin-kok/kotlin-libp2p/actions/workflows/ci.yaml)
[![Maven Central](https://img.shields.io/maven-central/v/org.erwinkok.libp2p/libp2p-core)](https://central.sonatype.com/artifact/org.erwinkok.libp2p/libp2p-core)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/github/license/erwin-kok/kotlin-libp2p.svg)](https://github.com/erwin-kok/kotlin-libp2p/blob/master/LICENSE)

## libp2p overview

libp2p is a modular peer-to-peer networking framework that standardizes how peers discover each other, establish secure connections, negotiate protocols, and exchange data.

This project provides an idiomatic Kotlin implementation of libp2p, closely aligned with the reference implementations while favoring explicit design, readability, and correctness.

For a detailed introduction to libp2p concepts, see https://libp2p.io/.

## Scope and Status

This project is under active development. Not all libp2p components are implemented, and API stability is not guaranteed.

The primary goals are:
- Protocol correctness
- Clear, explicit architecture
- Interoperability with existing libp2p implementations

Non-goals include:
- Feature completeness at all costs
- Aggressive performance optimizations
- Hiding libp2p concepts behind opaque abstractions

## Features

### Multiformats
(see https://github.com/erwin-kok/multiformat)

- [x] multiaddr
- [x] multibase
- [x] multicodec
- [x] multihash
- [x] multistream-select

### Cryptography

- [x] Ed25519
- [x] ECDSA
- [x] Secp256k1
- [x] RSA

### Transports

- [x] TCP
- [ ] QUIC (planned)

### Stream Multiplexers

- [x] Mplex
- [ ] Yamux (planned)
- [ ] QUIC (planned)

### Security Transports

- [x] Noise
- [ ] TLS (planned)
- [ ] QUIC (planned)

### Protocols

- [x] Identify
- [x] Ping
- [ ] DHT / Kademlia (planned)
- [ ] GossipSub (planned)

### Peer Discovery

- [ ] mDNS (planned)
- [ ] DHT / Kademlia (planned)

### Datastore

- [x] RocksDB

## Getting started

Add the required dependencies:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.erwinkok.result:libp2p-xxx:$latest")
}
```

libp2p-core is required. Other modules are optional and can be included depending on the desired feature set.

### Creating a Host

```kotlin
val hostBuilder = host {
    identity(localIdentity)
    muxers {
        mplex()
    }
    securityTransport {
        noise()
    }
    transports {
        tcp()
    }
    swarm {
        dialTimeout = 10.minutes
        listenAddresses {
            multiAddress("/ip4/0.0.0.0/tcp/10333")
        }
    }
    datastore(datastore)
}

val host = hostBuilder.build(scope)
    .getOrElse {
        logger.error { "The following errors occurred while creating the host: ${errorMessage(it)}" }
        return@runBlocking
    }
```

The configuration explicitly defines the identity, transports, security protocols, and multiplexers used by the host.

### Registering Protocol Handlers

```kotlin
host.setStreamHandler(ProtocolId.of("/chat/1.0.0")) {
    chatHandler(it)
}
```
When a peer negotiates the /chat/1.0.0 protocol, the corresponding handler is invoked.

### Opening Streams

```kotlin
val stream = host.newStream(aPeerId, ProtocolId.of("/chat/1.0.0"))
    .getOrElse {
        logger.error { "Could not open chat stream with peer: ${errorMessage(it)}" }
        return
    }
chatHandler(stream)
```
This establishes a connection to the peer (if needed) and opens a new stream for the requested protocol.

### Examples

A simple chat example is available in examples/chat.

When started, the application logs the listening address, for example:

```shell
./chat -d /ip4/0.0.0.0/tcp/10333/p2p/12D3KooWDfaEJxpmjbFLb9wUakCd6Lo6LRntaV3drb4EaYZRtYuY
```

This address can be used by other libp2p implementations, including `go-libp2p`, to connect to the node.

## Acknowledgements

This project is heavily inspired by the reference `go-libp2p` implementation.

Many design decisions and protocol details are derived from their work. Credit goes to the go-libp2p authors for laying the foundation of the libp2p ecosystem.

See also `ACKNOWLEDGEMENTS`

## License

This project is licensed under the BSD-3-Clause license, see [`LICENSE`](LICENSE) file for more details. 
