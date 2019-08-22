/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.device

import android.bluetooth.BluetoothDevice
import com.juul.able.experimental.device.android.isConnected

object CoroutinesGattDevices {

    val deviceManager = DeviceManager()

    fun wrapped(bluetoothDevice: BluetoothDevice): CoroutinesGattDevice =
        deviceManager.wrapped(bluetoothDevice)

    fun remove(bluetoothDevice: BluetoothDevice): CoroutinesGattDevice? =
        deviceManager.remove(bluetoothDevice)

    operator fun minusAssign(bluetoothDevice: BluetoothDevice) {
        deviceManager.minusAssign(bluetoothDevice)
    }
}

val CoroutinesGattDevices.connectedBluetoothDevices
    get() = deviceManager.bluetoothDevices.filter { it.isConnected() }
