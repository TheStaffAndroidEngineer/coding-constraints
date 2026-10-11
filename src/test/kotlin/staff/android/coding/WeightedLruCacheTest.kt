package staff.android.coding

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.random.Random

class WeightedLruCacheTest {
    @Test fun cameraReadThenHeadphonesNeedsTwoEvictions() {
        val cache = WeightedLruCache<String, String>(10)
        cache.put("camera", "c", 4); cache.put("tea", "t", 2); cache.put("bag", "b", 4)
        assertEquals("c", cache.get("camera"))
        assertEquals(CacheSnapshot(listOf("tea", "bag", "camera"), 10), cache.snapshot())
        assertEquals(CachePut(true, listOf("tea", "bag")), cache.put("headphones", "h", 5))
        assertEquals(CacheSnapshot(listOf("camera", "headphones"), 9), cache.snapshot())
        assertNull(cache.get("tea")); assertNull(cache.get("bag"))
    }

    @Test fun oversizedReplacementPreservesValuesAndRecency() {
        val cache = WeightedLruCache<String, String>(10)
        cache.put("camera", "old", 4); cache.put("headphones", "h", 5)
        val before = cache.snapshot()
        assertEquals(CachePut<String>(false, emptyList()), cache.put("camera", "new", 11))
        assertEquals(before, cache.snapshot())
        assertEquals("old", cache.get("camera"))
        assertEquals("h", cache.get("headphones"))
    }

    @Test fun replacementSubtractsOldChargeAndCountsOnlyOtherEvictions() {
        val cache = WeightedLruCache<String, Int>(10)
        cache.put("a", 1, 4); cache.put("b", 2, 6)
        assertEquals(CachePut<String>(true, emptyList()), cache.put("a", 3, 2))
        assertEquals(CacheSnapshot(listOf("b", "a"), 8), cache.snapshot())
        assertEquals(CachePut(true, listOf("b")), cache.put("a", 4, 10))
        assertEquals(CacheSnapshot(listOf("a"), 10), cache.snapshot())
        assertEquals(4, cache.get("a"))
    }

    @Test fun zeroCapacityAndInvalidChargesDoNotMutate() {
        assertThrows<IllegalArgumentException> { WeightedLruCache<String, Int>(-1) }
        val zero = WeightedLruCache<String, Int>(0)
        assertFalse(zero.put("a", 1, 1).admitted)
        assertEquals(CacheSnapshot<String>(emptyList(), 0), zero.snapshot())
        val cache = WeightedLruCache<String, Int>(4); cache.put("a", 1, 4)
        for (bad in listOf(0L, -1L, Long.MIN_VALUE)) {
            val before = cache.snapshot()
            assertThrows<IllegalArgumentException> { cache.put("a", 2, bad) }
            assertEquals(before, cache.snapshot())
        }
    }

    @Test fun fullLongCapacityAvoidsOverflowingIntermediateSum() {
        val cache = WeightedLruCache<String, Int>(Long.MAX_VALUE)
        cache.put("a", 1, Long.MAX_VALUE - 1); cache.put("b", 2, 1)
        assertEquals(CachePut(true, listOf("a")), cache.put("c", 3, Long.MAX_VALUE - 1))
        assertEquals(CacheSnapshot(listOf("b", "c"), Long.MAX_VALUE), cache.snapshot())
        assertEquals(CachePut(true, listOf("b")), cache.put("c", 4, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, cache.snapshot().used)
    }

    @Test fun missesAndSnapshotsDoNotTouchAndReturnedListsAreDetached() {
        val cache = WeightedLruCache<String, Int>(2)
        cache.put("a", 1, 1); cache.put("b", 2, 1)
        val snapshot = cache.snapshot()
        assertNull(cache.get("missing")); assertEquals(snapshot, cache.snapshot())
        cache.get("a")
        assertEquals(listOf("a", "b"), snapshot.keysLruToMru)
        assertEquals(listOf("b", "a"), cache.snapshot().keysLruToMru)
        val result = cache.put("c", 3, 2)
        cache.put("d", 4, 2)
        assertEquals(listOf("b", "a"), result.evicted)
    }

    private data class Item(val key: Int, val value: Int, val charge: Long)
    @Test fun randomizedHistoriesMatchIndependentLinearOracle() {
        val random = Random(3001)
        for (capacity in listOf(0L, 1L, 7L, 25L)) {
            val cache = WeightedLruCache<Int, Int>(capacity)
            val items = mutableListOf<Item>()
            repeat(1000) { step ->
                val key = random.nextInt(8)
                if (random.nextInt(3) == 0) {
                    val found = items.firstOrNull { it.key == key }
                    if (found != null) { items.remove(found); items.add(found) }
                    assertEquals(found?.value, cache.get(key))
                } else {
                    val charge = random.nextLong(1, 31)
                    val evicted = mutableListOf<Int>()
                    val admitted = charge <= capacity
                    if (admitted) {
                        items.removeAll { it.key == key }
                        while (items.sumOf { it.charge } + charge > capacity) evicted.add(items.removeAt(0).key)
                        items.add(Item(key, step, charge))
                    }
                    assertEquals(CachePut(admitted, evicted), cache.put(key, step, charge))
                }
                assertEquals(CacheSnapshot(items.map { it.key }, items.sumOf { it.charge }), cache.snapshot())
            }
        }
    }

    @Test fun concurrentInsertionsPreserveOneBoundedState() {
        val cache = WeightedLruCache<Int, Int>(17)
        val pool = Executors.newFixedThreadPool(6)
        try {
            val results = pool.invokeAll((0 until 120).map { n -> Callable { cache.put(n, n, 1) } })
            assertTrue(results.all { it.get().admitted })
            val state = cache.snapshot()
            assertEquals(17L, state.used); assertEquals(17, state.keysLruToMru.toSet().size)
            for (key in state.keysLruToMru) assertEquals(key, cache.get(key))
        } finally { pool.shutdownNow() }
    }
}
