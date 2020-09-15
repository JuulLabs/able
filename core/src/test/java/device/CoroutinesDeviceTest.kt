/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.test.device

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothProfile.STATE_CONNECTING
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.os.RemoteException
import com.juul.able.device.ConnectGattResult
import com.juul.able.device.ConnectGattResult.Failure
import com.juul.able.device.ConnectGattResult.Success
import com.juul.able.device.CoroutinesDevice
import com.juul.able.gatt.ConnectionLostException
import com.juul.able.gatt.GATT_CONN_CANCEL
import com.juul.able.gatt.GattCallback
import com.juul.able.gatt.GattStatusException
import com.juul.able.gatt.OnConnectionStateChange
import com.juul.able.test.logger.ConsoleLoggerTestRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule

private const val MAC_ADDRESS = "00:11:22:33:FF:EE"

class CoroutinesDeviceTest {

    @get:Rule
    val loggerRule = ConsoleLoggerTestRule()

    @Test
    fun `Non-success GATT status during connectGatt returns Failure`() = runBlocking<Unit> {
        val callbackSlot = slot<BluetoothGattCallback>()
        lateinit var bluetoothGatt: BluetoothGatt
        val bluetoothDevice = mockk<BluetoothDevice> {
            bluetoothGatt = createBluetoothGatt(this@mockk)
            every { connectGatt(any(), false, capture(callbackSlot)) } returns bluetoothGatt
            every { this@mockk.toString() } returns MAC_ADDRESS
        }
        val device = CoroutinesDevice(bluetoothDevice)

        launch {
            // Wait for `CoroutinesGatt` to spin up and provide us with the `GattCallback`.
            while (!callbackSlot.isCaptured) yield()
            val callback = callbackSlot.captured as GattCallback

            callback.onConnectionStateChange(bluetoothGatt, GATT_SUCCESS, STATE_CONNECTING)
            callback.onConnectionStateChange(bluetoothGatt, GATT_CONN_CANCEL, STATE_CONNECTED)
        }

        val failure = device.connectGatt(mockk()) as Failure.Connection

        assertEquals(
            expected = OnConnectionStateChange(GATT_CONN_CANCEL, STATE_CONNECTED).toString(),
            actual = (failure.cause as GattStatusException).message
        )
        verify(exactly = 1) { bluetoothGatt.close() }
    }

    @Test
    fun `Success GATT status during connectGatt returns Success`() = runBlocking<Unit> {
        val callbackSlot = slot<BluetoothGattCallback>()
        lateinit var bluetoothGatt: BluetoothGatt
        val bluetoothDevice = mockk<BluetoothDevice> {
            bluetoothGatt = createBluetoothGatt(this@mockk)
            every { connectGatt(any(), false, capture(callbackSlot)) } returns bluetoothGatt
            every { this@mockk.toString() } returns MAC_ADDRESS
        }
        val device = CoroutinesDevice(bluetoothDevice)

        launch {
            // Wait for `CoroutinesGatt` to spin up and provide us with the `GattCallback`.
            while (!callbackSlot.isCaptured) yield()
            val callback = callbackSlot.captured as GattCallback

            callback.onConnectionStateChange(bluetoothGatt, GATT_SUCCESS, STATE_CONNECTING)
            callback.onConnectionStateChange(bluetoothGatt, GATT_SUCCESS, STATE_CONNECTED)
        }

        val result = device.connectGatt(mockk())

        assertEquals<Class<out ConnectGattResult>>(
            expected = Success::class.java,
            actual = result.javaClass
        )
        verify(exactly = 0) { bluetoothGatt.close() }
    }

    @Test
    fun `Receiving STATE_DISCONNECTED during connectGatt returns Failure`() = runBlocking<Unit> {
        val callbackSlot = slot<BluetoothGattCallback>()
        lateinit var bluetoothGatt: BluetoothGatt
        val bluetoothDevice = mockk<BluetoothDevice> {
            bluetoothGatt = createBluetoothGatt(this@mockk)
            every { connectGatt(any(), false, capture(callbackSlot)) } returns bluetoothGatt
            every { this@mockk.toString() } returns MAC_ADDRESS
        }
        val device = CoroutinesDevice(bluetoothDevice)

        launch {
            // Wait for `CoroutinesGatt` to spin up and provide us with the `GattCallback`.
            while (!callbackSlot.isCaptured) yield()
            val callback = callbackSlot.captured as GattCallback
            callback.onConnectionStateChange(bluetoothGatt, GATT_SUCCESS, STATE_DISCONNECTED)
        }

        val failure = device.connectGatt(mockk()) as Failure.Connection

        assertEquals<Class<out Throwable>>(
            expected = ConnectionLostException::class.java,
            actual = failure.cause.javaClass
        )
        verify(exactly = 1) { bluetoothGatt.close() }
    }

    @Test
    fun `Cancelling during connectGatt closes underlying BluetoothGatt`() = runBlocking<Unit> {
        val callbackSlot = slot<BluetoothGattCallback>()
        lateinit var bluetoothGatt: BluetoothGatt
        val bluetoothDevice = mockk<BluetoothDevice> {
            bluetoothGatt = createBluetoothGatt(this@mockk)
            every { connectGatt(any(), false, capture(callbackSlot)) } returns bluetoothGatt
            every { this@mockk.toString() } returns MAC_ADDRESS
        }
        val device = CoroutinesDevice(bluetoothDevice)

        val job = launch {
            device.connectGatt(mockk())
        }

        // Wait for `CoroutinesGatt` to spin up and provide us with the `GattCallback`.
        while (!callbackSlot.isCaptured) yield()
        job.cancelAndJoin()

        verify(exactly = 1) { bluetoothGatt.close() }
    }

    @Test
    fun `Null return from connectGatt results in Failure Rejected`() = runBlocking {
        val bluetoothDevice = mockk<BluetoothDevice> {
            every { connectGatt(any(), false, any()) } returns null
            every { this@mockk.toString() } returns MAC_ADDRESS
        }
        val device = CoroutinesDevice(bluetoothDevice)
        val failure = device.connectGatt(mockk()) as Failure.Rejected

        assertEquals<Class<out Throwable>>(
            expected = RemoteException::class.java,
            actual = failure.cause.javaClass
        )
    }
}

private fun createBluetoothGatt(
    bluetoothDevice: BluetoothDevice
): BluetoothGatt = mockk {
    every { device } returns bluetoothDevice
    every { close() } returns Unit
}
