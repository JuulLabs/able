/*
 * Copyright 2018 JUUL Labs, Inc.
 */

@file:Suppress("RedundantUnitReturnType")

package com.juul.able.experimental

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
import android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import com.juul.able.experimental.messenger.OnCharacteristicChanged
import com.juul.able.experimental.messenger.OnCharacteristicRead
import com.juul.able.experimental.messenger.OnCharacteristicWrite
import com.juul.able.experimental.messenger.OnConnectionStateChange
import com.juul.able.experimental.messenger.OnDescriptorWrite
import com.juul.able.experimental.messenger.OnMtuChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
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

/** https://android.googlesource.com/platform/development/+/7167a054a8027f75025c865322fa84791a9b3bd1/samples/BluetoothLeGatt/src/com/example/bluetooth/le/SampleGattAttributes.java#27 */
private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

class DescriptorNotFound(val uuid: UUID) : Exception("Descriptor $uuid not found")

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
    characteristic: BluetoothGattCharacteristic, value: ByteArray
): OnCharacteristicWrite = writeCharacteristic(characteristic, value, WRITE_TYPE_DEFAULT)

fun Gatt.observe(
    characteristic: BluetoothGattCharacteristic,
    descriptor: BluetoothGattDescriptor? = null
): Flow<OnCharacteristicChanged> = flow {
    setCharacteristicNotification(characteristic, true)

    val configDescriptor = descriptor
        ?: characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        ?: throw DescriptorNotFound(CLIENT_CHARACTERISTIC_CONFIG_UUID)
    writeDescriptor(configDescriptor, ENABLE_NOTIFICATION_VALUE)

    val channel = onCharacteristicChanged.openSubscription()
    try {
        for (change in channel) {
            if (change.characteristic.uuid == characteristic.uuid) {
                emit(change)
            }
        }
    } finally {
        channel.cancel()
        withContext(NonCancellable) {
            writeDescriptor(configDescriptor, DISABLE_NOTIFICATION_VALUE)
            setCharacteristicNotification(characteristic, false)
        }
    }
}
