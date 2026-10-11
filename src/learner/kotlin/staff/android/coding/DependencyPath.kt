package staff.android.coding

/** Deliberately incomplete independent exercise; preserve your work separately. */
fun shortestDependencyPath(
    input: Map<String, Set<String>>, from: String, to: String
): List<String>? {
    val graph = checkedGraph(input)
    require(from in graph && to in graph)
    if (from == to) return listOf(from)
    // Implement cycle-safe breadth-first search and predecessor reconstruction.
    return null
}
