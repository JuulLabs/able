/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

import android.content.Context
import com.juul.able.experimental.ConnectGattResult.ConnectGattSuccess
import kotlinx.coroutines.experimental.CancellationException

sealed class ConnectGattResult {
    data class ConnectGattSuccess(val gatt: Gatt) : ConnectGattResult()
    data class ConnectGattCanceled(val cause: CancellationException) : ConnectGattResult()
    data class ConnectGattFailure(val cause: Throwable) : ConnectGattResult()
}

interface Device {
    suspend fun connectGatt(context: Context, autoConnect: Boolean): ConnectGattResult
}

suspend fun Device.connectGattOrNull(context: Context, autoConnect: Boolean): Gatt? {
    val result = connectGatt(context, autoConnect)

    @Suppress("IfThenToSafeAccess") // Easier to read as `if` statement.
    return if (result is ConnectGattSuccess) result.gatt else null
}
