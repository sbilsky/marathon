package com.malinskiy.marathon.android.exception

class DeviceTimeoutException: RuntimeException {
    constructor(cause: Throwable): super(cause)
    constructor(message: String): super(message)
}