/*
 * Copyright 2020 JUUL Labs, Inc.
 */

@file:Suppress("RedundantUnitReturnType")

package com.juul.able.gatt

import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import com.juul.able.Able
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach

/**
 * Represents the possible GATT connection statuses as defined in the Android source code.
 *
 * - [GATT_SUCCESS]
 * - [GATT_CONN_L2C_FAILURE]
 * - [GATT_CONN_L2C_FAILURE]
 * - [GATT_CONN_TIMEOUT]
 * - [GATT_CONN_TERMINATE_PEER_USER]
 * - [GATT_CONN_TERMINATE_LOCAL_HOST]
 * - [GATT_CONN_FAIL_ESTABLISH]
 * - [GATT_CONN_LMP_TIMEOUT]
 * - [GATT_CONN_CANCEL]
 */
typealias GattConnectionStatus = Int

/**
 * Represents the possible GATT states as defined in [BluetoothProfile]:
 *
 * - [BluetoothProfile.STATE_DISCONNECTED]
 * - [BluetoothProfile.STATE_CONNECTING]
 * - [BluetoothProfile.STATE_CONNECTED]
 * - [BluetoothProfile.STATE_DISCONNECTING]
 */
typealias GattConnectionState = Int

interface GattConnection {

    @FlowPreview
    val onConnectionStateChange: Flow<OnConnectionStateChange>

    suspend fun disconnect(): Unit
}

class GattStatusFailure(
    val event: OnConnectionStateChange
) : IllegalStateException("Received $event")

class ConnectionLost : Exception()

internal suspend fun GattConnection.suspendUntilConnectionState(state: GattConnectionState) {
    Able.debug { "Suspending until ${state.asGattConnectionStateString()}" }
    onConnectionStateChange
        .onEach { event ->
            Able.verbose { "← Received $event while waiting for ${state.asGattConnectionStateString()}" }
            if (event.status != GATT_SUCCESS) throw GattStatusFailure(event)
        }
        .firstOrNull { (_, newState) -> newState == state }
        .also {
            if (it == null) { // Upstream Channel closed due to STATE_DISCONNECTED.
                if (state == STATE_DISCONNECTED) {
                    Able.info { "Reached (implicit) STATE_DISCONNECTED" }
                } else {
                    throw ConnectionLost()
                }
            }
        }
        ?.also { (_, newState) ->
            Able.info { "Reached ${newState.asGattConnectionStateString()}" }
        }
}
