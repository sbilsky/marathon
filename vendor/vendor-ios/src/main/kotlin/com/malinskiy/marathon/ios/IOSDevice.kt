package com.malinskiy.marathon.ios


import com.google.gson.Gson
import com.malinskiy.marathon.analytics.tracker.device.InMemoryDeviceTracker
import com.malinskiy.marathon.device.Device
import com.malinskiy.marathon.device.DeviceFeature
import com.malinskiy.marathon.device.DeviceInfo
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.device.NetworkState
import com.malinskiy.marathon.device.OperatingSystem
import com.malinskiy.marathon.exceptions.DeviceLostException
import com.malinskiy.marathon.execution.Configuration
import com.malinskiy.marathon.execution.TestBatchResults
import com.malinskiy.marathon.execution.progress.ProgressReporter
import com.malinskiy.marathon.io.FileManager
import com.malinskiy.marathon.ios.cmd.remote.SshjCommandExecutor
import com.malinskiy.marathon.ios.cmd.remote.SshjCommandUnresponsiveException
import com.malinskiy.marathon.ios.cmd.remote.exec
import com.malinskiy.marathon.ios.cmd.remote.execOrNull
import com.malinskiy.marathon.ios.device.RemoteSimulator
import com.malinskiy.marathon.ios.device.RemoteSimulatorFeatureProvider
import com.malinskiy.marathon.ios.logparser.IOSDeviceLogParser
import com.malinskiy.marathon.ios.logparser.target.XctestrunTestTargetResolver
import com.malinskiy.marathon.ios.logparser.parser.DeviceFailureException
import com.malinskiy.marathon.ios.logparser.parser.DeviceFailureReason
import com.malinskiy.marathon.ios.simctl.Simctl
import com.malinskiy.marathon.ios.xctestrun.Xctestrun
import com.malinskiy.marathon.log.MarathonLogging
import com.malinskiy.marathon.test.TestBatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.connection.channel.OpenFailException
import net.schmizz.sshj.transport.TransportException
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext

