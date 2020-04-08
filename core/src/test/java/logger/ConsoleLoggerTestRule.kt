/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.test.logger

import com.juul.able.Able
import com.juul.able.Logger
import java.util.Collections
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** JUnit test rule that only prints logs on test failure. */
class ConsoleLoggerTestRule : TestRule {

    override fun apply(
        base: Statement,
        description: Description
    ): Statement = ConsoleLoggerStatement(base)
}

private class ConsoleLoggerStatement(
    private val base: Statement
) : Statement() {

    private val bufferedLogger = BufferedConsoleLogger()

    override fun evaluate() {
        val previousLogger = Able.logger
        Able.logger = bufferedLogger

        try {
            base.evaluate()
        } catch (throwable: Throwable) {
            bufferedLogger.flush()
            throw throwable
        } finally {
            Able.logger = previousLogger
        }
    }
}

private class BufferedConsoleLogger private constructor(
    private val logs: MutableList<String>,
    private val logger: Logger = ConsoleLogger { logs += it }
) : Logger by logger {

    constructor() : this(Collections.synchronizedList(mutableListOf()))

    fun flush() {
        logs.forEach(::println)
    }
}
