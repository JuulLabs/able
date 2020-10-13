/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.messenger

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.juul.able.experimental.Able
import com.juul.able.experimental.GattState
import com.juul.able.experimental.GattStatus
import com.juul.able.experimental.asGattConnectionStatusString
import com.juul.able.experimental.asGattStateString
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex

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
        Channel<GattStatus>(config.onServicesDiscoveredCapacity)
    internal val onCharacteristicRead =
        Channel<OnCharacteristicRead>(config.onCharacteristicReadCapacity)
    internal val onCharacteristicWrite =
        Channel<OnCharacteristicWrite>(config.onCharacteristicWriteCapacity)
    internal val onDescriptorRead = Channel<OnDescriptorRead>(config.onDescriptorReadCapacity)
    internal val onDescriptorWrite = Channel<OnDescriptorWrite>(config.onDescriptorWriteCapacity)
    internal val onReliableWriteCompleted =
        Channel<OnReliableWriteCompleted>(config.onReliableWriteCompletedCapacity)
    internal val onMtuChanged = Channel<OnMtuChanged>(config.onMtuChangedCapacity)

    private val gattLock = Mutex()
    internal suspend fun waitForGattReady() = gattLock.lock()
    internal fun notifyGattReady() {
        if (gattLock.isLocked) {
            gattLock.unlock()
        }
    }

    fun close() {
        Able.verbose { "close → Begin" }

        onConnectionStateChange.close()
        onCharacteristicChanged.close()

        onServicesDiscovered.close()
        onCharacteristicRead.close()
        onCharacteristicWrite.close()
        onDescriptorRead.close()
        onDescriptorWrite.close()
        onReliableWriteCompleted.close()

        Able.verbose { "close → End" }
    }

    override fun onConnectionStateChange(
        gatt: BluetoothGatt,
        status: GattStatus,
        newState: GattState
    ) {
        Able.debug {
            val statusString = status.asGattConnectionStatusString()
            val stateString = newState.asGattStateString()
            "onConnectionStateChange → status = $statusString, newState = $stateString"
        }

        if (!onConnectionStateChange.offerCatching(OnConnectionStateChange(status, newState))) {
            Able.warn { "onConnectionStateChange → dropped" }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: GattStatus) {
        Able.verbose { "onServicesDiscovered → status = $status" }
        if (!onServicesDiscovered.offer(status)) {
            Able.warn { "onServicesDiscovered → dropped" }
        }
        notifyGattReady()
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
        Able.verbose { "onCharacteristicRead → uuid = ${characteristic.uuid}" }
        val event = OnCharacteristicRead(characteristic, characteristic.value, status)
        if (!onCharacteristicRead.offer(event)) {
            Able.warn { "onCharacteristicRead → dropped" }
        }
        notifyGattReady()
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
        Able.verbose { "onCharacteristicWrite → uuid = ${characteristic.uuid}" }
        if (!onCharacteristicWrite.offer(OnCharacteristicWrite(characteristic, status))) {
            Able.warn { "onCharacteristicWrite → dropped" }
        }
        notifyGattReady()
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        Able.verbose { "onCharacteristicChanged → uuid = ${characteristic.uuid}" }
        val event = OnCharacteristicChanged(characteristic, characteristic.value)
        if (!onCharacteristicChanged.offer(event)) {
            Able.warn { "OnCharacteristicChanged → dropped" }
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
        Able.verbose { "onDescriptorRead → uuid = ${descriptor.uuid}" }
        if (!onDescriptorRead.offer(OnDescriptorRead(descriptor, descriptor.value, status))) {
            Able.warn { "onDescriptorRead → dropped" }
        }
        notifyGattReady()
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: GattStatus
    ) {
        Able.verbose { "onDescriptorWrite → uuid = ${descriptor.uuid}" }
        if (!onDescriptorWrite.offer(OnDescriptorWrite(descriptor, status))) {
            Able.warn { "onDescriptorWrite → dropped" }
        }
        notifyGattReady()
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
        Able.verbose { "onReliableWriteCompleted → status = $status" }
        if (!onReliableWriteCompleted.offer(OnReliableWriteCompleted(status))) {
            Able.warn { "onReliableWriteCompleted → dropped" }
        }
        notifyGattReady()
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Able.verbose { "onMtuChanged → status = $status" }
        if (!onMtuChanged.offer(OnMtuChanged(mtu, status))) {
            Able.warn { "onMtuChanged → dropped" }
        }
        notifyGattReady()
    }
}

fun <E> SendChannel<E>.offerCatching(element: E): Boolean {
    return runCatching { offer(element) }.getOrDefault(false)
}
