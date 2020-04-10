/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.throwable.android

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.content.Context
import com.juul.able.android.connectGatt
import com.juul.able.device.ConnectGattResult.Failure
import com.juul.able.device.ConnectGattResult.Success
import com.juul.able.device.ConnectionFailed
import com.juul.able.gatt.Gatt

/**
 * @throws ConnectionFailed if underlying [BluetoothDevice.connectGatt] returns `null` or an error (non-[GATT_SUCCESS] status) occurs during connection process.
 */
suspend fun BluetoothDevice.connectGattOrThrow(
    context: Context
): Gatt = when (val result = connectGatt(context)) {
    is Success -> result.gatt
    is Failure -> throw result.cause
}
