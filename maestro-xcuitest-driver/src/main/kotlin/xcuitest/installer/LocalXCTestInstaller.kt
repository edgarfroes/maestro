package xcuitest.installer

import maestro.logger.Logger
import maestro.utils.MaestroTimer
import okio.buffer
import okio.sink
import okio.source
import org.rauschig.jarchivelib.ArchiverFactory
import util.XCRunnerSimctl
import xcuitest.XCTestDriverClient
import java.io.File
import java.net.ConnectException

class LocalXCTestInstaller(
    private val logger: Logger,
    private val deviceId: String,
    private val driverClient: XCTestDriverClient,
) : XCTestInstaller {

    private var xcTestProcess: Process? = null

    override fun killAndUninstall() {
        if (xcTestProcess?.isAlive == true) {
            logger.info("[Start] Killing the started XCUITest")
            xcTestProcess?.destroy()
            logger.info("[Done] Killing the started XCUITest")
        }
        logger.info("[Start] Uninstalling the XCUITest runner app")
        XCRunnerSimctl.uninstall(UI_TEST_RUNNER_APP_BUNDLE_ID)
        logger.info("[Done] Uninstalling the XCUITest runner app")
    }

    override fun setup() {
        repeat(3) { i ->
            logger.info("[Start] Installing xctest ui runner on $deviceId")
            runXCTest()
            logger.info("[Done] Installing xctest ui runner on $deviceId")

            logger.info("[Start] Ensuring ui test runner app is launched on $deviceId")
            if (ensureOpen()) {
                logger.info("[Done] Ensuring ui test runner app is launched on $deviceId")
                return
            } else {
                logger.info("[Failed] Ensuring ui test runner app is launched on $deviceId")
                logger.info("[Retry] Retrying setup() ${i}th time")
            }
        }
    }

    private fun ensureOpen(): Boolean {
        XCRunnerSimctl.ensureAppAlive(UI_TEST_RUNNER_APP_BUNDLE_ID)
        return MaestroTimer.retryUntilTrue(10_000, 100) {
            try {
                driverClient.subTree(UI_TEST_RUNNER_APP_BUNDLE_ID).use { it.isSuccessful }
            } catch (ignore: ConnectException) {
                false
            }
        }
    }

    private fun runXCTest() {
        val processOutput = ProcessBuilder(
            "bash",
            "-c",
            "xcrun simctl spawn booted launchctl list | grep $UI_TEST_RUNNER_APP_BUNDLE_ID | awk '/$UI_TEST_RUNNER_APP_BUNDLE_ID/ {print \$3}'"
        ).start().inputStream.source().buffer().readUtf8().trim()

        if (!processOutput.contains(UI_TEST_RUNNER_APP_BUNDLE_ID)) {
            logger.info("Not able to find ui test runner app, going to install now")
            val tempDir = System.getenv("TMPDIR")
            val xctestRunFile = File("$tempDir/maestro-driver-ios-config.xctestrun")

            logger.info("[Start] Writing xctest run file")
            writeFileToDestination(XCTEST_RUN_PATH, xctestRunFile)
            logger.info("[Done] Writing xctest run file")

            logger.info("[Start] Writing maestro-driver-iosUITests-Runner app")
            extractZipToApp("maestro-driver-iosUITests-Runner", UI_TEST_RUNNER_PATH)
            logger.info("[Done] Writing maestro-driver-iosUITests-Runner app")

            logger.info("[Start] Writing maestro-driver-ios app")
            extractZipToApp("maestro-driver-ios", UI_TEST_HOST_PATH)
            logger.info("[Done] Writing maestro-driver-ios app")

            logger.info("[Start] Running XcUITest with xcode build command")
            xcTestProcess = XCRunnerSimctl.runXcTestWithoutBuild(
                deviceId,
                xctestRunFile.absolutePath
            )
            logger.info("[Done] Running XcUITest with xcode build command")
        } else {
            logger.info("UI test runner already installed and running")
        }
    }

    override fun close() {
        logger.info("[Start] Cleaning up the ui test runner files")
        val xctestConfig = "${System.getenv("TMP_DIR")}/$XCTEST_RUN_PATH"
        val hostApp = "${System.getenv("TMPDIR")}/Debug-iphonesimulator/maestro-driver-ios.app"
        val uiTestRunnerApp = "${System.getenv("TMPDIR")}/Debug-iphonesimulator/maestro-driver-iosUITests-Runner.app"
        File(xctestConfig).delete()
        File(uiTestRunnerApp).deleteRecursively()
        File(hostApp).deleteRecursively()
        killAndUninstall()
        logger.info("[Done] Cleaning up the ui test runner files")
    }

    private fun extractZipToApp(appFileName: String, srcAppPath: String) {
        val appFile = File("${System.getenv("TMPDIR")}/Debug-iphonesimulator").apply { mkdir() }
        val appZip = File("${System.getenv("TMPDIR")}/$appFileName.zip")

        writeFileToDestination(srcAppPath, appZip)
        ArchiverFactory.createArchiver(appZip).apply {
            extract(appZip, appFile)
        }
    }

    private fun writeFileToDestination(srcPath: String, destFile: File) {
        LocalXCTestInstaller::class.java.getResourceAsStream(srcPath)?.let {
            val bufferedSink = destFile.sink().buffer()
            bufferedSink.writeAll(it.source())
            bufferedSink.flush()
        }
    }

    companion object {
        private const val UI_TEST_RUNNER_PATH = "/maestro-driver-iosUITests-Runner.zip"
        private const val XCTEST_RUN_PATH = "/maestro-driver-ios-config.xctestrun"
        private const val UI_TEST_HOST_PATH = "/maestro-driver-ios.zip"
        private const val UI_TEST_RUNNER_APP_BUNDLE_ID = "dev.mobile.maestro-driver-iosUITests.xctrunner"
    }
}