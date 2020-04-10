/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.logger

/*
 * Log values chosen to match Android's:
 * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/util/Log.java;l=71-99?q=Log.java&ss=android
 */
const val VERBOSE = 2
const val DEBUG = 3
const val INFO = 4
const val WARN = 5
const val ERROR = 6
const val ASSERT = 7

interface Logger {
    fun isLoggable(priority: Int): Boolean
    fun log(priority: Int, throwable: Throwable? = null, message: String)
}
