package com.malinskiy.marathon.ios.logparser

import com.malinskiy.marathon.device.Device
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.execution.TestBatchResults
import com.malinskiy.marathon.execution.progress.ProgressReporter
import com.malinskiy.marathon.ios.logparser.target.TestTargetResolver
import com.malinskiy.marathon.ios.logparser.listener.ProgressReportingListener
import com.malinskiy.marathon.ios.logparser.listener.TestLogListener
import com.malinskiy.marathon.ios.logparser.parser.*
import com.malinskiy.marathon.test.TestBatch
import com.malinskiy.marathon.time.SystemTimer
import kotlinx.coroutines.CompletableDeferred

class IOSDeviceLogParser(device: Device,
                         testTargetResolver: TestTargetResolver,
                         poolId: DevicePoolId,
                         testBatch: TestBatch,
                         deferredResults: CompletableDeferred<TestBatchResults>,
                         progressReporter: ProgressReporter,
                         hideRunnerOutput: Boolean,
                         ignoreSystemProcessCrashes: Boolean,
                         derivedDataPath: String): StreamingLogParser {

    private val underlyingLogParser: StreamingLogParser
    private val testLogListener: TestLogListener
    private val diagnosticLogsPathFinder: DiagnosticLogsPathFinder
    private val sessionResultsPathFinder: SessionResultsPathFinder
    init {
        testLogListener = TestLogListener()
        diagnosticLogsPathFinder = DiagnosticLogsPathFinder(listOf(
                device.serialNumber,
                device.toDeviceInfo().serialNumber
        ).distinct())
        sessionResultsPathFinder = SessionResultsPathFinder()
        underlyingLogParser = CompositeLogParser(
            listOf(
                //Order matters here: first grab the log with log listener,
                //then use this log to insert into the test report
                testLogListener,
                DeviceFailureParser(ignoreSystemProcessCrashes),
                diagnosticLogsPathFinder,
                sessionResultsPathFinder,
                TestRunProgressParser(
                    SystemTimer(),
                    testTargetResolver,
                    listOf(
                        ProgressReportingListener(
                            device = device,
                            poolId = poolId,
                            testBatch = testBatch,
                            deferredResults = deferredResults,
                            progressReporter = progressReporter,
                            testLogListener = testLogListener,
                            diagnosticLogsPathFinder = diagnosticLogsPathFinder,
                            derivedDataPath = derivedDataPath
                        ),
                        testLogListener
                    )
                ),
                DebugLogPrinter(hideRunnerOutput = hideRunnerOutput)
            )
        )
    }

    val diagnosticLogPaths: Collection<String>
        get() = diagnosticLogsPathFinder.diagnosticLogPaths
    val sessionResultPaths: Collection<String>
        get() = sessionResultsPathFinder.resultPaths

    fun getLastLog(): String = testLogListener.getLastLog()

    override fun close() = underlyingLogParser.close()

    override fun onLine(line: String) = underlyingLogParser.onLine(line)
}
