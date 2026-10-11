package staff.android.coding

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BoundedSingleFlightTest {
    @Test fun sameKeySharesOneProducerAndDistinctKeyLimitStillAllowsJoining() = runTest {
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        BoundedSingleFlight(this, 1, 3) { key: String ->
            calls += key
            release.await()
            "value:$key"
        }.use { flight ->
            val a = async { flight.get("camera") }
            val b = async { flight.get("camera") }
            runCurrent()
            assertEquals(listOf("camera"), calls)
            assertEquals(1, flight.activeKeys())
            assertTrue(runCatching { flight.get("tea") }.exceptionOrNull() is AdmissionRejected)
            val c = async { flight.get("camera") }
            runCurrent()
            release.complete(Unit)
            assertEquals(listOf("value:camera", "value:camera", "value:camera"), listOf(a.await(), b.await(), c.await()))
            assertEquals(0, flight.activeKeys())
        }
    }

    @Test fun waiterLimitRejectsWithoutLoadingAndCancellationReleasesWaiterSlot() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        BoundedSingleFlight(this, 2, 1) { _: String -> calls++; release.await(); 7 }.use { flight ->
            val first = async { flight.get("camera") }
            runCurrent()
            assertTrue(runCatching { flight.get("camera") }.exceptionOrNull() is AdmissionRejected)
            first.cancelAndJoin()
            assertEquals(1, flight.activeKeys())
            val replacement = async { flight.get("camera") }
            runCurrent()
            assertEquals(1, calls)
            release.complete(Unit)
            assertEquals(7, replacement.await())
        }
    }

    @Test fun allWaitersMayLeaveWhileProducerOccupiesItsKeyUntilCompletion() = runTest {
        val release = CompletableDeferred<Unit>()
        var finished = false
        BoundedSingleFlight(this, 1, 2) { _: String -> release.await(); finished = true; 9 }.use { flight ->
            val a = async { flight.get("camera") }
            val b = async { flight.get("camera") }
            runCurrent()
            a.cancelAndJoin(); b.cancelAndJoin()
            assertFalse(finished)
            assertEquals(1, flight.activeKeys())
            assertTrue(runCatching { flight.get("bag") }.exceptionOrNull() is AdmissionRejected)
            release.complete(Unit)
            runCurrent()
            assertTrue(finished)
            assertEquals(0, flight.activeKeys())
            assertEquals(9, flight.get("bag"))
        }
    }

    @Test fun producerFailureIsSharedButDoesNotCancelSiblingAndNextCallRetries() = runTest {
        val releaseBad = CompletableDeferred<Unit>()
        val releaseGood = CompletableDeferred<Unit>()
        val counts = mutableMapOf<String, Int>()
        BoundedSingleFlight(this, 2, 2) { key: String ->
            counts[key] = counts.getOrDefault(key, 0) + 1
            if (key == "bad" && counts[key] == 1) {
                releaseBad.await()
                throw IllegalArgumentException("fixture failure")
            }
            if (key == "good") releaseGood.await()
            key
        }.use { flight ->
            val a = async { runCatching { flight.get("bad") } }
            val b = async { runCatching { flight.get("bad") } }
            val good = async { flight.get("good") }
            runCurrent()
            releaseBad.complete(Unit)
            for (result in listOf(a.await(), b.await())) {
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
                assertEquals("fixture failure", result.exceptionOrNull()?.message)
            }
            assertFalse(good.isCompleted)
            assertEquals(1, flight.activeKeys())
            assertEquals("bad", flight.get("bad"))
            assertEquals(2, counts["bad"])
            releaseGood.complete(Unit)
            assertEquals("good", good.await())
        }
    }

    @Test fun immediateProducerSeesRegisteredEntryAndCompletedValueIsNotCached() = runTest {
        var calls = 0
        lateinit var flight: BoundedSingleFlight<String, Int>
        flight = BoundedSingleFlight(this, 1, 1) {
            assertEquals(1, flight.activeKeys())
            ++calls
        }
        flight.use {
            assertEquals(1, it.get("camera"))
            assertEquals(0, it.activeKeys())
            assertEquals(2, it.get("camera"))
            assertEquals(0, it.activeKeys())
        }
    }

    @Test fun closeCancelsOwnedWorkAndRejectsNewCallsWithoutCancelingParent() = runTest {
        val started = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val flight = BoundedSingleFlight(this, 1, 1) { _: String ->
            started.complete(Unit)
            try { awaitCancellation() } finally { cleaned.complete(Unit) }
        }
        val waiter = async { runCatching { flight.get("camera") } }
        started.await()
        flight.close()
        assertTrue(waiter.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
        cleaned.await()
        assertEquals(0, flight.activeKeys())
        assertTrue(coroutineContext[Job]!!.isActive)
        assertTrue(runCatching { flight.get("camera") }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        flight.close()
    }

    @Test fun parentCancellationCancelsTheOwnedProducer() = runTest {
        val parent = SupervisorJob(coroutineContext[Job])
        val ownedScope = CoroutineScope(coroutineContext + parent)
        val entered = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        BoundedSingleFlight(ownedScope, 1, 1) { _: String ->
            entered.complete(Unit)
            try { awaitCancellation() } finally { cleaned.complete(Unit) }
        }.use { flight ->
            val waiter = async { runCatching { flight.get("camera") } }
            entered.await()
            parent.cancel()
            assertTrue(waiter.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
            cleaned.await()
            assertEquals(0, flight.activeKeys())
        }
        parent.join()
    }

    @Test fun canceledCallerNeverCreatesWork() = runTest {
        var calls = 0
        BoundedSingleFlight(this, 1, 1) { _: String -> ++calls }.use { flight ->
            val caller = async { flight.get("camera") }
            caller.cancelAndJoin()
            runCurrent()
            assertEquals(0, calls)
            assertEquals(0, flight.activeKeys())
        }
    }

    @Test fun invalidLimitsAndMissingParentJobAreRejected() = runTest {
        for (limits in listOf(0 to 1, 1 to 0, -1 to 1, 1 to -1)) {
            assertThrows<IllegalArgumentException> { BoundedSingleFlight(this, limits.first, limits.second) { _: String -> 1 } }
        }
        val noJob = object : CoroutineScope { override val coroutineContext = EmptyCoroutineContext }
        assertThrows<IllegalArgumentException> { BoundedSingleFlight(noJob, 1, 1) { _: String -> 1 } }
    }
}
