/*
 * Copyright 2020 JUUL Labs, Inc.
 */

@file:Suppress("RedundantUnitReturnType")

package com.juul.able.throwable

import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.juul.able.gatt.Gatt
import com.juul.able.gatt.WriteType

/**
 * @throws [IllegalStateException] if [Gatt.discoverServices] call does not return [GATT_SUCCESS].
 */
suspend fun Gatt.discoverServicesOrThrow() {
    discoverServices()
        .also { status ->
            check(status == GATT_SUCCESS) {
                "Service discovery failed with gatt status $status."
            }
        }
}

/**
 * @throws [IllegalStateException] if [Gatt.readCharacteristic] call does not return [GATT_SUCCESS].
 */
suspend fun Gatt.readCharacteristicOrThrow(
    characteristic: BluetoothGattCharacteristic
): ByteArray {
    return readCharacteristic(characteristic)
        .also { (_, _, status) ->
            check(status == GATT_SUCCESS) {
                "Reading characteristic ${characteristic.uuid} failed with status $status."
            }
        }.value
}

/**
 * @throws [IllegalStateException] if [Gatt.setCharacteristicNotification] call returns `false`.
 */
fun Gatt.setCharacteristicNotificationOrThrow(
    characteristic: BluetoothGattCharacteristic,
    enable: Boolean
) {
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
) {
    writeCharacteristic(characteristic, value, writeType)
        .also { (_, status) ->
            check(status == GATT_SUCCESS) {
                "Writing characteristic ${characteristic.uuid} failed with status $status."
            }
        }
}

/**
 * @throws [IllegalStateException] if [Gatt.writeDescriptor] call does not return [GATT_SUCCESS].
 */
suspend fun Gatt.writeDescriptorOrThrow(
    descriptor: BluetoothGattDescriptor,
    value: ByteArray
) {
    writeDescriptor(descriptor, value)
        .also { (_, status) ->
            check(status == GATT_SUCCESS) { "Descriptor write failed with status $status." }
        }
}
