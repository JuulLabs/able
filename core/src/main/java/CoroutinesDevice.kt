/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.STATE_CONNECTED
import android.content.Context
import com.github.ajalt.timberkt.Timber
import com.juul.able.experimental.ConnectGattResult.ConnectGattCanceled
import com.juul.able.experimental.ConnectGattResult.ConnectGattFailure
import com.juul.able.experimental.ConnectGattResult.ConnectGattSuccess
import com.juul.able.experimental.messenger.GattCallback
import com.juul.able.experimental.messenger.GattCallbackConfig
import com.juul.able.experimental.messenger.Messenger
import kotlinx.coroutines.experimental.CancellationException

class CoroutinesDevice(
    private val device: BluetoothDevice,
    private val callbackConfig: GattCallbackConfig = GattCallbackConfig()
) : Device {

    /**
     * Requests that a connection to the [device] be established.
     *
     * A dedicated thread is spun up to handle interacting with the newly retrieved [BluetoothGatt]
     * and must be closed when the connection is no longer needed by invoking [Gatt.close].
     */
    private fun requestConnectGatt(context: Context, autoConnect: Boolean): CoroutinesGatt? {
        val callback = GattCallback(callbackConfig)
        val bluetoothGatt = device.connectGatt(context, autoConnect, callback) ?: return null
        val messenger = Messenger(bluetoothGatt, callback)
        return CoroutinesGatt(bluetoothGatt, messenger)
    }

    /**
     * Establishes a connection to the [BluetoothDevice], suspending until connection is successful
     * or error occurs.
     *
     * To cancel an in-flight connection attempt, the Coroutine from which this method was called
     * can be canceled:
     *
     * ```
     * fun connect(androidContext: Context, device: BluetoothDevice) {
     *     connectJob = async {
     *         device.connectGattOrNull(androidContext, autoConnect = false)
     *     }
     * }
     *
     * fun cancelConnection() {
     *     connectJob?.cancel() // cancels the above `connectGatt`
     * }
     * ```
     *
     * A dedicated thread is spun up to handle interacting with the underlying [BluetoothGatt], and
     * can be stopped by invoking [Gatt.close] on the returned [Gatt] object.
     *
     * If an error occurs during connection process, then [Gatt.close] is automatically called
     * (which stops the dedicated thread).
     */
    override suspend fun connectGatt(context: Context, autoConnect: Boolean): ConnectGattResult {
        val gatt = requestConnectGatt(context, autoConnect)
            ?: return ConnectGattFailure(
                NullPointerException("`BluetoothDevice.connectGatt` returned `null`.")
            )
        val connectionStateMonitor = ConnectionStateMonitor(gatt)

        val didConnect = try {
            connectionStateMonitor.suspendUntilConnectionState(STATE_CONNECTED)
        } catch (e: CancellationException) {
            Timber.i { "connectGatt() canceled." }
            gatt.close()
            return ConnectGattCanceled(e)
        } finally {
            connectionStateMonitor.close()
        }

        return if (didConnect) {
            ConnectGattSuccess(gatt)
        } else {
            Timber.w { "connectGatt() failed." }
            gatt.close()
            return ConnectGattFailure(
                IllegalStateException("Failed to connect to ${device.address}.")
            )
        }
    }
}
