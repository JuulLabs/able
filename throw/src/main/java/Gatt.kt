/*
 * Copyright 2018 JUUL Labs, Inc.
 */

@file:Suppress("RedundantUnitReturnType", "unused")

package com.juul.able.experimental.throwable

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.RemoteException
import com.juul.able.experimental.Gatt
import com.juul.able.experimental.WriteType

/**
 * @throws [IllegalStateException] if [Gatt.connect] call returns `false`.
 */
suspend fun Gatt.connectOrThrow(): Unit {
    connect() || error("connect() returned `false`.")
}

/**
 * @throws [RemoteException] if underlying [BluetoothGatt.discoverServices] returns `false`.
 * @throws [IllegalStateException] if [Gatt.discoverServices] call does not return [GATT_SUCCESS].
 */
suspend fun Gatt.discoverServicesOrThrow(): Unit {
    discoverServices()?.also { status ->
        check(status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
            "Service discovery failed with gatt status $status."
        }
    } ?: throw RemoteException("BluetoothGatt.discoverServices returned false.")
}

/**
 * @throws [RemoteException] if underlying [BluetoothGatt.readCharacteristic] returns `false`.
 * @throws [IllegalStateException] if [Gatt.readCharacteristic] call does not return [GATT_SUCCESS].
 */
suspend fun Gatt.readCharacteristicOrThrow(
    characteristic: BluetoothGattCharacteristic
): BluetoothGattCharacteristic =
    readCharacteristic(characteristic)?.also { (_, _, status) ->
        check(status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
            "Reading characteristic ${characteristic.uuid} failed with status $status."
        }
    }?.characteristic ?: throw RemoteException("BluetoothGatt.readCharacteristic returned false.")

/**
 * @throws [RemoteException] if underlying [BluetoothGatt.setCharacteristicNotification] returns `false`.
 */
fun Gatt.setCharacteristicNotificationOrThrow(
    characteristic: BluetoothGattCharacteristic,
    enable: Boolean
): Unit {
    if (!setCharacteristicNotification(characteristic, enable)) {
        throw RemoteException("BluetoothGatt.setCharacteristicNotification returned false.")
    }
}

/**
 * @throws [RemoteException] if underlying [BluetoothGatt.writeCharacteristic] returns `false`.
 * @throws [IllegalStateException] if [Gatt.writeCharacteristic] call does not return [GATT_SUCCESS].
 */
suspend fun Gatt.writeCharacteristicOrThrow(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    writeType: WriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
): BluetoothGattCharacteristic =
    writeCharacteristic(characteristic, value, writeType)?.also { (_, status) ->
        check(status == GATT_SUCCESS) {
            "Writing characteristic ${characteristic.uuid} failed with status $status."
        }
    }?.characteristic ?: throw RemoteException("BluetoothGatt.writeCharacteristic returned false.")

/**
 * @throws [RemoteException] if underlying [BluetoothGatt.writeDescriptor] returns `false`.
 * @throws [IllegalStateException] if [Gatt.writeDescriptor] call does not return [GATT_SUCCESS].
 */
suspend fun Gatt.writeDescriptorOrThrow(
    descriptor: BluetoothGattDescriptor,
    value: ByteArray
): BluetoothGattDescriptor =
    writeDescriptor(descriptor, value)?.also { (_, status) ->
        check(status == GATT_SUCCESS) { "Descriptor write failed with gatt status $status." }
    }?.descriptor ?: throw RemoteException("BluetoothGatt.writeDescriptor returned false.")
