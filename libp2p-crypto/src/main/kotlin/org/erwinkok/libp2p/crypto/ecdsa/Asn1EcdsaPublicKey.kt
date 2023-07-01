// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import org.apache.kerby.asn1.type.Asn1BitString
import org.apache.kerby.asn1.type.Asn1ObjectIdentifier
import org.apache.kerby.asn1.type.Asn1Sequence
import org.erwinkok.libp2p.crypto.asn1.Asn1Decoder
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import java.io.IOException

object Asn1EcdsaPublicKey {
    const val OID_PUBLIC_KEY_ECDSA = "1.2.840.10045.2.1"

    fun unmarshal(data: ByteArray): Result<EcdsaPublicKey> {
        return try {
            val decoder = Asn1Decoder.fromBytes(data)
                .getOrElse { return Err(it) }
            // AlgorithmIdentifier
            val decoder1 = decoder.nextSequence
                .getOrElse { return Err(it) }
            if (decoder1.size < 2) {
                return Err("ASN1 Ecdsa public key sequence must have parameters")
            }
            val oid = decoder1.nextObjectIdentifier
                .getOrElse { return Err(it) }
            if (oid != OID_PUBLIC_KEY_ECDSA) {
                return Err("Ecdsa public key expected")
            }
            val namedCurveOID1 = decoder1.nextObjectIdentifier
                .getOrElse { return Err(it) }
            val curve = Ecdsa.namedCurveFromOid(namedCurveOID1) ?: return Err("unknown elliptic curve: $namedCurveOID1")
            val publicKey1 = decoder.nextBitString
                .getOrElse { return Err(it) }
            val cp = Curve.unmarshal(curve, publicKey1)
                .getOrElse { return Err(it) }
            Ok(EcdsaPublicKey(curve, cp))
        } catch (e: ClassCastException) {
            Err("Could not unmarshal ASN1 PrivateKey: ${errorMessage(e)}")
        } catch (e: IOException) {
            Err("Could not unmarshal ASN1 PrivateKey: ${errorMessage(e)}")
        }
    }

    fun marshal(ecdsaPublicKey: EcdsaPublicKey): Result<ByteArray> {
        return try {
            val publicKeyBytes = Curve.marshal(ecdsaPublicKey.curve, ecdsaPublicKey.cp)
            val oid = Ecdsa.oidFromNamedCurve(ecdsaPublicKey.curve) ?: return Err("unknown elliptic curve")
            val sequence = Asn1Sequence()
            val list = Asn1Sequence()
            list.addItem(Asn1ObjectIdentifier(OID_PUBLIC_KEY_ECDSA))
            list.addItem(Asn1ObjectIdentifier(oid))
            sequence.addItem(list)
            sequence.addItem(Asn1BitString(publicKeyBytes))
            Ok(sequence.encode())
        } catch (e: ClassCastException) {
            Err("Could not marshal ASN1 PublicKey: ${errorMessage(e)}")
        } catch (e: IOException) {
            Err("Could not marshal ASN1 PublicKey: ${errorMessage(e)}")
        }
    }
}
