/*
 * Copyright 2018 JUUL Labs, Inc.
 */

@file:JvmName("BluetoothDeviceCoreKt")

package com.juul.able.experimental.android

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.juul.able.experimental.ConnectGattResult
import com.juul.able.experimental.CoroutinesDevice
import com.juul.able.experimental.Gatt
import com.juul.able.experimental.GattCallbackConfig
import com.juul.able.experimental.connectGattOrNull

fun BluetoothDevice.asCoroutinesDevice(
    callbackConfig: GattCallbackConfig = GattCallbackConfig()
): CoroutinesDevice = CoroutinesDevice(this, callbackConfig)

suspend fun BluetoothDevice.connectGatt(
    context: Context,
    autoConnect: Boolean,
    callbackConfig: GattCallbackConfig = GattCallbackConfig()
): ConnectGattResult = asCoroutinesDevice(callbackConfig).connectGatt(context, autoConnect)

suspend fun BluetoothDevice.connectGattOrNull(
    context: Context,
    autoConnect: Boolean,
    callbackConfig: GattCallbackConfig = GattCallbackConfig()
): Gatt? = asCoroutinesDevice(callbackConfig).connectGattOrNull(context, autoConnect)
