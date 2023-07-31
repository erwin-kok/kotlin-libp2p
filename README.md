# kotlin-libp2p

[![ci](https://github.com/erwin-kok/kotlin-libp2p/actions/workflows/ci.yaml/badge.svg)](https://github.com/erwin-kok/kotlin-libp2p/actions/workflows/ci.yaml)
[![Maven Central](https://img.shields.io/maven-central/v/org.erwinkok.libp2p/kotlin-libp2p)](https://central.sonatype.com/artifact/org.erwinkok.libp2p/kotlin-libp2p)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/github/license/erwin-kok/kotlin-libp2p.svg)](https://github.com/erwin-kok/kotlin-libp2p/blob/master/LICENSE)

## Disclaimer

Welcome to my personal pet project! This project is currently under heavy development (WIP), and as such I do not
guarantee functionality, stable interfaces and/or operation without any bugs/issues (See also [`LICENSE`](LICENSE) for
more details). The current main goal of this project is educational, and to research new technologies.

## libp2p overview

Traditional communication is client-server based. The client initiates a connection to the server, and communicates with
the server using a protocol suited for that server. As an example: suppose we have two WhatsApp clients lets say `A`
and `B`. And `A` wants to send a message to `B`. Typically, they both initiate a connection to the server. `A` sends a
message to the server and tells the server to relay this message to `B`. The server knows both clients so this
communication is pretty straightforward.

In a peer-to-peer network (P2P), this communication happens quite differently. There is no longer a server, only
clients (commonly they are referred to as `nodes` or `peers`) organized in a mesh network, and they communicate directly
to each other. For this to work a few challenges have to be solved:

- How do the nodes know and find each other? In a client-server communication, the client established the connection to
  the server, and the server is known by some pre-defined ip address (or addresses). In a P2P mesh network how to know
  the ip address of the client? Potentially, the client is roaming which means its ip address can change over time.
**In libp2p this is solved by mDNS, DHT/Kademlia, and more**.

- How can a node directly connect to another node? In client-server communication, the client initiates a connection to
  the server. This is an outbound connection. However, in a P2P connection, a node connects directly to another node,
  which is an inbound connection. Inbound connections are not always possible. Think of security (the router rejects
  inbound connection attempts), but also think of NAT devices (what is the correct ip address? A node might know a
  different ip address of itself than the outside world is able to connect to) **In libp2p this is solved by autonat, 
holepunching, relay-service, and more**.

- Which wire protocol to use? In a client-server network the used protocol is obvious: the server dictates the protocol
  to be used, or else it rejects the connection. A server can easily support the current protocol version while also
  supporting the older version(s) temporarily. A server can also force a client upgrade. In a P2P network this is not
  obvious, because there are several nodes connected to each other. They all could have a different implementation with
  different capabilities and with different versions **In libp2p this is solved by multiformats**.

There already exists some P2P network protocols, but they lacked standardization. Libp2p standardizes they way nodes
communicate to each other and has a solution for the above-mentioned issues (and more).

See for an in-depth description of libp2p, please see: https://libp2p.io/

## Features

- Multiformats. See my other repo: https://github.com/erwin-kok/multiformat
  - [X] multiaddr
  - [X] multibase 
  - [X] multicodec
  - [X] multihash
  - [X] multistream-select 

- Crypto 
  - [X] ED25519
  - [X] ECDSA
  - [X] SECp256k1
  - [X] RSA

- Transports
  - [X] Tcp
  - [ ] Quic (planned) 

- Muxers
  - [X] Mplex
  - [ ] Yamux (planned)
  - [ ] Quic (planned)

- Security
  - [X] Noise
  - [ ] Tls (planned)
  - [ ] Quic (planned)

- Protocols
  - [ ] Identify (planned)
  - [ ] Ping (planned)
  - [ ] DHT/Kademlia (planned)
  - [ ] pubsub (planned)

- Peer discovery
  - [ ] mDNS (planned)
  - [ ] DHT/Kademlia (planned)

- Datastore
  - [X] RocksDB 

## Getting started

First add the proper dependencies to your project:

Kotlin DSL:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.erwinkok.result:libp2p-xxx:$latest")
}
```

`libp2p-core` is mandatory, other dependencies are optional depending on your needs. For example, if you need tcp 
transport, include `libp2p-transport-tcp`, or if you want the mplex muxer include `libp2p-muxer-plex`.

In your code, first create a host:

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
            peerstore {
                gcInterval = 1.hours
                keyStore {
                    password = "APasswordThatIsAtLeast20CharactersLong"
                    dek {
                        salt = "W/SC6fnZfBIWdeAD3l+ClLpQtfICEtn+KYTUhfKq6d7l"
                    }
                }
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

The layout is hopefully clear: for example, the code above will use `tcp` as a transport, `noise` for security and 
`mplex` as a muxer.

Then you can add a handler:

```kotlin
    host.setStreamHandler(ProtocolId.of("/chat/1.0.0")) {
        chatHandler(it)
    }
```
This means that if a peer connects and requests the `/chat/1.0.0` protocol, the corresponding handler will be called. 

To call a peer and open a new stream, use the following code:

```kotlin
    val stream = host.newStream(aPeerId, ProtocolId.of("/chat/1.0.0"))
        .getOrElse { 
            logger.error { "Could not open chat stream with peer: ${errorMessage(it)}" }
            return@runBlocking
        }
    chatHandler(stream)
```

This tries to connect to peer `aPeerId` and tries to open a new stream for protocol `/chat/1.0.0`. If it fails, it 
returns if it succeeds it progresses to the chatHandler.

See also the example application in `app`.

To use this sample app, start the application. It will create a new random LocalIdentity (key-pair) and logs the adres
on which it listens on the output:

```shell
[main] o.e.l.a.ApplicationKt$main$1: Local addresses the Host listens on: /ip4/0.0.0.0/tcp/10333/p2p/12D3KooWDfaEJxpmjbFLb9wUakCd6Lo6LRntaV3drb4EaYZRtYuY 
```

In the libp2p-Go repository you can find `chat` in the examples directory. Then you can connect to the running instance
by using:

```shell
./chat -d /ip4/0.0.0.0/tcp/10333/p2p/12D3KooWDfaEJxpmjbFLb9wUakCd6Lo6LRntaV3drb4EaYZRtYuY
```

On both sides it should mention that a connection is established. 

## Contact

If you want to contact me, please write an e-mail to: [erwin-kok@gmx.com](mailto:erwin-kok@gmx.com)

## Acknowledgements

This work is largely based on the awesome libp2p-go implementation. This work would not have been possible without their
effort. Please consider giving kudos to the libp2p-go authors.
(See also [`ACKNOWLEDGEMENTS`](ACKNOWLEDGEMENTS.md))

## License

This project is licensed under the BSD-3-Clause license, see [`LICENSE`](LICENSE) file for more details. 