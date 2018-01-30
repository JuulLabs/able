/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.messenger

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTING
import com.github.ajalt.timberkt.Timber
import com.juul.able.experimental.GattState
import com.juul.able.experimental.GattStatus
import com.juul.able.experimental.asGattConnectionStatusString
import com.juul.able.experimental.asGattStateString
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.sync.Mutex

data class GattCallbackConfig(
    val onCharacteristicChangedCapacity: Int = Channel.CONFLATED,
    val onServicesDiscoveredCapacity: Int = Channel.CONFLATED,
    val onCharacteristicReadCapacity: Int = Channel.CONFLATED,
    val onCharacteristicWriteCapacity: Int = Channel.CONFLATED,
    val onDescriptorReadCapacity: Int = Channel.CONFLATED,
    val onDescriptorWriteCapacity: Int = Channel.CONFLATED,
    val onReliableWriteCompletedCapacity: Int = Channel.CONFLATED,
    val onMtuChangedCapacity: Int = Channel.CONFLATED
) {
    constructor(capacity: Int = Channel.CONFLATED) : this(
        onCharacteristicChangedCapacity = capacity,
        onServicesDiscoveredCapacity = capacity,
        onCharacteristicReadCapacity = capacity,
        onCharacteristicWriteCapacity = capacity,
        onDescriptorReadCapacity = capacity,
        onDescriptorWriteCapacity = capacity,
        onReliableWriteCompletedCapacity = capacity,
        onMtuChangedCapacity = capacity
    )
}

internal class GattCallback(config: GattCallbackConfig) : BluetoothGattCallback() {

    internal val onConnectionStateChange =
        BroadcastChannel<OnConnectionStateChange>(Channel.CONFLATED)
    internal val onCharacteristicChanged =
        BroadcastChannel<OnCharacteristicChanged>(config.onCharacteristicChangedCapacity)

    internal val onServicesDiscovered =
        Channel<OnServicesDiscovered>(config.onServicesDiscoveredCapacity)
    internal val onCharacteristicRead =
        Channel<OnCharacteristicRead>(config.onCharacteristicReadCapacity)
    internal val onCharacteristicWrite =
        Channel<OnCharacteristicWrite>(config.onCharacteristicWriteCapacity)
    internal val onDescriptorRead = Channel<OnDescriptorRead>(config.onDescriptorReadCapacity)
    internal val onDescriptorWrite = Channel<OnDescriptorWrite>(config.onDescriptorWriteCapacity)
    internal val onReliableWriteCompleted =
        Channel<OnReliableWriteCompleted>(config.onReliableWriteCompletedCapacity)
    internal val onMtuChanged = Channel<OnMtuChanged>(config.onMtuChangedCapacity)

    private val gattLock = Mutex(locked = true) // Start locked then unlock upon STATE_CONNECTED.
    internal suspend fun waitForGattReady() = gattLock.lock()
    internal fun notifyGattReady() {
        if (gattLock.isLocked) {
            gattLock.unlock()
        }
    }

    fun close() {
        Timber.v { "close → Begin" }

        onConnectionStateChange.close()
        onCharacteristicChanged.close()

        onServicesDiscovered.close()
        onCharacteristicRead.close()
        onCharacteristicWrite.close()
        onDescriptorRead.close()
        onDescriptorWrite.close()
        onReliableWriteCompleted.close()

        Timber.v { "close → End" }
    }

    override fun onConnectionStateChange(
        gatt: BluetoothGatt,
        status: GattStatus,
        newState: GattState
    ) {
        Timber.d {
            val statusString = status.asGattConnectionStatusString()
            val stateString = newState.asGattStateString()
            "onConnectionStateChange → status = $statusString, newState = $stateString"
        }

        if (!onConnectionStateChange.offer(OnConnectionStateChange(status, newState))) {
            Timber.w { "onConnectionStateChange → dropped" }
        }

        when (newState) {
            STATE_DISCONNECTING, STATE_DISCONNECTED -> gattLock.tryLock()
            STATE_CONNECTED -> notifyGattReady()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: GattStatus) {
        Timber.v { "onServicesDiscovered → status = $status" }
        if (!onServicesDiscovered.offer(OnServicesDiscovered(status))) {
            Timber.w { "onServicesDiscovered → dropped" }
        }
        notifyGattReady()
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
        Timber.v { "onCharacteristicRead → uuid = ${characteristic.uuid}" }
        val event = OnCharacteristicRead(characteristic, characteristic.value, status)
        if (!onCharacteristicRead.offer(event)) {
            Timber.w { "onCharacteristicRead → dropped" }
        }
        notifyGattReady()
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
        Timber.v { "onCharacteristicWrite → uuid = ${characteristic.uuid}" }
        if (!onCharacteristicWrite.offer(OnCharacteristicWrite(characteristic, status))) {
            Timber.w { "onCharacteristicWrite → dropped" }
        }
        notifyGattReady()
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        Timber.v { "onCharacteristicChanged → uuid = ${characteristic.uuid}" }
        val event = OnCharacteristicChanged(characteristic, characteristic.value)
        if (!onCharacteristicChanged.offer(event)) {
            Timber.w { "OnCharacteristicChanged → dropped" }
        }

        // We don't call `notifyGattReady` because `onCharacteristicChanged` is called whenever a
        // characteristic changes (after notification(s) have been enabled) so is not directly tied
        // to a specific call (or lock).
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: GattStatus
    ) {
        Timber.v { "onDescriptorRead → uuid = ${descriptor.uuid}" }
        if (!onDescriptorRead.offer(OnDescriptorRead(descriptor, descriptor.value, status))) {
            Timber.w { "onDescriptorRead → dropped" }
        }
        notifyGattReady()
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: GattStatus
    ) {
        Timber.v { "onDescriptorWrite → uuid = ${descriptor.uuid}" }
        if (!onDescriptorWrite.offer(OnDescriptorWrite(descriptor, status))) {
            Timber.w { "onDescriptorWrite → dropped" }
        }
        notifyGattReady()
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
        Timber.v { "onReliableWriteCompleted → status = $status" }
        if (!onReliableWriteCompleted.offer(OnReliableWriteCompleted(status))) {
            Timber.w { "onReliableWriteCompleted → dropped" }
        }
        notifyGattReady()
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Timber.v { "onMtuChanged → status = $status" }
        if (!onMtuChanged.offer(OnMtuChanged(mtu, status))) {
            Timber.w { "onMtuChanged → dropped" }
        }
        notifyGattReady()
    }
}
