// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

class ProjP2 {
    val x: Element
    val y: Element
    val z: Element

    private constructor(x: Element, y: Element, z: Element) {
        this.x = x
        this.y = y
        this.z = z
    }

    constructor(p: ProjP1xP1) : this(p.x * p.t, p.y * p.z, p.z * p.t)
    constructor(p: Point) : this(p.x, p.y, p.z)

    fun timesTwo(): ProjP1xP1 {
        val xx = x.square()
        val yy = y.square()
        val zz = z.square()
        return ProjP1xP1((x + y).square() - (yy + xx), yy + xx, yy - xx, zz + zz - (yy - xx))
    }

    companion object {
        val Zero = ProjP2(Element.Zero, Element.One, Element.One)
    }
}
