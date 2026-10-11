package staff.android.coding

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class DependencyPlanTest {
    @Test fun newlyReadySmallestNodePrecedesAnOlderLargerReadyNode() {
        val graph = linkedMapOf("z" to emptySet(), "b" to emptySet(), "a" to setOf("b"))
        assertEquals(DependencyPlan(listOf("b", "a", "z"), emptyList()), dependencyPlan(graph))
    }

    @Test fun inputIterationOrderDoesNotChooseThePlan() {
        val graph = linkedMapOf("shop" to setOf("images", "catalog"), "images" to setOf("disk"), "catalog" to setOf("disk"), "disk" to emptySet())
        val reverse = graph.entries.reversed().associate { it.key to it.value.reversed().toSet() }
        val expected = DependencyPlan(listOf("disk", "catalog", "images", "shop"), emptyList())
        assertEquals(expected, dependencyPlan(graph))
        assertEquals(expected, dependencyPlan(reverse))
    }

    @Test fun residualIncludesDownstreamDependentsThatAreNotCycleMembers() {
        val graph = mapOf("a" to setOf("b"), "b" to setOf("a"), "c" to setOf("a"), "d" to emptySet())
        assertEquals(DependencyPlan(listOf("d"), listOf("a", "b", "c")), dependencyPlan(graph))
        assertEquals(DependencyPlan(emptyList(), listOf("self")), dependencyPlan(mapOf("self" to setOf("self"))))
    }

    @Test fun emptyAndGloballyInvalidGraphsAreHandledBeforePlanning() {
        assertEquals(DependencyPlan(emptyList(), emptyList()), dependencyPlan(emptyMap()))
        assertThrows<IllegalArgumentException> { dependencyPlan(mapOf("okay" to emptySet(), "hidden" to setOf("missing"))) }
        for (invalid in listOf("", "Upper", "space here", "é")) {
            assertThrows<IllegalArgumentException> { dependencyPlan(mapOf(invalid to emptySet())) }
        }
    }

    @Test fun randomizedPlansMatchRepeatedFullScanOracleIncludingCycles() {
        val random = Random(3004)
        repeat(400) {
            val nodes = (0 until random.nextInt(0, 9)).map { "n-$it" }
            val graph = nodes.shuffled(random).associateWith { nodes.filter { random.nextInt(5) == 0 }.shuffled(random).toSet() }
            val emitted = mutableListOf<String>()
            val left = graph.keys.toMutableSet()
            while (true) {
                val next = left.filter { graph.getValue(it).all { pre -> pre in emitted } }.minOrNull() ?: break
                emitted += next; left.remove(next)
            }
            assertEquals(DependencyPlan(emitted, left.sorted()), dependencyPlan(graph), "graph=$graph")
        }
    }

    @Test fun resultDoesNotRetainMutableInputCollections() {
        val prerequisites = mutableSetOf("a")
        val graph = mutableMapOf("a" to mutableSetOf<String>(), "b" to prerequisites)
        val result = dependencyPlan(graph)
        prerequisites.clear(); graph.clear()
        assertEquals(DependencyPlan(listOf("a", "b"), emptyList()), result)
    }
}
