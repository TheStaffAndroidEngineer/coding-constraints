package staff.android.coding

import java.util.LinkedHashMap

data class CachePut<K>(val admitted: Boolean, val evicted: List<K>)
data class CacheSnapshot<K>(val keysLruToMru: List<K>, val used: Long)

/** Charge is a supplied stable accounting value, not measured process memory. */
class WeightedLruCache<K : Any, V : Any>(private val capacity: Long) {
    private data class Entry<V>(val value: V, val charge: Long)
    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)
    private var used = 0L

    init { require(capacity >= 0) }

    @Synchronized
    fun get(key: K): V? = entries[key]?.value

    @Synchronized
    fun put(key: K, value: V, charge: Long): CachePut<K> {
        require(charge > 0)
        if (charge > capacity) return CachePut(false, emptyList())
        entries.remove(key)?.let { used -= it.charge }
        val evicted = mutableListOf<K>()
        // Subtract before adding: used + charge could overflow a Long.
        while (used > capacity - charge) {
            val iterator = entries.entries.iterator()
            val eldest = iterator.next()
            used -= eldest.value.charge
            evicted += eldest.key
            iterator.remove()
        }
        entries[key] = Entry(value, charge)
        used += charge
        return CachePut(true, evicted)
    }

    @Synchronized
    fun snapshot(): CacheSnapshot<K> =
        CacheSnapshot(entries.keys.toList(), used)
}
