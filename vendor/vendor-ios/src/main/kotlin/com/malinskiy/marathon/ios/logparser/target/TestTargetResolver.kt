package com.malinskiy.marathon.ios.logparser.target

interface TestTargetResolver {
    fun targetNameOf(productModule: String?): String?
}
