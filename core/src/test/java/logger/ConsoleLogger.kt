/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.test.logger

import com.juul.able.logger.ASSERT
import com.juul.able.logger.ERROR
import com.juul.able.logger.INFO
import com.juul.able.logger.Logger
import com.juul.able.logger.VERBOSE
import com.juul.able.logger.WARN

private const val TAG = "Able"

class ConsoleLogger(
    private val logLevel: Int = VERBOSE,
    private inline val logHandler: (String) -> Unit = { println(it) }
) : Logger {

    private val labels = charArrayOf('V', 'D', 'I', 'W', 'E', 'A')

    /**
     * Check if the [priority] of the message to be logged is at least the [logLevel] of this
     * [Logger]. In other words, all logs of lower [priority] than this [Logger]'s [logLevel] will
     * not be logged.
     *
     * For example, if the [ConsoleLogger]'s [logLevel] is set to [INFO] (`4`), only logs of
     * [priority] [INFO] (or higher: [WARN], [ERROR], [ASSERT]) will be logged.
     */
    override fun isLoggable(priority: Int) = priority >= logLevel

    override fun log(priority: Int, throwable: Throwable?, message: String) {
        // labels[0] = 'V', labels[1] = 'D', labels[2] = 'I', ...
        //    VERBOSE = 2 ,      DEBUG = 3 ,       INFO = 4 , ...
        //
        // We coerce requested priority within `priority` range (VERBOSE to ASSERT) then offset down
        // to `labels` who's index starts at `0`.
        val index = priority.coerceIn(VERBOSE, ASSERT) - VERBOSE

        val label = labels[index]
        val error = if (throwable != null) "${throwable.message}" else ""
        logHandler.invoke("$label/$TAG: $error$message")
    }
}
