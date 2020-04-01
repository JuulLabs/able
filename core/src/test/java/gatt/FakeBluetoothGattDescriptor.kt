/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.gatt

import android.bluetooth.BluetoothGattDescriptor
import java.util.UUID

class FakeBluetoothGattDescriptor(
    uuid: UUID,
    value: ByteArray = byteArrayOf()
) : BluetoothGattDescriptor(uuid, 0) {

    private val fakeUuid: UUID = uuid
    private var fakeValue: ByteArray = value

    override fun getUuid(): UUID = fakeUuid
    override fun setValue(value: ByteArray): Boolean {
        fakeValue = value
        return true
    }
    override fun getValue(): ByteArray = fakeValue
}
