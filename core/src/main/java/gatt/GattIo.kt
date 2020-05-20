/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.gatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.RemoteException
import java.util.UUID
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.Flow

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

interface GattIo {

    /**
     * @throws [RemoteException] if underlying [BluetoothGatt.discoverServices] returns `false`.
     * @throws [ConnectionLost] if [Gatt] disconnects while method is executing.
     */
    suspend fun discoverServices(): GattStatus

    val services: List<BluetoothGattService>
    fun getService(uuid: UUID): BluetoothGattService?

    /** Will be removed when https://github.com/Kotlin/kotlinx.coroutines/issues/2034 is closed. */
    @Deprecated(
        message = "Will be removed when Kotlin/kotlinx.coroutines#2034 is closed; use onCharacteristicChanged instead.",
        replaceWith = ReplaceWith(expression = "onCharacteristicChanged")
    )
    val onCharacteristicChangedChannel: BroadcastChannel<OnCharacteristicChanged>

    @FlowPreview
    val onCharacteristicChanged: Flow<OnCharacteristicChanged>

    /**
     * @throws [RemoteException] if underlying [BluetoothGatt.requestMtu] returns `false`.
     * @throws [ConnectionLost] if [Gatt] disconnects while method is executing.
     */
    suspend fun requestMtu(mtu: Int): OnMtuChanged

    /**
     * @throws [RemoteException] if underlying [BluetoothGatt.readCharacteristic] returns `false`.
     * @throws [ConnectionLost] if [Gatt] disconnects while method is executing.
     */
    suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic
    ): OnCharacteristicRead

    /**
     * @param value applied to [characteristic] when characteristic is written.
     * @param writeType applied to [characteristic] when characteristic is written.
     * @throws [RemoteException] if underlying [BluetoothGatt.writeCharacteristic] returns `false`.
     * @throws [ConnectionLost] if [Gatt] disconnects while method is executing.
     */
    suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): OnCharacteristicWrite

    /**
     * @param value applied to [descriptor] when descriptor is written.
     * @throws [RemoteException] if underlying [BluetoothGatt.writeDescriptor] returns `false`.
     * @throws [ConnectionLost] if [Gatt] disconnects while method is executing.
     */
    suspend fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): OnDescriptorWrite

    fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Boolean

    suspend fun readRemoteRssi(): OnReadRemoteRssi
}

suspend fun GattIo.writeCharacteristic(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray
): OnCharacteristicWrite = writeCharacteristic(characteristic, value, WRITE_TYPE_DEFAULT)
