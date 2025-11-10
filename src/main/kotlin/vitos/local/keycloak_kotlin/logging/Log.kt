@file:Suppress("unused")

package vitos.local.keycloak_kotlin.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.StackWalker.StackFrame
import kotlin.toString

abstract class Log {

    companion object {
        const val UNKNOWN_METHOD = "<UNKNOWN_METHOD>"
        const val UNKNOWN_CLASS = "<UNKNOWN_CLASS>"
    }
    private val walker: StackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
    val logger: Logger = LoggerFactory.getLogger(this.javaClass)


    fun Logger.infoM(message: Any) {

        val methodName = walker.walk {
            it.skip(1).findFirst().map(StackFrame::getMethodName).orElse(UNKNOWN_METHOD)
        }
        info("{}() :: {}", methodName, message.toString())
    }

    fun Logger.infoCM(message: Any?) {

        val stackFrame = walker.walk { it.skip(1).findFirst() }
        var className = UNKNOWN_CLASS
        var methodName = UNKNOWN_METHOD
        if (stackFrame.isPresent) {
            className = stackFrame.map(StackFrame::getFileName).get()
            methodName = stackFrame.map(StackFrame::getMethodName).get()
        }
        info("{} :: {}() :: {}", className, methodName, message.toString())
    }

    fun Logger.debugM(message: Any?) {

        val methodName = walker.walk {
            it.skip(1).findFirst().map(StackFrame::getMethodName).orElse(UNKNOWN_METHOD)
        }
        debug("{}() :: {}", methodName, message.toString())
    }

    fun Logger.debugM(message: Any?, throwable: Throwable?) {

        val methodName = walker.walk {
            it.skip(1).findFirst().map(StackFrame::getMethodName).orElse(UNKNOWN_METHOD)
        }
        debug("{}() :: {}", methodName, message.toString(), throwable)
    }

    fun Logger.errorM(message: Any?) {

        val methodName = walker.walk {
            it.skip(1).findFirst().map(StackFrame::getMethodName).orElse(UNKNOWN_METHOD)
        }
        error("{}() :: {}", methodName, message.toString())
    }

    fun Logger.errorM(message: Any?, throwable: Throwable) {

        val methodName = walker.walk {
            it.skip(1).findFirst().map(StackFrame::getMethodName).orElse(UNKNOWN_METHOD)
        }
        error("{}() :: {}", methodName, message.toString(), throwable)
    }

    fun Logger.warnM(message: Any?) {

        val methodName = walker.walk {
            it.skip(1).findFirst().map(StackFrame::getMethodName).orElse(UNKNOWN_METHOD)
        }
        warn("{}() :: {}", methodName, message.toString())
    }
}

class Logger {
    companion object: Log()
}

