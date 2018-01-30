/*
 * Copyright 2018 JUUL Labs, Inc.
 */

@file:Suppress("RedundantUnitReturnType")

package com.juul.able.experimental.throwable

import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.juul.able.experimental.Gatt
import com.juul.able.experimental.WriteType

/**
 * @throws [IllegalStateException] if [Gatt.connect] call returns `false`.
 */
suspend fun Gatt.connectOrThrow(): Unit {
    connect() || error("connect() returned `false`.")
}

/**
 * @throws [IllegalStateException] if [Gatt.discoverServices] call does not return [GATT_SUCCESS].
 */
suspend fun Gatt.discoverServicesOrThrow(): Unit {
    discoverServices()
        .also { (status) ->
            check(status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                "Service discovery failed with gatt status $status."
            }
        }
}

/**
 * @throws [IllegalStateException] if [Gatt.readCharacteristic] call does not return [GATT_SUCCESS].
 */
suspend fun Gatt.readCharacteristicOrThrow(
    characteristic: BluetoothGattCharacteristic
): BluetoothGattCharacteristic {
    return readCharacteristic(characteristic)
        .also { (_, _, status) ->
            check(status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                "Reading characteristic ${characteristic.uuid} failed with status $status."
            }
        }.characteristic
}

/**
 * @throws [IllegalStateException] if [Gatt.setCharacteristicNotification] call returns `false`.
 */
suspend fun Gatt.setCharacteristicNotificationOrThrow(
    characteristic: BluetoothGattCharacteristic,
    enable: Boolean
): Unit {
    setCharacteristicNotification(characteristic, enable)
        .also {
            check(it) { "Setting characteristic notifications to $enable failed." }
        }
}


/**
 * @throws [IllegalStateException] if [Gatt.writeCharacteristic] call does not return [GATT_SUCCESS].
 */
suspend fun Gatt.writeCharacteristicOrThrow(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    writeType: WriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
): BluetoothGattCharacteristic {
    return writeCharacteristic(characteristic, value, writeType)
        .also { (_, status) ->
            check(status == GATT_SUCCESS) {
                "Writing characteristic ${characteristic.uuid} failed with status $status."
            }
        }.characteristic
}

/**
 * @throws [IllegalStateException] if [Gatt.writeDescriptor] call does not return [GATT_SUCCESS].
 */
suspend fun Gatt.writeDescriptorOrThrow(
    descriptor: BluetoothGattDescriptor,
    value: ByteArray
): BluetoothGattDescriptor {
    return writeDescriptor(descriptor, value)
        .also { (_, status) ->
            check(status == GATT_SUCCESS) { "Descriptor write failed with gatt status $status." }
        }.descriptor
}
