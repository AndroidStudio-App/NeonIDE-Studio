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

class JavaLanguageServerService : Service() {

    private var serverThread: Thread? = null
    private var process: Process? = null
    private var serverSocket: LocalServerSocket? = null

    // Debug: log the first request/response payloads to understand init failures
    @Volatile private var loggedClientPayload = false
    @Volatile private var loggedServerPayload = false

    companion object {
        private const val TAG = "JavaLangServerService"
        const val SOCKET_NAME = "java-lang-server"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting Java Language Server Service...")
        startServer()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Stopping Java Language Server Service...")
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
                
                // Construct command to start Java Language Server directly
                val termuxJava = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "java")
                val javaExecutable = if (termuxJava.exists()) termuxJava.absolutePath else "java"

                val classpath = listOf(
                    "gson-2.8.9.jar",
                    "protobuf-java-3.19.6.jar",
                    "java-language-server.jar"
                ).joinToString(File.pathSeparator) { File(serverDir, it).absolutePath }

                val vmOptions = listOf(
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                    "--add-opens", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
                )

                val command = mutableListOf(javaExecutable)
                command.addAll(vmOptions)
                command.add("-cp")
                command.add(classpath)
                command.add("org.javacs.Main")

                // Use abstract namespace for LocalServerSocket by prepending \u0000
                serverSocket = LocalServerSocket(SOCKET_NAME)
                Log.d(TAG, "Server socket opened at $SOCKET_NAME (abstract namespace)")

                while (!Thread.currentThread().isInterrupted) {
                    Log.d(TAG, "Waiting for LSP client connection...")
                    val clientSocket = try {
                        serverSocket?.accept()
                    } catch (e: IOException) {
                        null
                    } ?: break
                    Log.d(TAG, "Client connected.")

                    val processBuilder = ProcessBuilder(command)
                    processBuilder.directory(serverDir)
                    val environment = processBuilder.environment()
                    environment["HOME"] = TermuxConstants.TERMUX_HOME_DIR_PATH
                    environment["PATH"] = "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}:${environment["PATH"]}"
                    
                    process = processBuilder.start()

                    // Capture stderr
                    thread {
                        try {
                            val reader = process!!.errorStream.bufferedReader()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                Log.e(TAG, "LSP STDERR: $line")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading stderr", e)
                        }
                    }

                    val clientInput = clientSocket.inputStream
                    val clientOutput = clientSocket.outputStream
                    val serverInput = process!!.inputStream
                    val serverOutput = process!!.outputStream

                    val clientToServer = thread {
                        try {
                            val buf = ByteArray(8 * 1024)
                            val preview = StringBuilder()
                            while (true) {
                                val n = clientInput.read(buf)
                                if (n <= 0) break

                                if (!loggedClientPayload && preview.length < 4096) {
                                    preview.append(String(buf, 0, n, Charsets.UTF_8))
                                    if (preview.contains("\"method\":\"initialize\"")) {
                                        Log.d(TAG, "LSP CLIENT->SERVER first initialize payload (preview):\n${preview.take(4096)}")
                                        loggedClientPayload = true
                                    }
                                }

                                serverOutput.write(buf, 0, n)
                                serverOutput.flush()
                            }
                        } catch (e: IOException) {
                            // Ignore "Bad file descriptor" if the process has already exited
                            if (process?.isAlive == true) {
                                Log.e(TAG, "Error piping client to server", e)
                            }
                        } finally {
                            runCatching { serverOutput.close() }
                        }
                    }

                    val serverToClient = thread {
                        try {
                            val buf = ByteArray(8 * 1024)
                            val preview = StringBuilder()
                            while (true) {
                                val n = serverInput.read(buf)
                                if (n <= 0) break

                                if (!loggedServerPayload && preview.length < 4096) {
                                    preview.append(String(buf, 0, n, Charsets.UTF_8))
                                    if (preview.contains("\"result\"") || preview.contains("\"error\"")) {
                                        Log.d(TAG, "LSP SERVER->CLIENT first response payload (preview):\n${preview.take(4096)}")
                                        loggedServerPayload = true
                                    }
                                }

                                clientOutput.write(buf, 0, n)
                                clientOutput.flush()
                            }
                        } catch (e: IOException) {
                            Log.e(TAG, "Error piping server to client", e)
                        } finally {
                            runCatching { clientOutput.close() }
                        }
                    }

                    val code = runCatching { process!!.waitFor() }.getOrNull()
                    Log.d(TAG, "java-language-server exited: $code")

                    clientToServer.join()
                    serverToClient.join()

                    runCatching { clientSocket.close() }
                    Log.d(TAG, "Client disconnected.")
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
        // Standard Android location
        val serverDir = File(filesDir, "java-language-server")
        val marker = File(serverDir, ".extracted_v2")

        if (!marker.exists()) {
            Log.d(TAG, "Updating/Installing Java Language Server...")
            serverDir.deleteRecursively()
            serverDir.mkdirs()
            extractServerAssets(serverDir)
            marker.writeText("v2")
        }
        return serverDir
    }

    private fun extractServerAssets(targetDir: File) {
        try {
            // targetDir is already clean from setupServerDirectory
            
            val assetStream = try {
                assets.open("servers/java-language-server.zip")
            } catch (e: IOException) {
                Log.e(TAG, "java-language-server.zip not found in assets")
                null
            }

            assetStream?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry: ZipEntry?
                    while (true) {
                        entry = zip.nextEntry ?: break
                        val name = entry.name
                        if (name.contains("..")) {
                            zip.closeEntry()
                            continue
                        }
                        val outFile = File(targetDir, name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                zip.copyTo(out)
                            }
                            if (outFile.name.endsWith(".sh")) {
                                outFile.setExecutable(true, true)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract server assets", e)
        }
    }
}