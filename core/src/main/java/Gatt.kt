/*
 * Copyright 2018 JUUL Labs, Inc.
 */

@file:Suppress("RedundantUnitReturnType")

package com.juul.able.experimental

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BroadcastChannel
import java.io.Closeable
import java.util.UUID

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
 * Represents the possible GATT states as defined in [BluetoothProfile]:
 *
 * - [BluetoothProfile.STATE_DISCONNECTED]
 * - [BluetoothProfile.STATE_CONNECTING]
 * - [BluetoothProfile.STATE_CONNECTED]
 * - [BluetoothProfile.STATE_DISCONNECTING]
 */
typealias GattState = Int

/**
 * Represents the possible [BluetoothGattCharacteristic] write types:
 *
 * - [BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT]
 * - [BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE]
 * - [BluetoothGattCharacteristic.WRITE_TYPE_SIGNED]
 */
typealias WriteType = Int

interface Gatt : Closeable, CoroutineScope {

    val onConnectionStateChange: BroadcastChannel<OnConnectionStateChange>
    val onCharacteristicChanged: BroadcastChannel<OnCharacteristicChanged>

    val services: List<BluetoothGattService>

    fun requestConnect(): Boolean
    fun requestDisconnect(): Unit
    fun getService(uuid: UUID): BluetoothGattService?

    suspend fun connect(): Boolean
    suspend fun disconnect(): Unit
    suspend fun discoverServices(): GattStatus

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

    suspend fun requestMtu(mtu: Int): OnMtuChanged

    fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Boolean
}

suspend fun Gatt.writeCharacteristic(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray
): OnCharacteristicWrite = writeCharacteristic(characteristic, value, WRITE_TYPE_DEFAULT)
