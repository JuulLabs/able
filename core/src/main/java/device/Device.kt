/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.device

import android.content.Context
import com.juul.able.gatt.Gatt

class ConnectionFailed(message: String, cause: Throwable) : IllegalStateException(message, cause)

sealed class ConnectGattResult {
    data class Success(
        val gatt: Gatt
    ) : ConnectGattResult()

    data class Failure(
        val cause: Exception
    ) : ConnectGattResult()
}

interface Device {
    suspend fun connectGatt(context: Context): ConnectGattResult
}
