/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.gatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.os.RemoteException
import com.juul.able.Able
import java.util.UUID
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OutOfOrderGattCallback(message: String) : IllegalStateException(message)

class GattResponseFailure(
    message: String,
    cause: Throwable?
) : IllegalStateException(message, cause)

class CoroutinesGatt internal constructor(
    private val bluetoothGatt: BluetoothGatt,
    private val dispatcher: ExecutorCoroutineDispatcher,
    private val callback: GattCallback
) : Gatt {

    @FlowPreview
    override val onConnectionStateChange = callback.onConnectionStateChange.asFlow()

    @FlowPreview
    override val onCharacteristicChanged = callback.onCharacteristicChanged.asFlow()

    override val services: List<BluetoothGattService> get() = bluetoothGatt.services
    override fun getService(uuid: UUID): BluetoothGattService? = bluetoothGatt.getService(uuid)

    override suspend fun disconnect() {
        try {
            bluetoothGatt.disconnect()
            suspendUntilConnectionState(STATE_DISCONNECTED)
        } finally {
            callback.close(bluetoothGatt)
        }
    }

    /**
     * @throws [RemoteException] if underlying [BluetoothGatt.discoverServices] returns `false`.
     * @throws [ConnectionLost] if [Gatt] disconnects while method is executing.
     */
    override suspend fun discoverServices(): GattStatus =
        performBluetoothAction("discoverServices") {
            bluetoothGatt.discoverServices()
        }

    /**
     * @throws [RemoteException] if underlying [BluetoothGatt.readCharacteristic] returns `false`.
     * @throws [ConnectionLost] if [Gatt] disconnects while method is executing.
     */
    override suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic
    ): OnCharacteristicRead =
        performBluetoothAction("readCharacteristic") {
            bluetoothGatt.readCharacteristic(characteristic)
        }

    /**
     * @param value applied to [characteristic] when characteristic is written.
     * @param writeType applied to [characteristic] when characteristic is written.
     * @throws [RemoteException] if underlying [BluetoothGatt.writeCharacteristic] returns `false`.
     * @throws [ConnectionLost] if [Gatt] disconnects while method is executing.
     */
    override suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): OnCharacteristicWrite =
        performBluetoothAction("writeCharacteristic") {
            characteristic.value = value
            characteristic.writeType = writeType
            bluetoothGatt.writeCharacteristic(characteristic)
        }

    /**
     * @param value applied to [descriptor] when descriptor is written.
     * @throws [RemoteException] if underlying [BluetoothGatt.writeDescriptor] returns `false`.
     * @throws [ConnectionLost] if [Gatt] disconnects while method is executing.
     */
    override suspend fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): OnDescriptorWrite =
        performBluetoothAction("writeDescriptor") {
            descriptor.value = value
            bluetoothGatt.writeDescriptor(descriptor)
        }

    /**
     * @throws [RemoteException] if underlying [BluetoothGatt.requestMtu] returns `false`.
     * @throws [ConnectionLost] if [Gatt] disconnects while method is executing.
     */
    override suspend fun requestMtu(mtu: Int): OnMtuChanged =
        performBluetoothAction("requestMtu") {
            bluetoothGatt.requestMtu(mtu)
        }

    override fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Boolean {
        Able.info { "setCharacteristicNotification → uuid=${characteristic.uuid}, enable=$enable" }
        return bluetoothGatt.setCharacteristicNotification(characteristic, enable)
    }

    private val lock = Mutex()

    private suspend inline fun <reified T> performBluetoothAction(
        methodName: String,
        crossinline action: () -> Boolean
    ): T = lock.withLock {
        Able.verbose { "$methodName → withContext $dispatcher" }
        withContext(dispatcher) {
            action.invoke() || throw RemoteException("BluetoothGatt.$methodName returned false")
        }

        Able.verbose { "$methodName ← Waiting for BluetoothGattCallback" }
        val response = try {
            callback.onResponse.receive()
        } catch (e: Exception) {
            throw GattResponseFailure("Failed to receive response for $methodName", e)
        }
        Able.info { "$methodName ← $response" }

        // `lock` should always enforce a 1:1 matching of request to response, but if an Android
        // `BluetoothGattCallback` method gets called out of order then we'll cast to the wrong
        // response type.
        response as? T
            ?: throw OutOfOrderGattCallback(
                "Unexpected response type ${response.javaClass.simpleName} received for $methodName"
            )
    }

    override fun toString(): String = "CoroutinesGatt(device=${bluetoothGatt.device})"
}
