/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.keepalive

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.juul.able.Able
import com.juul.able.android.connectGatt
import com.juul.able.device.ConnectGattResult.Failure
import com.juul.able.device.ConnectGattResult.Success
import com.juul.able.gatt.Gatt
import com.juul.able.gatt.GattIo
import com.juul.able.gatt.GattStatus
import com.juul.able.gatt.OnCharacteristicChanged
import com.juul.able.gatt.OnCharacteristicRead
import com.juul.able.gatt.OnCharacteristicWrite
import com.juul.able.gatt.OnDescriptorWrite
import com.juul.able.gatt.OnMtuChanged
import com.juul.able.gatt.OnReadRemoteRssi
import com.juul.able.gatt.WriteType
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class NotReady internal constructor(message: String) : IllegalStateException(message)

typealias EventHandler = suspend (Event) -> Unit

private class ReconnectException(cause: Throwable) : Exception(cause)

fun CoroutineScope.keepAliveGatt(
    androidContext: Context,
    bluetoothDevice: BluetoothDevice,
    disconnectTimeoutMillis: Long,
    eventHandler: EventHandler? = null
) = KeepAliveGatt(
    parentCoroutineContext = coroutineContext,
    androidContext = androidContext,
    bluetoothDevice = bluetoothDevice,
    disconnectTimeoutMillis = disconnectTimeoutMillis,
    eventHandler = eventHandler
)

class KeepAliveGatt internal constructor(
    parentCoroutineContext: CoroutineContext,
    androidContext: Context,
    private val bluetoothDevice: BluetoothDevice,
    private val disconnectTimeoutMillis: Long,
    private val eventHandler: EventHandler?
) : GattIo {

    private val applicationContext = androidContext.applicationContext

    private val job = SupervisorJob(parentCoroutineContext[Job]).apply {
        invokeOnCompletion { cause ->
            _state.value = State.Cancelled(cause)
            _onCharacteristicChanged.cancel()
        }
    }
    private val scope = CoroutineScope(parentCoroutineContext + job)

    internal val isRunning = AtomicBoolean()

    @Volatile
    private var _gatt: GattIo? = null
    private val gatt: GattIo
        inline get() = _gatt ?: throw NotReady(toString())

    private val _state = MutableStateFlow<State>(State.Disconnected())

    /**
     * Provides a [Flow] of the [KeepAliveGatt]'s [State].
     *
     * The initial [state] is [Disconnected][State.Disconnected] and will typically transition
     * through the following [State]s after [connect] is called:
     *
     * ```
     *                    connect()
     *                        :
     *  .--------------.      v       .------------.       .-----------.
     *  | Disconnected | ----------> | Connecting | ----> | Connected |
     *  '--------------'             '------------'       '-----------'
     *         ^                                                |
     *         |                                         connection drop
     *         |                                                v
     *         |                                        .---------------.
     *         '----------------------------------------| Disconnecting |
     *                                                  '---------------'
     * ```
     */
    @FlowPreview
    val state: Flow<State> = _state

    private val _onCharacteristicChanged = BroadcastChannel<OnCharacteristicChanged>(BUFFERED)

    private var connectionAttempt = 0

    fun connect(): Boolean {
        check(!job.isCancelled) { "Cannot connect, $this is closed" }
        isRunning.compareAndSet(false, true) || return false

        scope.launch(CoroutineName("KeepAliveGatt@$bluetoothDevice")) {
            while (isActive) {
                connectionAttempt++
                val result = supervisorScope {
                    try {
                        establishConnection()
                        null
                    } catch (reconnectException: ReconnectException) {
                        // Throw unwrapped Exception (from ReconnectException) within
                        // `supervisorScope` so that Coroutine remains active (reconnects) **and**
                        // unwrapped Exception propagates to parent scope.
                        throw reconnectException.cause!!
                    } catch (exception: Exception) {
                        exception // Thrown outside of `supervisorScope` to cancel Coroutine.
                    }
                }
                if (result != null) throw result
            }
        }.invokeOnCompletion { isRunning.set(false) }
        return true
    }

    suspend fun disconnect() {
        job.children.forEach { it.cancelAndJoin() }
    }

    /**
     * Establishes a connection, suspending until either the attempt at establishing connection
     * fails or an established connection drops.
     */
    private suspend fun establishConnection() {
        var didConnect = false
        _state.value = State.Connecting

        val gatt: Gatt = when (val result = bluetoothDevice.connectGatt(applicationContext)) {
            is Success -> result.gatt
            is Failure.Rejected -> {
                _state.value = State.Disconnected(result.cause)
                eventHandler?.invoke(Event.Rejected(result.cause))
                throw result.cause
            }
            is Failure.Connection -> {
                Able.error { "Failed to connect to device $bluetoothDevice due to ${result.cause}" }
                _state.value = State.Disconnected(result.cause)
                eventHandler?.invoke(Event.Disconnected(didConnect, connectionAttempt))
                return
            }
        }

        try {
            try {
                coroutineScope {
                    gatt.onCharacteristicChanged
                        .onEach(_onCharacteristicChanged::send)
                        .catch { cause ->
                            // Wrap Exceptions in special ReconnectException to signal a reconnect.
                            throw ReconnectException(cause)
                        }
                        .launchIn(this, start = UNDISPATCHED)

                    _gatt = gatt
                    eventHandler?.invoke(Event.Connected(gatt))

                    didConnect = true
                    _state.value = State.Connected
                }
            } finally {
                _gatt = null
                _state.value = State.Disconnecting

                withContext(NonCancellable) {
                    withTimeoutOrNull(disconnectTimeoutMillis) {
                        gatt.disconnect()
                    } ?: Able.warn { "Timeout waiting ${disconnectTimeoutMillis}ms for disconnect" }
                }
            }

            _state.value = State.Disconnected()
        } catch (failure: Exception) {
            _state.value = State.Disconnected(
                // Unwrap ReconnectException which is used to signal a reconnect.
                if (failure is ReconnectException) failure.cause else failure
            )
            throw failure
        } finally {
            eventHandler?.invoke(Event.Disconnected(didConnect, connectionAttempt))
        }
    }

    @FlowPreview
    override val onCharacteristicChanged: Flow<OnCharacteristicChanged> =
        _onCharacteristicChanged.asFlow()

    override suspend fun discoverServices(): GattStatus = gatt.discoverServices()

    override val services: List<BluetoothGattService> get() = gatt.services
    override fun getService(uuid: UUID): BluetoothGattService? = gatt.getService(uuid)

    override suspend fun requestMtu(mtu: Int): OnMtuChanged = gatt.requestMtu(mtu)

    override suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic
    ): OnCharacteristicRead = gatt.readCharacteristic(characteristic)

    override fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Boolean = gatt.setCharacteristicNotification(characteristic, enable)

    override suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): OnCharacteristicWrite = gatt.writeCharacteristic(characteristic, value, writeType)

    override suspend fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): OnDescriptorWrite = gatt.writeDescriptor(descriptor, value)

    override suspend fun readRemoteRssi(): OnReadRemoteRssi = gatt.readRemoteRssi()

    override fun toString() =
        "KeepAliveGatt(device=$bluetoothDevice, gatt=$_gatt, state=${_state.value})"
}

private fun <T> Flow<T>.launchIn(
    scope: CoroutineScope,
    start: CoroutineStart = CoroutineStart.DEFAULT
): Job = scope.launch(start = start) {
    collect()
}
