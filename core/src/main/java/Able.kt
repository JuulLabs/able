/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able

import com.juul.able.logger.AndroidLogger

object Able {

    @Volatile
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
            logger.log(priority, throwable, message.invoke())
        }
    }
}
