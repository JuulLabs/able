/*
 * Copyright 2020 JUUL Labs, Inc.
 */

@file:Suppress("RedundantUnitReturnType")

package com.juul.able.gatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import com.juul.able.Able
import java.util.UUID
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

/**
 * Represents the possible GATT statuses as defined in [BluetoothGatt]:
 *
 * - [BluetoothGatt.GATT_SUCCESS]
 * - [BluetoothGatt.GATT_READ_NOT_PERMITTED]
 * - [BluetoothGatt.GATT_WRITE_NOT_PERMITTED]
 * - [BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION]
 * - [BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED]
 * - [BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION]
 * - [BluetoothGatt.GATT_INVALID_OFFSET]
 * - [BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH]
 * - [BluetoothGatt.GATT_CONNECTION_CONGESTED]
 * - [BluetoothGatt.GATT_FAILURE]
 */
typealias GattStatus = Int

/**
 * Represents the possible [BluetoothGattCharacteristic] write types:
 *
 * - [BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT]
 * - [BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE]
 * - [BluetoothGattCharacteristic.WRITE_TYPE_SIGNED]
 */
typealias WriteType = Int

class ConnectionLost : Exception()

class FailedToDeliverEvent(message: String) : IllegalStateException(message)

class GattStatusFailure(
    val event: OnConnectionStateChange
) : IllegalStateException("Received $event")

interface Gatt {

    val onConnectionStateChange: Flow<OnConnectionStateChange>
    val onCharacteristicChanged: Flow<OnCharacteristicChanged>

    suspend fun discoverServices(): GattStatus

    val services: List<BluetoothGattService>
    fun getService(uuid: UUID): BluetoothGattService?

    suspend fun requestMtu(mtu: Int): OnMtuChanged

    suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic
    ): OnCharacteristicRead

    suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): OnCharacteristicWrite

    suspend fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): OnDescriptorWrite

    fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Boolean

    suspend fun disconnect(): Unit
}

suspend fun Gatt.writeCharacteristic(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray
): OnCharacteristicWrite = writeCharacteristic(characteristic, value, WRITE_TYPE_DEFAULT)

internal suspend fun Gatt.suspendUntilConnectionState(state: GattConnectionState) {
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
