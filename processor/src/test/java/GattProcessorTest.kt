/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.processor.test

import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattDescriptor
import com.juul.able.gatt.Gatt
import com.juul.able.gatt.OnCharacteristicRead
import com.juul.able.gatt.OnCharacteristicWrite
import com.juul.able.gatt.OnDescriptorWrite
import com.juul.able.gatt.WriteType
import com.juul.able.processor.Processor
import com.juul.able.processor.withProcessors
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

private val testUuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")

class GattProcessorTest {

    private val reverseBytes = object : Processor {
        override fun readCharacteristic(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ): ByteArray = value.reversedArray()

        override fun writeCharacteristic(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            writeType: WriteType
        ): ByteArray = value.reversedArray()

        override fun writeDescriptor(
            descriptor: BluetoothGattDescriptor,
            value: ByteArray
        ): ByteArray = value.reversedArray()
    }

    @Test
    fun `Processor modifies readCharacteristic data`() {
        val characteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns testUuid
            every { instanceId } returns 0
        }
        val gatt = mockk<Gatt> {
            coEvery { readCharacteristic(characteristic) } returns OnCharacteristicRead(
                characteristic = characteristic,
                value = byteArrayOf(0xF, 0x0, 0x0, 0xD),
                status = GATT_SUCCESS
            )
        }
        val withProcessors = gatt.withProcessors(reverseBytes)

        val result = runBlocking {
            withProcessors.readCharacteristic(characteristic)
        }

        val expected = OnCharacteristicRead(
            characteristic = characteristic,
            value = byteArrayOf(0xD, 0x0, 0x0, 0xF),
            status = GATT_SUCCESS
        )
        assertEquals(
            expected = expected.value.toList(),
            actual = result.value.toList()
        )
        assertEquals(
            expected = expected,
            actual = result
        )
    }

    @Test
    fun `Processor modifies writeCharacteristic data`() {
        val characteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns testUuid
            every { instanceId } returns 0
        }
        val slot = slot<ByteArray>()
        val gatt = mockk<Gatt> {
            coEvery {
                writeCharacteristic(characteristic, capture(slot), any())
            } returns OnCharacteristicWrite(characteristic = characteristic, status = GATT_SUCCESS)
        }
        val withProcessors = gatt.withProcessors(reverseBytes)

        val result = runBlocking {
            withProcessors.writeCharacteristic(
                characteristic = characteristic,
                value = byteArrayOf(0xF, 0x0, 0x0, 0xD),
                writeType = WRITE_TYPE_DEFAULT
            )
        }

        assertEquals(
            expected = OnCharacteristicWrite(
                characteristic = characteristic,
                status = GATT_SUCCESS
            ),
            actual = result
        )
        assertEquals(
            expected = byteArrayOf(0xD, 0x0, 0x0, 0xF).toList(),
            actual = slot.captured.toList()
        )
    }

    @Test
    fun `Processor modifies writeDescriptor data`() {
        val descriptor = mockk<BluetoothGattDescriptor> {
            every { uuid } returns testUuid
        }
        val slot = slot<ByteArray>()
        val gatt = mockk<Gatt> {
            coEvery {
                writeDescriptor(descriptor, capture(slot))
            } returns OnDescriptorWrite(descriptor = descriptor, status = GATT_SUCCESS)
        }
        val withProcessors = gatt.withProcessors(reverseBytes)

        val result = runBlocking {
            withProcessors.writeDescriptor(
                descriptor = descriptor,
                value = byteArrayOf(0xF, 0x0, 0x0, 0xD)
            )
        }

        assertEquals(
            expected = OnDescriptorWrite(
                descriptor = descriptor,
                status = GATT_SUCCESS
            ),
            actual = result
        )
        assertEquals(
            expected = byteArrayOf(0xD, 0x0, 0x0, 0xF).toList(),
            actual = slot.captured.toList()
        )
    }
}
