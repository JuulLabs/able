/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.device

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.content.Context
import android.os.RemoteException
import com.juul.able.Able
import com.juul.able.device.ConnectGattResult.Failure
import com.juul.able.device.ConnectGattResult.Success
import com.juul.able.gatt.ConnectionLost
import com.juul.able.gatt.CoroutinesGatt
import com.juul.able.gatt.GattCallback
import com.juul.able.gatt.GattConnection
import com.juul.able.gatt.GattErrorStatus
import com.juul.able.gatt.asGattConnectionStateString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.newSingleThreadContext

private const val DISPATCHER_NAME = "Gatt"

internal class CoroutinesDevice(
    private val device: BluetoothDevice
) : Device {

    override suspend fun connectGatt(context: Context): ConnectGattResult {
        val dispatcher = newSingleThreadContext("$DISPATCHER_NAME@$device")
        val callback = GattCallback(dispatcher)
        val bluetoothGatt = device.connectGatt(context, false, callback)
            ?: return Failure.Rejected(
                RemoteException("`BluetoothDevice.connectGatt` returned `null` for device $device")
            )

        return try {
            val gatt = CoroutinesGatt(bluetoothGatt, dispatcher, callback)
            gatt.suspendUntilConnected()
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
            Failure.Connection(failure)
        }
    }

    private suspend fun GattConnection.suspendUntilConnected() {
        Able.debug { "Suspending until device $device is connected" }
        onConnectionStateChange
            .onEach { event ->
                Able.verbose { "← Device $device received $event while waiting for connection" }
                if (event.status != GATT_SUCCESS) throw GattErrorStatus(event)
                if (event.newState == STATE_DISCONNECTED) throw ConnectionLost()
            }
            .first { (_, newState) -> newState == STATE_CONNECTED }
            .also { (_, newState) ->
                Able.info { "Device $device reached ${newState.asGattConnectionStateString()}" }
            }
    }

    override fun toString(): String = "CoroutinesDevice(device=$device)"
}
