/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.throwable.android

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.content.Context
import android.os.RemoteException
import com.juul.able.android.connectGatt
import com.juul.able.device.ConnectGattResult.Failure
import com.juul.able.device.ConnectGattResult.Success
import com.juul.able.gatt.ConnectionLost
import com.juul.able.gatt.Gatt
import com.juul.able.gatt.GattErrorStatus

/**
 * @throws RemoteException if underlying [BluetoothDevice.connectGatt] returns `null`.
 * @throws GattErrorStatus if non-[GATT_SUCCESS] status is received during connection process.
 * @throws ConnectionLost if [STATE_DISCONNECTED] is received during connection process.
 */
suspend fun BluetoothDevice.connectGattOrThrow(
    context: Context
): Gatt = when (val result = connectGatt(context)) {
    is Success -> result.gatt
    is Failure -> throw result.cause
}
