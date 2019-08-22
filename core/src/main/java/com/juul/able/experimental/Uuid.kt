/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental

import java.util.UUID

/**
 * @throw [IllegalArgumentException] if receiver does not conform to the string representation as described in [UUID.toString].
 */
fun String.toUuid(): UUID = UUID.fromString(this)
