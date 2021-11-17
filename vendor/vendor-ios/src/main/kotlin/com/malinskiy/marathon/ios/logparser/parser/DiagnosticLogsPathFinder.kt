package com.malinskiy.marathon.ios.logparser.parser

import com.malinskiy.marathon.ios.logparser.StreamingLogParser

class DiagnosticLogsPathFinder : StreamingLogParser {

    /*
    Output removed in Xcode 13, previously used to detect log paths

    2021-10-25 18:49:01.160 xcodebuild[8727:7061917]  IDETestOperationsObserverDebug: Writing diagnostic log for test session to:
    /Users/master/Library/Developer/Xcode/DerivedData/temporary-gxqqhpeheiydicdzhkedlmsvfdwr/Logs/Test/Test-Transient Testing-2021.10.27_18-49-01-+0700.xcresult/Staging/1_Test/Diagnostics/sample-appUITests-841BFA49-E2BF-4BB1-AE16-F16A831E0812/sample-appUITests-FADD8C8B-B83A-4379-9B45-37DA215A714F/Session-sample-appUITests-2021-10-27_184901-HHr8Wt.log
     */
    /*
    Results location message, prints to stdout in Xcode 12 and 13

    Test session results, code coverage, and logs:
	    /Users/master/Library/Developer/Xcode/DerivedData/temporary-gjmogzzslfptroczoktrncamfdbv/Logs/Test/Test-Transient Testing-2564.10.27_18-36-57-+0700.xcresult
     */
    private val xcresultPathPattern = """(^\s*|\s+)/.+\.xcresult\s*$""".toRegex()
    private var paths = arrayListOf<String>()

    val diagnosticLogPaths: Collection<String>
        get() = paths

    override fun onLine(line: String) {
        xcresultPathPattern.find(line)?.groupValues?.firstOrNull()
                ?.let { paths.add(it) }
    }

    override fun close() = Unit
}
