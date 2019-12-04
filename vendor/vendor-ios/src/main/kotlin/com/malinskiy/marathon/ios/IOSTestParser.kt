package com.malinskiy.marathon.ios

import com.malinskiy.marathon.execution.Configuration
import com.malinskiy.marathon.execution.TestParser
import com.malinskiy.marathon.ios.testparser.SwiftBinaryTestParser
import com.malinskiy.marathon.ios.xctestrun.TestBundleInfo
import com.malinskiy.marathon.ios.xctestrun.Xctestrun
import com.malinskiy.marathon.log.MarathonLogging
import com.malinskiy.marathon.report.html.relativePathTo
import com.malinskiy.marathon.test.Test
import java.io.File

class IOSTestParser : TestParser {
    private val swiftTestClassRegex = """class ([^:\s]+)\s*:\s*XCTestCase""".toRegex()
    private val swiftTestMethodRegex = """^.*func\s+(test[^(\s]*)\s*\(.*$""".toRegex()

    private val logger = MarathonLogging.logger(IOSTestParser::class.java.simpleName)

    /**
     * Extracts a list of tests using a method determined by configuration values in Marathonfile.
     *
     * When `binaryParserDockerImageName` is specified, parser runs docker image against provided
     * binaries, extracting test names from a list of compiled symbols.
     * Supports multiple modules and targets per configuration.
     *
     * When `binaryParserDockerImageName` is unspecified or empty, parser tries to collect test names
     * from source files. It looks for swift files under the `sourceRoot` directory. Additionally,
     * when `sourceRootsRegex` is specified, search results will be limited to paths that match.
     * This option supports a single target per configuration. Source files do not provide enough
     * information to accurately test targets. Value of `sourceTargetName` will be used as target
     * of all extracted tests.
     */
    override fun extract(configuration: Configuration): List<Test> {
        val vendorConfiguration = configuration.vendorConfiguration as? IOSConfiguration
                ?: throw IllegalStateException("Expected IOS configuration")

        val dockerImageName = vendorConfiguration.binaryParserDockerImageName
        return when {
            !dockerImageName.isNullOrEmpty() -> extractWithDocker(vendorConfiguration)
            else -> extractFromSourceFiles(vendorConfiguration)
        }
    }

    /**
     * Executes a docker image against binaries provided for testing and collects discovered
     * test methods. Collected test object stores its build target name as a metaproperty, required
     * for xcodebuild command execution.
     * 
     * @return Tests found in the provided test runner binaries, excluding ones disabled in scheme.
     */
    private fun extractWithDocker(vendorConfiguration: IOSConfiguration): List<Test> {
        val dockerImageName = vendorConfiguration.binaryParserDockerImageName
                ?: throw IllegalStateException("Expected a docker image name")

        val files = targetExecutables(vendorConfiguration.xctestrunPath)

        val parser = SwiftBinaryTestParser(dockerImageName)
        val xctestrun = Xctestrun(vendorConfiguration.xctestrunPath)

        val compiledTests = files.entries.map { (targetName, file) ->
            parser.listTests(targetName, file)
        }.flatten()

        logger.info { "Discovered ${compiledTests.size} tests in ${files.size} executable files."}

        val filteredTests = compiledTests.filter { !xctestrun.isSkipped(it) }

        logger.info { "Skipping ${compiledTests.count() - filteredTests.count()} to comply with xctestrun configuration."}

        logger.trace { filteredTests.map { "${it.clazz}.${it.method}" }.joinToString() }

        return filteredTests
    }

    private fun targetExecutables(xctestrunPath: File): Map<String, File> {
        val xctestrun = Xctestrun(xctestrunPath)
        return xctestrun.targetNames.mapNotNull { targetName ->
            xctestrun.testHostBundlePath(targetName)?.let { testHostBundle ->
                val testHostBundlePath = xctestrunPath.resolveSibling(testHostBundle)
                bundleExecutable(testHostBundlePath)?.let { executable ->
                    targetName to executable
                }
            }
        }.toMap()
    }

