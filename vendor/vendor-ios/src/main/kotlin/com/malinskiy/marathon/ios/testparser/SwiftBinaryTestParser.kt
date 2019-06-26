package com.malinskiy.marathon.ios.testparser

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

  override fun listTests(testRunnerPaths: List<File>): List<Test> {
    return extractTestStrings(testRunnerPaths)
        .map {
          val (pkg, clazz, method) = TEST_STRING_PATTERN.find(it)
              ?.destructured
              ?: throw IllegalStateException("Invalid test name $it")

          Test(pkg, clazz, method, emptyList())
        }
  }

  private fun extractTestStrings(testRunnerPaths: List<File>): List<String> {
    val pathMappings = testRunnerPaths.map {
      it.absolutePath to File("/tmp").resolve(it.name).absolutePath }

    val command =
        listOf("docker", "run", "--rm") +
            pathMappings.map { listOf("-v", "${it.first}:${it.second}:ro") }.flatten() +
            binaryParserDockerImageName +
            pathMappings.map { it.second }

    val process = ProcessBuilder()
      .command(command)
      .directory(File(System.getProperty("user.dir")))
      .start()

    val output = mutableListOf<String>()
    val outputReader = DockerOutputReader(process.inputStream) { output.add(it) }
    val executor = Executors.newSingleThreadExecutor()
    executor.submit(outputReader)
    return if (process.waitFor() == 0) {
      executor.shutdown()
      output.toList()
    } else {
      executor.shutdown()
      throw IllegalStateException("Unable to read list of tests")
    }
  }
}