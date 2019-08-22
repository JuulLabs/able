/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.os.RemoteException
import com.juul.able.experimental.messenger.GattCallback
import com.juul.able.experimental.messenger.GattCallbackConfig
import com.juul.able.experimental.messenger.Messenger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.BeforeClass
import org.junit.Test
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoroutinesGattTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            Able.logger = NoOpLogger()
        }
    }

    /**
     * Verifies that [BluetoothGattCharacteristic]s that are received by the [GattCallback] remain
     * in-order when consumed from the [Gatt.onCharacteristicChanged] channel.
     */
    @Test
    fun correctOrderOfOnCharacteristicChanged() {
        val numberOfFakeCharacteristicNotifications = 10_000L
        val numberOfFakeBinderThreads = 10
        val onCharacteristicChangedCapacity = numberOfFakeCharacteristicNotifications.toInt()

        val bluetoothGatt = mockk<BluetoothGatt>()
        val callback = GattCallback(GattCallbackConfig(onCharacteristicChangedCapacity)).apply {
            onConnectionStateChange(bluetoothGatt, GATT_SUCCESS, STATE_CONNECTED)
        }
        val messenger = Messenger(bluetoothGatt, callback)
        val gatt = CoroutinesGatt(bluetoothGatt, messenger)

        val binderThreads = FakeBinderThreadHandler(numberOfFakeBinderThreads)
        for (i in 0..numberOfFakeCharacteristicNotifications) {
            binderThreads.enqueue {
                val characteristic = mockCharacteristic(data = i.asByteArray())
                callback.onCharacteristicChanged(bluetoothGatt, characteristic)
            }
        }

        runBlocking {
            var i = 0L
            gatt.onCharacteristicChanged.openSubscription().also { subscription ->
                binderThreads.start()

                subscription.consumeEach { (_, value) ->
                    assertEquals(i++, value.longValue)

                    if (i == numberOfFakeCharacteristicNotifications) {
                        subscription.cancel()
                    }
                }
            }
            assertEquals(numberOfFakeCharacteristicNotifications, i)

            binderThreads.stop()
        }
    }

    @Test
    fun readCharacteristic_bluetoothGattReturnsFalse_doesNotDeadlock() {
        val bluetoothGatt = mockk<BluetoothGatt> {
            every { readCharacteristic(any()) } returns false
        }
        val callback = GattCallback(GattCallbackConfig()).apply {
            onConnectionStateChange(bluetoothGatt, GATT_SUCCESS, STATE_CONNECTED)
        }
        val messenger = Messenger(bluetoothGatt, callback)
        val gatt = CoroutinesGatt(bluetoothGatt, messenger)

        assertFailsWith(RemoteException::class, "First invocation") {
            runBlocking {
                withTimeout(5_000L) {
                    gatt.readCharacteristic(mockCharacteristic())
                }
            }
        }

        // Perform another read to verify that the previous failure did not deadlock `Messenger`.
        assertFailsWith(RemoteException::class, "Second invocation") {
            runBlocking {
                withTimeout(5_000L) {
                    gatt.readCharacteristic(mockCharacteristic())
                }
            }
        }
    }
}

private fun mockCharacteristic(
    uuid: UUID = UUID.randomUUID(),
    data: ByteArray? = null
): BluetoothGattCharacteristic = mockk {
    every { getUuid() } returns uuid
    every { value } returns data
}

private fun Long.asByteArray(): ByteArray = ByteBuffer.allocate(8).putLong(this).array()

private val ByteArray.longValue: Long
    get() = ByteBuffer.wrap(this).long
