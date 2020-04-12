/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.test

import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.juul.able.gatt.OnCharacteristicChanged
import com.juul.able.gatt.OnCharacteristicRead
import com.juul.able.gatt.OnDescriptorRead
import com.juul.able.gatt.OnMtuChanged
import com.juul.able.gatt.OnReadRemoteRssi
import com.juul.able.test.gatt.FakeBluetoothGattCharacteristic as FakeCharacteristic
import com.juul.able.test.gatt.FakeBluetoothGattDescriptor as FakeDescriptor
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import nl.jqno.equalsverifier.EqualsVerifier

private val testUuid = "01234567-89ab-cdef-0123-456789abcdef".toUuid()

class EventsTest {

    @Test
    fun `Verify equals of OnCharacteristicRead`() {
        verifyEquals<OnCharacteristicRead>()
    }

    @Test
    fun `Verify toString of OnCharacteristicRead`() {
        val value = createByteArray(size = 256)
        val event = OnCharacteristicRead(
            characteristic = FakeCharacteristic(testUuid),
            value = value,
            status = GATT_SUCCESS
        )

        assertEquals(
            expected = "OnCharacteristicRead(" +
                "uuid=$testUuid, " +
                "instanceId=0, " +
                "value=[B@${value.hashCode().hex()}(size=256), " +
                "status=GATT_SUCCESS(0)" +
            ")",
            actual = event.toString()
        )
    }

    @Test
    fun `Verify equals of OnCharacteristicChanged`() {
        verifyEquals<OnCharacteristicChanged>()
    }

    @Test
    fun `Verify toString of OnCharacteristicChanged`() {
        val value = createByteArray(size = 256)
        val event = OnCharacteristicChanged(
            characteristic = FakeCharacteristic(testUuid),
            value = value
        )

        assertEquals(
            expected = "OnCharacteristicChanged(" +
                "uuid=$testUuid, " +
                "value=[B@${value.hashCode().hex()}(size=256)" +
            ")",
            actual = event.toString()
        )
    }

    @Test
    fun `Verify equals of OnDescriptorRead`() {
        verifyEquals<OnDescriptorRead>()
    }

    @Test
    fun `Verify toString of OnDescriptorRead`() {
        val value = createByteArray(size = 256)
        val event = OnDescriptorRead(
            descriptor = FakeDescriptor(testUuid),
            value = value,
            status = GATT_SUCCESS
        )

        assertEquals(
            expected = "OnDescriptorRead(" +
                "uuid=$testUuid, " +
                "value=[B@${value.hashCode().hex()}(size=256), " +
                "status=GATT_SUCCESS(0)" +
            ")",
            actual = event.toString()
        )
    }

    @Test
    fun `Verify equals of OnMtuChanged`() {
        verifyEquals<OnMtuChanged>()
    }

    @Test
    fun `Verify toString of OnMtuChanged`() {
        val event = OnMtuChanged(status = GATT_SUCCESS, mtu = 512)

        assertEquals(
            expected = "OnMtuChanged(mtu=512, status=GATT_SUCCESS(0))",
            actual = event.toString()
        )
    }

    @Test
    fun `Verify toString of OnReadRemoteRssi`() {
        val event = OnReadRemoteRssi(rssi = 0, status = GATT_SUCCESS)

        assertEquals(
            expected = "OnReadRemoteRssi(rssi=0, status=GATT_SUCCESS(0))",
            actual = event.toString()
        )
    }
}

private val redCharacteristic = FakeCharacteristic("63057836-0b22-4341-969a-8fee3a8be2b3".toUuid())
private val blackCharacteristic = FakeCharacteristic("2a5346f9-1aec-4752-acec-5d269aa96e7d".toUuid())

private val redDescriptor = FakeDescriptor("bce47f52-6c2a-43e9-a382-2c460fcc6f6c".toUuid())
private val blackDescriptor = FakeDescriptor("de923c26-b18a-474a-84e0-7837300fc666".toUuid())

/**
 * Preconfigures [EqualsVerifier] for validating proper implementation of `GattCallback` event
 * `data class`es that have custom `equals` and `hashCode` implementations:
 *
 * > `EqualsVerifier` can be used in unit tests to verify whether the contract for the `equals` and
 * > `hashCode` methods in a class is met.
 */
private inline fun <reified T> verifyEquals() {
    EqualsVerifier
        .forClass(T::class.java)
        .withPrefabValues(BluetoothGattDescriptor::class.java, redDescriptor, blackDescriptor)
        .withPrefabValues(
            BluetoothGattCharacteristic::class.java,
            redCharacteristic,
            blackCharacteristic
        )
        .verify()
}

private fun createByteArray(size: Int) = ByteArray(size) { it.toByte() }
private fun Int.hex() = Integer.toHexString(this)
private fun String.toUuid(): UUID = UUID.fromString(this)
