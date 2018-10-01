/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import java.util.Arrays

data class OnConnectionStateChange(
    val status: GattStatus,
    val newState: GattState
) {
    override fun toString(): String {
        val connectionStatus = status.asGattConnectionStatusString()
        val gattState = newState.asGattStateString()
        return "OnConnectionStateChange(status=$connectionStatus, newState=$gattState}"
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

        @Suppress("UnsafeCast") // Safe per Java class comparison above.
        other as OnCharacteristicRead

        return status == other.status
            && characteristic.uuid == other.characteristic.uuid
            && Arrays.equals(value, other.value)
    }

    @Suppress("MagicNumber")
    override fun hashCode(): Int {
        var result = characteristic.uuid.hashCode()
        result = 31 * result + Arrays.hashCode(value)
        result = 31 * result + status
        return result
    }

    override fun toString(): String {
        val uuid = characteristic.uuid
        val size = value.size
        val gattStatus = status.asGattStatusString()
        return "OnCharacteristicRead(uuid=$uuid, value=$value, value.size=$size, status=$gattStatus)"
    }
}

data class OnCharacteristicWrite(
    val characteristic: BluetoothGattCharacteristic,
    val status: GattStatus
) {
    override fun toString(): String {
        val uuid = characteristic.uuid
        val gattStatus = status.asGattStatusString()
        return "OnCharacteristicWrite(uuid=$uuid, status=$gattStatus)"
    }
}

data class OnCharacteristicChanged(
    val characteristic: BluetoothGattCharacteristic,
    val value: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        @Suppress("UnsafeCast") // Safe per Java class comparison above.
        other as OnCharacteristicChanged

        return characteristic.uuid == other.characteristic.uuid && Arrays.equals(value, other.value)
    }

    @Suppress("MagicNumber")
    override fun hashCode(): Int {
        var result = characteristic.uuid.hashCode()
        result = 31 * result + Arrays.hashCode(value)
        return result
    }

    override fun toString(): String {
        val uuid = characteristic.uuid
        val size = value.size
        return "OnCharacteristicChanged(uuid=$uuid, value=$value, value.size=$size)"
    }
}

data class OnDescriptorRead(
    val descriptor: BluetoothGattDescriptor,
    val value: ByteArray,
    val status: GattStatus
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        @Suppress("UnsafeCast") // Safe per Java class comparison above.
        other as OnDescriptorRead

        return status == other.status
            && descriptor.uuid == other.descriptor.uuid
            && Arrays.equals(value, other.value)
    }

    @Suppress("MagicNumber")
    override fun hashCode(): Int {
        var result = descriptor.uuid.hashCode()
        result = 31 * result + Arrays.hashCode(value)
        result = 31 * result + status
        return result
    }

    override fun toString(): String {
        val uuid = descriptor.uuid
        val size = value.size
        val gattStatus = status.asGattStatusString()
        return "OnDescriptorRead(uuid=$uuid, value=$value, value.size=$size, status=$gattStatus)"
    }
}

data class OnDescriptorWrite(
    val descriptor: BluetoothGattDescriptor,
    val status: GattStatus
) {
    override fun toString(): String {
        val uuid = descriptor.uuid
        val gattStatus = status.asGattStatusString()
        return "OnDescriptorWrite(uuid=$uuid, status=$gattStatus)"
    }
}

data class OnReliableWriteCompleted(val status: GattStatus) {
    override fun toString(): String =
        "OnReliableWriteCompleted(status=${status.asGattStatusString()})"
}

data class OnMtuChanged(val mtu: Int, val status: GattStatus) {
    override fun toString(): String =
        "OnMtuChanged(mtu=$mtu, status=${status.asGattStatusString()})"
}
