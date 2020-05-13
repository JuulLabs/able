/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.keepalive.test

import android.bluetooth.BluetoothDevice
import com.juul.able.Able
import com.juul.able.android.connectGatt
import com.juul.able.device.ConnectGattResult
import com.juul.able.gatt.Gatt
import com.juul.able.gatt.OnCharacteristicChanged
import com.juul.able.keepalive.KeepAliveGatt
import com.juul.able.keepalive.KeepAliveGatt.Configuration
import com.juul.able.keepalive.State
import com.juul.able.keepalive.State.Connected
import com.juul.able.keepalive.State.Connecting
import com.juul.able.keepalive.State.Disconnected
import com.juul.able.keepalive.State.Disconnecting
import com.juul.able.keepalive.keepAliveGatt
import com.juul.able.logger.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

private const val MAC_ADDRESS = "00:11:22:33:FF:EE"
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
    fun `When Coroutine is cancelled, Gatt is disconnected`() = runBlocking {
        val bluetoothDevice = mockk<BluetoothDevice> {
            every { this@mockk.toString() } returns MAC_ADDRESS
        }
        val gatt = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }

        mockkStatic("com.juul.able.android.BluetoothDeviceKt") {
            coEvery { bluetoothDevice.connectGatt(any()) } returns ConnectGattResult.Success(gatt)
            val mutex = Mutex(locked = true)

            val job = launch {
                keepAliveGatt(
                    androidContext = mockk(relaxed = true),
                    bluetoothDevice = bluetoothDevice,
                    configuration = Configuration(DISCONNECT_TIMEOUT)
                ).apply {
                    connect()
                }.state.first { it == Connected }

                mutex.unlock()
            }

            mutex.lock() // Wait until "connected".
            job.cancelAndJoin()

            coVerify {
                gatt.disconnect()
            }
        }
    }

    @Test
    fun `When connection drops, Gatt reconnects`() = runBlocking {
        val bluetoothDevice = mockk<BluetoothDevice> {
            every { this@mockk.toString() } returns MAC_ADDRESS
        }
        val mockBluetoothLe = listOf(
            createMockBluetoothLe(),
            createMockBluetoothLe()
        )

        mockkStatic("com.juul.able.android.BluetoothDeviceKt") {
            var i = 0
            coEvery { bluetoothDevice.connectGatt(any()) } answers {
                ConnectGattResult.Success(mockBluetoothLe[i++].gatt)
            }

            val states = Channel<State>(BUFFERED)
            val keepAlive = keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                configuration = Configuration(DISCONNECT_TIMEOUT, stateCapacity = BUFFERED)
            )
            keepAlive.state.onEach(states::send).launchIn(this)
//            keepAlive.state.onEach(states::send).onStart { keepAlive.connect() }.launchIn(this)

            // Give the `launchIn` above time to spin up.
            // todo: Instead of `delay`, use `onStart` when Kotlin/kotlinx.coroutines#1758 is fixed.
            // https://github.com/Kotlin/kotlinx.coroutines/issues/1758
            delay(1_000L)
            keepAlive.connect()

            assertEquals(
                expected = Connecting,
                actual = states.receive()
            )
            assertEquals(
                expected = Connected,
                actual = states.receive()
            )

            mockBluetoothLe[0].onCharacteristicChanged.close() // Simulates connection drop.

            assertEquals(
                expected = Disconnecting,
                actual = states.receive()
            )
            assertEquals(
                expected = Disconnected,
                actual = states.receive()
            )
            assertEquals(
                expected = Connecting,
                actual = states.receive()
            )
            assertEquals(
                expected = Connected,
                actual = states.receive()
            )

            keepAlive.cancelAndJoin()

            coVerify(exactly = 1) { mockBluetoothLe[0].gatt.disconnect() }
            coVerify(exactly = 1) { mockBluetoothLe[1].gatt.disconnect() }
            coVerify(exactly = 2) { bluetoothDevice.connectGatt(any()) }
        }
    }

    @Test
    fun `Subsequent calls to 'open' return false`() {
        val bluetoothDevice = mockk<BluetoothDevice> {
            every { this@mockk.toString() } returns MAC_ADDRESS
        }
        val gatt = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }

        mockkStatic("com.juul.able.android.BluetoothDeviceKt") {
            coEvery { bluetoothDevice.connectGatt(any()) } returns ConnectGattResult.Success(gatt)

            val keepAlive = KeepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                configuration = Configuration(DISCONNECT_TIMEOUT)
            )

            assertTrue(keepAlive.connect())
            assertFalse(keepAlive.connect())
            assertFalse(keepAlive.connect())
        }
    }

    @Test
    fun `toString after close doesn't throw Exception`() {
        val bluetoothDevice = mockk<BluetoothDevice> {
            every { this@mockk.toString() } returns MAC_ADDRESS
        }
        val keepAlive = KeepAliveGatt(
            androidContext = mockk(relaxed = true),
            bluetoothDevice = bluetoothDevice,
            configuration = Configuration(DISCONNECT_TIMEOUT)
        )
        keepAlive.cancel()
        assertEquals(
            expected = "KeepAliveGatt(device=$MAC_ADDRESS, gatt=null, state=null)",
            actual = keepAlive.toString()
        )
    }
}

private data class MockBluetoothLe(
    val gatt: Gatt,
    val onCharacteristicChanged: SendChannel<OnCharacteristicChanged>
)

private fun createMockBluetoothLe(): MockBluetoothLe {
    val channel = BroadcastChannel<OnCharacteristicChanged>(BUFFERED)
    val flow = channel.asFlow()
    return MockBluetoothLe(
        gatt = mockk {
            every { onCharacteristicChanged } returns flow
            coEvery { disconnect() } returns Unit
        },
        onCharacteristicChanged = channel
    )
}
