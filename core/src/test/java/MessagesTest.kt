/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import nl.jqno.equalsverifier.EqualsVerifier
import org.junit.Test
import java.util.UUID

private val redDescriptor = mockDescriptor("bce47f52-6c2a-43e9-a382-2c460fcc6f6c")
private val blackDescriptor = mockDescriptor("de923c26-b18a-474a-84e0-7837300fc666")
private val redCharacteristic = mockCharacteristic("63057836-0b22-4341-969a-8fee3a8be2b3")
private val blackCharacteristic = mockCharacteristic("2a5346f9-1aec-4752-acec-5d269aa96e7d")

class MessagesTest {

    @Test
    fun onCharacteristicReadEquals() {
        verifyEquals<OnCharacteristicRead>()
    }

    @Test
    fun onCharacteristicChangedEquals() {
        verifyEquals<OnCharacteristicChanged>()
    }

    @Test
    fun onDescriptorReadEquals() {
        verifyEquals<OnDescriptorRead>()
    }
}

private fun mockDescriptor(uuidString: String): BluetoothGattDescriptor {
    val uuid = UUID.fromString(uuidString)
    return mock {
        whenever(it.uuid).thenReturn(uuid)
    }
}

private fun mockCharacteristic(uuidString: String): BluetoothGattCharacteristic {
    val uuid = UUID.fromString(uuidString)
    return mock {
        whenever(it.uuid).thenReturn(uuid)
    }
}

/**
 * Preconfigures [EqualsVerifier] for validating proper implementation of `data class`es in the
 * `Messages.kt` file that have custom `equals` and `hashCode` implementations:
 *
 * > `EqualsVerifier` can be used in unit tests to verify whether the contract for the `equals` and
 * > `hashCode` methods in a class is met.
 */
private inline fun <reified T> verifyEquals() {
    EqualsVerifier
        .forClass(T::class.java)
        .withPrefabValues(BluetoothGattDescriptor::class.java, redDescriptor, blackDescriptor)
        .withPrefabValues(
            BluetoothGattCharacteristic::class.java,
            redCharacteristic,
            blackCharacteristic
        )
        .verify()
}
