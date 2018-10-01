/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.processor

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.juul.able.experimental.Gatt
import com.juul.able.experimental.OnCharacteristicRead
import com.juul.able.experimental.OnCharacteristicWrite
import com.juul.able.experimental.OnDescriptorWrite
import com.juul.able.experimental.WriteType

fun Gatt.withProcessors(vararg processors: Processor) = GattProcessor(this, processors)

class GattProcessor(
    private val gatt: Gatt,
    private val processors: Array<out Processor>
) : Gatt by gatt {

    override suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic
    ): OnCharacteristicRead {
        val onCharacteristicRead = gatt.readCharacteristic(characteristic)

        val processedValue = processors.fold(onCharacteristicRead.value) { processed, processor ->
            processor.readCharacteristic(characteristic, processed)
        }

        return onCharacteristicRead.copy(value = processedValue)
    }

    override suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): OnCharacteristicWrite {
        val processedValue = processors.fold(value) { processed, processor ->
            processor.writeCharacteristic(characteristic, processed, writeType)
        }

        return gatt.writeCharacteristic(characteristic, processedValue, writeType)
    }

    override suspend fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): OnDescriptorWrite {
        val processedValue = processors.fold(value) { processed, processor ->
            processor.writeDescriptor(descriptor, processed)
        }

        return gatt.writeDescriptor(descriptor, processedValue)
    }
}
