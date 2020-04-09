/*
 * Copyright 2020 JUUL Labs, Inc.
 */

package com.juul.able.logger.timber.test

import com.juul.able.Able
import com.juul.able.logger.Logger
import com.juul.able.logger.timber.AndroidTagGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.BeforeClass

class AndroidTagGeneratorTest {

    private val simpleName = this::class.java.simpleName

    companion object {

        private val logger = CaptureTagLogger()

        @BeforeClass
        @JvmStatic
        fun setUp() {
            Able.logger = logger
        }
    }

    @Test
    fun `Tag matches classname`() {
        val dog = Dog()
        dog.bark()
        assertEquals(dog.javaClass.simpleName, logger.lastTag)
    }

    @Test
    fun `Tag is captured from anonymous method`() {
        val anonymous = object {
            fun go() {
                Able.info { "hello world" }
            }
        }
        anonymous.go()

        val methodName = object {}.javaClass.enclosingMethod.name
        assertEquals("$simpleName\$$methodName\$anonymous", logger.lastTag)
    }

    @Test
    fun `Tag is captured from anonymous method within Runnable`() {
        Runnable {
            object {
                fun go() {
                    Able.info { "hello world" }
                }
            }.go()
        }.run()

        val methodName = object {}.javaClass.enclosingMethod.name
        assertEquals("$simpleName\$$methodName", logger.lastTag)
    }
}

private class Dog {
    fun bark() {
        Able.info { "ruff!" }
    }
}

private class CaptureTagLogger : Logger {

    var lastTag: String? = null

    private val generator = AndroidTagGenerator()

    override fun isLoggable(priority: Int): Boolean = true

    override fun log(priority: Int, throwable: Throwable?, message: String) {
        lastTag = generator.generate()
    }
}
