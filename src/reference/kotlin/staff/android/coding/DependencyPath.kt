package staff.android.coding

import java.util.ArrayDeque

/** Fewest dependency edges; ties use lexicographic order of complete node paths. */
fun shortestDependencyPath(
    input: Map<String, Set<String>>, from: String, to: String
): List<String>? {
    val graph = checkedGraph(input)
    require(from in graph && to in graph)
    val queue = ArrayDeque<String>()
    val parent = HashMap<String, String?>()
    parent[from] = null
    queue.addLast(from)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current == to) {
            val reversed = mutableListOf<String>()
            var step: String? = current
            while (step != null) {
                reversed += step
                step = parent.getValue(step)
            }
            return reversed.asReversed().toList()
        }
        for (next in graph.getValue(current)) {
            if (!parent.containsKey(next)) {
                parent[next] = current
                queue.addLast(next)
            }
        }
    }
    return null
}
