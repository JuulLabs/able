/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.keepalive

import com.juul.able.gatt.Gatt

sealed class Event {

    /** Triggered upon a connection being successfully established. */
    data class Connected(val gatt: Gatt) : Event()

    /**
     * Triggered either immediately after an established connection has dropped or after a failed
     * connection attempt.
     *
     * The [connectionAttempt] property represents which connection attempt iteration over the
     * lifespan of the [KeepAliveGatt]. The value begins at 1 and increase by 1 for each iteration.
     *
     * @param wasConnected is `true` if event follows an established connection, or `false` if previous connection attempt failed.
     * @param connectionAttempt is the number of connection attempts since creation of [KeepAliveGatt].
     */
    data class Disconnected(
        val wasConnected: Boolean,
        val connectionAttempt: Int
    ) : Event()

    /**
     * Triggered when the connection request was rejected by the operating system (e.g. bluetooth
     * hardware unavailable). [KeepAliveGatt] will not attempt to reconnect until
     * [connect][KeepAliveGatt.connect] is called again.
     */
    data class Rejected(val cause: Throwable) : Event()
}

suspend fun Event.onConnected(action: suspend Gatt.() -> Unit) {
    if (this is Event.Connected) action.invoke(gatt)
}

suspend fun Event.onDisconnected(action: suspend (Event.Disconnected) -> Unit) {
    if (this is Event.Disconnected) action.invoke(this)
}

suspend fun Event.onRejected(action: suspend (Event.Rejected) -> Unit) {
    if (this is Event.Rejected) action.invoke(this)
}
