/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

import java.util.ArrayDeque
import kotlin.concurrent.thread

typealias BinderThreadOperation = () -> Unit

private const val THREAD_NAME_PREFIX = "MockBinder"

/**
 * Emulates multiple Android Binder threads and invokes [BinderThreadOperation]s serially that were
 * enqueued via [enqueue] method:
 *
 * ```
 *                .--------------------------------------------------------.
 *                | FakeBinderThreadHandler.enqueue(BinderThreadOperation) |
 *                '--------------------------------------------------------'
 *                                            |
 *                                            v
 *                            .------------------------------.
 *                            | Queue<BinderThreadOperation> |
 *                            '------------------------------'
 *                                   |        |       |
 *                                   |        |       |
 *  .~~~~~ synchronized ~~~~~~~~~~~~ | ~~~~~~ | ~~~~~ | ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~.
 *  #                                |        |       |                                         #
 *  #                                |        |       |                                         #
 *  #             .------------------'        |       '-------------------.                     #
 *  #             |                           |                           |                     #
 *  #             v                           v                           v                     #
 *  #  .----------------------.    .----------------------.    .----------------------.         #
 *  #  | Thread{FakeBinder#1} |    | Thread{FakeBinder#2} |    | Thread{FakeBinder#3} |   ...   #
 *  #  '----------------------'    '----------------------'    '----------------------'         #
 *  #             |                           |                           |                     #
 *  #             '------------------.        |       .-------------------'                     #
 *  #                                |        |       |                                         #
 *  #                                v        v       v                                         #
 *  #                        .--------------------------------.                                 #
 *  #                        | BinderThreadOperation.invoke() |                                 #
 *  #                        '--------------------------------'                                 #
 *  #                                                                                           #
 *  '~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~'
 * ```
 */
class FakeBinderThreadHandler(private val binderThreadCount: Int) {

    private val operationQueue = ArrayDeque<BinderThreadOperation>()
    private var threads: List<Thread>? = null

    @Synchronized
    fun start() {
        check(threads == null) { "$this already started" }
        threads = (1..binderThreadCount).map { i -> fakeBinderThread("$THREAD_NAME_PREFIX$i") }
    }

    @Synchronized
    fun stop() {
        threads?.shutdown()
        threads = null
    }

    fun enqueue(operation: BinderThreadOperation) {
        synchronized(operationQueue) {
            operationQueue += operation
        }
    }

    private fun fakeBinderThread(name: String): Thread {
        return thread(name = name) {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    Thread.yield()
                    synchronized(operationQueue) {
                        operationQueue.poll()?.invoke()
                    }
                }
            } catch (e: InterruptedException) {
                // We've been asked to shutdown, no need to log exception.
            }
        }
    }
}

private fun List<Thread>.shutdown() = forEach {
    it.interrupt()
    it.join()
}
