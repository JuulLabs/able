/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.device

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.juul.able.gatt.Gatt

sealed class ConnectGattResult {
    data class Success(
        val gatt: Gatt
    ) : ConnectGattResult()

    sealed class Failure : ConnectGattResult() {

        abstract val cause: Exception

        /** Android's [BluetoothDevice.connectGatt] returned `null` (e.g. BLE unsupported). */
        data class Rejected(
            override val cause: Exception
        ) : Failure()

        /** Connection could not be established (e.g. device is out of range). */
        data class Connection(
            override val cause: Exception
        ) : Failure()
    }
}

interface Device {
    suspend fun connectGatt(context: Context): ConnectGattResult
}
