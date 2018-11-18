package com.malinskiy.marathon.log

import mu.KLogger

class KLoggerDebug(underlyingLogger: KLogger) : KLogger by underlyingLogger {

    override fun debug(msg: () -> Any?) {
        warn(msg)
    }

    override fun debug(t: Throwable?, msg: () -> Any?) {
        warn(t, msg)
    }

    override fun debug(marker: mu.Marker?, msg: () -> Any?) {
        warn(marker, msg)
    }

    override fun debug(marker: mu.Marker?, t: Throwable?, msg: () -> Any?) {
        warn(marker, t, msg)
    }

    override fun info(msg: () -> Any?) {
        warn(msg)
    }

    override fun info(t: Throwable?, msg: () -> Any?) {
        warn(t, msg)
    }

    override fun info(marker: mu.Marker?, msg: () -> Any?) {
        warn(marker, msg)
    }

    override fun info(marker: mu.Marker?, t: Throwable?, msg: () -> Any?) {
        warn(marker, t, msg)
    }
}