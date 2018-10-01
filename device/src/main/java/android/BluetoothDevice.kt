/*
 * Copyright 2018 JUUL Labs, Inc.
 */

@file:Suppress("unused")

package com.juul.able.experimental.device.android

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.juul.able.experimental.ConnectGattResult
import com.juul.able.experimental.GattCallbackConfig
import com.juul.able.experimental.GattStatus
import com.juul.able.experimental.OnCharacteristicChanged
import com.juul.able.experimental.OnCharacteristicRead
import com.juul.able.experimental.OnConnectionStateChange
import com.juul.able.experimental.OnDescriptorWrite
import com.juul.able.experimental.OnMtuChanged
import com.juul.able.experimental.WriteType
import com.juul.able.experimental.device.CoroutinesGattDevices
import kotlinx.coroutines.channels.BroadcastChannel
import java.util.UUID

private val BluetoothDevice.coroutinesGattDevice
    get() = CoroutinesGattDevices.wrapped(this)

fun BluetoothDevice.isConnected(): Boolean = coroutinesGattDevice.isConnected()

suspend fun BluetoothDevice.connect(
    context: Context,
    autoConnect: Boolean,
    callbackConfig: GattCallbackConfig = GattCallbackConfig()
): ConnectGattResult = coroutinesGattDevice.connect(context, autoConnect, callbackConfig)

val BluetoothDevice.onConnectionStateChange: BroadcastChannel<OnConnectionStateChange>
    get() = coroutinesGattDevice.onConnectionStateChange

val BluetoothDevice.onCharacteristicChanged: BroadcastChannel<OnCharacteristicChanged>
    get() = coroutinesGattDevice.onCharacteristicChanged

val BluetoothDevice.services: List<BluetoothGattService>
    get() = coroutinesGattDevice.services

fun BluetoothDevice.requestConnect(): Boolean = coroutinesGattDevice.requestConnect()

fun BluetoothDevice.requestDisconnect(): Unit = coroutinesGattDevice.requestDisconnect()

fun BluetoothDevice.getService(uuid: UUID): BluetoothGattService? =
    coroutinesGattDevice.getService(uuid)

suspend fun BluetoothDevice.connect(): Boolean = coroutinesGattDevice.connect()

suspend fun BluetoothDevice.disconnect(): Unit = coroutinesGattDevice.disconnect()

suspend fun BluetoothDevice.discoverServices(): GattStatus = coroutinesGattDevice.discoverServices()

suspend fun BluetoothDevice.readCharacteristic(
    characteristic: BluetoothGattCharacteristic
): OnCharacteristicRead = coroutinesGattDevice.readCharacteristic(characteristic)

suspend fun BluetoothDevice.writeCharacteristic(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    writeType: WriteType = WRITE_TYPE_DEFAULT
) = coroutinesGattDevice.writeCharacteristic(characteristic, value, writeType)

suspend fun BluetoothDevice.writeDescriptor(
    descriptor: BluetoothGattDescriptor,
    value: ByteArray
): OnDescriptorWrite = coroutinesGattDevice.writeDescriptor(descriptor, value)

suspend fun BluetoothDevice.requestMtu(mtu: Int): OnMtuChanged =
    coroutinesGattDevice.requestMtu(mtu)

fun BluetoothDevice.setCharacteristicNotification(
    characteristic: BluetoothGattCharacteristic,
    enable: Boolean
): Boolean = coroutinesGattDevice.setCharacteristicNotification(characteristic, enable)

fun BluetoothDevice.close() {
    coroutinesGattDevice.close()
}
