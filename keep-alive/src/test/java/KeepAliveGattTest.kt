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
import com.juul.able.keepalive.ConnectionRejected
import com.juul.able.keepalive.KeepAliveGatt
import com.juul.able.keepalive.NotReady
import com.juul.able.keepalive.State.Connected
import com.juul.able.keepalive.State.Connecting
import com.juul.able.keepalive.State.Disconnected
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
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

private const val BLUETOOTH_DEVICE_CLASS = "com.juul.able.android.BluetoothDeviceKt"
private const val MAC_ADDRESS = "00:11:22:33:FF:EE"
private const val DISCONNECT_TIMEOUT = 5_000L // milliseconds

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

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery { bluetoothDevice.connectGatt(any()) } returns ConnectGattResult.Success(gatt)
            val mutex = Mutex(locked = true)

            val job = launch {
                keepAliveGatt(
                    androidContext = mockk(relaxed = true),
                    bluetoothDevice = bluetoothDevice,
                    disconnectTimeoutMillis = DISCONNECT_TIMEOUT
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

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returnsMany listOf(gatt1, gatt2).map { ConnectGattResult.Success(it) }

            val keepAlive = KeepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
            )
            assertEquals(
                expected = Disconnected(),
                actual = keepAlive.state.first()
            )

            keepAlive.connect()
            keepAlive.state.first { it == Connected } // Wait until connected.

            val dropped = async(start = UNDISPATCHED) {
                keepAlive.state.first { it != Connected }
            }
            onCharacteristicChanged1.close() // Simulates connection drop.
            dropped.await() // Validates that connection dropped.

            keepAlive.state.first { it == Connected } // Wait until reconnected.
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

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery { bluetoothDevice.connectGatt(any()) } returns ConnectGattResult.Success(gatt)

            val keepAlive = KeepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
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
            disconnectTimeoutMillis = DISCONNECT_TIMEOUT
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
        val scope = CoroutineScope(Job() + CoroutineExceptionHandler { _, cause ->
            if (cause is EndOfTest) lock.unlock()
        })

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            var attempt = 0
            coEvery {
                bluetoothDevice.connectGatt(any())
            } answers {
                if (++attempt >= connectionAttempts) throw EndOfTest()
                Failure.Connection(ConnectionLost())
            }

            val keepAlive = scope.keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
            )
            assertTrue(keepAlive.connect())

            runBlocking { lock.lock() } // Wait until we've performed desired # of connect attempts.
            coVerify(exactly = connectionAttempts) { bluetoothDevice.connectGatt(any()) }
        }
    }

    @Test
    fun `KeepAliveGatt settles on Disconnected when connection is rejected`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()

        val scope = CoroutineScope(Job())
        val keepAlive = scope.keepAliveGatt(
            androidContext = mockk(relaxed = true),
            bluetoothDevice = bluetoothDevice,
            disconnectTimeoutMillis = DISCONNECT_TIMEOUT
        )

        assertTrue(keepAlive.connect())
        val closed = keepAlive.state.first { it is Disconnected } as Disconnected

        assertThrowable<ConnectionRejected>(closed.cause)
        assertThrowable<RemoteException>(closed.cause?.cause)

        assertFalse(keepAlive.isRunning.get(), "Rejection should mark KeepAliveGatt as not running")
        coVerify(exactly = 1) { bluetoothDevice.connectGatt(any(), false, any()) }
    }

    @Test
    fun `KeepAliveGatt does not fail CoroutineContext on connection rejection`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
        val done = Channel<Unit>()

        val job = launch {
            val keepAlive = keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
            )

            assertTrue(keepAlive.connect())
            keepAlive.state.first { it is Disconnected && it.cause is ConnectionRejected }
            done.offer(Unit)
        }

        done.receive() // Wait for KeepAliveGatt to be in a (rejected) Disconnected state.
        coVerify(exactly = 1) { bluetoothDevice.connectGatt(any(), false, any()) }

        assertTrue(job.isActive, "Coroutine did not remain active")
        job.cancelAndJoin()
    }

    @Test
    fun `Can connect after connection is rejected`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
        val done = Channel<Unit>()

        val job = launch {
            val keepAlive = keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
            )

            assertTrue(keepAlive.connect())
            keepAlive.state.first { it is Disconnected && it.cause is ConnectionRejected }

            assertTrue(keepAlive.connect())
            done.offer(Unit)
        }

        done.receive() // Wait for KeepAliveGatt to be in a (rejected) Disconnected state.

        coVerify(exactly = 2) { bluetoothDevice.connectGatt(any(), false, any()) }
        assertTrue(job.isActive, "Coroutine did not remain active")

        job.cancelAndJoin()
    }

    @Test
    fun `Cancelling KeepAliveGatt does not cancel parent`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
        val scope = CoroutineScope(Job())

        val keepAlive = scope.keepAliveGatt(
            androidContext = mockk(relaxed = true),
            bluetoothDevice = bluetoothDevice,
            disconnectTimeoutMillis = DISCONNECT_TIMEOUT
        )
        keepAlive.cancelAndJoin()

        assertFailsWith<IllegalStateException> {
            keepAlive.connect()
        }

        assertTrue(scope.isActive, "KeepAlive cancellation should not cancel parent")
        coVerify(exactly = 0) { bluetoothDevice.connectGatt(any(), false, any()) }
    }

    @Test
    fun `After 'disconnect' request, KeepAliveGatt does not attempt to reconnect`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
        val gatt = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery { bluetoothDevice.connectGatt(any()) } returns ConnectGattResult.Success(gatt)

            val keepAlive = KeepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
            )
            val connected = async(start = UNDISPATCHED) {
                keepAlive.state.first { it == Connected }
            }
            keepAlive.connect()
            connected.await()

            val disconnected = async(start = UNDISPATCHED) {
                keepAlive.state.first { it is Disconnected }
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

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returnsMany listOf(gatt1, gatt2).map { ConnectGattResult.Success(it) }

            val keepAlive = KeepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
            )
            val ready = async(start = UNDISPATCHED) {
                keepAlive.state.first { it == Connecting || it == Connected }
            }
            keepAlive.connect()
            ready.await()

            val disconnected = async(start = UNDISPATCHED) {
                keepAlive.state.first { it is Disconnected }
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

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery { bluetoothDevice.connectGatt(any()) } returns ConnectGattResult.Success(gatt)

            val keepAlive = KeepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
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
    fun `toString shows state as Disconnected before connecting`() {
        val bluetoothDevice = mockBluetoothDevice()
        val keepAlive = KeepAliveGatt(
            androidContext = mockk(relaxed = true),
            bluetoothDevice = bluetoothDevice,
            disconnectTimeoutMillis = DISCONNECT_TIMEOUT
        )
        assertEquals(
            expected = "KeepAliveGatt(device=$MAC_ADDRESS, gatt=null, state=Disconnected)",
            actual = keepAlive.toString()
        )
    }

    private class ExceptionFromConnectAction : Exception()

    @Test
    fun `An Exception thrown from 'connectAction' causes reconnect`() {
        val bluetoothDevice = mockBluetoothDevice()
        val gatt1 = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }
        val gatt2 = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }
        var thrown: Throwable? = null
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            thrown = throwable
        }

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returnsMany listOf(gatt1, gatt2).map { ConnectGattResult.Success(it) }

            runBlocking(exceptionHandler) {
                var shouldThrow = true
                val keepAlive = keepAliveGatt(
                    androidContext = mockk(relaxed = true),
                    bluetoothDevice = bluetoothDevice,
                    disconnectTimeoutMillis = DISCONNECT_TIMEOUT
                ) {
                    if (shouldThrow) {
                        shouldThrow = false // Only throw on the first connection attempt.
                        throw ExceptionFromConnectAction()
                    }
                }
                assertEquals(
                    expected = Disconnected(),
                    actual = keepAlive.state.first()
                )

                keepAlive.connect()
                keepAlive.state.first { it == Connected } // Wait until connected.
                keepAlive.cancelAndJoin()
            }

            assertThrowable<ExceptionFromConnectAction>(thrown)
            coVerify(exactly = 2) { bluetoothDevice.connectGatt(any()) }
            coVerify(exactly = 1) { gatt1.disconnect() }
            coVerify(exactly = 1) { gatt2.disconnect() }
        }
    }
}

private fun mockBluetoothDevice(): BluetoothDevice = mockk {
    every { this@mockk.toString() } returns MAC_ADDRESS

    // Mocked as returning `null` to indicate BLE request rejected (i.e. BLE turned off).
    // Most tests use `mockkStatic` to mock `BluetoothDevice.connectGatt(Context)` extension
    // function (which would normally call this function), so this mocked method usually isn't used.
    every { connectGatt(any(), any(), any()) } returns null
}

private inline fun <reified T : Throwable> assertThrowable(throwable: Throwable?) {
    assertEquals<Class<out Throwable>?>(
        expected = T::class.java,
        actual = throwable?.javaClass
    )
}
