/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

import android.util.Log

interface Logger {
    fun isLoggable(priority: Int): Boolean
    fun log(priority: Int, throwable: Throwable? = null, message: String)
}

object Able {

    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ASSERT = 7

    var logger: Logger = AndroidLogger()

    inline fun assert(throwable: Throwable? = null, message: () -> String) {
        log(ASSERT, throwable, message)
    }

    inline fun error(throwable: Throwable? = null, message: () -> String) {
        log(ERROR, throwable, message)
    }

    inline fun warn(throwable: Throwable? = null, message: () -> String) {
        log(WARN, throwable, message)
    }

    inline fun info(throwable: Throwable? = null, message: () -> String) {
        log(INFO, throwable, message)
    }

    inline fun debug(throwable: Throwable? = null, message: () -> String) {
        log(DEBUG, throwable, message)
    }

    inline fun verbose(throwable: Throwable? = null, message: () -> String) {
        log(VERBOSE, throwable, message)
    }

    inline fun log(priority: Int, throwable: Throwable? = null, message: () -> String) {
        if (logger.isLoggable(priority)) {
            logger.log(priority, throwable, message())
        }
    }
}

class AndroidLogger : Logger {

    private val tag = "Able"

    override fun isLoggable(priority: Int): Boolean = Log.isLoggable(tag, priority)

    override fun log(priority: Int, throwable: Throwable?, message: String) {
        Log.println(priority, tag, message)
    }
}
