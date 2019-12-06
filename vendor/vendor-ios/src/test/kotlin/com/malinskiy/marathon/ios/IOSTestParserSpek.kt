package com.malinskiy.marathon.ios

import com.malinskiy.marathon.execution.Configuration
import com.malinskiy.marathon.test.Test
import org.amshove.kluent.shouldContainSame
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.File

object IOSTestParserSpek : Spek({
    describe("iOS test parser") {
        val parser = IOSTestParser()

        on("project sources") {
            val sourceRoot = File(javaClass.classLoader.getResource("sample-xcworkspace/sample-appUITests").file)
            val derivedDataDir = File(javaClass.classLoader.getResource("sample-xcworkspace/derived-data").file)
            val xctestrunPath = File(javaClass.classLoader.getResource("sample-xcworkspace/derived-data/Build/Products/UITesting_iphonesimulator11.2-x86_64.xctestrun").file)
            val configuration = Configuration(name = "",
                    outputDir = File(""),
                    analyticsConfiguration = null,
                    poolingStrategy = null,
                    shardingStrategy = null,
                    sortingStrategy = null,
                    batchingStrategy = null,
                    flakinessStrategy = null,
                    retryStrategy = null,
                    filteringConfiguration = null,
                    ignoreFailures = null,
                    isCodeCoverageEnabled = null,
                    fallbackToScreenshots = null,
                    testSuiteNameMatchesClassName = null,
                    strictMode = null,
                    uncompletedTestRetryQuota = null,
                    testClassRegexes = null,
                    includeSerialRegexes = null,
                    excludeSerialRegexes = null,
                    testBatchTimeoutMillis = null,
                    testOutputTimeoutMillis = null,
                    debug = null,
                    vendorConfiguration =  IOSConfiguration(
                            derivedDataDir = derivedDataDir,
                            xctestrunPath = xctestrunPath,
                            remoteUsername = "testuser",
                            remotePrivateKey = File("/home/fakekey"),
                            knownHostsPath = null,
                            remoteRsyncPath = "/remote/rsync",
                            sourceRoot = sourceRoot,
                            debugSsh = false,
                            alwaysEraseSimulators = true,
                            sourceTargetName = null,
                            sourceRootsRegex = null,
                            binaryParserDockerImageName = null),
                    analyticsTracking = false
            )

            it("should return accurate list of tests") {
                val extractedTests = parser.extract(configuration)

                extractedTests shouldContainSame expectedTests
            }

            it("should assign correct target names") {
                val testTargets = parser.extract(configuration).map { it.targetName }

                testTargets shouldContainSame expectedTests.map { it.targetName }
            }
        }
    }
})

private val expectedTests = listOf(
    Test("sample_appUITests", "StoryboardTests", "testButton", "sample-appUITests"),
    Test("sample_appUITests", "StoryboardTests", "testLabel", "sample-appUITests"),
    Test("sample_appUITests", "MoreTests", "testPresentModal", "sample-appUITests"),
    Test("sample_appUITests", "CrashingTests", "testButton", "sample-appUITests"),
    Test("sample_appUITests", "FailingTests", "testAlwaysFailing", "sample-appUITests"),
    Test("sample_appUITests", "FlakyTests", "testTextFlaky", "sample-appUITests"),
    Test("sample_appUITests", "FlakyTests", "testTextFlaky1", "sample-appUITests"),
    Test("sample_appUITests", "FlakyTests", "testTextFlaky2", "sample-appUITests"),
    Test("sample_appUITests", "FlakyTests", "testTextFlaky3", "sample-appUITests"),
    Test("sample_appUITests", "FlakyTests", "testTextFlaky4", "sample-appUITests"),
    Test("sample_appUITests", "FlakyTests", "testTextFlaky5", "sample-appUITests"),
    Test("sample_appUITests", "FlakyTests", "testTextFlaky6", "sample-appUITests"),
    Test("sample_appUITests", "FlakyTests", "testTextFlaky7", "sample-appUITests"),
    Test("sample_appUITests", "FlakyTests", "testTextFlaky8", "sample-appUITests"),
    Test("sample_appUITests", "SlowTests", "testTextSlow", "sample-appUITests"),
    Test("sample_appUITests", "SlowTests", "testTextSlow1", "sample-appUITests"),
    Test("sample_appUITests", "SlowTests", "testTextSlow2", "sample-appUITests"),
    Test("sample_appUITests", "SlowTests", "testTextSlow3", "sample-appUITests"),
    Test("sample_appUITests", "SlowTests", "testTextSlow4", "sample-appUITests")
)