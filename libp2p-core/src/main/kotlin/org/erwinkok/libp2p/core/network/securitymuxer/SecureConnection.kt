// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.securitymuxer

import org.erwinkok.libp2p.core.network.Connection

interface SecureConnection : Connection, ConnectionSecurity
