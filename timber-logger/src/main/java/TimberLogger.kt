/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.logger.timber

import android.os.Build
import com.juul.able.logger.Logger
import timber.log.Timber

private const val MAX_TAG_LENGTH = 23
private val ANONYMOUS_CLASS_REGEX = "(\\$\\d+)+$".toRegex()

class TimberLogger : Logger {

    override fun isLoggable(priority: Int) = Timber.treeCount() > 0

    override fun log(priority: Int, throwable: Throwable?, message: String) {
        Timber
            .tag(generateTag())
            .log(priority, throwable, message)
    }

    private fun generateTag(): String {
        val stackTraceElement = Throwable().stackTrace
            .first { it.className != TimberLogger::class.java.name }

        val tag = ANONYMOUS_CLASS_REGEX
            .replace(stackTraceElement.className.substringAfterLast('.'), "")

        // Tag length limit was removed in API 24.
        return if (tag.length <= MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tag
        } else {
            tag.substring(0, MAX_TAG_LENGTH)
        }
    }
}
