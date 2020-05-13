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
import com.juul.able.keepalive.KeepAliveGatt.Configuration
import com.juul.able.keepalive.State.Connected
import com.juul.able.keepalive.State.Connecting
import com.juul.able.keepalive.State.Disconnected
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

typealias ConnectAction = suspend GattIo.() -> Unit

class NotReady(message: String) : IllegalStateException(message)

enum class State {
    Connecting,
    Connected,
    Disconnecting,
    Disconnected,
}

fun CoroutineScope.keepAliveGatt(
    androidContext: Context,
    bluetoothDevice: BluetoothDevice,
    configuration: Configuration
) = KeepAliveGatt(
    parentCoroutineContext = coroutineContext,
    androidContext = androidContext,
    bluetoothDevice = bluetoothDevice,
    configuration = configuration
)

class KeepAliveGatt(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    androidContext: Context,
    private val bluetoothDevice: BluetoothDevice,
    configuration: Configuration
) : GattIo {

    data class Configuration(
        val disconnectTimeoutMillis: Long,
        val exceptionHandler: CoroutineExceptionHandler? = null,
        val onConnectAction: ConnectAction? = null,
        internal val stateCapacity: Int = CONFLATED
    )

    private val applicationContext = androidContext.applicationContext

    private val job = SupervisorJob(parentCoroutineContext[Job])
    private val scope = CoroutineScope(
        parentCoroutineContext + job + (configuration.exceptionHandler ?: EmptyCoroutineContext)
    )

    private val isRunning = AtomicBoolean()

    private val disconnectTimeoutMillis = configuration.disconnectTimeoutMillis
    private val onConnectAction = configuration.onConnectAction

    @Volatile
    private var _gatt: GattIo? = null
    private val gatt: GattIo
        inline get() = _gatt ?: throw NotReady(toString())

    private val _state = BroadcastChannel<State>(configuration.stateCapacity)

    @FlowPreview
    val state: Flow<State> = _state.asFlow()

    private val _onCharacteristicChanged = BroadcastChannel<OnCharacteristicChanged>(BUFFERED)

    // todo: Replace with `trySend` when Kotlin/kotlinx.coroutines#974 is fixed.
    // https://github.com/Kotlin/kotlinx.coroutines/issues/974
    private fun setState(state: State) {
        _state.offerCatching(state)
    }

    fun connect(): Boolean {
        check(!job.isCancelled) { "Cannot connect, $this is closed" }
        isRunning.compareAndSet(false, true) || return false

        scope.launch(CoroutineName("KeepAliveGatt@$bluetoothDevice")) {
            while (isActive) {
                try {
                    spawnConnection()
                } finally {
                    setState(Disconnected)
                }
            }
        }
        return true
    }

    suspend fun disconnect() {
        job.children.forEach { it.cancelAndJoin() }
        isRunning.set(false)
    }

    private fun cancel(cause: CancellationException?) {
        job.cancel(cause)
        _onCharacteristicChanged.cancel()
        _state.cancel()
    }

    fun cancel() {
        cancel(null)
    }

    suspend fun cancelAndJoin() {
        job.cancelAndJoin()
        _onCharacteristicChanged.cancel()
        _state.cancel()
    }

    // todo: Fix `@see` documentation link when https://github.com/Kotlin/dokka/issues/80 is fixed.
    /** @see `Job.invokeOnCompletion(CompletionHandler)` */
    fun invokeOnCompletion(
        handler: CompletionHandler
    ): DisposableHandle = job.invokeOnCompletion(handler)

    private suspend fun spawnConnection() {
        setState(Connecting)

        val gatt = when (val result = bluetoothDevice.connectGatt(applicationContext)) {
            is Success -> result.gatt
            is Failure.Rejected -> {
                cancel(CancellationException("Connection request was rejected", result.cause))
                return
            }
            is Failure.Connection -> {
                Able.error { "Failed to connect to device $bluetoothDevice due to ${result.cause}" }
                return
            }
        }

        try {
            coroutineScope {
                gatt.onCharacteristicChanged
                    .onEach(_onCharacteristicChanged::send)
                    .launchIn(this)
                onConnectAction?.invoke(gatt)
                _gatt = gatt
                setState(Connected)
            }
        } finally {
            _gatt = null
            setState(State.Disconnecting)
            withContext(NonCancellable) {
                withTimeoutOrNull(disconnectTimeoutMillis) {
                    gatt.disconnect()
                } ?: Able.warn {
                    "Timed out waiting ${disconnectTimeoutMillis}ms for disconnect"
                }
            }
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
        "KeepAliveGatt(device=$bluetoothDevice, gatt=$_gatt, state=${_state.consume { poll() }})"
}

// https://github.com/Kotlin/kotlinx.coroutines/issues/974
private fun <E> SendChannel<E>.offerCatching(element: E): Boolean {
    return runCatching { offer(element) }.getOrDefault(false)
}
