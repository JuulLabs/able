/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.throwable

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.content.Context
import com.juul.able.experimental.ConnectGattResult
import com.juul.able.experimental.Device
import com.juul.able.experimental.Gatt
import kotlinx.coroutines.CancellationException

class ConnectionCanceledException(cause: CancellationException) : Exception(cause)
class ConnectionFailedException(cause: Throwable) : Exception(cause)

/**
 * @throws ConnectionCanceledException if a [CancellationException] occurs during the connection process.
 * @throws ConnectionFailedException if underlying [BluetoothDevice.connectGatt] returns `null`.
 * @throws ConnectionFailedException if an error (non-[GATT_SUCCESS] status) occurs during connection process.
 */
suspend fun Device.connectGattOrThrow(context: Context, autoConnect: Boolean): Gatt {
    val result = connectGatt(context, autoConnect)
    return when (result) {
        is ConnectGattResult.Success -> result.gatt
        is ConnectGattResult.Canceled -> throw ConnectionCanceledException(result.cause)
        is ConnectGattResult.Failure -> throw ConnectionFailedException(result.cause)
    }
}
