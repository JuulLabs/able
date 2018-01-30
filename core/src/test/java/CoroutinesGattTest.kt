/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import com.juul.able.experimental.messenger.GattCallback
import com.juul.able.experimental.messenger.GattCallbackConfig
import com.juul.able.experimental.messenger.Messenger
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.runBlocking
import org.junit.BeforeClass
import org.junit.Test
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.test.assertEquals

class CoroutinesGattTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            // Useful for debugging Gatt related unit tests:
//            Timber.plant(UnitTestTree())
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

        val bluetoothGatt = mock<BluetoothGatt>()
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
}

private fun mockCharacteristic(
    uuid: UUID = UUID.randomUUID(),
    data: ByteArray? = null
): BluetoothGattCharacteristic = mock {
    on { getUuid() }.thenReturn(uuid)
    on { value }.thenReturn(data)
}

private fun Long.asByteArray(): ByteArray = ByteBuffer.allocate(8).putLong(this).array()

private val ByteArray.longValue: Long
    get() = ByteBuffer.wrap(this).long
