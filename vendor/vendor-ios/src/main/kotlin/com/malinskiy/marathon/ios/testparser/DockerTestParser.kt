package com.malinskiy.marathon.ios.testparser

import com.malinskiy.marathon.test.Test
import java.io.File

interface DockerTestParser {
    fun listTests(testRunnerPaths: List<File>): List<Test>
}