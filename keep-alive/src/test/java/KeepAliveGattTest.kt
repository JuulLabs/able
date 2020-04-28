/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.keepalive.test

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import com.juul.able.Able
import com.juul.able.android.connectGatt
import com.juul.able.device.ConnectGattResult
import com.juul.able.gatt.Gatt
import com.juul.able.keepalive.KeepAliveGatt
import com.juul.able.keepalive.State.Connected
import com.juul.able.keepalive.State.Connecting
import com.juul.able.keepalive.keepAliveGatt
import com.juul.able.logger.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private const val DISCONNECT_TIMEOUT = 5_000L

class KeepAliveGattTest {

    @BeforeTest
    fun setup() {
        Able.logger = object : Logger {
            override fun isLoggable(priority: Int): Boolean = true
            override fun log(priority: Int, throwable: Throwable?, message: String) {
                println("[$priority] $message")
                throwable?.printStackTrace()
            }
        }
    }

    @Test
    fun `When parent scope is lazy, connect occurs when parent is started`() = runBlocking {
        val callbackSlot = slot<BluetoothGattCallback>()
        lateinit var bluetoothGatt: BluetoothGatt
        val bluetoothDevice = mockk<BluetoothDevice> {
            bluetoothGatt = createBluetoothGatt(this@mockk)
            every { connectGatt(any(), false, capture(callbackSlot)) } returns bluetoothGatt
            every { this@mockk.toString() } returns "00:11:22:33:FF:EE"
        }

        val gatt = AtomicReference<KeepAliveGatt>()
        val job = launch(start = LAZY) {
            gatt.set(keepAliveGatt(mockk(relaxed = true), bluetoothDevice, DISCONNECT_TIMEOUT) {})
        }

        delay(500L)
        assertFalse(callbackSlot.isCaptured)
        assertNull(gatt.get())

        job.start()

        // Wait for `CoroutinesGatt` to spin up and provide us with the `GattCallback`.
        while (!callbackSlot.isCaptured) yield()

        assertEquals(
            expected = Connecting,
            actual = gatt.get().state.firstOrNull()
        )
    }

    @Test
    fun `When Coroutine is cancelled, Gatt is disconnected`() = runBlocking {
        val bluetoothDevice = mockk<BluetoothDevice> {
            every { this@mockk.toString() } returns "00:11:22:33:FF:EE"
        }
        val gatt = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }

        mockkStatic("com.juul.able.android.BluetoothDeviceKt")
        try {
            coEvery { bluetoothDevice.connectGatt(any()) } returns ConnectGattResult.Success(gatt)

            coroutineScope {
                val result = AtomicReference<KeepAliveGatt>()
                val job = launch {
                    result.set(keepAliveGatt(
                        androidContext = mockk(relaxed = true),
                        device = bluetoothDevice,
                        disconnectTimeoutMillis = DISCONNECT_TIMEOUT,
                        onConnectAction = {}
                    ))
                }

                val keepAliveGatt = result.awaitNonNull()
                keepAliveGatt.state.first { it == Connected }
                job.cancel()
            }

            coVerify {
                gatt.disconnect()
            }
        } finally {
            unmockkStatic("com.juul.able.android.BluetoothDeviceKt")
        }
    }
}

private fun createBluetoothGatt(
    bluetoothDevice: BluetoothDevice
): BluetoothGatt = mockk {
    every { device } returns bluetoothDevice
    every { close() } returns Unit
}

private suspend fun <T> AtomicReference<T>.awaitNonNull(): T {
    var value: T?
    do {
        yield()
        value = get()
    } while (value == null)
    return value
}
