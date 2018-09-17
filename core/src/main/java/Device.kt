/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

import android.content.Context
import com.juul.able.experimental.ConnectGattResult.Success
import kotlinx.coroutines.experimental.CancellationException

sealed class ConnectGattResult {
    data class Success(val gatt: Gatt) : ConnectGattResult()
    data class Canceled(val cause: CancellationException) : ConnectGattResult()
    data class Failure(val cause: Throwable) : ConnectGattResult()
}

interface Device {
    suspend fun connectGatt(context: Context, autoConnect: Boolean): ConnectGattResult
}

suspend fun Device.connectGattOrNull(context: Context, autoConnect: Boolean): Gatt? {
    val result = connectGatt(context, autoConnect)

    @Suppress("IfThenToSafeAccess") // Easier to read as `if` statement.
    return if (result is Success) result.gatt else null
}
