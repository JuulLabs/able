/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.processor

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.juul.able.experimental.WriteType

/**
 * Processors add the ability to process (and optionally modify) GATT data pre-write or post-read.
 */
interface Processor {

    /**
     * @param characteristic that was read.
     * @param value read from characteristic to be processed.
     * @return result of processing [value].
     */
    fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): ByteArray

    /**
     * @param characteristic that will be written to.
     * @param value to be processed prior to being written to [characteristic].
     * @param writeType of the characteristic that will be written.
     * @return result of processing [value].
     */
    fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): ByteArray

    /**
     * @param descriptor that will be written to.
     * @param value to be processed prior to being written to [descriptor].
     * @return result of processing [value].
     */
    fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): ByteArray
}
