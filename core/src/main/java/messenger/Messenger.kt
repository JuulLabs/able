/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.messenger

import android.bluetooth.BluetoothGatt
import com.github.ajalt.timberkt.Timber
import com.juul.able.experimental.messenger.Message.CharacteristicNotification
import com.juul.able.experimental.messenger.Message.DiscoverServices
import com.juul.able.experimental.messenger.Message.ReadCharacteristic
import com.juul.able.experimental.messenger.Message.RequestMtu
import com.juul.able.experimental.messenger.Message.WriteCharacteristic
import com.juul.able.experimental.messenger.Message.WriteDescriptor
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.newSingleThreadContext

class Messenger internal constructor(
    private val bluetoothGatt: BluetoothGatt,
    internal val callback: GattCallback
) {

    internal suspend fun send(message: Message) = actor.send(message)

    private val context = newSingleThreadContext("Gatt")
    private val actor = actor<Message>(context) {
        Timber.v { "Begin" }
        consumeEach { message ->
            Timber.d { "Waiting for Gatt" }
            callback.waitForGattReady()

            Timber.d { "Processing ${message.javaClass.simpleName}" }
            val result: Boolean = when (message) {
                is DiscoverServices -> bluetoothGatt.discoverServices()
                is ReadCharacteristic -> bluetoothGatt.readCharacteristic(message.characteristic)
                is RequestMtu -> bluetoothGatt.requestMtu(message.mtu)
                is CharacteristicNotification ->
                    bluetoothGatt.setCharacteristicNotification(
                        message.characteristic,
                        message.enable
                    ).also {
                        // We release the lock right away because `setCharacteristicNotification`
                        // does not result in a `BluetoothGattCallback` method being invoked.
                        callback.notifyGattReady()
                    }
                is WriteCharacteristic -> {
                    message.characteristic.value = message.value
                    message.characteristic.writeType = message.writeType
                    bluetoothGatt.writeCharacteristic(message.characteristic)
                }
                is WriteDescriptor -> {
                    message.descriptor.value = message.value
                    bluetoothGatt.writeDescriptor(message.descriptor)
                }
            }
            Timber.d { "Processed ${message.javaClass.simpleName}, result=$result" }
            message.response.complete(result)
        }
        Timber.v { "End" }
    }

    fun close() {
        Timber.v { "close → Begin" }
        callback.close()
        actor.close()
        closeContext()
        Timber.v { "close → End" }
    }

    /**
     * Explicitly close context (this is only needed until #261 is fixed).
     *
     * [Kotlin Coroutines Issue #261](https://github.com/Kotlin/kotlinx.coroutines/issues/261)
     * [Coroutines actor test Gist](https://gist.github.com/twyatt/c51f81d763a6ee39657233fa725f5435)
     */
    private fun closeContext(): Unit = context.close()
}
