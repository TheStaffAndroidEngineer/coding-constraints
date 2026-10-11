package staff.android.coding

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

fun main() = runBlocking {
    val mib = 1_048_576L
    val cache = WeightedLruCache<String, String>(10 * mib)
    cache.put("camera", "camera-photo", 4 * mib)
    cache.put("tea", "tea-photo", 2 * mib)
    cache.put("bag", "bag-photo", 4 * mib)
    check(cache.get("camera") == "camera-photo")
    println("cache before=${cache.snapshot()}")
    val admitted = cache.put("headphones", "headphones-photo", 5 * mib)
    check(admitted.evicted == listOf("tea", "bag"))
    println("cache admission=$admitted after=${cache.snapshot()}")
    val rejected = cache.put("camera", "oversize-photo", 11 * mib)
    check(!rejected.admitted && cache.get("camera") == "camera-photo")
    println("oversize replacement admitted=${rejected.admitted}; old value retained")

    val top = topProducts(listOf(ProductScore("tea", 8), ProductScore("camera", 9),
        ProductScore("bag", 9), ProductScore("headphones", Long.MIN_VALUE)), 2)
    check(top.map { it.id } == listOf("bag", "camera"))
    println("top2=$top")

    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<String>()
    val calls = AtomicInteger()
    val shared = BoundedSingleFlight<String, String>(this, 1, 2) {
        calls.incrementAndGet()
        entered.complete(Unit)
        release.await()
    }
    try {
        val one = async(start = CoroutineStart.UNDISPATCHED) { shared.get("camera-256-v1") }
        val two = async(start = CoroutineStart.UNDISPATCHED) { shared.get("camera-256-v1") }
        entered.await()
        val overloaded = runCatching { shared.get("tea-256-v1") }.exceptionOrNull()
        check(overloaded is AdmissionRejected)
        one.cancel()
        release.complete("decoded-photo")
        check(two.await() == "decoded-photo" && calls.get() == 1)
        one.join()
        println("single-flight calls=${calls.get()} cancelled waiter detached; second waiter received result")
    } finally { shared.close() }

    val graph = mapOf("app" to setOf("saved", "cart"), "saved" to setOf("cart-api"),
        "cart" to setOf("cart-api"), "cart-api" to emptySet())
    val plan = dependencyPlan(graph)
    check(plan.order == listOf("cart-api", "cart", "saved", "app"))
    println("dependency order=$plan")
    val path = shortestDependencyPath(graph, "app", "cart-api")
    check(path == listOf("app", "cart", "cart-api"))
    println("shortest witness=$path")
}
