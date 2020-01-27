package com.malinskiy.marathon.ios.logparser.parser

import com.malinskiy.marathon.ios.logparser.StreamingLogParser

class DeviceFailureParser(ignoreSystemProcessCrashes: Boolean): StreamingLogParser {
    private val simulatorFailurePatterns = listOf(
        "Failed to install or launch the test runner",
        "Software caused connection abort",
        "Unable to find a destination matching the provided destination specifier",
        "Terminating since there is no system app",
        "Exiting because the workspace server has disconnected",
        "Not authorized for performing UI testing PropertyActions",
        "Timed out waiting for automation session",
        "Failed to terminate",
        "Failed to launch app with identifier",
        "Test runner exited before starting test execution",
        "Early unexpected exit, operation never finished bootstrapping",
        "Connection peer refused channel request",
        "CoreSimulatorService connection became invalid", // occurs in simctl output
        "Simulator services will no longer be available", // occurs in simctl output
        "Unable to locate device set" // occurs in simctl output
    )
    private val systemProcessCrashPatterns = listOf("SpringBoard crashed in", "backboardd crashed in")
    private val crashPatterns = listOf("Assertion Failure: <unknown>:0: [^\\s]+ crashed in ")
    private val patterns = simulatorFailurePatterns + if (ignoreSystemProcessCrashes) systemProcessCrashPatterns else listOf()
    private var count = 0
    override fun onLine(line: String) {
        patterns.firstOrNull { line.contains(it) }
            ?.let {
                throw DeviceFailureException(
                    when (simulatorFailurePatterns.indexOf(it)) {
                        0 -> DeviceFailureReason.FailedRunner
                        1 -> DeviceFailureReason.ConnectionAbort
                        2 -> DeviceFailureReason.InvalidSimulatorIdentifier
                        else -> {
                            when (systemProcessCrashPatterns.indexOf(it)) {
                                0 -> DeviceFailureReason.SpringboardCrash
                                1 -> DeviceFailureReason.BackboarddCrash
                                else -> DeviceFailureReason.Unknown
                            }
                        }
                    },
                    it
                )
            }
    }

    override fun close() = Unit
}
