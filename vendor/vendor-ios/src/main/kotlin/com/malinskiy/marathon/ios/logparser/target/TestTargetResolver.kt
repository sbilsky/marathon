package com.malinskiy.marathon.ios.logparser.target

interface TestTargetResolver {
    /**
     * Implementation is expected to return a name of existing build target that contains given module
     */
    fun targetNameOf(productModule: String?): String?
}
