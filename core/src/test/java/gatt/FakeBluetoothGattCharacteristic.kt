/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.gatt

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

class FakeBluetoothGattCharacteristic(
    uuid: UUID,
    instanceId: Int = 0,
    value: ByteArray = byteArrayOf()
) : BluetoothGattCharacteristic(uuid, 0, 0) {

    private val fakeUuid: UUID = uuid
    private val fakeInstanceId: Int = instanceId
    private var fakeValue: ByteArray = value
    private var fakeWriteType: WriteType = WRITE_TYPE_DEFAULT

    override fun getUuid(): UUID = fakeUuid

    override fun getInstanceId(): Int = fakeInstanceId

    override fun setValue(value: ByteArray): Boolean {
        fakeValue = value
        return true
    }
    override fun getValue(): ByteArray = fakeValue

    override fun setWriteType(writeType: WriteType) {
        fakeWriteType = writeType
    }
    override fun getWriteType(): WriteType = fakeWriteType
}
