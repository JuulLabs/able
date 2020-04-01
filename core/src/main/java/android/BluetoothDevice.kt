/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.android

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.juul.able.device.ConnectGattResult
import com.juul.able.device.CoroutinesDevice

suspend fun BluetoothDevice.connectGatt(
    context: Context
): ConnectGattResult = asCoroutinesDevice().connectGatt(context)

private fun BluetoothDevice.asCoroutinesDevice() = CoroutinesDevice(this)
