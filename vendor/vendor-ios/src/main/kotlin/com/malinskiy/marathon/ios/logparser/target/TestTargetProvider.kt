package com.malinskiy.marathon.ios.logparser.target

interface TestTargetProvider {
    fun targetOf(productModule: String?): String?
}
