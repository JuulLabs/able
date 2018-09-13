/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.logger.timber

import android.os.Build
import com.juul.able.experimental.Logger
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
    if (Build.VERSION.SDK_INT < 24 && length > MAX_TAG_LENGTH) {
        substring(0, MAX_TAG_LENGTH)
    } else {
        this
    }
