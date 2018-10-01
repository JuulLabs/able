/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.device

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.juul.able.experimental.Able
import com.juul.able.experimental.ConnectGattResult
import com.juul.able.experimental.Gatt
import com.juul.able.experimental.GattCallbackConfig
import com.juul.able.experimental.GattState
import com.juul.able.experimental.GattStatus
import com.juul.able.experimental.OnCharacteristicChanged
import com.juul.able.experimental.OnCharacteristicRead
import com.juul.able.experimental.OnCharacteristicWrite
import com.juul.able.experimental.OnConnectionStateChange
import com.juul.able.experimental.OnDescriptorWrite
import com.juul.able.experimental.OnMtuChanged
import com.juul.able.experimental.WriteType
import com.juul.able.experimental.android.connectGatt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

class GattUnavailable(message: String) : IllegalStateException(message)

class CoroutinesGattDevice internal constructor(
    private val bluetoothDevice: BluetoothDevice
) : Gatt, CoroutineScope {

    /*
     * Constructor must **not** have side-effects as we're relying on `ConcurrentMap<K, V>.getOrPut`
     * in `DeviceManager.wrapped` which uses `putIfAbsent` as an alternative to `computeIfAbsent`
     * (`computeIfAbsent` is only available on API >= 24).
     *
     * As stated in [Equivalent of ComputeIfAbsent in Java 7](https://stackoverflow.com/a/40665232):
     *
     * > This is pretty much functionally equivalent the `computeIfAbsent` call in Java 8, with the
     * > only difference being that sometimes you construct a `Value` object that never makes it
     * > into the map - because another thread put it in first. It never results in returning the
     * > wrong object or anything like that - the function consistently returns the right `Value` no
     * > matter what, but _if the construction of `Value` has side-effects*_, this may not be
     * > acceptable.
     */

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job

    private val connectMutex = Mutex()
    private var connectDeferred: Deferred<ConnectGattResult>? = null

    private var _gatt: Gatt? = null
    private val gatt: Gatt
        get() = _gatt
            ?: throw GattUnavailable("Gatt unavailable for bluetooth device $bluetoothDevice")

    private val _connectionState = AtomicInteger()
    fun getConnectionState(): GattState = _connectionState.get()

    private var eventJob = Job(job)
        set(value) {
            // Prevent runaways: cancel the previous Job that we are loosing a reference to.
            field.cancel()
            field = value
        }

    /**
     * Scopes forwarding events to (i.e. the following `Channel`s are consumed under this scope):
     *
     * - [onConnectionStateChange]
     * - [onCharacteristicChanged]
     *
     * @see createConnection
     */
    private val eventCoroutineScope
        get() = CoroutineScope(eventJob)

    override val onConnectionStateChange = BroadcastChannel<OnConnectionStateChange>(CONFLATED)
    override val onCharacteristicChanged = BroadcastChannel<OnCharacteristicChanged>(1)

    fun isConnected(): Boolean =
        _gatt != null && getConnectionState() == BluetoothProfile.STATE_CONNECTED

    suspend fun connect(
        context: Context,
        autoConnect: Boolean,
        callbackConfig: GattCallbackConfig
    ): ConnectGattResult {
        Able.verbose { "Connection requested to bluetooth device $bluetoothDevice" }

        val result = connectMutex.withLock {
            connectDeferred ?: createConnection(context, autoConnect, callbackConfig).also {
                connectDeferred = it
            }
        }.await()

        Able.info { "connect ← result=$result" }
        return result
    }

    private suspend fun cancelConnect() = connectMutex.withLock {
        connectDeferred?.cancel()
            ?: Able.verbose { "No connection to cancel for bluetooth device $bluetoothDevice" }
        connectDeferred = null
    }

    private fun createConnection(
        context: Context,
        autoConnect: Boolean,
        callbackConfig: GattCallbackConfig
    ): Deferred<ConnectGattResult> = async {
        Able.info { "Creating connection for bluetooth device $bluetoothDevice" }
        val result = bluetoothDevice.connectGatt(context, autoConnect, callbackConfig)

        if (result is ConnectGattResult.Success) {
            val newGatt = result.gatt
            _gatt = newGatt

            eventJob = Job(job) // Prepare event Coroutine scope.

            eventCoroutineScope.launch {
                Able.verbose { "onConnectionStateChange → $bluetoothDevice → Begin" }
                newGatt.onConnectionStateChange.consumeEach {
                    if (it.status == BluetoothGatt.GATT_SUCCESS) {
                        _connectionState.set(it.newState)
                    }
                    Able.verbose { "Forwarding $it for $bluetoothDevice" }
                    onConnectionStateChange.send(it)
                }
                Able.verbose { "onConnectionStateChange ← $bluetoothDevice ← End" }
            }.invokeOnCompletion {
                Able.verbose { "onConnectionStateChange for $bluetoothDevice completed, cause=$it" }
            }

            eventCoroutineScope.launch {
                Able.verbose { "onCharacteristicChanged → $bluetoothDevice → Begin" }
                newGatt.onCharacteristicChanged.consumeEach {
                    Able.verbose { "Forwarding $it for $bluetoothDevice" }
                    onCharacteristicChanged.send(it)
                }
                Able.verbose { "onCharacteristicChanged ← $bluetoothDevice ← End" }
            }.invokeOnCompletion {
                Able.verbose { "onCharacteristicChanged for $bluetoothDevice completed, cause=$it" }
            }

            ConnectGattResult.Success(this@CoroutinesGattDevice)
        } else {
            result
        }
    }

    override val services: List<BluetoothGattService>
        get() = gatt.services

    override fun requestConnect(): Boolean = gatt.requestConnect()

    override fun requestDisconnect(): Unit = gatt.requestDisconnect()

    override fun getService(uuid: UUID): BluetoothGattService? = gatt.getService(uuid)

    override suspend fun connect(): Boolean = gatt.connect()

    override suspend fun disconnect() {
        cancelConnect()

        Able.verbose { "Disconnecting from bluetooth device $bluetoothDevice" }
        _gatt?.disconnect()
            ?: Able.warn { "Unable to disconnect from bluetooth device $bluetoothDevice" }

        eventJob.cancel()
    }

    override fun close() {
        Able.verbose { "close → Begin" }

        runBlocking {
            cancelConnect()
        }

        eventJob.cancel()

        Able.debug { "close → Closing bluetooth device $bluetoothDevice" }
        _gatt?.close()
        _gatt = null

        Able.verbose { "close ← End" }
    }

    internal fun dispose() {
        Able.verbose { "dispose → Begin" }

        job.cancel()
        _gatt?.close()

        Able.verbose { "dispose ← End" }
    }

    override suspend fun discoverServices(): GattStatus = gatt.discoverServices()

    override suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic
    ): OnCharacteristicRead = gatt.readCharacteristic(characteristic)

    override suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): OnCharacteristicWrite = gatt.writeCharacteristic(characteristic, value, writeType)

    override suspend fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): OnDescriptorWrite = gatt.writeDescriptor(descriptor, value)

    override suspend fun requestMtu(mtu: Int): OnMtuChanged = gatt.requestMtu(mtu)

    override fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Boolean = gatt.setCharacteristicNotification(characteristic, enable)

    override fun toString(): String =
        "CoroutinesGattDevice(bluetoothDevice=$bluetoothDevice, state=${getConnectionState()})"
}

