/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.throwable.android

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.content.Context
import com.juul.able.experimental.Gatt
import com.juul.able.experimental.GattCallbackConfig
import com.juul.able.experimental.android.asCoroutinesDevice
import com.juul.able.experimental.throwable.ConnectionCanceledException
import com.juul.able.experimental.throwable.ConnectionFailedException
import com.juul.able.experimental.throwable.connectGattOrThrow
import kotlinx.coroutines.CancellationException

/**
 * @throws ConnectionCanceledException if a [CancellationException] occurs during the connection process.
 * @throws ConnectionFailedException if underlying [BluetoothDevice.connectGatt] returns `null`.
 * @throws ConnectionFailedException if an error (non-[GATT_SUCCESS] status) occurs during connection process.
 */
suspend fun BluetoothDevice.connectGattOrThrow(
    context: Context,
    autoConnect: Boolean,
    callbackConfig: GattCallbackConfig = GattCallbackConfig()
): Gatt = asCoroutinesDevice(callbackConfig).connectGattOrThrow(context, autoConnect)
