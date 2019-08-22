/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

class NoOpLogger : Logger {
    override fun isLoggable(priority: Int): Boolean = false
    override fun log(priority: Int, throwable: Throwable?, message: String) {
        error("No-op logger is not loggable.")
    }
}