    private fun bundleExecutable(bundle: File): File? {
        return bundleInfoPath(bundle)?.let { infoPath ->
            TestBundleInfo(infoPath).CFBundleExecutable()?.let { executable ->
                infoPath.resolveSibling(executable)
            }
        }
    }

    private fun bundleInfoPath(bundle: File): File? = bundle.walkTopDown().maxDepth(1).firstOrNull { it.name == "Info.plist" }

    /**
     *  Looks up test methods running a text search in swift files. Considers classes that explicitly inherit
     *  from `XCTestCase` and method names starting with `test`. Scans all swift files found under `sourceRoot`
     *  specified in Marathonfile. When not specified, starts in working directory. Result excludes any tests
     *  marked as skipped in `xctestrun` file.
     */
    private fun extractFromSourceFiles(vendorConfiguration: IOSConfiguration): List<Test> {
        if (!vendorConfiguration.sourceRoot.isDirectory) {
            throw IllegalArgumentException("Expected a directory at ${vendorConfiguration.sourceRoot}")
        }

        val sourceRoots = if (vendorConfiguration.sourceRootsRegex != null) {
            vendorConfiguration.sourceRoot.walkTopDown().filter {
                it.isDirectory &&
                    vendorConfiguration.sourceRootsRegex
                        .containsMatchIn(it.relativePathTo(vendorConfiguration.sourceRoot))
            }.toList()
        } else listOf(vendorConfiguration.sourceRoot)

        val xctestrun = Xctestrun(vendorConfiguration.xctestrunPath)
        val targetName = vendorConfiguration.sourceTargetName
                ?: xctestrun.targetNames.firstOrNull()
                ?: throw IllegalStateException("sourceTargetName is not specified and " +
                        "there are no named targets in the provided xctestrun file")

        val moduleName = xctestrun.productModuleName(targetName)
                ?: throw IllegalStateException("Unable to find target name $targetName" +
                        "in the provided xctestrun file")

        val swiftFilesWithTests = sourceRoots.map { sourceRoot ->
            sourceRoot.listFiles("swift").filter(swiftTestClassRegex)
        }

        val implementedTests = mutableListOf<Test>()
        for (fileSet in swiftFilesWithTests) {
            for (file in fileSet) {
                var testClassName: String? = null
                for (line in file.readLines()) {
                    val className = line.firstMatchOrNull(swiftTestClassRegex)
                    val methodName = line.firstMatchOrNull(swiftTestMethodRegex)

                    if (className != null) { testClassName = className }

                    if (testClassName != null && methodName != null) {
                        implementedTests.add(Test(moduleName, testClassName, methodName, targetName))
                    }
                }
            }
        }

        logger.info { "Discovered ${implementedTests.size} tests in ${swiftFilesWithTests.size} source files."}

        val filteredTests = implementedTests.filter { !xctestrun.isSkipped(it) }

        logger.info { "Skipping ${implementedTests.count() - filteredTests.count()} to comply with xctestrun configuration."}

        logger.trace { filteredTests.map { "${it.clazz}.${it.method}" }.joinToString() }

        return filteredTests
    }
}

private fun Sequence<File>.filter(contentsRegex: Regex): Sequence<File> {
    return filter { it.contains(contentsRegex) }
}

private fun File.listFiles(extension: String): Sequence<File> {
    return walkTopDown().filter { it.extension == extension }
}

private val MatchResult.firstGroup: String?
    get() { return groupValues.get(1) }

private fun String.firstMatchOrNull(regex: Regex): String? {
    return regex.find(this)?.firstGroup
}

private fun File.contains(contentsRegex: Regex): Boolean {
    return inputStream().bufferedReader().lineSequence().any { it.contains(contentsRegex) }
}

private fun File.resolveAsSiblingOf(file: File): File = file.resolveSibling(this)