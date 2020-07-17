/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.keepalive

sealed class State {

    object Connecting : State()

    object Connected : State()

    object Disconnecting : State()

    data class Disconnected(val cause: Throwable? = null) : State() {
        override fun toString() =
            if (cause != null) "Disconnected(cause=$cause)" else "Disconnected"
    }

    data class Cancelled(val cause: Throwable?) : State() {
        override fun toString() =
            if (cause != null) "Cancelled(cause=$cause)" else "Cancelled"
    }

    override fun toString(): String = javaClass.simpleName
}
