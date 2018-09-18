/*
 * Copyright 2018 JUUL Labs, Inc.
 */

@file:Suppress("RedundantUnitReturnType")

package com.juul.able.experimental

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.os.RemoteException
import com.juul.able.experimental.messenger.Message.DiscoverServices
import com.juul.able.experimental.messenger.Message.ReadCharacteristic
import com.juul.able.experimental.messenger.Message.RequestMtu
import com.juul.able.experimental.messenger.Message.WriteCharacteristic
import com.juul.able.experimental.messenger.Message.WriteDescriptor
import com.juul.able.experimental.messenger.Messenger
import com.juul.able.experimental.messenger.OnCharacteristicChanged
import com.juul.able.experimental.messenger.OnCharacteristicRead
import com.juul.able.experimental.messenger.OnCharacteristicWrite
import com.juul.able.experimental.messenger.OnConnectionStateChange
import com.juul.able.experimental.messenger.OnDescriptorWrite
import com.juul.able.experimental.messenger.OnMtuChanged
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import java.util.UUID

class CoroutinesGatt(
    private val bluetoothGatt: BluetoothGatt,
    private val messenger: Messenger
) : Gatt {

    private val connectionStateMonitor by lazy { ConnectionStateMonitor(this) }

    override val onConnectionStateChange: BroadcastChannel<OnConnectionStateChange>
        get() = messenger.callback.onConnectionStateChange

    override val onCharacteristicChanged: BroadcastChannel<OnCharacteristicChanged>
        get() = messenger.callback.onCharacteristicChanged

    override fun requestConnect(): Boolean = bluetoothGatt.connect()
    override fun requestDisconnect(): Unit = bluetoothGatt.disconnect()

    override suspend fun connect(): Boolean {
        return if (requestConnect()) {
            connectionStateMonitor.suspendUntilConnectionState(STATE_CONNECTED)
        } else {
            Able.error { "connect → BluetoothGatt.requestConnect() returned false " }
            false
        }
    }

    override suspend fun disconnect(): Unit {
        requestDisconnect()
        connectionStateMonitor.suspendUntilConnectionState(STATE_DISCONNECTED)
    }

    override fun close() {
        Able.verbose { "close → Begin" }
        connectionStateMonitor.close()
        messenger.close()
        bluetoothGatt.close()
        Able.verbose { "close → End" }
    }

    override val services: List<BluetoothGattService> get() = bluetoothGatt.services
    override fun getService(uuid: UUID): BluetoothGattService? = bluetoothGatt.getService(uuid)

    /**
     * @return [GattStatus] or `null` if underlying [BluetoothGatt.discoverServices] returns `false`.
     */
    override suspend fun discoverServices(): GattStatus? {
        Able.debug { "discoverServices → send(DiscoverServices)" }

        val response = CompletableDeferred<Boolean>()
        messenger.send(DiscoverServices(response))

        Able.verbose { "discoverServices → Waiting for BluetoothGatt.discoverServices()" }
        return if (response.await()) {
            Able.verbose { "discoverServices → Waiting for BluetoothGattCallback" }
            messenger.callback.onServicesDiscovered.receive().also { status ->
                Able.info { "discoverServices ← status=${status.asGattStatusString()}" }
            }
        } else {
            Able.error { "BluetoothGatt.discoverServices() returned false." }
            null
        }
    }

    /**
     * @return [OnCharacteristicRead] or `null` if underlying [BluetoothGatt.readCharacteristic] returns `false`.
     */
    override suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic
    ): OnCharacteristicRead? {
        val uuid = characteristic.uuid
        Able.debug { "readCharacteristic → send(ReadCharacteristic($uuid))" }

        val response = CompletableDeferred<Boolean>()
        messenger.send(ReadCharacteristic(characteristic, response))

        Able.verbose { "readCharacteristic → Waiting for BluetoothGatt.readCharacteristic($uuid)" }
        return if (response.await()) {
            Able.verbose { "readCharacteristic → Waiting for BluetoothGattCallback" }
            messenger.callback.onCharacteristicRead.receive().also { (_, value, status) ->
                Able.info {
                    val bytesString = value.size.bytesString
                    "readCharacteristic($uuid) ← $bytesString, ${status.asGattStatusString()}"
                }
            }
        } else {
            Able.error { "BluetoothGatt.readCharacteristic($uuid) returned false." }
            null
        }
    }

    /**
     * @param value applied to [characteristic] when characteristic is written.
     * @param writeType applied to [characteristic] when characteristic is written.
     * @return [OnCharacteristicWrite] or `null` if underlying [BluetoothGatt.writeCharacteristic] returns `false`.
     */
    override suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): OnCharacteristicWrite? {
        val uuid = characteristic.uuid
        Able.debug { "writeCharacteristic → send(WriteCharacteristic[uuid=$uuid])" }

        val response = CompletableDeferred<Boolean>()
        messenger.send(WriteCharacteristic(characteristic, value, writeType, response))

        Able.verbose { "writeCharacteristic → Waiting for BluetoothGatt.writeCharacteristic($uuid)" }
        return if (response.await()) {
            Able.verbose { "writeCharacteristic → Waiting for BluetoothGattCallback" }
            messenger.callback.onCharacteristicWrite.receive().also { (_, status) ->
                Able.info {
                    val bytesString = value.size.bytesString
                    val typeString = writeType.asWriteTypeString()
                    val statusString = status.asGattStatusString()
                    "→ writeCharacteristic $uuid ($bytesString), type=$typeString, status=$statusString"
                }
            }
        } else {
            Able.error { "BluetoothGatt.writeCharacteristic($uuid) returned false." }
            null
        }
    }

    /**
     * @param value applied to [descriptor] when descriptor is written.
     * @return [OnDescriptorWrite] or `null` if underlying [BluetoothGatt.writeDescriptor] returns `false`.
     */
    override suspend fun writeDescriptor(
        descriptor: BluetoothGattDescriptor, value: ByteArray
    ): OnDescriptorWrite? {
        val uuid = descriptor.uuid
        Able.debug { "writeDescriptor → send(WriteDescriptor[uuid=$uuid])" }

        val response = CompletableDeferred<Boolean>()
        messenger.send(WriteDescriptor(descriptor, value, response))

        val call = "BluetoothGatt.writeDescriptor(BluetoothGattDescriptor[uuid=$uuid])"
        Able.verbose { "writeDescriptor → Waiting for $call" }
        if (!response.await()) {
            throw RemoteException("$call returned false.")
        }

        Able.verbose { "writeDescriptor → Waiting for BluetoothGattCallback" }
        return messenger.callback.onDescriptorWrite.receive().also { (_, status) ->
            Able.info {
                val bytesString = value.size.bytesString
                val statusString = status.asGattStatusString()
                "→ writeDescriptor $uuid ($bytesString), status=$statusString"
            }
        }
    }

    /**
     * @return [OnMtuChanged] or `null` if underlying [BluetoothGatt.requestMtu] returns `false`.
     */
    override suspend fun requestMtu(mtu: Int): OnMtuChanged? {
        Able.debug { "requestMtu → send(RequestMtu[mtu=$mtu])" }

        val response = CompletableDeferred<Boolean>()
        messenger.send(RequestMtu(mtu, response))

        val call = "BluetoothGatt.requestMtu($mtu)"
        Able.verbose { "requestMtu → Waiting for $call" }
        if (!response.await()) {
            throw RemoteException("$call returned false.")
        }

        Able.verbose { "requestMtu → Waiting for BluetoothGattCallback" }
        return messenger.callback.onMtuChanged.receive().also { (mtu, status) ->
            Able.info { "requestMtu $mtu, status=${status.asGattStatusString()}" }
        }
    }

    override fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Boolean {
        Able.info { "setCharacteristicNotification ${characteristic.uuid} enable=$enable" }
        return bluetoothGatt.setCharacteristicNotification(characteristic, enable)
    }
}

private val Int.bytesString get() = if (this == 1) "$this byte" else "$this bytes"
