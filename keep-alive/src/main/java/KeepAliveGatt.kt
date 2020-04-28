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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
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
import java.util.UUID
import kotlin.coroutines.CoroutineContext

enum class State {
    Connecting,
    Connected,
    Disconnecting,
    Disconnected,
}

typealias ConnectAction = suspend GattIo.() -> Unit

class NotReady(message: String) : IllegalStateException(message)

fun CoroutineScope.keepAliveGatt(
    androidContext: Context,
    device: BluetoothDevice,
    disconnectTimeoutMillis: Long,
    onConnectAction: ConnectAction
) = KeepAliveGatt(coroutineContext, androidContext, device, disconnectTimeoutMillis, onConnectAction)

class KeepAliveGatt internal constructor(
    coroutineContext: CoroutineContext,
    androidContext: Context,
    private val device: BluetoothDevice,
    private val disconnectTimeoutMillis: Long,
    private val onConnectAction: ConnectAction
) : GattIo {

    private val applicationContext = androidContext.applicationContext

    init {
        val parentJob = coroutineContext[Job]
        CoroutineScope(coroutineContext + SupervisorJob(parentJob)).launch(
            CoroutineName("KeepAliveGatt@$device")
        ) {
            while (isActive) spawnConnection()
        }.apply {
            invokeOnCompletion {
                _onCharacteristicChanged.cancel()
                _state.cancel()
            }
        }
    }

    @Volatile
    private var _gatt: GattIo? = null

    private val gatt: GattIo
        inline get() = _gatt ?: throw NotReady(toString())

    private suspend fun spawnConnection() {
        _state.offer(State.Connecting)

        val gatt = when (val result = device.connectGatt(applicationContext)) {
            is Success -> result.gatt
            is Failure -> {
                _state.offer(State.Disconnected)
                Able.error(result.cause) { "Failed to connect to $device" }
                return
            }
        }

        try {
            coroutineScope {
                gatt.onCharacteristicChanged
                    .onEach(_onCharacteristicChanged::send)
                    .launchIn(this)
                onConnectAction.invoke(gatt)
                _gatt = gatt
                _state.offer(State.Connected)
            }
        } finally {
            _state.offer(State.Disconnecting)
            withContext(NonCancellable) {
                withTimeoutOrNull(disconnectTimeoutMillis) {
                    gatt.disconnect()
                } ?: Able.warn { "Timed out waiting ${disconnectTimeoutMillis}ms for disconnect" }
                _state.offer(State.Disconnected)
            }
        }
    }

    private val _state = BroadcastChannel<State>(Channel.CONFLATED)

    @FlowPreview
    val state: Flow<State> = _state.asFlow()

    private val _onCharacteristicChanged = BroadcastChannel<OnCharacteristicChanged>(Channel.BUFFERED)

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

    // todo: Verify that this doesn't throw after _state is closed.
    override fun toString() =
        "KeepAliveGatt(device=$device, gatt=$_gatt, state=${_state.consume { poll() }})"
}
