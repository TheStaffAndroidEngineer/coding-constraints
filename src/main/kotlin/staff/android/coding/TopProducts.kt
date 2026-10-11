package staff.android.coding

import java.util.PriorityQueue

data class ProductScore(val id: String, val score: Long)

internal fun validId(id: String): Boolean =
    id.isNotEmpty() && id.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }

/** Exact ASCII product IDs; score ties are settled by ascending ID. */
fun topProducts(products: List<ProductScore>, k: Int): List<ProductScore> {
    require(k >= 0)
    val ids = HashSet<String>()
    for (product in products) {
        require(validId(product.id))
        require(ids.add(product.id)) { "Duplicate product ID: ${product.id}" }
    }
    if (k == 0) return emptyList()
    val bestFirst = compareByDescending<ProductScore> { it.score }
        .thenBy { it.id }
    val heap = PriorityQueue(bestFirst.reversed())
    for (product in products) {
        if (heap.size < k) heap.add(product)
        else if (bestFirst.compare(product, heap.peek()) < 0) {
            heap.remove()
            heap.add(product)
        }
    }
    return heap.toList().sortedWith(bestFirst)
}
