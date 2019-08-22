/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.device

import android.bluetooth.BluetoothDevice
import com.juul.able.experimental.Able
import java.util.concurrent.ConcurrentHashMap

class DeviceManager {

    private val wrapped = ConcurrentHashMap<BluetoothDevice, CoroutinesGattDevice>()

    val bluetoothDevices
        get() = wrapped.keys().toList()

    val coroutinesGattDevices
        get() = wrapped.values.toList()

    fun wrapped(bluetoothDevice: BluetoothDevice): CoroutinesGattDevice =
        wrapped.getOrPut(bluetoothDevice) {
            CoroutinesGattDevice(bluetoothDevice)
        }

    fun remove(bluetoothDevice: BluetoothDevice): CoroutinesGattDevice? {
        val removed = wrapped.remove(bluetoothDevice)
        if (removed != null) {
            removed.dispose()
        } else {
            Able.warn { "remove ← Bluetooth device $bluetoothDevice not found" }
        }
        return removed
    }

    operator fun minusAssign(bluetoothDevice: BluetoothDevice) {
        remove(bluetoothDevice)
        Able.debug { "close ← Remaining: ${wrapped.values}" }
    }
}
