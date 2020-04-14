/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.device

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt.STATE_CONNECTED
import android.content.Context
import android.os.RemoteException
import com.juul.able.Able
import com.juul.able.device.ConnectGattResult.Failure
import com.juul.able.device.ConnectGattResult.Success
import com.juul.able.gatt.CoroutinesGatt
import com.juul.able.gatt.GattCallback
import com.juul.able.gatt.suspendUntilConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.newSingleThreadContext

private const val DISPATCHER_NAME = "Gatt"

internal class CoroutinesDevice(
    private val device: BluetoothDevice
) : Device {

    override suspend fun connectGatt(context: Context): ConnectGattResult {
        val dispatcher = newSingleThreadContext("$DISPATCHER_NAME@$device")
        val callback = GattCallback(dispatcher)
        val bluetoothGatt = device.connectGatt(context, false, callback)
            ?: return Failure(
                RemoteException("`BluetoothDevice.connectGatt` returned `null` for device $device")
            )

        return try {
            val gatt = CoroutinesGatt(bluetoothGatt, dispatcher, callback)
            gatt.suspendUntilConnectionState(STATE_CONNECTED)
            Success(gatt)
        } catch (cancellation: CancellationException) {
            Able.info { "connectGatt() canceled for $this" }
            callback.close(bluetoothGatt)
            dispatcher.close()
            throw cancellation
        } catch (failure: Exception) {
            Able.warn { "connectGatt() failed for $this" }
            callback.close(bluetoothGatt)
            dispatcher.close()
            Failure(ConnectionFailed("Failed to connect to device $device", failure))
        }
    }

    override fun toString(): String = "CoroutinesDevice(device=$device)"
}
