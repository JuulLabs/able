/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.logger.timber

import android.os.Build
import com.juul.able.logger.Logger
import timber.log.Timber

private const val MAX_TAG_LENGTH = 23

class TimberLogger : Logger {

    private val tag = AndroidTagGenerator()

    override fun isLoggable(priority: Int) = Timber.treeCount() > 0

    override fun log(priority: Int, throwable: Throwable?, message: String) {
        Timber
            .tag(tag.generate().asSafeTag())
            .log(priority, throwable, message)
    }
}

// Tag length limit was removed in API 24.
private fun String.asSafeTag(): String =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N && length > MAX_TAG_LENGTH) {
        substring(0, MAX_TAG_LENGTH)
    } else {
        this
    }
