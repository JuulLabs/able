/*
 * Copyright 2018 JUUL Labs, Inc.
 */

@file:Suppress("RedundantUnitReturnType")

package com.juul.able.experimental.retry

import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import com.juul.able.experimental.Able
import com.juul.able.experimental.Gatt
import com.juul.able.experimental.OnCharacteristicRead
import com.juul.able.experimental.OnCharacteristicWrite
import com.juul.able.experimental.OnDescriptorWrite
import com.juul.able.experimental.WriteType
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun Gatt.withRetry(timeoutDuration: Long, timeoutUnit: TimeUnit) =
    Retry(this, timeoutDuration, timeoutUnit)

/**
 * Wraps a [Gatt] to add I/O retry functionality and on-demand connection establishment.
 *
 * All I/O operations (e.g. [writeCharacteristic], [readCharacteristic], etc) will:
 * - Check that a connection is established
 *     - If a connection is not established, then it will request a connection
 * - Attempt the I/O operation
 *
 * If I/O operation fails then it will repeat the above process until it is successful or timeout
 * occurs.
 *
 * Explicitly calling [disconnect] or [close] will disable (set [isEnabled] to `false`) this
 * [Retry]. When this [Retry] is disabled, it will not repeat the aforementioned process.
 *
 * Timeout is the allowed duration that an I/O operation can take before timing out. When a timeout
 * occurs, a [TimeoutCancellationException] is thrown (see [withTimeout]).
 *
 * The timeout functionality cannot be disabled (i.e. timeout must be > `0`); alternatively a large
 * timeout may be used, such as `Long.MAX_VALUE`.
 *
 * The timeout is backed by a variable representing timeout in milliseconds, as such, the maximum
 * supported timeout is `Long.MAX_VALUE` (2^63 - 1) milliseconds or ~292,471,207.50888 years.
 */
class Retry(private val gatt: Gatt, timeoutDuration: Long, timeoutUnit: TimeUnit) : Gatt by gatt {

    private val timeoutMillis = timeoutUnit.toMillis(timeoutDuration).also {
        require(it > 0L) { "Timeout (milliseconds) must be > 0, was $it." }
    }

    private val _isEnabled = AtomicBoolean(true)

    /**
     * Determines if this [Retry] is enabled. When [isEnabled] is `false`, [checkConnection] will
     * not attempt to re-establish connection.
     */
    var isEnabled: Boolean
        get() = _isEnabled.get()
        set(value) = _isEnabled.set(value)

    /**
     * Suspends until connection state is [STATE_CONNECTED].
     *
     * If [STATE_DISCONNECTED] occurs then [Gatt.connect] will be invoked.
     *
     * This method will return immediately if [isEnabled] is `false`.
     *
     * @throws [IllegalStateException] if [Gatt.connect] call returns `false`.
     */
    private suspend fun checkConnection(): Unit {
        if (!isEnabled) {
            return
        }

        Able.verbose { "checkConnection → Begin" }
        onConnectionStateChange.openSubscription().also { subscription ->
            subscription.consumeEach { (_, newState) ->
                Able.verbose { "checkConnection → consumeEach → newState = $newState" }
                if (!isEnabled) {
                    subscription.cancel()
                    return
                }

                when (newState) {
                    STATE_DISCONNECTED -> {
                        Able.debug { "checkConnection → consumeEach → STATE_DISCONNECTED" }
                        gatt.requestConnect() || error("`BluetoothGatt.connect()` returned false.")
                    }
                    STATE_CONNECTED -> {
                        Able.debug { "checkConnection → consumeEach → STATE_CONNECTED" }
                        subscription.cancel()
                    }
                }
                Able.verbose { "checkConnection → consumeEach → Repeat" }
            }
            Able.verbose { "checkConnection → End" }
        }
    }

    private val lock = Mutex(locked = false)
    private suspend fun <T> withTimeoutLock(millis: Long, block: suspend () -> T): T {
        return lock.withLock {
            withTimeout(millis) {
                block()
            }
        }
    }

    override fun requestConnect(): Boolean = gatt.requestConnect().also { isEnabled = true }

    override fun requestDisconnect() {
        isEnabled = false
        gatt.requestDisconnect()
    }

    override fun close(): Unit {
        isEnabled = false
        gatt.close()
    }

    /**
     * Reads (and retries if necessary) characteristic, connecting to [Gatt] as needed.
     *
     * @throws [TimeoutCancellationException] if timeout occurs.
     * @throws [IllegalStateException] if [checkConnection] calls [Gatt.connect] and it returns `false`.
     */
    override suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic
    ): OnCharacteristicRead {
        return withTimeoutLock(timeoutMillis) {
            var result: OnCharacteristicRead
            do {
                checkConnection()
                result = gatt.readCharacteristic(characteristic)
            } while (result.status != GATT_SUCCESS && isEnabled)
            result
        }
    }

    /**
     * Writes (and retries if necessary) characteristic, connecting to [Gatt] as needed.
     *
     * @throws [TimeoutCancellationException] if timeout occurs.
     * @throws [IllegalStateException] if [checkConnection] calls [Gatt.connect] and it returns `false`.
     */
    override suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): OnCharacteristicWrite {
        return withTimeoutLock(timeoutMillis) {
            var result: OnCharacteristicWrite
            do {
                checkConnection()
                result = gatt.writeCharacteristic(characteristic, value, writeType)
            } while (result.status != GATT_SUCCESS && isEnabled)
            result
        }
    }

    /**
     * Writes (and retries if necessary) descriptor, connecting to [Gatt] as needed.
     *
     * @throws [TimeoutCancellationException] if timeout occurs.
     * @throws [IllegalStateException] if [checkConnection] calls [Gatt.connect] and it returns `false`.
     */
    override suspend fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): OnDescriptorWrite {
        return withTimeoutLock(timeoutMillis) {
            var result: OnDescriptorWrite
            do {
                checkConnection()
                result = gatt.writeDescriptor(descriptor, value)
            } while (result.status != GATT_SUCCESS && isEnabled)
            result
        }
    }
}
