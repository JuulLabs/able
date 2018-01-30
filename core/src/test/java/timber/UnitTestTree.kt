/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.timber

import timber.log.Timber

class UnitTestTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        log(message)
        if (t != null) {
            log(t.toString())
        }
    }

    private fun log(message: String) {
        println("[${Thread.currentThread().name}] $message")
    }
}
