package staff.android.coding

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class DependencyPathTest {
    @Test fun shortestPathWinsOverFirstDepthFirstRoute() {
        val graph = mapOf("app" to setOf("a", "z"), "a" to setOf("b"), "b" to setOf("data"), "z" to setOf("data"), "data" to emptySet())
        assertEquals(listOf("app", "z", "data"), shortestDependencyPath(graph, "app", "data"))
    }

    @Test fun equalLengthPathsUseCompleteLexicographicPathOrder() {
        val graph = linkedMapOf("app" to linkedSetOf("z", "a"), "z" to setOf("b"), "a" to setOf("y"), "b" to setOf("data"), "y" to setOf("data"), "data" to emptySet())
        assertEquals(listOf("app", "a", "y", "data"), shortestDependencyPath(graph, "app", "data"))
        val reversed = graph.entries.reversed().associate { it.key to it.value.reversed().toSet() }
        assertEquals(shortestDependencyPath(graph, "app", "data"), shortestDependencyPath(reversed, "app", "data"))
    }

    @Test fun convergingDiamondKeepsFirstShortestPredecessor() {
        val graph = mapOf("app" to setOf("b", "a"), "a" to setOf("join"), "b" to setOf("join"), "join" to setOf("data"), "data" to emptySet())
        assertEquals(listOf("app", "a", "join", "data"), shortestDependencyPath(graph, "app", "data"))
    }

    @Test fun cyclesAndSelfEdgesDoNotPreventReachableWitness() {
        val graph = mapOf("app" to setOf("app", "a"), "a" to setOf("app", "target"), "target" to setOf("a"))
        assertEquals(listOf("app", "a", "target"), shortestDependencyPath(graph, "app", "target"))
    }

    @Test fun unreachableAndReverseDirectionReturnNull() {
        val graph = mapOf("app" to setOf("data"), "data" to emptySet(), "alone" to setOf("alone"))
        assertNull(shortestDependencyPath(graph, "data", "app"))
        assertNull(shortestDependencyPath(graph, "alone", "data"))
    }

    @Test fun identityWitnessStillRequiresGloballyValidGraph() {
        assertEquals(listOf("app"), shortestDependencyPath(mapOf("app" to emptySet()), "app", "app"))
        assertThrows<IllegalArgumentException> { shortestDependencyPath(mapOf("app" to emptySet(), "other" to setOf("missing")), "app", "app") }
        assertThrows<IllegalArgumentException> { shortestDependencyPath(mapOf("app" to emptySet(), "INVALID" to emptySet()), "app", "app") }
    }

    @Test fun bothEndpointsMustBeDeclared() {
        val graph = mapOf("app" to emptySet<String>())
        assertThrows<IllegalArgumentException> { shortestDependencyPath(graph, "missing", "app") }
        assertThrows<IllegalArgumentException> { shortestDependencyPath(graph, "app", "missing") }
        assertThrows<IllegalArgumentException> { shortestDependencyPath(emptyMap(), "app", "app") }
    }

    @Test fun anUnknownNodeInUnvisitedComponentIsStillRejected() {
        val graph = mapOf("app" to setOf("data"), "data" to emptySet(), "unvisited" to setOf("missing"))
        assertThrows<IllegalArgumentException> { shortestDependencyPath(graph, "app", "data") }
    }

    @Test fun returnedWitnessIsDetachedFromCallerMutation() {
        val deps = mutableSetOf("data")
        val graph = mutableMapOf("app" to deps, "data" to mutableSetOf<String>())
        val path = shortestDependencyPath(graph, "app", "data")
        deps.clear(); graph.clear()
        assertEquals(listOf("app", "data"), path)
    }

    @Test fun randomizedResultsMatchExhaustiveSimplePathOracle() {
        val random = Random(3003)
        repeat(160) {
            val nodes = (0 until random.nextInt(1, 7)).map { "n-$it" }
            val graph = nodes.shuffled(random).associateWith { nodes.filter { random.nextInt(3) == 0 }.shuffled(random).toSet() }
            val from = nodes.random(random); val to = nodes.random(random)
            val all = mutableListOf<List<String>>()
            fun visit(path: List<String>) {
                if (path.last() == to) { all += path; return }
                for (next in graph.getValue(path.last())) if (next !in path) visit(path + next)
            }
            visit(listOf(from))
            val expected = all.minWithOrNull(Comparator { a, b ->
                val length = a.size.compareTo(b.size)
                if (length != 0) length else {
                    var order = 0
                    for (index in a.indices) { order = a[index].compareTo(b[index]); if (order != 0) break }
                    order
                }
            })
            assertEquals(expected, shortestDependencyPath(graph, from, to), "$from->$to graph=$graph")
        }
    }
}
