package com.malinskiy.marathon.ios.testparser

import com.malinskiy.marathon.test.Test
import java.io.File

interface DockerTestParser {
    fun listTests(targetName: String, testRunnerPath: File): List<Test>
}