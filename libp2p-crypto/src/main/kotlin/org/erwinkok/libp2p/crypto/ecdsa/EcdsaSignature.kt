// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import java.math.BigInteger

data class EcdsaSignature(val r: BigInteger, val s: BigInteger)
