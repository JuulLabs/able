/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.throwable

import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.RemoteException
import com.juul.able.gatt.GattIo
import com.juul.able.gatt.GattStatus
import com.juul.able.gatt.GattStatusException
import com.juul.able.gatt.WriteType

/**
 * @throws [GattStatusException] if [GattIo.discoverServices] call does not return [GATT_SUCCESS].
 */
suspend fun GattIo.discoverServicesOrThrow() {
    discoverServices().also { status ->
        check(status) { "Discover services" }
    }
}

/**
 * @throws [GattStatusException] if [GattIo.readRemoteRssi] call does not return [GATT_SUCCESS].
 */
suspend fun GattIo.readRemoteRssiOrThrow(): Int {
    return readRemoteRssi()
        .also { (_, status) ->
            check(status) { "Read remote RSSI" }
        }
        .rssi
}

/**
 * @throws [GattStatusException] if [GattIo.readCharacteristic] call does not return [GATT_SUCCESS].
 */
suspend fun GattIo.readCharacteristicOrThrow(
    characteristic: BluetoothGattCharacteristic
): ByteArray {
    return readCharacteristic(characteristic)
        .also { (_, _, status) ->
            check(status) { "Read characteristic ${characteristic.uuid}" }
        }
        .value
}

/**
 * @throws [RemoteException] if [GattIo.setCharacteristicNotification] call returns `false`.
 */
fun GattIo.setCharacteristicNotificationOrThrow(
    characteristic: BluetoothGattCharacteristic,
    enable: Boolean
) {
    setCharacteristicNotification(characteristic, enable)
        .also { successful ->
            if (!successful) {
                val uuid = characteristic.uuid
                val message = "Setting characteristic $uuid notifications to $enable failed"
                throw RemoteException(message)
            }
        }
}

/**
 * @throws [GattStatusException] if [GattIo.writeCharacteristic] call does not return [GATT_SUCCESS].
 */
suspend fun GattIo.writeCharacteristicOrThrow(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    writeType: WriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
) {
    writeCharacteristic(characteristic, value, writeType)
        .also { (_, status) ->
            check(status) { "Write characteristic ${characteristic.uuid}" }
        }
}

/**
 * @throws [GattStatusException] if [GattIo.writeDescriptor] call does not return [GATT_SUCCESS].
 */
suspend fun GattIo.writeDescriptorOrThrow(
    descriptor: BluetoothGattDescriptor,
    value: ByteArray
) {
    writeDescriptor(descriptor, value)
        .also { (_, status) ->
            check(status) { "Write descriptor ${descriptor.uuid}" }
        }
}

/**
 * @throws [GattStatusException] if [GattIo.requestMtu] call does not return [GATT_SUCCESS].
 */
suspend fun GattIo.requestMtuOrThrow(
    mtu: Int
): Int {
    return requestMtu(mtu)
        .also { (_, status) ->
            check(status) { "Request MTU of $mtu" }
        }.mtu
}

private fun check(status: GattStatus, lazyPrefix: () -> Any) {
    if (status != GATT_SUCCESS) {
        val prefix = lazyPrefix.invoke()
        throw GattStatusException(status, prefix.toString())
    }
}
