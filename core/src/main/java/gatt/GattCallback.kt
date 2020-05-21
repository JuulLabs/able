/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.gatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTING
import com.juul.able.Able
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.runBlocking

internal class GattCallback(
    private val dispatcher: ExecutorCoroutineDispatcher
) : BluetoothGattCallback() {

    private val _onConnectionStateChange = MutableStateFlow<OnConnectionStateChange?>(null)
    val onConnectionStateChange: Flow<OnConnectionStateChange> =
        _onConnectionStateChange.filterNotNull()

    val onCharacteristicChanged = BroadcastChannel<OnCharacteristicChanged>(BUFFERED)
    val onResponse = Channel<Any>(CONFLATED)

    private val isClosed = AtomicBoolean()

    private fun onDisconnecting() {
        onCharacteristicChanged.close()
        onResponse.close(ConnectionLost())
    }

    fun close(gatt: BluetoothGatt) {
        if (isClosed.compareAndSet(false, true)) {
            Able.verbose { "Closing GattCallback belonging to device ${gatt.device}" }
            onDisconnecting() // Duplicate call in case Android skips STATE_DISCONNECTING.
            gatt.close()

            _onConnectionStateChange.value =
                OnConnectionStateChange(GATT_SUCCESS, STATE_DISCONNECTED)

            // todo: Remove when https://github.com/Kotlin/kotlinx.coroutines/issues/261 is fixed.
            dispatcher.close()
        }
    }

    override fun onConnectionStateChange(
        gatt: BluetoothGatt,
        status: GattConnectionStatus,
        newState: GattConnectionState
    ) {
        val event = OnConnectionStateChange(status, newState)
        Able.debug { "← $event" }
        _onConnectionStateChange.value = event

        when (newState) {
            STATE_DISCONNECTING -> onDisconnecting()
            STATE_DISCONNECTED -> close(gatt)
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val value = characteristic.value
        val event = OnCharacteristicChanged(characteristic, value)
        Able.verbose { "← $event" }

        if (!onCharacteristicChanged.offer(event)) {
            Able.warn { "Subscribers are slow to consume, blocking thread for $event" }
            runBlocking { onCharacteristicChanged.send(event) }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: GattStatus) {
        emitEvent(OnServicesDiscovered(status))
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
        val value = characteristic.value
        emitEvent(OnCharacteristicRead(characteristic, value, status))
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
        emitEvent(OnCharacteristicWrite(characteristic, status))
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: GattStatus
    ) {
        val value = descriptor.value
        emitEvent(OnDescriptorRead(descriptor, value, status))
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: GattStatus
    ) {
        emitEvent(OnDescriptorWrite(descriptor, status))
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        emitEvent(OnReadRemoteRssi(rssi, status))
    }

    override fun onMtuChanged(
        gatt: BluetoothGatt,
        mtu: Int,
        status: Int
    ) {
        emitEvent(OnMtuChanged(mtu, status))
    }

    private fun emitEvent(event: Any) {
        Able.verbose { "← $event" }
        onResponse.offer(event)
    }
}
