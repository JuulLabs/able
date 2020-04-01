/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.logger.timber

private const val CALL_STACK_INDEX = 2
private val ANONYMOUS_CLASS_REGEX = "(\\$\\d+)+$".toRegex()

/** Inspired by [DebugTree](https://git.io/fACGF) implementation. */
internal class AndroidTagGenerator {

    fun generate(): String {
        // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The unit tests will pass
        // because they are run on the JVM, but on Android the elements are different.
        val stackTrace = Throwable().stackTrace
        check(stackTrace.size > CALL_STACK_INDEX) {
            "Synthetic stacktrace didn't have enough elements: are you using proguard?"
        }
        return stackTrace[CALL_STACK_INDEX].tag()
    }

    private fun StackTraceElement.tag() =
        ANONYMOUS_CLASS_REGEX.replace(className) { _ -> "" }.substringAfterLast('.')
}
