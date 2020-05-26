/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.throwable.test

import android.bluetooth.BluetoothGatt.GATT_FAILURE
import android.bluetooth.BluetoothGatt.GATT_READ_NOT_PERMITTED
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGatt.GATT_WRITE_NOT_PERMITTED
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.juul.able.gatt.Gatt
import com.juul.able.gatt.OnCharacteristicRead
import com.juul.able.gatt.OnCharacteristicWrite
import com.juul.able.gatt.OnDescriptorWrite
import com.juul.able.gatt.OnMtuChanged
import com.juul.able.gatt.OnReadRemoteRssi
import com.juul.able.throwable.discoverServicesOrThrow
import com.juul.able.throwable.readCharacteristicOrThrow
import com.juul.able.throwable.readRemoteRssiOrThrow
import com.juul.able.throwable.requestMtuOrThrow
import com.juul.able.throwable.setCharacteristicNotificationOrThrow
import com.juul.able.throwable.writeCharacteristicOrThrow
import com.juul.able.throwable.writeDescriptorOrThrow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

private val testUuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")

class GattTest {

    @Test
    fun `discoverServicesOrThrow throws IllegalStateException for non-GATT_SUCCESS response`() {
        val gatt = mockk<Gatt> {
            coEvery { discoverServices() } returns GATT_FAILURE
        }

        assertFailsWith<IllegalStateException> {
            runBlocking {
                gatt.discoverServicesOrThrow()
            }
        }
    }

    @Test
    fun `readRemoteRssiOrThrow throws IllegalStateException for non-GATT_SUCCESS response`() {
        val gatt = mockk<Gatt> {
            coEvery { readRemoteRssi() } returns OnReadRemoteRssi(rssi = 0, status = GATT_FAILURE)
        }

        assertFailsWith<IllegalStateException> {
            runBlocking {
                gatt.readRemoteRssiOrThrow()
            }
        }
    }

    @Test
    fun `readRemoteRssiOrThrow returns RSSI as Int for GATT_SUCCESS response`() {
        val gatt = mockk<Gatt> {
            coEvery { readRemoteRssi() } returns OnReadRemoteRssi(rssi = 1, status = GATT_SUCCESS)
        }

        val result = runBlocking {
            gatt.readRemoteRssiOrThrow()
        }

        assertEquals(
            expected = 1,
            actual = result
        )
    }

    @Test
    fun `readCharacteristicOrThrow throws IllegalStateException for non-GATT_SUCCESS response`() {
        val characteristic = mockk<BluetoothGattCharacteristic> {
            every<UUID> { uuid } returns testUuid
        }
        val gatt = mockk<Gatt> {
            coEvery { readCharacteristic(characteristic) } returns OnCharacteristicRead(
                characteristic = characteristic,
                value = byteArrayOf(),
                status = GATT_READ_NOT_PERMITTED
            )
        }

        assertFailsWith<IllegalStateException> {
            runBlocking {
                gatt.readCharacteristicOrThrow(characteristic)
            }
        }
    }

    @Test
    fun `readCharacteristicOrThrow returns ByteArray for GATT_SUCCESS response`() {
        val characteristic = mockk<BluetoothGattCharacteristic> {
            every<UUID> { uuid } returns testUuid
        }
        val gatt = mockk<Gatt> {
            coEvery { readCharacteristic(characteristic) } returns OnCharacteristicRead(
                characteristic = characteristic,
                value = byteArrayOf(0xF, 0x0, 0x0, 0xD),
                status = GATT_SUCCESS
            )
        }

        val result = runBlocking {
            gatt.readCharacteristicOrThrow(characteristic)
        }

        // Convert to `List` so equality verification is done on the contents.
        assertEquals(
            expected = byteArrayOf(0xF, 0x0, 0x0, 0xD).toList(),
            actual = result.toList()
        )
    }

    @Test
    fun `setCharacteristicNotificationOrThrow throws IllegalStateException for false return`() {
        val gatt = mockk<Gatt> {
            coEvery { setCharacteristicNotification(any(), any()) } returns false
        }

        assertFailsWith<IllegalStateException> {
            val characteristic = mockk<BluetoothGattCharacteristic> {
                every<UUID> { uuid } returns testUuid
            }
            gatt.setCharacteristicNotificationOrThrow(characteristic, true)
        }
    }

    @Test
    fun `writeCharacteristicOrThrow throws IllegalStateException for non-GATT_SUCCESS response`() {
        val characteristic = mockk<BluetoothGattCharacteristic> {
            every<UUID> { uuid } returns testUuid
        }
        val gatt = mockk<Gatt> {
            coEvery { writeCharacteristic(characteristic, any(), any()) } returns OnCharacteristicWrite(
                characteristic = characteristic,
                status = GATT_WRITE_NOT_PERMITTED
            )
        }

        assertFailsWith<IllegalStateException> {
            runBlocking {
                gatt.writeCharacteristicOrThrow(characteristic, byteArrayOf())
            }
        }
    }

    @Test
    fun `writeDescriptorOrThrow throws IllegalStateException for non-GATT_SUCCESS response`() {
        val descriptor = mockk<BluetoothGattDescriptor> {
            every { uuid } returns testUuid
        }
        val gatt = mockk<Gatt> {
            coEvery { writeDescriptor(descriptor, any()) } returns OnDescriptorWrite(
                descriptor = descriptor,
                status = GATT_WRITE_NOT_PERMITTED
            )
        }

        assertFailsWith<IllegalStateException> {
            runBlocking {
                gatt.writeDescriptorOrThrow(descriptor, byteArrayOf())
            }
        }
    }

    @Test
    fun `requestMtuOrThrow throws IllegalStateException for non-GATT_SUCCESS response`() {
        val gatt = mockk<Gatt> {
            coEvery { requestMtu(any()) } returns OnMtuChanged(
                mtu = 23,
                status = GATT_FAILURE
            )
        }

        assertFailsWith<IllegalStateException> {
            runBlocking {
                gatt.requestMtuOrThrow(1024)
            }
        }
    }

    @Test
    fun `requestMtuOrThrow returns MTU as Int for GATT_SUCCESS response`() {
        val gatt = mockk<Gatt> {
            coEvery { requestMtu(any()) } returns OnMtuChanged(
                mtu = 128,
                status = GATT_SUCCESS
            )
        }

        assertEquals(
            expected = 128,
            actual = runBlocking { gatt.requestMtuOrThrow(128) }
        )
    }
}
