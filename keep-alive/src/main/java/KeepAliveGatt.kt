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
import com.juul.able.gatt.GattIo
import com.juul.able.gatt.GattStatus
import com.juul.able.gatt.OnCharacteristicChanged
import com.juul.able.gatt.OnCharacteristicRead
import com.juul.able.gatt.OnCharacteristicWrite
import com.juul.able.gatt.OnDescriptorWrite
import com.juul.able.gatt.OnMtuChanged
import com.juul.able.gatt.OnReadRemoteRssi
import com.juul.able.gatt.WriteType
import com.juul.able.keepalive.State.Cancelled
import com.juul.able.keepalive.State.Connected
import com.juul.able.keepalive.State.Connecting
import com.juul.able.keepalive.State.Disconnected
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

typealias ConnectAction = suspend GattIo.() -> Unit

class NotReady(message: String) : IllegalStateException(message)
class ConnectionRejected(cause: Throwable) : IllegalStateException(cause)

sealed class State {
    object Connecting : State()
    object Connected : State()
    object Disconnecting : State()
    data class Disconnected(val cause: Throwable? = null) : State() {
        override fun toString() = super.toString()
    }
    data class Cancelled(val cause: Throwable?) : State() {
        override fun toString() = super.toString()
    }

    override fun toString(): String = javaClass.simpleName
}

fun CoroutineScope.keepAliveGatt(
    androidContext: Context,
    bluetoothDevice: BluetoothDevice,
    disconnectTimeoutMillis: Long,
    onConnectAction: ConnectAction? = null
) = KeepAliveGatt(
    parentCoroutineContext = coroutineContext,
    androidContext = androidContext,
    bluetoothDevice = bluetoothDevice,
    disconnectTimeoutMillis = disconnectTimeoutMillis,
    onConnectAction = onConnectAction
)

class KeepAliveGatt internal constructor(
    parentCoroutineContext: CoroutineContext,
    androidContext: Context,
    private val bluetoothDevice: BluetoothDevice,
    private val disconnectTimeoutMillis: Long,
    private val onConnectAction: ConnectAction? = null
) : GattIo {

    private val applicationContext = androidContext.applicationContext

    private val job = SupervisorJob(parentCoroutineContext[Job]).apply {
        invokeOnCompletion { cause ->
            _state.value = Cancelled(cause)
            _onCharacteristicChanged.cancel()
        }
    }
    private val scope = CoroutineScope(parentCoroutineContext + job)

    private val isRunning = AtomicBoolean()

    @Volatile
    private var _gatt: GattIo? = null
    private val gatt: GattIo
        inline get() = _gatt ?: throw NotReady(toString())

    private val _state = MutableStateFlow<State>(Disconnected())

    /**
     * Provides a [Flow] of the [KeepAliveGatt]'s [State].
     *
     * The initial [state] is [Disconnected] and will typically transition through the following
     * [State]s after [connect] is called:
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

    fun connect(): Boolean {
        check(!job.isCancelled) { "Cannot connect, $this is closed" }
        isRunning.compareAndSet(false, true) || return false

        scope.launch(CoroutineName("KeepAliveGatt@$bluetoothDevice")) {
            while (isActive) {
                spawnConnection()
            }
        }.invokeOnCompletion { isRunning.set(false) }
        return true
    }

    suspend fun disconnect() {
        job.children.forEach { it.cancelAndJoin() }
    }

    private suspend fun spawnConnection() {
        try {
            _state.value = Connecting

            val gatt = when (val result = bluetoothDevice.connectGatt(applicationContext)) {
                is Success -> result.gatt
                is Failure.Rejected -> throw ConnectionRejected(result.cause)
                is Failure.Connection -> {
                    Able.error { "Failed to connect to device $bluetoothDevice due to ${result.cause}" }
                    return
                }
            }

            supervisorScope {
                launch {
                    try {
                        coroutineScope {
                            gatt.onCharacteristicChanged
                                .onEach(_onCharacteristicChanged::send)
                                .launchIn(this, start = UNDISPATCHED)
                            onConnectAction?.invoke(gatt)
                            _gatt = gatt
                            _state.value = Connected
                        }
                    } finally {
                        _gatt = null
                        _state.value = State.Disconnecting

                        withContext(NonCancellable) {
                            withTimeoutOrNull(disconnectTimeoutMillis) {
                                gatt.disconnect()
                            } ?: Able.warn {
                                "Timed out waiting ${disconnectTimeoutMillis}ms for disconnect"
                            }
                        }
                    }
                }
            }
            _state.value = Disconnected()
        } catch (failure: Exception) {
            _state.value = Disconnected(failure)
            throw failure
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
