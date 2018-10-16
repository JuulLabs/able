/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

import android.content.Context
import com.juul.able.experimental.ConnectGattResult.Success
import kotlinx.coroutines.experimental.CancellationException

interface ConnectGattError {
    val cause: Throwable
}

sealed class ConnectGattResult {
    data class Success(val gatt: Gatt) : ConnectGattResult()

    data class Canceled(
        override val cause: CancellationException
    ) : ConnectGattResult(), ConnectGattError

    data class Failure(
        override val cause: Throwable
    ) : ConnectGattResult(), ConnectGattError
}

interface Device {
    suspend fun connectGatt(context: Context, autoConnect: Boolean): ConnectGattResult
}

suspend fun Device.connectGattOrNull(context: Context, autoConnect: Boolean): Gatt? {
    val result = connectGatt(context, autoConnect)

    @Suppress("IfThenToSafeAccess") // Easier to read as `if` statement.
    return if (result is Success) result.gatt else null
}
