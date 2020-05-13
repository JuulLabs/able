/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.keepalive.test

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.RemoteException
import com.juul.able.Able
import com.juul.able.android.connectGatt
import com.juul.able.device.ConnectGattResult
import com.juul.able.device.ConnectGattResult.Failure
import com.juul.able.gatt.ConnectionLost
import com.juul.able.gatt.Gatt
import com.juul.able.gatt.OnCharacteristicChanged
import com.juul.able.gatt.OnReadRemoteRssi
import com.juul.able.keepalive.KeepAliveGatt
import com.juul.able.keepalive.KeepAliveGatt.Configuration
import com.juul.able.keepalive.NotReady
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
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

private const val MAC_ADDRESS = "00:11:22:33:FF:EE"
private const val DISCONNECT_TIMEOUT = 5_000L

private val testUuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")

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
        val bluetoothDevice = mockBluetoothDevice()
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
        val bluetoothDevice = mockBluetoothDevice()
        val onCharacteristicChanged1 = BroadcastChannel<OnCharacteristicChanged>(BUFFERED)
        val gatt1 = mockk<Gatt> {
            every { onCharacteristicChanged } returns onCharacteristicChanged1.asFlow()
            coEvery { disconnect() } returns Unit
        }
        val gatt2 = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }

        mockkStatic("com.juul.able.android.BluetoothDeviceKt") {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returnsMany listOf(gatt1, gatt2).map { ConnectGattResult.Success(it) }

            val states = Channel<State>(BUFFERED)
            val keepAlive = keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                configuration = Configuration(DISCONNECT_TIMEOUT, stateCapacity = BUFFERED)
            )
            keepAlive.state.onEach(states::send).launchIn(this, start = UNDISPATCHED)
            keepAlive.connect()

            assertEquals(
                expected = Connecting,
                actual = states.receive()
            )
            assertEquals(
                expected = Connected,
                actual = states.receive()
            )

            onCharacteristicChanged1.close() // Simulates connection drop.

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

            coVerify(exactly = 2) { bluetoothDevice.connectGatt(any()) }
            coVerify(exactly = 1) { gatt1.disconnect() }
            coVerify(exactly = 1) { gatt2.disconnect() }
        }
    }

    @Test
    fun `Subsequent calls to 'open' return false`() {
        val bluetoothDevice = mockBluetoothDevice()
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
    fun `Bluetooth IO when not connected throws NotReady`() = runBlocking<Unit> {
        val keepAlive = KeepAliveGatt(
            androidContext = mockk(relaxed = true),
            bluetoothDevice = mockk(),
            configuration = Configuration(DISCONNECT_TIMEOUT, stateCapacity = BUFFERED)
        )

        assertFailsWith<NotReady> {
            keepAlive.discoverServices()
        }
    }

    private class EndOfTest : Exception()

    @Test
    fun `Retries connection on connection failure`() {
        val connectionAttempts = 5
        val bluetoothDevice = mockBluetoothDevice()
        val lock = Mutex(locked = true)
        val exceptionHandler = CoroutineExceptionHandler { _, cause ->
            if (cause is EndOfTest) lock.unlock()
        }

        mockkStatic("com.juul.able.android.BluetoothDeviceKt") {
            var attempt = 0
            coEvery {
                bluetoothDevice.connectGatt(any())
            } answers {
                if (++attempt >= connectionAttempts) throw EndOfTest()
                Failure.Connection(ConnectionLost())
            }

            val keepAlive = KeepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                configuration = Configuration(DISCONNECT_TIMEOUT, exceptionHandler)
            )
            assertTrue(keepAlive.connect())

            runBlocking { lock.lock() } // Wait until we've performed desired # of connect attempts.
            coVerify(exactly = connectionAttempts) { bluetoothDevice.connectGatt(any()) }
        }
    }

    @Test
    fun `Does not retry connection when connection is rejected`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()

        mockkStatic("com.juul.able.android.BluetoothDeviceKt") {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returns Failure.Rejected(RemoteException())

            val scope = CoroutineScope(Job())
            val keepAlive = scope.keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                configuration = Configuration(DISCONNECT_TIMEOUT)
            )

            val completion = Channel<Throwable?>(CONFLATED)
            keepAlive.invokeOnCompletion { cause -> completion.offer(cause) }

            assertTrue(keepAlive.connect())
            keepAlive.state.collect() // Collection aborts when `keepAlive` cancels due to rejection.

            assertTrue(scope.isActive, "KeepAlive cancellation should not cancel parent")
            coVerify(exactly = 1) { bluetoothDevice.connectGatt(any()) }

            val cancellation = completion.receive()
            assertEquals<Class<out Throwable>?>(
                expected = RemoteException::class.java,
                actual = cancellation?.cause?.javaClass
            )
        }
    }

    @Test
    fun `After 'disconnect' request, KeepAliveGatt does not attempt to reconnect`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
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
            val connected = async(start = UNDISPATCHED) {
                keepAlive.state.first { it == Connected }
            }
            keepAlive.connect()
            connected.await()

            val disconnected = async(start = UNDISPATCHED) {
                keepAlive.state.first { it == Disconnected }
            }
            keepAlive.disconnect()
            disconnected.await()

            delay(1_000L) // Wait to make sure `KeepAliveGatt` doesn't make another connect attempt.

            coVerify(exactly = 1) { bluetoothDevice.connectGatt(any()) }
        }
    }

    @Test
    fun `Can connect again after calling disconnect`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
        val gatt1 = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }
        val gatt2 = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }

        mockkStatic("com.juul.able.android.BluetoothDeviceKt") {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returnsMany listOf(gatt1, gatt2).map { ConnectGattResult.Success(it) }

            val keepAlive = KeepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                configuration = Configuration(DISCONNECT_TIMEOUT)
            )
            val ready = async(start = UNDISPATCHED) {
                keepAlive.state.first { it == Connecting || it == Connected }
            }
            keepAlive.connect()
            ready.await()

            val disconnected = async(start = UNDISPATCHED) {
                keepAlive.state.first { it == Disconnected }
            }
            keepAlive.disconnect()
            disconnected.await()

            keepAlive.connect()
            val connected = async(start = UNDISPATCHED) {
                keepAlive.state.first { it == Connected }
            }
            keepAlive.connect()
            connected.await()

            keepAlive.cancelAndJoin()

            coVerify(exactly = 2) { bluetoothDevice.connectGatt(any()) }
            coVerify(exactly = 1) { gatt1.disconnect() }
            coVerify(exactly = 1) { gatt2.disconnect() }
        }
    }

    @Test
    fun `Bluetooth IO methods are forwarded to current Gatt`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
        val service1 = mockk<BluetoothGattService>()
        val service2 = mockk<BluetoothGattService>()
        val mockServices = listOf(service1, service2)
        val characteristic = mockk<BluetoothGattCharacteristic>()
        val descriptor = mockk<BluetoothGattDescriptor>()
        val rssi = OnReadRemoteRssi(-1, GATT_SUCCESS)
        val gatt = mockk<Gatt>(relaxed = true) {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            every { services } returns mockServices
            every { getService(testUuid) } returns service1
            coEvery { requestMtu(512) } returns mockk()
            coEvery { discoverServices() } returns GATT_SUCCESS
            coEvery { readRemoteRssi() } returns rssi
            coEvery { disconnect() } returns Unit
        }
        val data = byteArrayOf(0xF0.toByte(), 0x0D)
        val writeType = WRITE_TYPE_DEFAULT

        mockkStatic("com.juul.able.android.BluetoothDeviceKt") {
            coEvery { bluetoothDevice.connectGatt(any()) } returns ConnectGattResult.Success(gatt)

            val keepAlive = KeepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                configuration = Configuration(DISCONNECT_TIMEOUT)
            )

            val connected = async(start = UNDISPATCHED) {
                keepAlive.state.first { it == Connected }
            }
            keepAlive.connect()
            connected.await()
            coVerify(exactly = 1) { bluetoothDevice.connectGatt(any()) }

            assertEquals(
                expected = GATT_SUCCESS,
                actual = keepAlive.discoverServices()
            )
            assertEquals(
                expected = mockServices,
                actual = keepAlive.services
            )
            assertEquals(
                expected = service1,
                actual = keepAlive.getService(testUuid)
            )
            keepAlive.requestMtu(512)
            keepAlive.readCharacteristic(characteristic)
            keepAlive.writeCharacteristic(characteristic, data, writeType)
            keepAlive.writeDescriptor(descriptor, data)
            assertEquals(
                expected = rssi,
                actual = keepAlive.readRemoteRssi()
            )

            coVerify(exactly = 1) { gatt.discoverServices() }
            coVerify(exactly = 1) { gatt.services }
            coVerify(exactly = 1) { gatt.getService(testUuid) }
            coVerify(exactly = 1) { gatt.requestMtu(512) }
            coVerify(exactly = 1) { gatt.readCharacteristic(characteristic) }
            coVerify(exactly = 1) { gatt.writeCharacteristic(characteristic, data, writeType) }
            coVerify(exactly = 1) { gatt.writeDescriptor(descriptor, data) }
            coVerify(exactly = 1) { gatt.readRemoteRssi() }
        }
    }

    @Test
    fun `toString after close doesn't throw Exception`() {
        val bluetoothDevice = mockBluetoothDevice()
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

private fun mockBluetoothDevice(): BluetoothDevice = mockk {
    every { this@mockk.toString() } returns MAC_ADDRESS
}

private fun <T> Flow<T>.launchIn(
    scope: CoroutineScope,
    start: CoroutineStart = CoroutineStart.DEFAULT
): Job = scope.launch(start = start) {
    collect()
}
