package staff.android.coding

import java.util.PriorityQueue

data class DependencyPlan(val order: List<String>, val blocked: List<String>)

internal fun checkedGraph(input: Map<String, Set<String>>): Map<String, List<String>> {
    require(input.keys.all(::validId))
    require(input.values.all { dependencies -> dependencies.all { it in input } }) {
        "Every dependency must name a declared node"
    }
    return input.mapValues { (_, dependencies) -> dependencies.sorted() }
}

/** Input maps a node to its prerequisites. Output is an order, not an execution schedule. */
fun dependencyPlan(input: Map<String, Set<String>>): DependencyPlan {
    val graph = checkedGraph(input)
    val remaining = graph.mapValues { it.value.size }.toMutableMap()
    val dependents = graph.keys.associateWith { mutableListOf<String>() }
    for ((node, prerequisites) in graph)
        for (prerequisite in prerequisites) dependents.getValue(prerequisite).add(node)
    val ready = PriorityQueue<String>()
    for ((node, count) in remaining) if (count == 0) ready.add(node)
    val order = mutableListOf<String>()
    while (ready.isNotEmpty()) {
        val next = ready.remove()
        order += next
        for (dependent in dependents.getValue(next)) {
            val count = remaining.getValue(dependent) - 1
            remaining[dependent] = count
            if (count == 0) ready.add(dependent)
        }
    }
    // Residual nodes include downstream dependents; not all are on a cycle.
    val blocked = remaining.filterValues { it > 0 }.keys.sorted()
    return DependencyPlan(order, blocked)
}
