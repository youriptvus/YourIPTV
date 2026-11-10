package com.youriptv.app.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class WorkQueueSnapshotTest {
    /** A worker wins the race immediately after a collection reader observes its size. */
    private class ShrinkingQueue : ArrayBlockingQueue<Runnable>(4) {
        override val size: Int
            get() = super.size.also { if (it > 0) poll() }
    }

    @Test fun oldSingletonToListCrashesIfTheWorkerTakesTheTaskAfterSizeIsRead() {
        val queue = ShrinkingQueue().apply { add(Runnable {}) }
        assertThrows(NoSuchElementException::class.java) { queue.toList() }
    }

    @Test fun snapshotDoesNotSplitTheSizeReadFromCopyingTheTasks() {
        val task = Runnable {}
        val queue = ShrinkingQueue().apply { add(task) }
        val snapshot = WorkQueueSnapshot.take(queue)
        assertEquals(1, snapshot.size)
        assertSame(task, snapshot.single())
        assertSame(task, queue.poll())
        assertTrue(WorkQueueSnapshot.take(queue).isEmpty())
    }

    @Test fun snapshotIsDetachedAndDoesNotUseTheQueuesIterator() {
        val first = Runnable {}
        val second = Runnable {}
        val queue = object : ArrayBlockingQueue<Runnable>(4) {
            override fun iterator(): MutableIterator<Runnable> = throw AssertionError("A queue snapshot must not iterate the live queue")
        }.apply { add(first); add(second) }
        val snapshot = WorkQueueSnapshot.take(queue)
        queue.clear()
        queue.add(Runnable {})
        assertArrayEquals(arrayOf<Any>(first, second), snapshot)
    }

    @Test fun boundedSnapshotsRemainValidWhileWorkersContinuouslyAddAndTakeTasks() {
        val capacity = 48
        val queue = ArrayBlockingQueue<Runnable>(capacity)
        val running = AtomicBoolean(true)
        val started = CountDownLatch(2)
        val failed = AtomicReference<Throwable?>()
        val offered = AtomicInteger()
        val consumed = AtomicInteger()
        val task = Runnable {}
        fun worker(body: () -> Unit) = Thread {
            started.countDown()
            try { while (running.get()) body() }
            catch (error: Throwable) { failed.compareAndSet(null, error) }
        }.apply { isDaemon = true; start() }
        val producer = worker { if (queue.offer(task)) offered.incrementAndGet() }
        val consumer = worker { if (queue.poll() != null) consumed.incrementAndGet() }
        try {
            assertTrue("Workers did not start", started.await(5, TimeUnit.SECONDS))
            repeat(20_000) {
                val snapshot = WorkQueueSnapshot.take(queue)
                assertTrue(snapshot.size <= capacity)
                assertTrue(snapshot.all { it === task })
                if (it % 1000 == 0) Thread.yield()
            }
        } finally {
            running.set(false)
            producer.join(5_000)
            consumer.join(5_000)
        }
        assertFalse("Producer did not stop", producer.isAlive)
        assertFalse("Consumer did not stop", consumer.isAlive)
        failed.get()?.let { throw AssertionError("Concurrent queue operation failed", it) }
        assertTrue("No tasks were offered", offered.get() > 0)
        assertTrue("No tasks were consumed", consumed.get() > 0)
    }
}
