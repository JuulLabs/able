/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.logger

import com.juul.able.Logger

class NoOpLogger : Logger {
    override fun isLoggable(priority: Int): Boolean = false
    override fun log(priority: Int, throwable: Throwable?, message: String) {
        error("No-op logger is not loggable.")
    }
}
