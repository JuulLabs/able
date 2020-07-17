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
import com.juul.able.keepalive.Event
import com.juul.able.keepalive.NotReady
import com.juul.able.keepalive.State
import com.juul.able.keepalive.State.Connected
import com.juul.able.keepalive.State.Connecting
import com.juul.able.keepalive.State.Disconnected
import com.juul.able.keepalive.keepAliveGatt
import com.juul.able.keepalive.onConnected
import com.juul.able.keepalive.onDisconnected
import com.juul.able.keepalive.onRejected
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

private const val BLUETOOTH_DEVICE_CLASS = "com.juul.able.android.BluetoothDeviceKt"
private const val MAC_ADDRESS = "00:11:22:33:FF:EE"
private const val DISCONNECT_TIMEOUT = 5_000L // milliseconds

private val testUuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")

private class EndOfTest : Exception()

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

        val didDisconnect = Mutex(locked = true)

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returnsMany listOf(gatt1, gatt2).map { ConnectGattResult.Success(it) }

            val job = Job()
            val keepAlive = CoroutineScope(job).keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
            ) { event ->
                event.onDisconnected { didDisconnect.unlock() }
            }

            assertEquals(
                expected = Disconnected(),
                actual = keepAlive.state.first()
            )

            keepAlive.connect()
            keepAlive.state.first { it == Connected } // Wait until connected.

            onCharacteristicChanged1.close() // Simulates connection drop.
            didDisconnect.lock() // Validates that connection dropped.

            keepAlive.state.first { it == Connected } // Wait until reconnected.
            job.cancelAndJoin()

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

            val keepAlive = GlobalScope.keepAliveGatt(
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
        val keepAlive = GlobalScope.keepAliveGatt(
            androidContext = mockk(relaxed = true),
            bluetoothDevice = mockk(),
            disconnectTimeoutMillis = DISCONNECT_TIMEOUT
        )

        assertFailsWith<NotReady> {
            keepAlive.discoverServices()
        }
    }

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
                Failure.Connection(mockk<ConnectionLost>())
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
        val disconnected = keepAlive.state.filterIsInstance<Disconnected>().first()
        assertThrowable<RemoteException>(disconnected.cause)

        withTimeout(5_000L) {
            while (keepAlive.isRunning.get()) yield()
        }
        coVerify(exactly = 1) { bluetoothDevice.connectGatt(any(), false, any()) }
    }

    @Test
    fun `KeepAliveGatt does not fail CoroutineContext on connection rejection`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
        val wasRejected = Mutex(locked = true)

        val job = launch {
            val keepAlive = keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
            ) { event ->
                event.onRejected { wasRejected.unlock() }
            }

            assertTrue(keepAlive.connect())
        }

        wasRejected.lock() // Wait for KeepAliveGatt to be in a (rejected) Disconnected state.
        coVerify(exactly = 1) { bluetoothDevice.connectGatt(any(), false, any()) }

        delay(2_000L) // Wait to ensure parent Coroutine isn't spinning down.

        assertTrue(job.isActive, "Parent Coroutine did not remain active")
        job.cancelAndJoin()
    }

    private class Rejected : Exception()

    @Test
    fun `Can connect after connection is rejected`() = runBlocking {
        val wasRejected = Mutex(locked = true)
        val rejectedException = Rejected()

        val bluetoothDevice = mockBluetoothDevice()
        val gatt = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returnsMany listOf(
                Failure.Rejected(rejectedException),
                ConnectGattResult.Success(gatt)
            )

            val job = Job()
            val keepAlive = CoroutineScope(job).keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
            ) { event ->
                event.onRejected { wasRejected.unlock() }
            }

            assertTrue(keepAlive.connect())
            wasRejected.lock() // Wait for KeepAliveGatt to be in a disconnected (Rejected) state.
            assertEquals(
                expected = Disconnected(cause = rejectedException),
                actual = keepAlive.state.first()
            )

            withTimeout(5_000L) {
                while (keepAlive.isRunning.get()) yield()
            }

            assertTrue(keepAlive.connect())
            keepAlive.state.first { it == Connected }
            coVerify(exactly = 2) { bluetoothDevice.connectGatt(any()) }

            job.cancelAndJoin()
        }
    }

    @Test
    fun `Connect throws IllegalStateException after parent scope is cancelled`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()

        val job = Job()
        val scope = CoroutineScope(job)
        val keepAlive = scope.keepAliveGatt(
            androidContext = mockk(relaxed = true),
            bluetoothDevice = bluetoothDevice,
            disconnectTimeoutMillis = DISCONNECT_TIMEOUT
        )
        job.cancelAndJoin()

        assertFailsWith<IllegalStateException> {
            keepAlive.connect()
        }

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

            val keepAlive = GlobalScope.keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT
            )

            keepAlive.connect()
            keepAlive.state.first { it == Connected }

            keepAlive.disconnect()
            keepAlive.state.first { it is Disconnected }

            withTimeout(5_000L) {
                while (keepAlive.isRunning.get()) yield()
            }
            coVerify(exactly = 1) { bluetoothDevice.connectGatt(any()) }
        }
    }

    @Test
    fun `When connection established, EventHandler is called with Connected event`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
        val gatt = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }

        var connectedEvents = 0

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returns ConnectGattResult.Success(gatt)

            val job = Job()
            val keepAlive = CoroutineScope(job).keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT,
                eventHandler = { event ->
                    event.onConnected { connectedEvents++ }
                }
            )

            assertEquals(
                expected = 0,
                actual = connectedEvents
            )

            keepAlive.connect()
            keepAlive.state.first { it == Connected }
            assertEquals(
                expected = 1,
                actual = connectedEvents
            )

            job.cancelAndJoin()
        }
    }

    private class OnConnectedException : Exception()

    @Test
    fun `Exception in onConnected makes KeepAliveGatt settle on Disconnected`() = runBlocking {
        val job = Job()
        val onConnectedException = OnConnectedException()

        val bluetoothDevice = mockBluetoothDevice()
        val gatt = mockk<Gatt> {
            every { onCharacteristicChanged } returns flow { delay(Long.MAX_VALUE) }
            coEvery { disconnect() } returns Unit
        }

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returns ConnectGattResult.Success(gatt)

            val scope = CoroutineScope(job)
            val keepAlive = scope.keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT,
                eventHandler = { event ->
                    event.onConnected { throw onConnectedException }
                }
            )

            val states = mutableListOf<State>()
            keepAlive.state.onEach { states += it }.launchIn(scope)

            keepAlive.connect()
            delay(5_000L) // Slight wait to make sure we've **settled** on Disconnected state.

            // Asserting that no more than the following states were emitted:
            // - Disconnected
            // - Connecting
            // - Connected
            // - Disconnecting
            // - Disconnected
            assertTrue(states.size <= 5)

            val disconnected = states.last() as Disconnected
            assertThrowable<OnConnectedException>(disconnected.cause)
        }

        job.cancelAndJoin()
    }

    @Test
    fun `Exception in onDisconnected makes KeepAliveGatt settle on Disconnected`() = runBlocking {
        val job = Job()
        val connectionDroppedException = IllegalStateException("Connection dropped")

        val bluetoothDevice = mockBluetoothDevice()
        val onCharacteristicChanged = BroadcastChannel<OnCharacteristicChanged>(BUFFERED)
        val gatt = mockk<Gatt> {
            every { this@mockk.onCharacteristicChanged } returns onCharacteristicChanged.asFlow()
            coEvery { disconnect() } returns Unit
        }

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returns ConnectGattResult.Success(gatt)

            val scope = CoroutineScope(job)
            val keepAlive = scope.keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT,
                eventHandler = { event ->
                    event.onDisconnected { error("Test Exception in onDisconnected lambda") }
                }
            )

            val states = mutableListOf<State>()
            keepAlive.state.onEach { states += it }.launchIn(scope)

            keepAlive.connect()
            keepAlive.state.first { it == Connected }
            onCharacteristicChanged.close(connectionDroppedException) // Emulate a connection drop.

            delay(5_000L) // Slight wait to make sure we've **settled** on Disconnected state.

            // Asserting that no more than the following states were emitted:
            // - Disconnected
            // - Connecting
            // - Connected
            // - Disconnecting
            // - Disconnected
            assertTrue(states.size <= 5)

            assertEquals(
                expected = Disconnected(cause = connectionDroppedException),
                actual = states.last()
            )
        }

        job.cancelAndJoin()
    }

    @Test
    fun `Exception in onRejected makes KeepAliveGatt settle on Disconnected`() = runBlocking {
        val job = Job()
        val bluetoothDevice = mockBluetoothDevice()

        val scope = CoroutineScope(job)
        val keepAlive = scope.keepAliveGatt(
            androidContext = mockk(relaxed = true),
            bluetoothDevice = bluetoothDevice,
            disconnectTimeoutMillis = DISCONNECT_TIMEOUT,
            eventHandler = { event ->
                event.onRejected { error("Test Exception in onRejected lambda") }
            }
        )

        val states = mutableListOf<State>()
        keepAlive.state.onEach { states += it }.launchIn(scope)

        keepAlive.connect()

        delay(5_000L) // Slight wait to make sure we've **settled** on Disconnected state.

        // Asserting that no more than the following states were emitted:
        // - Disconnected
        // - Connecting
        // - Connected
        // - Disconnecting
        // - Disconnected
        assertTrue(states.size <= 5)

        val disconnected = states.last() as Disconnected
        assertThrowable<RemoteException>(disconnected.cause)

        job.cancelAndJoin()
    }

    @Test
    fun `wasConnected is true upon disconnect from established connection`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
        val onCharacteristicChanged = BroadcastChannel<OnCharacteristicChanged>(BUFFERED)
        val gatt = mockk<Gatt> {
            every { this@mockk.onCharacteristicChanged } returns onCharacteristicChanged.asFlow()
            coEvery { disconnect() } returns Unit
        }

        val disconnectedEvents = Channel<Event.Disconnected>()

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery {
                bluetoothDevice.connectGatt(any())
            } returns ConnectGattResult.Success(gatt)

            val job = Job()
            val keepAlive = CoroutineScope(job).keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT,
                eventHandler = { event ->
                    event.onDisconnected { disconnectedEvents.send(it) }
                }
            )

            keepAlive.connect()
            keepAlive.state.first { it == Connected }

            onCharacteristicChanged.close() // Simulates connection drop.

            assertEquals(
                expected = Event.Disconnected(wasConnected = true, connectionAttempt = 1),
                actual = disconnectedEvents.receive()
            )

            job.cancelAndJoin()
        }
    }

    @Test
    fun `Subsequent connection failures yield increasing connectionAttempt values`() = runBlocking {
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
                if (++attempt > connectionAttempts) throw EndOfTest()
                Failure.Connection(mockk<ConnectionLost>())
            }

            val events = mutableListOf<Event>()

            val keepAlive = scope.keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT,
                eventHandler = { event -> events += event }
            )

            keepAlive.connect()
            lock.lock() // Wait for "end of test" signal.

            assertEquals<List<Event>>(
                expected = (1..5).map {
                    Event.Disconnected(wasConnected = false, connectionAttempt = it)
                },
                actual = events
            )
        }
    }

    @Test
    fun `Return of null from connectGatt during connect emits Rejected event`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
        val scope = CoroutineScope(Job())

        val events = Channel<Event>()

        val keepAlive = scope.keepAliveGatt(
            androidContext = mockk(relaxed = true),
            bluetoothDevice = bluetoothDevice,
            disconnectTimeoutMillis = DISCONNECT_TIMEOUT,
            eventHandler = events::send
        )

        assertTrue(keepAlive.connect())
        assertEquals<Class<out Event>>(
            expected = Event.Rejected::class.java,
            actual = events.receive().javaClass
        )
    }

    @Test
    fun `Exception while connected emits Disconnected event`() = runBlocking {
        val bluetoothDevice = mockBluetoothDevice()
        val onCharacteristicChanged = BroadcastChannel<OnCharacteristicChanged>(BUFFERED)
        val gatt = mockk<Gatt> {
            every { this@mockk.onCharacteristicChanged } returns onCharacteristicChanged.asFlow()
            coEvery { disconnect() } returns Unit
        }

        val events = Channel<Event>()

        mockkStatic(BLUETOOTH_DEVICE_CLASS) {
            coEvery { bluetoothDevice.connectGatt(any()) } returns ConnectGattResult.Success(gatt)

            val job = Job()
            val keepAlive = CoroutineScope(job).keepAliveGatt(
                androidContext = mockk(relaxed = true),
                bluetoothDevice = bluetoothDevice,
                disconnectTimeoutMillis = DISCONNECT_TIMEOUT,
                eventHandler = events::send
            )

            keepAlive.connect()
            assertEquals(
                expected = Event.Connected(gatt),
                actual = events.receive()
            )

            keepAlive.state.first { it == Connected }

            // Emulate failure while connected.
            onCharacteristicChanged.close(IllegalStateException("Connection dropped"))

            assertEquals(
                expected = Event.Disconnected(wasConnected = true, connectionAttempt = 1),
                actual = events.receive()
            )
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

            val job = Job()
            val keepAlive = CoroutineScope(job).keepAliveGatt(
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

            job.cancelAndJoin()

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

            val keepAlive = GlobalScope.keepAliveGatt(
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
        val keepAlive = GlobalScope.keepAliveGatt(
            androidContext = mockk(relaxed = true),
            bluetoothDevice = bluetoothDevice,
            disconnectTimeoutMillis = DISCONNECT_TIMEOUT
        )
        assertEquals(
            expected = "KeepAliveGatt(device=$MAC_ADDRESS, gatt=null, state=Disconnected)",
            actual = keepAlive.toString()
        )
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
