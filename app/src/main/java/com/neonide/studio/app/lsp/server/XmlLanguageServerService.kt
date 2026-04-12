package com.neonide.studio.app.lsp.server

import android.app.Service
import android.content.Intent
import android.net.LocalServerSocket
import android.os.IBinder
import com.neonide.studio.shared.logger.Logger
import com.neonide.studio.shared.termux.TermuxConstants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

/**
 * XML Language Server bridge (LemMinX).
 *
 * The Sora editor LSP client connects to [SOCKET_NAME] (abstract local socket). For each client
 * connection we start a fresh LemMinX process (stdio is single-client) and pipe bytes between
 * the client socket and the process stdin/stdout.
 */
class XmlLanguageServerService : Service() {

    private var serverThread: Thread? = null
    private var process: Process? = null
    private var serverSocket: LocalServerSocket? = null

    // Debug: log the first request/response payloads to understand init failures
    @Volatile private var loggedClientPayload = false
    @Volatile private var loggedServerPayload = false

    companion object {
        private const val TAG = "XmlLangServerService"

        /** Must match [com.neonide.studio.app.lsp.impl.SoraEditorLspController.XML_SOCKET]. */
        const val SOCKET_NAME = "xml-lang-server"

        private const val SERVER_DIR_NAME = "xml-language-server"
        private const val SERVER_JAR_NAME = "lemminx.jar"
        private const val MARKER_FILE_NAME = ".extracted_v1"

        /**
         * Try multiple asset names to keep compatibility with different bundle layouts.
         *
         * Expected (recommended) path: app/src/main/assets/servers/lemminx-uber.jar
         * (built by scripts/build_lemminx_asset.sh)
         */
        private val ASSET_JAR_CANDIDATES = listOf(
            "servers/lemminx-uber.jar",
            "servers/lemminx-uber-0.27.0.jar",
            "servers/lemminx.jar"
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.logDebug(TAG, "onStartCommand received. Starting XML Language Server Service...")
        startServer()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.logDebug(TAG, "onDestroy: Stopping XML Language Server Service...")
        stopServer()
    }

    private fun startServer() {
        if (serverThread?.isAlive == true) {
            Logger.logDebug(TAG, "Server thread already running.")
            return
        }
        Logger.logDebug(TAG, "startServer() called - launching server thread")

        serverThread = thread(name = "XmlLanguageServerThread") {
            try {
                Logger.logDebug(TAG, "Server thread started")
                val serverDir = setupServerDirectory()
                Logger.logDebug(TAG, "Server directory ready: ${serverDir.absolutePath}")
                val jarFile = File(serverDir, SERVER_JAR_NAME)

                if (!jarFile.isFile) {
                    Logger.logError(TAG, "LemMinX jar not found: ${jarFile.absolutePath}")
                    Logger.logError(TAG, "Server directory contents: ${serverDir.listFiles()?.map { it.name }?.joinToString(",") ?: "(empty)"}")
                    return@thread
                }
                Logger.logDebug(TAG, "LemMinX JAR found: ${jarFile.name} (${jarFile.length()} bytes)")

                val termuxJava = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "java")
                val javaExecutable = if (termuxJava.exists()) termuxJava.absolutePath else "java"
                Logger.logDebug(TAG, "Using Java executable: $javaExecutable")
                Logger.logDebug(TAG, "Java exists: ${termuxJava.exists()}")

                Logger.logDebug(TAG, "Opening LocalServerSocket at $SOCKET_NAME")
                serverSocket = LocalServerSocket(SOCKET_NAME)
                Logger.logDebug(TAG, "Server socket opened successfully at $SOCKET_NAME")

                while (!Thread.currentThread().isInterrupted) {
                    Logger.logDebug(TAG, "Accepting client connections on $SOCKET_NAME...")
                    val clientSocket = try {
                        serverSocket?.accept()
                    } catch (e: IOException) {
                        Logger.logDebug(TAG, "accept() threw IOException: ${e.message}")
                        null
                    } ?: break

                    Logger.logDebug(TAG, "Client connected! Starting LemMinX process...")
                    loggedClientPayload = false
                    loggedServerPayload = false

                    // Start LemMinX for this connection.
                    val command = listOf(
                        javaExecutable,
                        // Avoid some large schema/DTD issues on Android devices.
                        "-Djdk.xml.maxOccur=20000",
                        "-jar",
                        jarFile.absolutePath
                    )

                    Logger.logDebug(TAG, "Starting LemMinX with command: ${command.joinToString(" ")}")

                    val processBuilder = ProcessBuilder(command)
                    processBuilder.directory(serverDir)

                    val environment = processBuilder.environment()
                    environment["HOME"] = TermuxConstants.TERMUX_HOME_DIR_PATH
                    environment["PATH"] = "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}:${environment["PATH"]}"

                    Logger.logDebug(TAG, "ProcessBuilder env HOME=${environment["HOME"]}, TERMUX_BIN=${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}")

                    process = try {
                        val p = processBuilder.start()
                        Logger.logDebug(TAG, "LemMinX process started")
                        p
                    } catch (e: IOException) {
                        Logger.logStackTraceWithMessage(TAG, "Failed to start LemMinX process. Is openjdk installed in Termux?", e)
                        runCatching { clientSocket.close() }
                        break
                    }

                    // Log stderr for debugging.
                    thread(name = "XmlLangServerStderr") {
                        try {
                            process!!.errorStream.bufferedReader().useLines { lines ->
                                lines.forEach { line -> Logger.logError(TAG, "LSP STDERR: $line") }
                            }
                        } catch (e: Exception) {
                            if (!isExpectedSocketError(e)) {
                                Logger.logStackTraceWithMessage(TAG, "Error reading server stderr", e)
                            }
                        }
                    }

                    val clientInput = clientSocket.inputStream
                    val clientOutput = clientSocket.outputStream
                    val serverInput = process!!.inputStream
                    val serverOutput = process!!.outputStream

                    val clientToServer = thread(name = "XmlClientToServer") {
                        try {
                            val buf = ByteArray(8 * 1024)
                            val preview = StringBuilder()
                            while (true) {
                                val n = clientInput.read(buf)
                                if (n <= 0) break

                                if (!loggedClientPayload && preview.length < 4096) {
                                    preview.append(String(buf, 0, n, Charsets.UTF_8))
                                    if (preview.contains("\"method\":\"initialize\"")) {
                                        Logger.logDebug(TAG, "LSP CLIENT->SERVER first initialize payload (preview):\n${preview.take(4096)}")
                                        loggedClientPayload = true
                                    }
                                }

                                serverOutput.write(buf, 0, n)
                                serverOutput.flush()
                            }
                        } catch (e: IOException) {
                            if (process?.isAlive == true && !isExpectedSocketError(e)) {
                                Logger.logStackTraceWithMessage(TAG, "Error piping client to server", e)
                            }
                        } finally {
                            runCatching { serverOutput.close() }
                        }
                    }

                    val serverToClient = thread(name = "XmlServerToClient") {
                        try {
                            val buf = ByteArray(8 * 1024)
                            val preview = StringBuilder()
                            while (true) {
                                val n = serverInput.read(buf)
                                if (n <= 0) break

                                if (!loggedServerPayload && preview.length < 4096) {
                                    preview.append(String(buf, 0, n, Charsets.UTF_8))
                                    if (preview.length > 50) { // Log early content
                                         Logger.logDebug(TAG, "LSP SERVER->CLIENT first response payload (preview):\n${preview.take(4096)}")
                                         loggedServerPayload = true
                                    }
                                }

                                clientOutput.write(buf, 0, n)
                                clientOutput.flush()
                            }
                        } catch (e: IOException) {
                            if (!isExpectedSocketError(e)) {
                                Logger.logStackTraceWithMessage(TAG, "Error piping server to client", e)
                            }
                        } finally {
                            runCatching { clientOutput.close() }
                        }
                    }

                    val code = runCatching { process!!.waitFor() }.getOrNull()
                    Logger.logDebug(TAG, "LemMinX exited: $code")

                    clientToServer.join()
                    serverToClient.join()

                    runCatching { clientSocket.close() }
                    Logger.logDebug(TAG, "Client disconnected.")

                    process = null
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(TAG, "Server thread crashed", e)
            } finally {
                stopServer()
            }
        }
    }

    private fun stopServer() {
        serverThread?.interrupt()
        process?.destroy()
        process = null
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
    }

    private fun setupServerDirectory(): File {
        val serverDir = File(filesDir, SERVER_DIR_NAME)
        val jarFile = File(serverDir, SERVER_JAR_NAME)
        val marker = File(serverDir, MARKER_FILE_NAME)

        if (!marker.exists() || !jarFile.exists()) {
            Logger.logDebug(TAG, "Installing/Updating LemMinX server files...")
            serverDir.deleteRecursively()
            serverDir.mkdirs()

            extractServerJar(jarFile)

            if (jarFile.exists()) {
                marker.writeText("ok")
            }
        }

        return serverDir
    }

    private fun extractServerJar(jarFile: File) {
        var extracted = false

        for (assetPath in ASSET_JAR_CANDIDATES) {
            try {
                assets.open(assetPath).use { input ->
                    FileOutputStream(jarFile).use { output ->
                        input.copyTo(output)
                    }
                }
                extracted = true
                Logger.logDebug(TAG, "Extracted LemMinX jar from assets/$assetPath to ${jarFile.absolutePath}")
                break
            } catch (_: IOException) {
                // Try next asset
            }
        }

        if (!extracted) {
            Logger.logError(TAG, "No LemMinX jar found in assets. Tried: $ASSET_JAR_CANDIDATES")
        }
    }

    private fun isExpectedSocketError(e: Throwable): Boolean {
        if (e is java.io.InterruptedIOException) return true
        val msg = e.message ?: return false
        return msg.contains("Broken pipe", ignoreCase = true) ||
                msg.contains("Stream closed", ignoreCase = true) ||
                msg.contains("Socket closed", ignoreCase = true) ||
                msg.contains("Connection reset", ignoreCase = true)
    }
}
