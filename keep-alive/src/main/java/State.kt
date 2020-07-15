/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.keepalive

sealed class State {

    object Connecting : State()

    object Connected : State()

    object Disconnecting : State()

    data class Disconnected(val cause: Throwable? = null) : State() {
        override fun toString() = super.toString()
    }

    data class Cancelled(val cause: Throwable?) : State() {
        override fun toString() = super.toString()
    }

    override fun toString(): String = javaClass.simpleName
}
