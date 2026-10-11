package staff.android.coding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class AdmissionRejected(message: String) : IllegalStateException(message)

/** Shares only unfinished work. No result cache or waiting queue is implied. */
class BoundedSingleFlight<K : Any, V>(
    parentScope: CoroutineScope,
    private val maxKeys: Int,
    private val maxWaitersPerKey: Int,
    private val load: suspend (K) -> V
) : AutoCloseable {
    init {
        require(maxKeys > 0)
        require(maxWaitersPerKey > 0)
    }

    private class Flight<V>(val work: Deferred<V>, var waiters: Int = 0)
    private val owner = SupervisorJob(requireNotNull(parentScope.coroutineContext[Job]) {
        "Parent scope must provide a Job"
    })
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)
    private val lock = Any()
    private val flights = HashMap<K, Flight<V>>()

    suspend fun get(key: K): V {
        currentCoroutineContext().ensureActive()
        val flight = synchronized(lock) {
            owner.ensureActive()
            val current = flights[key]
            val selected = if (current != null) current else {
                if (flights.size >= maxKeys)
                    throw AdmissionRejected("Distinct request limit reached")
                val created = Flight(scope.async(start = CoroutineStart.LAZY) { load(key) })
                flights[key] = created
                created.work.invokeOnCompletion {
                    synchronized(lock) {
                        if (flights[key] === created) flights.remove(key)
                    }
                }
                created
            }
            if (selected.waiters >= maxWaitersPerKey)
                throw AdmissionRejected("Waiter limit reached")
            selected.waiters++
            selected
        }
        // The entry exists before user work can run; no suspension holds the monitor.
        flight.work.start()
        return try {
            flight.work.await()
        } finally {
            synchronized(lock) { flight.waiters-- }
        }
    }

    fun activeKeys(): Int = synchronized(lock) { flights.size }
    override fun close() { owner.cancel() }
}
