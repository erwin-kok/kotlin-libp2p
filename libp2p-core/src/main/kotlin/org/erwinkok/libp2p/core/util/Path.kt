// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.util

object Path {
    fun clean(path: String): String {
        if (path.isEmpty()) {
            return "."
        }
        val rooted = path[0] == '/'
        val n = path.length

        val out = path.toCharArray()
        var w = 0
        var r = 0
        var dotdot = 0
        if (rooted) {
            out[w] = '/'
            w++
            r = 1
            dotdot = 1
        }
        while (r < n) {
            if (path[r] == '/') {
                // empty path element
                r++
            } else if (path[r] == '.' && (r + 1 == n || path[r + 1] == '/')) {
                // . element
                r++
            } else if (path[r] == '.' && path[r + 1] == '.' && (r + 2 == n || path[r + 2] == '/')) {
                // .. element: remove to last /
                r += 2
                if (w > dotdot) {
                    // can backtrack
                    w--
                    while (w > dotdot && out[w] != '/') {
                        w--
                    }
                } else if (!rooted) {
                    // cannot backtrack, but not rooted, so append .. element.
                    if (w > 0) {
                        out[w] = '/'
                        w++
                    }
                    out[w] = '.'
                    out[w + 1] = '.'
                    w += 2
                    dotdot = w
                }
            } else {
                // real path element.
                // add slash if needed
                if (rooted && w != 1 || !rooted && w != 0) {
                    out[w] = '/'
                    w++
                }
                // copy element
                while (r < n && path[r] != '/') {
                    out[w] = path[r]
                    w++
                    r++
                }
            }
        }
        // Turn empty string into "."
        if (w == 0) {
            return "."
        }
        return String(out, 0, w)
    }
}
