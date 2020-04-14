/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.android

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.juul.able.device.ConnectGattResult
import com.juul.able.device.CoroutinesDevice

/**
 * Establishes a connection to the [BluetoothDevice], suspending until connection is successful or
 * error occurs.
 *
 * To cancel an in-flight connection attempt, the Coroutine from which this method was called can be
 * canceled:
 *
 * ```
 * fun connect(context: Context, device: BluetoothDevice) {
 *     connectJob = async {
 *         device.connectGatt(context)
 *     }
 * }
 *
 * fun cancelConnection() {
 *     connectJob?.cancel() // cancels the above `connectGatt`
 * }
 * ```
 */
suspend fun BluetoothDevice.connectGatt(
    context: Context
): ConnectGattResult = asCoroutinesDevice().connectGatt(context)

private fun BluetoothDevice.asCoroutinesDevice() = CoroutinesDevice(this)
