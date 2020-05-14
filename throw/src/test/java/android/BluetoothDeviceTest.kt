/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.throwable.test.android

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt.GATT_FAILURE
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.content.Context
import com.juul.able.android.connectGatt
import com.juul.able.device.ConnectGattResult.Failure
import com.juul.able.device.ConnectGattResult.Success
import com.juul.able.gatt.Gatt
import com.juul.able.gatt.GattStatusFailure
import com.juul.able.gatt.OnConnectionStateChange
import com.juul.able.throwable.android.connectGattOrThrow
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class BluetoothDeviceTest {

    @Test
    fun `connectGattOrThrow returns result Gatt on success`() {
        val context = mockk<Context>()
        val gatt = mockk<Gatt>()
        val bluetoothDevice = mockk<BluetoothDevice>()

        mockkStatic("com.juul.able.android.BluetoothDeviceKt")
        try {
            coEvery { bluetoothDevice.connectGatt(any()) } returns Success(gatt)

            val result = runBlocking {
                bluetoothDevice.connectGattOrThrow(context)
            }

            assertEquals(
                expected = gatt,
                actual = result
            )
        } finally {
            unmockkStatic("com.juul.able.android.BluetoothDeviceKt")
        }
    }

    @Test
    fun `connectGattOrThrow throws result cause on failure`() {
        val context = mockk<Context>()
        val bluetoothDevice = mockk<BluetoothDevice>()
        val cause = GattStatusFailure(OnConnectionStateChange(GATT_FAILURE, STATE_DISCONNECTED))

        mockkStatic("com.juul.able.android.BluetoothDeviceKt") {
            coEvery { bluetoothDevice.connectGatt(any()) } returns Failure.Connection(cause)

            assertFailsWith<GattStatusFailure> {
                runBlocking {
                    bluetoothDevice.connectGattOrThrow(context)
                }
            }
        }
    }
}
