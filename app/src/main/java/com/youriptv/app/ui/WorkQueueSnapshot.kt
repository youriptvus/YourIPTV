package com.youriptv.app.ui

import java.util.concurrent.ArrayBlockingQueue

/**
 * ArrayBlockingQueue.toArray takes its own queue lock while copying the current contents.
 * Kotlin Collection.toList may read size == 1 and then call iterator.next after a worker
 * has removed that sole task. Do not substitute an iterator-based collection conversion.
 */
internal object WorkQueueSnapshot {
    fun take(queue: ArrayBlockingQueue<Runnable>): Array<Any> = queue.toArray()
}
