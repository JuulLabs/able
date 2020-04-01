/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.processor

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.juul.able.gatt.Gatt
import com.juul.able.gatt.OnCharacteristicRead
import com.juul.able.gatt.OnCharacteristicWrite
import com.juul.able.gatt.OnDescriptorWrite
import com.juul.able.gatt.WriteType

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
