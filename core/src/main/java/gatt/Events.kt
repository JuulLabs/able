/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.gatt

import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

interface HasGattStatus {
    val status: GattStatus
}

val HasGattStatus.isSuccess: Boolean get() = status == GATT_SUCCESS

data class OnConnectionStateChange(
    override val status: GattStatus,
    val newState: GattConnectionState
) : HasGattStatus {
    override fun toString(): String {
        val connectionStatus = status.asGattConnectionStatusString()
        val connectionState = newState.asGattConnectionStateString()
        return "OnConnectionStateChange(status=$connectionStatus, newState=$connectionState)"
    }
}

internal data class OnServicesDiscovered(
    override val status: GattStatus
) : HasGattStatus {
    override fun toString() = "OnServicesDiscovered(status=${status.asGattStatusString()})"
}

data class OnMtuChanged(
    val mtu: Int,
    override val status: GattStatus
) : HasGattStatus {
    override fun toString(): String =
        "OnMtuChanged(mtu=$mtu, status=${status.asGattStatusString()})"
}

data class OnReadRemoteRssi(
    val rssi: Int,
    override val status: GattStatus
) : HasGattStatus {
    override fun toString(): String =
        "OnReadRemoteRssi(rssi=$rssi, status=${status.asGattStatusString()})"
}

data class OnCharacteristicRead(
    val characteristic: BluetoothGattCharacteristic,
    val value: ByteArray,
    override val status: GattStatus
) : HasGattStatus {
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
    override val status: GattStatus
) : HasGattStatus {
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
    override val status: GattStatus
) : HasGattStatus {
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
    override val status: GattStatus
) : HasGattStatus {
    override fun toString(): String =
        "OnDescriptorWrite(uuid=${descriptor.uuid}, status=${status.asGattStatusString()})"
}