class IOSDevice(val simulator: RemoteSimulator,
                connectionAttempt: Int,
                configuration: IOSConfiguration,
                val gson: Gson,
                private val healthChangeListener: HealthChangeListener) : Device, CoroutineScope {

    val udid = simulator.udid
    val connectionId = "$udid@${simulator.host}-$connectionAttempt"
    private val deviceContext = newFixedThreadPoolContext(1, connectionId)

    override val coroutineContext: CoroutineContext
        get() = deviceContext + Job()

    val logger = MarathonLogging.logger(IOSDevice::class.java.simpleName)

    val hostCommandExecutor: SshjCommandExecutor

    val name: String?
    private val runtime: String?
    private val deviceType: String?
    private val features: Collection<DeviceFeature>

    init {
        val hostAddress = simulator.host.toInetAddressOrNull()
        if (hostAddress == null) {
            deviceContext.close()
            throw DeviceLostException(DeviceFailureException(DeviceFailureReason.UnreachableHost))
        }
        hostCommandExecutor = try {
            SshjCommandExecutor(
                    connectionId = connectionId,
                    hostAddress = hostAddress,
                    remoteUsername = simulator.username ?: configuration.remoteUsername,
                    remotePrivateKey = configuration.remotePrivateKey,
                    knownHostsPath = configuration.knownHostsPath,
                    verbose = configuration.debugSsh,
                    disableHostKeyVerifier = configuration.disableHostKeyVerifier
            )
        } catch (e: DeviceFailureException) {
            deviceContext.close()
            throw DeviceLostException(e)
        }

        val simctl = Simctl()
        val device = try {
            simctl.list(this, gson).find { it.udid == udid }
        } catch (e: DeviceFailureException) {
            dispose()
            throw DeviceLostException(e)
        }
        runtime = device?.runtime
        name = device?.name
        deviceType = try {
            simctl.deviceType(this)
        } catch (e: DeviceFailureException) {
            dispose()
            throw DeviceLostException(e)
        }
        features = try {
            RemoteSimulatorFeatureProvider.deviceFeatures(this)
        } catch (e: DeviceFailureException) {
            logger.warn("Exception requesting remote device features: $e")
            dispose()
            throw DeviceLostException(e)
        }
    }

    override val operatingSystem: OperatingSystem
        get() = OperatingSystem(runtime ?: "Unknown")
    override val serialNumber: String = "$udid@${simulator.host}"

    override val model: String
        get() = deviceType ?: "Unknown"
    override val manufacturer: String
        get() = "Apple"
    override val networkState: NetworkState
        get() = when (healthy) {
            true -> NetworkState.CONNECTED
            false -> NetworkState.DISCONNECTED
        }
    override val deviceFeatures: Collection<DeviceFeature> = features
    override var healthy: Boolean = true
        private set
    override val abi: String
        get() = "Simulator"

    var failureReason: DeviceFailureReason? = null
        private set

    override suspend fun execute(configuration: Configuration,
                                 devicePoolId: DevicePoolId,
                                 testBatch: TestBatch,
                                 deferred: CompletableDeferred<TestBatchResults>,
                                 progressReporter: ProgressReporter) = withContext(coroutineContext + CoroutineName("execute")) {
        val iosConfiguration = configuration.vendorConfiguration as IOSConfiguration
        val fileManager = FileManager(configuration.outputDir)

        if (iosConfiguration.simulatorAction == IOSConfiguration.SimulatorAction.ERASE_ALWAYS) {
            hostCommandExecutor.execOrNull(
                    "xcrun simctl shutdown $udid",
                    configuration.testBatchTimeoutMillis,
                    configuration.testOutputTimeoutMillis
            )
            hostCommandExecutor.execOrNull(
                    "xcrun simctl erase $udid",
                    configuration.testBatchTimeoutMillis,
                    configuration.testOutputTimeoutMillis
            )
        }

        val remoteXcresultPath = RemoteFileManager.remoteXcresultFile(this@IOSDevice)
        val remoteXctestrunFile = RemoteFileManager.remoteXctestrunFile(this@IOSDevice)
        val remoteDir = remoteXctestrunFile.parent

        logger.debug("Remote xctestrun = $remoteXctestrunFile")

        val xctestrun = Xctestrun(iosConfiguration.xctestrunPath)
        val testTargetProvider = XctestrunTestTargetResolver(xctestrun)

        logger.debug("Tests = ${testBatch.tests.toList()}")

        val derivedDataPath ="/tmp/DerivedData/$serialNumber-${testBatch.hashCode()}"
        val logParser = IOSDeviceLogParser(
            this@IOSDevice,
            testTargetProvider,
            devicePoolId,
            testBatch,
            deferred,
            progressReporter,
            iosConfiguration.hideRunnerOutput,
            iosConfiguration.ignoreSystemProcessCrashes,
            derivedDataPath
        )

        val command =
            listOf(
                "cd '$remoteDir' &&",
                "set -o pipefail &&",
                "NSUnbufferedIO=YES",
                "xcodebuild test-without-building",
                "-disable-concurrent-destination-testing",
                "-derivedDataPath $derivedDataPath",
                "-xctestrun ${remoteXctestrunFile.path}",
                testBatch.toXcodebuildArguments(),
                "-destination 'platform=iOS simulator,id=$udid' ;",
                "exit")
                .joinToString(" ")
                .also { logger.debug("\u001b[1m$it\u001b[0m") }

        val exitStatus = try {
            hostCommandExecutor.execInto(
                command,
                configuration.testBatchTimeoutMillis,
                configuration.testOutputTimeoutMillis,
                logParser::onLine
            )
        } catch (e: SshjCommandUnresponsiveException) {
            logger.error("No output from remote shell")
            disconnectAndThrow(e)
        } catch (e: TimeoutException) {
            logger.error("Connection timeout")
            disconnectAndThrow(e)
        } catch (e: ConnectionException) {
            logger.error("ConnectionException")
            disconnectAndThrow(e)
        } catch (e: TransportException) {
            logger.error("TransportException")
            disconnectAndThrow(e)
        } catch (e: OpenFailException) {
            logger.error("Unable to open session")
            disconnectAndThrow(e)
        } catch (e: IllegalStateException) {
            logger.error("Unable to start a new SSH session. Client is disconnected")
            disconnectAndThrow(e)
        } catch (e: DeviceFailureException) {
            logger.error("Execution failed because ${e.reason}")
            failureReason = e.reason
            disconnectAndThrow(e)
        } finally {
            logParser.close()

            if (!healthy) {
                logger.debug("Last log before device termination")
                logger.debug(logParser.getLastLog())
            }

            if (logParser.diagnosticLogPaths.isNotEmpty())
                logger.info("Diagnostic logs available at ${logParser.diagnosticLogPaths}")

            if (logParser.sessionResultPaths.isNotEmpty())
                logger.info("Session results available at ${logParser.sessionResultPaths}")
        }

        // 70 = no devices
        // 65 = ** TEST EXECUTE FAILED **: crash
        logger.debug("Finished test batch execution with exit status $exitStatus")
    }

    private suspend fun disconnectAndThrow(cause: Throwable) {
        healthy = false
        healthChangeListener.onDisconnect(this)
        throw DeviceLostException(cause)
    }

    private var derivedDataManager: DerivedDataManager? = null
    override suspend fun prepare(configuration: Configuration) = withContext(coroutineContext + CoroutineName("prepare")) {
        val iosConfiguration = configuration.vendorConfiguration as IOSConfiguration

        InMemoryDeviceTracker.trackDevicePreparing(this@IOSDevice) {
            RemoteFileManager.removeRemoteDirectory(this@IOSDevice)
            RemoteFileManager.createRemoteDirectory(this@IOSDevice)

            val derivedDataManager = DerivedDataManager(configuration)

            val remoteXctestrunFile = RemoteFileManager.remoteXctestrunFile(this@IOSDevice)
            val xctestrunFile = try {
                prepareXctestrunFile(derivedDataManager, remoteXctestrunFile)
            } catch (e: IOException) {
                logger.warn("Exception getting remote TCP port $e")
                throw e
            }

            derivedDataManager.sendSynchronized(
                    localPath = xctestrunFile,
                    remotePath = remoteXctestrunFile.absolutePath,
                    hostName = hostCommandExecutor.hostAddress.hostName,
                    port = hostCommandExecutor.port
            )

            derivedDataManager.sendSynchronized(
                    localPath = derivedDataManager.productsZip,
                    remotePath = RemoteFileManager.remoteDirectory(this@IOSDevice).path,
                    hostName = hostCommandExecutor.hostAddress.hostName,
                    port = hostCommandExecutor.port
            )

            RemoteFileManager.unzipRemoteArchive(this@IOSDevice, derivedDataManager.productsZip.name)

            this@IOSDevice.derivedDataManager = derivedDataManager

            if (iosConfiguration.simulatorAction == IOSConfiguration.SimulatorAction.ERASE_ONCE) {
                terminateRunningSimulators()
                try {
                    hostCommandExecutor.exec(
                            "xcrun simctl shutdown $udid",
                            configuration.testBatchTimeoutMillis,
                            configuration.testOutputTimeoutMillis
                    )
                } catch (e: Exception) {
                    logger.warn("Exception shutting down remote simulator $e")
                }
                try {
                    hostCommandExecutor.exec(
                            "xcrun simctl erase $udid",
                            configuration.testBatchTimeoutMillis,
                            configuration.testOutputTimeoutMillis
                    )
                } catch (e: Exception) {
                    logger.warn("Exception erasing remote simulator $e")
                }
            }
            disableHardwareKeyboard()
        }
    }

    private fun terminateRunningSimulators() {
        val result = hostCommandExecutor.execOrNull("/usr/bin/pkill -9 -l -f '$udid'")
        if (result?.exitStatus == 0) {
            logger.trace("Terminated loaded simulators")
        } else {
            logger.debug("Failed to terminate loaded simulators ${result?.stdout}")
        }

        val ps = hostCommandExecutor.execOrNull("/bin/ps | /usr/bin/grep '$udid'")?.stdout ?: ""
        if (ps.isNotBlank()) {
            logger.debug(ps)
        }
    }

    private fun disableHardwareKeyboard() {
        val result =
                hostCommandExecutor.execOrNull("/usr/libexec/PlistBuddy -c 'Add :DevicePreferences:$udid:ConnectHardwareKeyboard bool false' /Users/master/Library/Preferences/com.apple.iphonesimulator.plist" +
                        "|| /usr/libexec/PlistBuddy -c 'Set :DevicePreferences:$udid:ConnectHardwareKeyboard false' /Users/master/Library/Preferences/com.apple.iphonesimulator.plist")
        if (result?.exitStatus == 0) {
            logger.trace("Disabled hardware keyboard")
        } else {
            logger.debug("Failed to disable hardware keyboard ${result?.stdout}")
        }
    }

    override fun dispose() {
        logger.debug("Disposing device")
        try {
            hostCommandExecutor.close()
        } catch (e: Exception) {
            logger.debug("Error disconnecting ssh: $e")
        }

        try {
            deviceContext.close()
        } catch (e: Exception) {
            logger.debug("Error closing context: $e")
        }
    }

    override fun toString(): String {
        return "IOSDevice"
    }

    private val deviceIdentifier: String
        get() = "${hostCommandExecutor.hostAddress.hostAddress}:$udid"

    private fun prepareXctestrunFile(derivedDataManager: DerivedDataManager, remoteXctestrunFile: File): File {
        val remotePort = RemoteSimulatorFeatureProvider.availablePort(this)
                .also { logger.info("Using TCP port $it on device $deviceIdentifier") }

        val xctestrun = Xctestrun(derivedDataManager.xctestrunFile)
        xctestrun.allTargetsEnvironment("TEST_HTTP_SERVER_PORT", "$remotePort")

        return derivedDataManager.xctestrunFile.resolveSibling(remoteXctestrunFile.name)
                .also { it.writeBytes(xctestrun.toXMLByteArray()) }
    }

    override fun toDeviceInfo() = DeviceInfo(operatingSystem = operatingSystem,
            serialNumber = serialNumber,
            model = model,
            manufacturer = manufacturer,
            networkState = networkState,
            deviceFeatures = deviceFeatures,
            healthy = healthy,
            deviceLabel = simulator.label ?: serialNumber
    )
}

private const val REACHABILITY_TIMEOUT_MILLIS = 5000
private fun String.toInetAddressOrNull(): InetAddress? {
    val logger = MarathonLogging.logger(IOSDevice::class.java.simpleName)
    val address = try {
        InetAddress.getByName(this)
    } catch (e: UnknownHostException) {
        logger.error("Error resolving host $this: $e")
        return null
    }
    return if (try {
                address.isReachable(REACHABILITY_TIMEOUT_MILLIS)
            } catch (e: IOException) {
                logger.error("Error checking reachability of $this: $e")
                false
            }) {
        address
    } else {
        null
    }
}
