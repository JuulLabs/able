/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.throwable

import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.juul.able.gatt.GattIo
import com.juul.able.gatt.WriteType

/**
 * @throws [IllegalStateException] if [GattIo.discoverServices] call does not return [GATT_SUCCESS].
 */
suspend fun GattIo.discoverServicesOrThrow() {
    discoverServices()
        .also { status ->
            check(status == GATT_SUCCESS) {
                "Service discovery failed with gatt status $status."
            }
        }
}

/**
 * @throws [IllegalStateException] if [GattIo.readRemoteRssi] call does not return [GATT_SUCCESS].
 */
suspend fun GattIo.readRemoteRssiOrThrow(): Int {
    return readRemoteRssi()
        .also { (_, status) ->
            check(status == GATT_SUCCESS) {
                "Service discovery failed with gatt status $status."
            }
        }.rssi
}

/**
 * @throws [IllegalStateException] if [GattIo.readCharacteristic] call does not return [GATT_SUCCESS].
 */
suspend fun GattIo.readCharacteristicOrThrow(
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
 * @throws [IllegalStateException] if [GattIo.setCharacteristicNotification] call returns `false`.
 */
fun GattIo.setCharacteristicNotificationOrThrow(
    characteristic: BluetoothGattCharacteristic,
    enable: Boolean
) {
    setCharacteristicNotification(characteristic, enable)
        .also {
            check(it) { "Setting characteristic notifications to $enable failed." }
        }
}

/**
 * @throws [IllegalStateException] if [GattIo.writeCharacteristic] call does not return [GATT_SUCCESS].
 */
suspend fun GattIo.writeCharacteristicOrThrow(
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
 * @throws [IllegalStateException] if [GattIo.writeDescriptor] call does not return [GATT_SUCCESS].
 */
suspend fun GattIo.writeDescriptorOrThrow(
    descriptor: BluetoothGattDescriptor,
    value: ByteArray
) {
    writeDescriptor(descriptor, value)
        .also { (_, status) ->
            check(status == GATT_SUCCESS) { "Descriptor write failed with status $status." }
        }
}
