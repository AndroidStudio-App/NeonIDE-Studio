package com.neonide.studio.app.lsp.server

import android.app.Service
import android.content.Intent
import android.net.LocalServerSocket
import android.os.IBinder
import android.util.Log
import com.neonide.studio.shared.termux.TermuxConstants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

class KotlinLanguageServerService : Service() {

    private var serverThread: Thread? = null
    private var process: Process? = null
    private var serverSocket: LocalServerSocket? = null

    companion object {
        private const val TAG = "KotlinLangServerService"
        const val SOCKET_NAME = "kotlin-lang-server"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting Kotlin Language Server Service...")
        startServer()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Stopping Kotlin Language Server Service...")
        stopServer()
    }

    private fun startServer() {
        if (serverThread?.isAlive == true) {
            Log.d(TAG, "Server is already running.")
            return
        }

        serverThread = thread {
            try {
                val serverDir = setupServerDirectory()
                val launchScript = File(serverDir, "bin/kotlin-language-server")

                if (!launchScript.exists()) {
                    Log.e(TAG, "Launch script not found: ${launchScript.absolutePath}")
                    return@thread
                }

                // Open local socket server once, accept multiple sequential connections.
                serverSocket = LocalServerSocket(SOCKET_NAME)
                Log.d(TAG, "Server socket opened at $SOCKET_NAME")

                while (!Thread.currentThread().isInterrupted) {
                    Log.d(TAG, "Waiting for LSP client connection...")
                    val clientSocket = serverSocket?.accept() ?: break
                    Log.d(TAG, "Client connected.")

                    // Start a fresh LS process for this connection (stdio is single-client).
                    val termuxSh = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "sh")
                    val shCmd = if (termuxSh.exists()) termuxSh.absolutePath else "sh"

                    val processBuilder = ProcessBuilder(shCmd, launchScript.absolutePath)
                    processBuilder.directory(serverDir)
                    val environment = processBuilder.environment()
                    environment["HOME"] = TermuxConstants.TERMUX_HOME_DIR_PATH
                    environment["PATH"] = "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}:${environment["PATH"]}"

                    process = processBuilder.start()

                    val clientInput = clientSocket.inputStream
                    val clientOutput = clientSocket.outputStream
                    val serverInput = process!!.inputStream
                    val serverOutput = process!!.outputStream

                    val clientToServer = thread {
                        try {
                            clientInput.copyTo(serverOutput)
                        } catch (e: IOException) {
                            Log.e(TAG, "Error piping client to server", e)
                        } finally {
                            runCatching { serverOutput.close() }
                        }
                    }

                    val serverToClient = thread {
                        try {
                            serverInput.copyTo(clientOutput)
                        } catch (e: IOException) {
                            Log.e(TAG, "Error piping server to client", e)
                        } finally {
                            runCatching { clientOutput.close() }
                        }
                    }

                    val code = runCatching { process!!.waitFor() }.getOrNull()
                    Log.d(TAG, "kotlin-language-server exited: $code")

                    clientToServer.join()
                    serverToClient.join()

                    runCatching { clientSocket.close() }
                    Log.d(TAG, "Client disconnected.")

                    // Cleanup process handle
                    process = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server thread crashed", e)
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
        } catch (e: IOException) {
            // ignore
        }
        serverSocket = null
    }

    private fun setupServerDirectory(): File {
        val serverDir = File(filesDir, "kotlin-language-server")
        // Re-extract if missing critical files
        val launchScript = File(serverDir, "bin/kotlin-language-server")
        if (!launchScript.exists()) {
            serverDir.mkdirs()
            extractServerAssets(serverDir)
        }
        // Ensure executable bit
        launchScript.setExecutable(true, true)
        return serverDir
    }

    private fun extractServerAssets(targetDir: File) {
        val marker = File(targetDir, ".extracted")
        if (marker.exists()) return

        try {
            // Clean any partial extraction
            targetDir.listFiles()?.forEach { it.deleteRecursively() }
            targetDir.mkdirs()

            assets.open("servers/kotlin-language-server.zip").use { input ->
                ZipInputStream(input).use { zip ->
                    var entry: ZipEntry?
                    while (true) {
                        entry = zip.nextEntry ?: break
                        var name = entry!!.name
                        if (name.contains("..")) {
                            // basic zip-slip protection
                            zip.closeEntry()
                            continue
                        }
                        // The bundled distribution might have a top-level "server/" directory.
                        // Strip it so we get <targetDir>/bin and <targetDir>/lib.
                        if (name.startsWith("server/")) {
                            name = name.removePrefix("server/")
                        }
                        if (name.isBlank()) {
                            zip.closeEntry()
                            continue
                        }
                        val outFile = File(targetDir, name)
                        if (entry!!.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                zip.copyTo(out)
                            }
                            // Make scripts executable
                            if (outFile.name.endsWith(".sh") || outFile.name == "kotlin-language-server") {
                                outFile.setExecutable(true, true)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }

            marker.writeText("ok")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract server assets", e)
        }
    }
}
