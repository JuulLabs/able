/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.gatt

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

data class OnConnectionStateChange(
    val status: GattStatus,
    val newState: GattConnectionState
) {
    override fun toString(): String {
        val connectionStatus = status.asGattConnectionStatusString()
        val connectionState = newState.asGattConnectionStateString()
        return "OnConnectionStateChange(status=$connectionStatus, newState=$connectionState)"
    }
}

data class OnCharacteristicRead(
    val characteristic: BluetoothGattCharacteristic,
    val value: ByteArray,
    val status: GattStatus
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OnCharacteristicRead

        return status == other.status &&
            characteristic.uuid == other.characteristic.uuid &&
            characteristic.instanceId == other.characteristic.instanceId &&
            value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = characteristic.uuid.hashCode()
        result = 31 * result + characteristic.instanceId
        result = 31 * result + value.contentHashCode()
        result = 31 * result + status
        return result
    }

    override fun toString(): String {
        val uuid = characteristic.uuid
        val instanceId = characteristic.instanceId
        val size = value.size
        val gattStatus = status.asGattStatusString()
        return "OnCharacteristicRead(uuid=$uuid, instanceId=$instanceId, value=$value(size=$size), status=$gattStatus)"
    }
}

data class OnCharacteristicWrite(
    val characteristic: BluetoothGattCharacteristic,
    val status: GattStatus
) {
    override fun toString(): String =
        "OnCharacteristicWrite(uuid=${characteristic.uuid}, status=${status.asGattStatusString()})"
}

data class OnCharacteristicChanged(
    val characteristic: BluetoothGattCharacteristic,
    val value: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OnCharacteristicChanged

        return characteristic.uuid == other.characteristic.uuid &&
            characteristic.instanceId == other.characteristic.instanceId &&
            value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = characteristic.uuid.hashCode()
        result = 31 * result + characteristic.instanceId
        result = 31 * result + value.contentHashCode()
        return result
    }

    override fun toString(): String =
        "OnCharacteristicChanged(uuid=${characteristic.uuid}, value=$value(size=${value.size}))"
}

data class OnDescriptorRead(
    val descriptor: BluetoothGattDescriptor,
    val value: ByteArray,
    val status: GattStatus
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OnDescriptorRead

        return status == other.status &&
            descriptor.uuid == other.descriptor.uuid &&
            value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = descriptor.uuid.hashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + status
        return result
    }

    override fun toString(): String {
        val uuid = descriptor.uuid
        val gattStatus = status.asGattStatusString()
        return "OnDescriptorRead(uuid=$uuid, value=$value(size=${value.size}), status=$gattStatus)"
    }
}

data class OnDescriptorWrite(
    val descriptor: BluetoothGattDescriptor,
    val status: GattStatus
) {
    override fun toString(): String =
        "OnDescriptorWrite(uuid=${descriptor.uuid}, status=${status.asGattStatusString()})"
}

data class OnMtuChanged(val mtu: Int, val status: GattStatus) {
    override fun toString(): String =
        "OnMtuChanged(mtu=$mtu, status=${status.asGattStatusString()})"
}
