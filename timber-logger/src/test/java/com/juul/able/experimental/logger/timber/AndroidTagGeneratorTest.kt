/*
 * Copyright 2018 JUUL Labs, Inc.
 */

package com.juul.able.experimental.logger.timber

import com.juul.able.experimental.Able
import com.juul.able.experimental.Logger
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals

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
    fun tagMatchesClassname() {
        val dog = Dog()
        dog.bark()
        assertEquals(dog.javaClass.simpleName, logger.lastTag)
    }

    @Test
    fun tagFromAnonymousMethod() {
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
    fun tagFromAnonymousMethodWithinRunnable() {
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
