/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.gatt

import android.bluetooth.BluetoothGatt.GATT_CONNECTION_CONGESTED
import android.bluetooth.BluetoothGatt.GATT_FAILURE
import android.bluetooth.BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
import android.bluetooth.BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION
import android.bluetooth.BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
import android.bluetooth.BluetoothGatt.GATT_INVALID_OFFSET
import android.bluetooth.BluetoothGatt.GATT_READ_NOT_PERMITTED
import android.bluetooth.BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGatt.GATT_WRITE_NOT_PERMITTED
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_SIGNED
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothProfile.STATE_CONNECTING
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTING

/**
 * https://android.googlesource.com/platform/external/libnfc-nci/+/lollipop-release/src/include/hcidefs.h#447
 */
private const val HCI_ERR_CONNECTION_TOUT = 0x08
private const val HCI_ERR_PEER_USER = 0x13
private const val HCI_ERR_CONN_CAUSE_LOCAL_HOST = 0x16
private const val HCI_ERR_LMP_RESPONSE_TIMEOUT = 0x22
private const val HCI_ERR_CONN_FAILED_ESTABLISHMENT = 0x3E

/**
 * https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/lollipop-release/stack/include/l2cdefs.h#87
 */
private const val L2CAP_CONN_CANCEL = 256

/**
 * https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/lollipop-release/stack/include/gatt_api.h#106
 */
internal const val GATT_CONN_L2C_FAILURE = 1
internal const val GATT_CONN_TIMEOUT = HCI_ERR_CONNECTION_TOUT
internal const val GATT_CONN_TERMINATE_PEER_USER = HCI_ERR_PEER_USER
internal const val GATT_CONN_TERMINATE_LOCAL_HOST = HCI_ERR_CONN_CAUSE_LOCAL_HOST
internal const val GATT_CONN_FAIL_ESTABLISH = HCI_ERR_CONN_FAILED_ESTABLISHMENT
internal const val GATT_CONN_LMP_TIMEOUT = HCI_ERR_LMP_RESPONSE_TIMEOUT
internal const val GATT_CONN_CANCEL = L2CAP_CONN_CANCEL

/**
 * 0xE0 ~ 0xFC reserved for future use
 *
 * https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/lollipop-release/stack/include/gatt_api.h#27
 */
private const val GATT_INVALID_HANDLE = 0x01
private const val GATT_INVALID_PDU = 0x04
private const val GATT_INSUF_AUTHORIZATION = 0x08
private const val GATT_PREPARE_Q_FULL = 0x09
private const val GATT_NOT_FOUND = 0x0a
private const val GATT_NOT_LONG = 0x0b
private const val GATT_INSUF_KEY_SIZE = 0x0c
private const val GATT_ERR_UNLIKELY = 0x0e
private const val GATT_UNSUPPORT_GRP_TYPE = 0x10
private const val GATT_INSUF_RESOURCE = 0x11
private const val GATT_ILLEGAL_PARAMETER = 0x87
private const val GATT_NO_RESOURCES = 0x80
private const val GATT_INTERNAL_ERROR = 0x81
private const val GATT_WRONG_STATE = 0x82
private const val GATT_DB_FULL = 0x83
private const val GATT_BUSY = 0x84
private const val GATT_ERROR = 0x85
private const val GATT_CMD_STARTED = 0x86
private const val GATT_PENDING = 0x88
private const val GATT_AUTH_FAIL = 0x89
private const val GATT_MORE = 0x8a
private const val GATT_INVALID_CFG = 0x8b
private const val GATT_SERVICE_STARTED = 0x8c
private const val GATT_ENCRYPED_NO_MITM = 0x8d
private const val GATT_NOT_ENCRYPTED = 0x8e
private const val GATT_CCC_CFG_ERR = 0xFD
private const val GATT_PRC_IN_PROGRESS = 0xFE
private const val GATT_OUT_OF_RANGE = 0xFF

internal fun GattConnectionStatus.asGattConnectionStatusString() = when (this) {
    GATT_SUCCESS -> "GATT_SUCCESS"
    GATT_CONN_L2C_FAILURE -> "GATT_CONN_L2C_FAILURE"
    GATT_CONN_TIMEOUT -> "GATT_CONN_TIMEOUT"
    GATT_CONN_TERMINATE_PEER_USER -> "GATT_CONN_TERMINATE_PEER_USER"
    GATT_CONN_TERMINATE_LOCAL_HOST -> "GATT_CONN_TERMINATE_LOCAL_HOST"
    GATT_CONN_FAIL_ESTABLISH -> "GATT_CONN_FAIL_ESTABLISH"
    GATT_CONN_LMP_TIMEOUT -> "GATT_CONN_LMP_TIMEOUT"
    GATT_CONN_CANCEL -> "GATT_CONN_CANCEL"
    else -> "GATT_CONN_UNKNOWN"
}.let { name -> "$name($this)" }

