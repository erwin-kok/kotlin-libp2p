# kotlin-libp2p

[![ci](https://github.com/erwin-kok/kotlin-libp2p/actions/workflows/ci.yaml/badge.svg)](https://github.com/erwin-kok/kotlin-libp2p/actions/workflows/ci.yaml)
[![Maven Central](https://img.shields.io/maven-central/v/org.erwinkok.libp2p/kotlin-libp2p)](https://central.sonatype.com/artifact/org.erwinkok.libp2p/kotlin-libp2p)
[![Kotlin](https://img.shields.io/badge/kotlin-1.8.22-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/github/license/erwin-kok/kotlin-libp2p.svg)](https://github.com/erwin-kok/kotlin-libp2p/blob/master/LICENSE)

## Disclaimer

Welcome to my personal pet project! This project is currently under heavy development (WIP), and as such I do not
guarantee functionality, stable interfaces and/or operation without any bugs/issues (See also [`LICENSE`](LICENSE) for
more details). The current main goal of this project is educational, and to research new technologies.

## libp2p overview

Traditional communication is client-server based. The client initiates a connection to the server, and communicates with
the server using a protocol suited for that server. As an example: suppose we have two WhatsApp clients lets say `A`
and `B`. And `A` wants to send a message to `B`. Typically they both initiate a message to the server. `A` sends a
message to the server and tells the server to relay this message to `B`. The server knows both clients so this
communication is pretty straightforward.

In a peer-to-peer network (P2P), this communication happens quite differently. There is no longer a server, only
clients (commonly they are referred to as `nodes` or `peers`) organized in a mesh network and they communicate directly
to each other. For this to work a few challenges have to be solved:

- How do the nodes know and find each other? In a client-server communication, the client established the connection to
  the server, and the server is known by some pre-defined ip address (or addresses). In a P2P mesh network how to know
  the ip address of the client? Potentially, the client is roaming which means its ip address can change over time.

- How can a node directly connect to another node? In client-server communication, the client initiates a connection to
  the server. This is an outbound connection. However, in a P2P connection, a node connects directly to another node,
  which is an inbound connection. Inbound connections are not always possible. Think of security (the router rejects
  inbound connection attempts), but also think of NAT devices (what is the correct ip address? A node might know a
  different ip address of itself than the outside world is able to connect to).

- Which wire protocol to use? In a client-server network the used protocol is obvious: the server dictates the protocol
  to be used, or else it rejects the connection. A server can easily support the current protocol version while also
  supporting the older version(s) temporarily. A server can also force a client upgrade. In a P2P network this is not
  obvious, because there are several nodes connected to each other. They all could have a different implementation with
  different capabilities and with different versions.

There already exists some P2P network protocols, but they lacked standardization. Libp2p standardizes they way nodes
communicate to each other and has a solution for the above mentioned issues (and more).

See for an in-depth description of libp2p, please see: https://libp2p.io/

## Features

## Getting started

## Contact

If you want to contact me, please write an e-mail to: [erwin-kok@gmx.com](mailto:erwin-kok@gmx.com)

## License

This project is licensed under the BSD-3-Clause license, see [`LICENSE`](LICENSE) file for more details. 