package com.malinskiy.marathon.ios.logparser

import com.malinskiy.marathon.ios.logparser.target.TestTargetProvider
import com.malinskiy.marathon.ios.logparser.listener.TestRunListener
import com.malinskiy.marathon.ios.logparser.parser.TestRunProgressParser

import com.malinskiy.marathon.test.Test
import com.malinskiy.marathon.test.TestBatch
import com.malinskiy.marathon.time.Timer
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.amshove.kluent.Verify
import org.amshove.kluent.When
import org.amshove.kluent.any
import org.amshove.kluent.called
import org.amshove.kluent.calling
import org.amshove.kluent.itAnswers
import org.amshove.kluent.itReturns
import org.amshove.kluent.mock
import org.amshove.kluent.on
import org.amshove.kluent.that
import org.amshove.kluent.was
import org.amshove.kluent.withFirstArg
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.File

class ProgressParserSpek : Spek({
    describe("TestRunProgressParser") {
        val mockTimer = mock(Timer::class)
        val mockedStartTimeMillis = 1537187696000L
        val mockedEndTimeMillis = 1537187696999L
        var mockedTime = mockedStartTimeMillis

        val mockTargetProvider = mock(TestTargetProvider::class)
        val mockTestBatch = mock(TestBatch::class)
        val mockListener = mock(TestRunListener::class)
        val progressParser = TestRunProgressParser(mockTimer, mockTargetProvider, mockTestBatch, listOf(mockListener))

        beforeEachTest {
            mockedTime = mockedStartTimeMillis
            When calling mockTargetProvider.targetOf(any()) itAnswers withFirstArg()
            whenever(mockTimer.currentTimeMillis()).thenAnswer { mockedTime.also { mockedTime = mockedEndTimeMillis } }
        }
        afterEachTest { reset(mockTimer, mockListener, mockTestBatch, mockTargetProvider) }

        on("parsing a crashing test batch output") {
            val testOutputFile = File(javaClass.classLoader.getResource("fixtures/test_output/crash_0.log").file)

            it("should report a failed test with an estimated duration") {
                testOutputFile.readLines().forEach {
                    progressParser.onLine(it)
                }

                Verify on mockListener that mockListener.testStarted(Test("sample_appUITests", "CrashingTests", "testCrashingRoutine", emptyList())) was called
                Verify on mockListener that mockListener.testFailed(Test("sample_appUITests", "CrashingTests", "testCrashingRoutine", emptyList()),
                        mockedStartTimeMillis,
                        mockedEndTimeMillis) was called
            }
        }

        on("parsing a single crashing test output") {
            val testOutputFile = File(javaClass.classLoader.getResource("fixtures/test_output/crash_single_0.log").file)

            it("should report a failed test with an estimated duration") {
                testOutputFile.readLines().forEach {
                    progressParser.onLine(it)
                }

                Verify on mockListener that mockListener.testStarted(Test("sample_appUITests", "CrashingTests", "testCrashingRoutine", emptyList())) was called
                Verify on mockListener that mockListener.testFailed(Test("sample_appUITests", "CrashingTests", "testCrashingRoutine", emptyList()),
                        mockedStartTimeMillis,
                        mockedEndTimeMillis) was called
            }
        }
    }

    describe("TestRunProgressParser") {
        val mockTargetProvider = mock(TestTargetProvider::class)
        val mockTestBatch = mock(TestBatch::class)
        val mockListener = mock(TestRunListener::class)
        val mockTimer = mock(Timer::class)
        val mockedTimeMillis = 1537187696000L
        When calling mockTimer.currentTimeMillis() itReturns mockedTimeMillis

        val progressParser = TestRunProgressParser(mockTimer, mockTargetProvider, mockTestBatch, listOf(mockListener))

        beforeEachTest { When calling mockTargetProvider.targetOf(any()) itAnswers withFirstArg() }
        afterEachTest { reset(mockListener, mockTestBatch, mockTargetProvider) }

        on("parsing testing output") {
            val testOutputFile = File(javaClass.classLoader.getResource("fixtures/test_output/success_0.log").file)

            it("should use assigned test target provider") {
                testOutputFile.readLines().forEach {
                    progressParser.onLine(it)
                }

                verify(mockTargetProvider, atLeastOnce()) that mockTargetProvider.targetOf("sample_appUITests") was called
            }
        }

        on("parsing single success output") {
            val testOutputFile = File(javaClass.classLoader.getResource("fixtures/test_output/success_0.log").file)

            it("should report single start and success") {
                testOutputFile.readLines().forEach {
                    progressParser.onLine(it)
                }

                Verify on mockListener that mockListener.testStarted(Test("sample_appUITests", "MoreTests", "testPresentModal", emptyList())) was called
                Verify on mockListener that mockListener.testPassed(Test("sample_appUITests", "MoreTests", "testPresentModal", emptyList()),
                        mockedTimeMillis - 5315,
                        mockedTimeMillis) was called
            }
        }

        on("parsing multiple success output") {
            val testOutputFile = File(javaClass.classLoader.getResource("fixtures/test_output/success_multiple_0.log").file)

            it("should report multiple starts and successes") {
                testOutputFile.readLines().forEach {
                    progressParser.onLine(it)
                }

                Verify on mockListener that mockListener.testStarted(Test("sample_appUITests", "FlakyTests", "testTextFlaky1", emptyList())) was called
                Verify on mockListener that mockListener.testStarted(Test("sample_appUITests", "FlakyTests", "testTextFlaky2", emptyList())) was called

                Verify on mockListener that mockListener.testPassed(Test("sample_appUITests", "FlakyTests", "testTextFlaky1", emptyList()),
                        mockedTimeMillis - 4415,
                        mockedTimeMillis) was called
                Verify on mockListener that mockListener.testPassed(Test("sample_appUITests", "FlakyTests", "testTextFlaky2", emptyList()),
                        mockedTimeMillis - 4118,
                        mockedTimeMillis) was called
            }
        }
    }
})