internal fun GattConnectionState.asGattConnectionStateString() = when (this) {
    STATE_DISCONNECTING -> "STATE_DISCONNECTING"
    STATE_DISCONNECTED -> "STATE_DISCONNECTED"
    STATE_CONNECTING -> "STATE_CONNECTING"
    STATE_CONNECTED -> "STATE_CONNECTED"
    else -> "STATE_UNKNOWN"
}.let { name -> "$name($this)" }

internal fun GattStatus.asGattStatusString() = when (this) {
    GATT_SUCCESS -> "GATT_SUCCESS"
    GATT_INVALID_HANDLE -> "GATT_INVALID_HANDLE"
    GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED"
    GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED"
    GATT_INVALID_PDU -> "GATT_INVALID_PDU"
    GATT_INSUFFICIENT_AUTHENTICATION -> "GATT_INSUFFICIENT_AUTHENTICATION"
    GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED"
    GATT_INVALID_OFFSET -> "GATT_INVALID_OFFSET"
    GATT_INSUF_AUTHORIZATION -> "GATT_INSUF_AUTHORIZATION"
    GATT_PREPARE_Q_FULL -> "GATT_PREPARE_Q_FULL"
    GATT_NOT_FOUND -> "GATT_NOT_FOUND"
    GATT_NOT_LONG -> "GATT_NOT_LONG"
    GATT_INSUF_KEY_SIZE -> "GATT_INSUF_KEY_SIZE"
    GATT_INVALID_ATTRIBUTE_LENGTH -> "GATT_INVALID_ATTRIBUTE"
    GATT_ERR_UNLIKELY -> "GATT_ERR_UNLIKELY"
    GATT_INSUFFICIENT_ENCRYPTION -> "GATT_INSUFFICIENT_ENCRYPTION"
    GATT_UNSUPPORT_GRP_TYPE -> "GATT_UNSUPPORT_GRP_TYPE"
    GATT_INSUF_RESOURCE -> "GATT_INSUF_RESOURCE"
    GATT_ILLEGAL_PARAMETER -> "GATT_ILLEGAL_PARAMETER"
    GATT_NO_RESOURCES -> "GATT_NO_RESOURCES"
    GATT_INTERNAL_ERROR -> "GATT_INTERNAL_ERROR"
    GATT_WRONG_STATE -> "GATT_WRONG_STATE"
    GATT_DB_FULL -> "GATT_DB_FULL"
    GATT_BUSY -> "GATT_BUSY"
    GATT_ERROR -> "GATT_ERROR"
    GATT_CMD_STARTED -> "GATT_CMD_STARTED"
    GATT_PENDING -> "GATT_PENDING"
    GATT_AUTH_FAIL -> "GATT_AUTH_FAIL"
    GATT_MORE -> "GATT_MORE"
    GATT_INVALID_CFG -> "GATT_INVALID_CFG"
    GATT_SERVICE_STARTED -> "GATT_SERVICE_STARTED"
    GATT_ENCRYPED_NO_MITM -> "GATT_ENCRYPED_NO_MITM"
    GATT_NOT_ENCRYPTED -> "GATT_NOT_ENCRYPTED"
    GATT_CONNECTION_CONGESTED -> "GATT_CONNECTION_CONGESTED"
    GATT_CCC_CFG_ERR -> "GATT_CCC_CFG_ERR"
    GATT_PRC_IN_PROGRESS -> "GATT_PRC_IN_PROGRESS"
    GATT_OUT_OF_RANGE -> "GATT_OUT_OF_RANGE"
    GATT_FAILURE -> "GATT_FAILURE"
    else -> "GATT_UNKNOWN"
}.let { name -> "$name($this)" }

internal fun WriteType.asWriteTypeString() = when (this) {
    WRITE_TYPE_DEFAULT -> "WRITE_TYPE_DEFAULT"
    WRITE_TYPE_NO_RESPONSE -> "WRITE_TYPE_NO_RESPONSE"
    WRITE_TYPE_SIGNED -> "WRITE_TYPE_SIGNED"
    else -> "WRITE_TYPE_UNKNOWN"
}.let { name -> "$name($this)" }
