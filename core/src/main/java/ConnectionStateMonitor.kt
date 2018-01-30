/*
 * Copyright 2018 JUUL Labs, Inc.
 */

@file:Suppress("RedundantUnitReturnType")

package com.juul.able.experimental

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import com.github.ajalt.timberkt.Timber
import com.juul.able.experimental.messenger.OnConnectionStateChange
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.sync.Mutex
import kotlinx.coroutines.experimental.sync.withLock
import java.io.Closeable

class ConnectionStateMonitor(private val gatt: Gatt) : Closeable {

    private val connectionStateMutex = Mutex()
    private var connectionStateSubscription: ReceiveChannel<OnConnectionStateChange>? = null

    suspend fun suspendUntilConnectionState(state: GattState): Boolean {
        Timber.d { "Suspending until ${state.asGattStateString()}" }

        gatt.onConnectionStateChange.openSubscription().also { subscription ->
            if (state == BluetoothProfile.STATE_DISCONNECTED && subscription.isEmpty) {
                // When disconnecting, the channel may be empty if we've never connected before, so
                // we shouldn't wait.
                Timber.i { "suspendUntilConnectionState → subscription.isEmpty, aborting" }
                subscription.cancel()
                return true
            } else {
                connectionStateMutex.withLock {
                    // If another `suspendUntilConnectionState` is in progress then we abort it.
                    connectionStateSubscription?.cancel()
                    connectionStateSubscription = subscription
                }

                subscription.consumeEach { (status, newState) ->
                    Timber.v {
                        val statusString = status.asGattConnectionStatusString()
                        val stateString = newState.asGattStateString()
                        "status = $statusString, newState = $stateString"
                    }

                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Timber.i { "Received ${status.asGattConnectionStatusString()}, giving up." }
                        return false
                    }

                    if (state == newState) {
                        Timber.i { "Reached ${state.asGattStateString()}" }
                        return true
                    }
                }
            }
        }

        Timber.d { "suspendUntilConnectionState → Aborted." }
        return false
    }

    override fun close(): Unit {
        connectionStateSubscription?.cancel()
    }
}
