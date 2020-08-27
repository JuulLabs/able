/*
 * Copyright 2020 JUUL Labs, Inc.
 */

@file:Suppress("RedundantUnitReturnType")

package com.juul.able.gatt

import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothProfile
import java.io.IOException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

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

class GattStatusException(message: String?) : IOException(message) {
    constructor(
        status: GattStatus,
        prefix: String
    ) : this("$prefix failed with status ${status.asGattStatusString()}")
}

class ConnectionLostException internal constructor(
    message: String? = null,
    cause: Throwable? = null
) : IOException(message, cause)
