// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

interface Order : Comparator<Entry> {
    override fun compare(a: Entry, b: Entry): Int

    companion object {
        fun less(orders: List<Order>, a: Entry, b: Entry): Boolean {
            for (cmp in orders) {
                when (cmp.compare(a, b)) {
                    -1 -> return true
                    +1 -> return false
                    0 -> Unit
                }
            }
            return a.key.toString() < b.key.toString()
        }

        fun compare(orders: List<Order>, a: Entry, b: Entry): Int {
            for (cmp in orders) {
                val c = cmp.compare(a, b)
                if (c != 0) return c
            }
            return a.key.toString().compareTo(b.key.toString())
        }

        fun sort(orders: List<Order>, entries: List<Entry>): List<Entry> {
            return entries.sortedWith { a, b -> compare(orders, a, b) }
        }

        fun compareQueryResult(orders: List<Order>, a: QueryResult, b: QueryResult): Int {
            if (a.entry != null && b.entry != null) {
                for (cmp in orders) {
                    val c = cmp.compare(a.entry, b.entry)
                    if (c != 0) return c
                }
                return a.entry.key.toString().compareTo(b.entry.key.toString())
            }
            return 0
        }

        fun sortQueryResult(orders: List<Order>, entries: List<QueryResult>): List<QueryResult> {
            return entries.sortedWith { a, b -> compareQueryResult(orders, a, b) }
        }
    }
}
