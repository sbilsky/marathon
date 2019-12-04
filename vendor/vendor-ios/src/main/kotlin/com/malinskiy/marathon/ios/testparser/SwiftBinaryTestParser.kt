package com.malinskiy.marathon.ios.testparser

import com.malinskiy.marathon.ios.Test
import com.malinskiy.marathon.log.MarathonLogging
import com.malinskiy.marathon.test.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors

private val TEST_STRING_PATTERN= """^(.+)\.([^.]+)\.([^.(]+)\(\)$""".toRegex()

class SwiftBinaryTestParser(private val binaryParserDockerImageName: String): DockerTestParser {
  private val logger = MarathonLogging.logger(SwiftBinaryTestParser::class.java.simpleName)

  private class DockerOutputReader(private val inputStream: InputStream,
                                   private val consumer: (String) -> Unit) : Runnable {
    override fun run() {
      BufferedReader(InputStreamReader(inputStream)).lines().forEach(consumer)
    }
  }

  override fun listTests(targetName: String, testRunnerPath: File): List<Test> {
    return extractTestStrings(testRunnerPath)
        .map {
          val (pkg, clazz, method) = TEST_STRING_PATTERN.find(it)
              ?.destructured
              ?: throw IllegalStateException("Invalid test name $it")

          Test(pkg, clazz, method, targetName)
        }
  }

  private fun extractTestStrings(testRunnerPath: File): List<String> {
    val externalPath = testRunnerPath.absolutePath
    val internalPath = File("/tmp").resolve(testRunnerPath.name).absolutePath

    val command =
      listOf(
        "docker", "run", "--rm",
        "-v", "${externalPath}:${internalPath}:ro",
        binaryParserDockerImageName,
        internalPath
      )
    return execute(command)
  }

  private fun execute(command: List<String>): List<String> {

    logger.debug(command.joinToString(" "))

    val process = ProcessBuilder()
      .command(command)
      .directory(File(System.getProperty("user.dir")))
      .start()

    logger.debug(process.toString())

    val output = mutableListOf<String>()
    val outputReader = DockerOutputReader(process.inputStream) { output.add(it) }
    val errors = mutableListOf<String>()
    val errorReader = DockerOutputReader(process.errorStream) { errors.add(it) }
    val executor = Executors.newSingleThreadExecutor()
    executor.submit(outputReader)
    return if (process.waitFor() == 0) {
      executor.shutdown()
      output.toList()
    } else {
      executor.shutdown()
      logger.error(errors.joinToString("\n") { "${it}"})
      throw IllegalStateException("Unable to read list of tests")
    }
  }
}