package staff.android.coding

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class TopProductsTest {
    @Test fun highestScoresWinWithAscendingIdsAtTies() {
        val input = listOf(ProductScore("tea", 4), ProductScore("camera", 7), ProductScore("bag", 7), ProductScore("watch", -1))
        assertEquals(listOf(ProductScore("bag", 7), ProductScore("camera", 7)), topProducts(input, 2))
        assertEquals(listOf(ProductScore("bag", 7)), topProducts(input, 1))
    }
    @Test fun numericExtremesUseComparisonNotSubtraction() {
        val input = listOf(ProductScore("a", Long.MIN_VALUE), ProductScore("z", Long.MAX_VALUE), ProductScore("b", 0))
        assertEquals(listOf("z", "b", "a"), topProducts(input, 8).map { it.id })
    }
    @Test fun emptyZeroAndOversizedKHaveDefinedResults() {
        assertTrue(topProducts(emptyList(), Int.MAX_VALUE).isEmpty())
        assertTrue(topProducts(listOf(ProductScore("a", 1)), 0).isEmpty())
        assertEquals(listOf(ProductScore("a", 1)), topProducts(listOf(ProductScore("a", 1)), Int.MAX_VALUE))
        assertThrows<IllegalArgumentException> { topProducts(emptyList(), -1) }
    }
    @Test fun fullInputValidationAppliesEvenWhenKIsZero() {
        for (bad in listOf("", "A", "a b", "é", "a_", "🙂"))
            assertThrows<IllegalArgumentException> { topProducts(listOf(ProductScore("valid", 2), ProductScore(bad, 1)), 0) }
        assertThrows<IllegalArgumentException> { topProducts(listOf(ProductScore("same", 1), ProductScore("same", 9)), 0) }
        assertEquals(listOf(ProductScore("a-0", 1)), topProducts(listOf(ProductScore("a-0", 1)), 1))
    }
    @Test fun resultIsSortedAndDetachedFromInputOrderOrMutation() {
        val input = mutableListOf(ProductScore("d", 4), ProductScore("a", 1), ProductScore("c", 3), ProductScore("b", 2))
        val original = input.toList(); val result = topProducts(input, 3)
        assertEquals(original, input)
        input.clear()
        assertEquals(listOf("d", "c", "b"), result.map { it.id })
    }
    @Test fun randomizedSelectionMatchesFullSortOracle() {
        val random = Random(3002)
        repeat(500) {
            val input = (0 until random.nextInt(60)).map { ProductScore("p-$it", random.nextLong(-5, 6)) }.shuffled(random)
            val k = random.nextInt(70)
            val oracle = input.sortedWith(compareByDescending<ProductScore> { it.score }.thenBy { it.id }).take(k)
            assertEquals(oracle, topProducts(input, k))
        }
    }
}
