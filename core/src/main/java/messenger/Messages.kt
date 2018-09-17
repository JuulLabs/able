/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.messenger

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.juul.able.experimental.GattState
import com.juul.able.experimental.GattStatus
import kotlinx.coroutines.experimental.CompletableDeferred
import java.util.Arrays

internal sealed class Message {

    abstract val response: CompletableDeferred<Boolean>

    internal data class DiscoverServices(
        override val response: CompletableDeferred<Boolean>
    ) : Message()

    internal data class ReadCharacteristic(
        val characteristic: BluetoothGattCharacteristic,
        override val response: CompletableDeferred<Boolean>
    ) : Message()

    internal data class WriteCharacteristic(
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray,
        val writeType: Int,
        override val response: CompletableDeferred<Boolean>
    ) : Message()

    internal data class RequestMtu(
        val mtu: Int,
        override val response: CompletableDeferred<Boolean>
    ) : Message()

    internal data class WriteDescriptor(
        val descriptor: BluetoothGattDescriptor,
        val value: ByteArray,
        override val response: CompletableDeferred<Boolean>
    ) : Message()
}

data class OnConnectionStateChange(
    val status: GattStatus,
    val newState: GattState
)

data class OnServicesDiscovered(val status: GattStatus)

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
}

data class OnCharacteristicWrite(
    val characteristic: BluetoothGattCharacteristic,
    val status: GattStatus
)

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
}

data class OnDescriptorWrite(
    val descriptor: BluetoothGattDescriptor,
    val status: GattStatus
)

data class OnReliableWriteCompleted(val status: GattStatus)

data class OnMtuChanged(val mtu: Int, val status: GattStatus)
