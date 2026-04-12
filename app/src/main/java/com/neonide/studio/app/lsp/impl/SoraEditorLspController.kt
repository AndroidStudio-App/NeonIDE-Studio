package com.neonide.studio.app.lsp.impl

import android.content.Intent
import android.net.LocalSocket
import android.net.LocalSocketAddress
import com.neonide.studio.shared.logger.Logger
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.neonide.studio.app.lsp.EditorLspController
import com.neonide.studio.app.lsp.LspUtils
import com.neonide.studio.app.lsp.server.JavaLanguageServer
import com.neonide.studio.app.lsp.server.JavaLanguageServerService
import com.neonide.studio.app.lsp.server.KotlinLanguageServer
import com.neonide.studio.app.lsp.server.KotlinLanguageServerService
import com.neonide.studio.app.lsp.server.XMLLanguageServer
import com.neonide.studio.app.lsp.server.XmlLanguageServerService
import com.neonide.studio.shared.termux.TermuxConstants
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lsp.client.connection.CustomConnectProvider
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.LanguageServerDefinition
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DidChangeConfigurationParams
import java.io.File
import java.io.IOException

/**
 * LSP controller built on top of `io.github.rosemoe:editor-lsp`.
 *
 * NOTE: This class must only be loaded on API 26+.
 */
class SoraEditorLspController(private val context: android.content.Context) : EditorLspController {

    // Use IO dispatcher for LSP operations to avoid blocking main thread
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "SoraEditorLsp"

        /**
         * Socket names are shared in the "abstract" namespace.
         *
         * The corresponding language server service/binary must be listening on the same name.
         */
        private const val JAVA_SOCKET = "java-lang-server"
        private const val KOTLIN_SOCKET = "kotlin-lang-server"
        private const val XML_SOCKET = "xml-lang-server"
    }

    private var project: LspProject? = null
    private var current: LspEditor? = null
    private var currentFile: File? = null

    override fun attach(editor: CodeEditor, file: File, wrapperLanguage: Language, projectRoot: File?): Boolean {
        Logger.logDebug(TAG, "=== attach() ENTER ===")
        Logger.logDebug(TAG, "attach(): file=${file.absolutePath}")
        Logger.logDebug(TAG, "attach(): projectRoot=${projectRoot?.absolutePath ?: "null"}")

        // Debug-friendly: fail fast if server doesn't initialize
        io.github.rosemoe.sora.lsp.requests.Timeout[io.github.rosemoe.sora.lsp.requests.Timeouts.INIT] = 10000

        val serverId = LspUtils.getServerId(file) ?: run {
            Logger.logDebug(TAG, "attach(): no serverId for ${file.name}, detaching")
            detach()
            return false
        }
        Logger.logDebug(TAG, "attach(): serverId=$serverId for ${file.name}")

        startServer(serverId)

        val desiredRoot = (projectRoot ?: file.parentFile ?: file).let { if (it.isFile) it.parentFile ?: it else it }
        Logger.logDebug(TAG, "attach(): desiredRoot=${desiredRoot.absolutePath}")

        // If project root changed (user opened a file from a different project), recreate the LSP project
        // so servers get the correct workspace root.
        val p = project?.takeIf { it.projectUri.path == desiredRoot.absolutePath }
            ?: run {
                Logger.logDebug(TAG, "attach(): creating new LspProject for $desiredRoot")
                project?.let { old ->
                    Logger.logDebug(TAG, "attach(): disposing old LspProject")
                    runCatching { old.dispose() }
                }
                createProject(desiredRoot).also { project = it }
            }

        // Ensure server definitions are registered
        ensureServerDefinitions(p)

        // Reuse editor for same file ONLY if it is still valid and connected
        if (currentFile?.absolutePath == file.absolutePath && current != null) {
            Logger.logDebug(TAG, "Re-attaching to existing LSP editor for ${file.name}")
            current!!.editor = editor
            current!!.wrapperLanguage = wrapperLanguage
            return true
        }

        // Switch file or first-time attach: cleanup previous state
        Logger.logDebug(TAG, "Switching LSP editor from ${currentFile?.name} to ${file.name}")
        detach()

        val lspEditor = try {
            Logger.logDebug(TAG, "attach(): calling p.getOrCreateEditor(${file.absolutePath})")
            p.getOrCreateEditor(file.absolutePath)
        } catch (t: Throwable) {
            Logger.logStackTraceWithMessage(TAG, "Failed to create LSP editor for ${file.absolutePath}", t)
            return false
        }

        Logger.logDebug(TAG, "attach(): LspEditor created, setting editor and wrapperLanguage")
        lspEditor.editor = editor
        lspEditor.wrapperLanguage = wrapperLanguage

        // Launch connection in background to avoid blocking main thread
        scope.launch {
            Logger.logDebug(TAG, "attach(): coroutine launched for ${file.name}")
            try {
                // Async connect
                Logger.logDebug(TAG, "attach(): about to call lspEditor.connect(false) for ${file.name}")
                val ok = withContext(Dispatchers.IO) {
                    Logger.logDebug(TAG, "connect() running on IO dispatcher for ${file.name}")
                    lspEditor.connect(false)
                }
                Logger.logDebug(TAG, "attach(): lspEditor.connect returned $ok for ${file.name}")
                if (ok) {
                    Logger.logInfo(TAG, "LSP connected for ${file.absolutePath} (serverId=$serverId)")

                    // Configure server (best-effort) before using advanced features like Javadoc hover
                    runCatching { configureServer(serverId, lspEditor) }
                        .onFailure { t -> Logger.logStackTraceWithMessage(TAG, "Failed to configure server settings", t) }

                    current = lspEditor
                    currentFile = file
                    Logger.logDebug(TAG, "attach(): LSP attach complete for ${file.name}")
                } else {
                    Logger.logWarn(TAG, "LSP connect returned false for ${file.absolutePath}")
                    runCatching { lspEditor.dispose() }
                }
            } catch (t: Throwable) {
                Logger.logStackTraceWithMessage(TAG, "LSP connect failed for ${file.absolutePath}", t)
                runCatching { lspEditor.dispose() }
            }
        }

        Logger.logDebug(TAG, "=== attach() EXIT (returning true, connect async) ===")
        return true
    }

    override fun detach() {
        val prev = current
        val prevFile = currentFile
        current = null
        currentFile = null
        if (prev != null) {
            Logger.logDebug(TAG, "Disposing previous LSP editor for ${prevFile?.name}")
            runCatching { prev.dispose() }
        }
    }

    override fun dispose() {
        detach()
        project?.let { p ->
            project = null
            runCatching { p.dispose() }
        }
        runCatching { scope.coroutineContext.cancelChildren() }
        stopServers()
    }

    override fun currentEditor(): LspEditor? = current

    private fun createProject(base: File): LspProject {
        // LspProject expects a filesystem path (it internally converts it to a file:// uri).
        return LspProject(base.absolutePath).also { it.init() }
    }

    /**
     * Send best-effort settings to language server.
     *
     * For Java (org.javacs), this enables richer hover/Javadoc if JDK sources are present.
     */
    private fun configureServer(serverId: String, lspEditor: LspEditor) {
        val rm = lspEditor.requestManager ?: run {
            Logger.logWarn(TAG, "configureServer: requestManager is null")
            return
        }

        when (serverId) {
            JavaLanguageServer.SERVER_ID -> {
                // java-language-server expects settings.settings["java"] = {...}
                val root = JsonObject()
                val java = JsonObject()

                // Only configure standard library docs (option 1): JDK src.zip if available.
                val docPaths = detectJdkSourceZips()
                Logger.logDebug(TAG, "Detected JDK src.zip candidates: ${docPaths.joinToString()}")

                // NOTE: java-language-server only respects docPath() when classPath() is non-empty.
                // To enable Javadoc hover for JDK without project deps, provide a harmless existing directory.
                val runtimePrefix = File(context.filesDir, "usr")
                val classPathEntry = File(runtimePrefix, "lib").absolutePath
                val classPathArr = JsonArray().apply { add(classPathEntry) }
                java.add("classPath", classPathArr)
                Logger.logDebug(TAG, "Sending java.classPath=[${classPathEntry}]")

                if (docPaths.isNotEmpty()) {
                    val arr = JsonArray()
                    docPaths.forEach { arr.add(it) }
                    java.add("docPath", arr)
                }

                root.add("java", java)

                val params = DidChangeConfigurationParams().apply {
                    settings = root
                }

                Logger.logDebug(TAG, "Sending didChangeConfiguration(java.docPath count=${docPaths.size})")
                rm.didChangeConfiguration(params)
            }

            XMLLanguageServer.SERVER_ID -> {
                // LemMinX supports settings under the "xml" key.
                // We keep this minimal; users can extend later.
                val root = JsonObject()
                val xml = JsonObject()

                // Enable formatter by default (editor-lsp will call textDocument/formatting).
                val format = JsonObject()
                format.addProperty("enabled", true)
                xml.add("format", format)

                // Optional: enable client logs (LemMinX uses this for troubleshooting)
                // NOTE: This may create log files inside HOME depending on LemMinX settings.
                // val logs = JsonObject().apply { addProperty("client", true) }
                // xml.add("logs", logs)

                root.add("xml", xml)

                val params = DidChangeConfigurationParams().apply { settings = root }
                Logger.logDebug(TAG, "Sending didChangeConfiguration(xml)")
                rm.didChangeConfiguration(params)
            }

            else -> {
                // no-op
            }
        }
    }

    private fun detectJdkSourceZips(): List<String> {
        // Typical layout: $PREFIX/lib/jvm/<jdk>/lib/src.zip
        //
        // IMPORTANT: TermuxConstants.TERMUX_PREFIX_DIR_PATH may point to /data/data/com.neonide.studio/...,
        // but this app runs in a different sandbox (e.g. com.tom.rv2ide). So probe a few likely prefixes.
        // Prefer the runtime app sandbox prefix. This will be:
        //   /data/data/<applicationId>/files/usr
        // for the installed app (e.g. com.neonide.studio).
        val runtimePrefix = File(context.filesDir, "usr").absolutePath

        val prefixesToTry = linkedSetOf(
            runtimePrefix,
            // Kept for compatibility with environments still using the default TermuxConstants package name
            TermuxConstants.TERMUX_PREFIX_DIR_PATH
        )

        val candidates = ArrayList<String>()

        fun addIfExists(path: String) {
            if (File(path).exists()) candidates.add(path)
        }

        for (prefix in prefixesToTry) {
            val jvmDir = File(prefix, "lib/jvm")
            if (jvmDir.isDirectory) {
                jvmDir.listFiles()?.forEach { child ->
                    addIfExists(File(child, "lib/src.zip").absolutePath)
                    addIfExists(File(child, "src.zip").absolutePath)
                }
            }

            // Common explicit locations
            addIfExists("$prefix/lib/jvm/java-17-openjdk/lib/src.zip")
            addIfExists("$prefix/lib/jvm/java-21-openjdk/lib/src.zip")
            addIfExists("$prefix/lib/jvm/java-11-openjdk/lib/src.zip")
        }

        return candidates.distinct()
    }

    private fun ensureServerDefinitions(project: LspProject) {
        // LspEditor requires project.getServerDefinition(ext) to exist.
        // Use extension-only keys ("java", "kt", "kts", "xml").
        if (project.getServerDefinition("java") == null) {
            project.addServerDefinition(javaDefinition())
        }
        if (project.getServerDefinition("kt") == null) {
            project.addServerDefinition(kotlinDefinitionFor("kt"))
        }
        if (project.getServerDefinition("kts") == null) {
            project.addServerDefinition(kotlinDefinitionFor("kts"))
        }
        if (project.getServerDefinition("xml") == null) {
            project.addServerDefinition(xmlDefinition())
        }
    }

    private fun startServer(serverId: String) {
        val intent = when (serverId) {
            JavaLanguageServer.SERVER_ID -> {
                Logger.logDebug(TAG, "Starting JavaLanguageServerService")
                Intent(context, JavaLanguageServerService::class.java)
            }
            KotlinLanguageServer.SERVER_ID -> {
                Logger.logDebug(TAG, "Starting KotlinLanguageServerService")
                Intent(context, KotlinLanguageServerService::class.java)
            }
            XMLLanguageServer.SERVER_ID -> {
                Logger.logDebug(TAG, "Starting XmlLanguageServerService")
                Intent(context, XmlLanguageServerService::class.java)
            }
            else -> {
                Logger.logWarn(TAG, "Unknown server ID: $serverId")
                return
            }
        }
        context.startService(intent)
        Logger.logDebug(TAG, "startService() called for $serverId")
    }

    private fun stopServers() {
        context.stopService(Intent(context, JavaLanguageServerService::class.java))
        context.stopService(Intent(context, KotlinLanguageServerService::class.java))
        context.stopService(Intent(context, XmlLanguageServerService::class.java))
    }

    private fun javaDefinition(): LanguageServerDefinition {
        return CustomLanguageServerDefinition(
            "java",
            CustomLanguageServerDefinition.ServerConnectProvider {
                retryingLocalSocketProvider(JAVA_SOCKET)
            }
        )
    }

    private fun kotlinDefinitionFor(ext: String): LanguageServerDefinition {
        // Kotlin language server uses the "kotlin" language id but we register per extension.
        return CustomLanguageServerDefinition(
            ext,
            CustomLanguageServerDefinition.ServerConnectProvider {
                retryingLocalSocketProvider(KOTLIN_SOCKET)
            }
        )
    }

    private fun xmlDefinition(): LanguageServerDefinition {
        return CustomLanguageServerDefinition(
            "xml",
            CustomLanguageServerDefinition.ServerConnectProvider {
                retryingLocalSocketProvider(XML_SOCKET)
            }
        )
    }

    /**
     * Sora's built-in [io.github.rosemoe.sora.lsp.client.connection.LocalSocketStreamConnectionProvider]
     * does not retry. In our case, the Android Service may not have created the
     * [android.net.LocalServerSocket] yet, so we need a retry loop.
     */
    private fun retryingLocalSocketProvider(socketName: String): StreamConnectionProvider {
        return object : StreamConnectionProvider {
            private lateinit var socket: LocalSocket
            private lateinit var _inputStream: java.io.InputStream
            private lateinit var _outputStream: java.io.OutputStream

            override fun start() {
                val startTime = System.currentTimeMillis()
                val deadlineMs = startTime + 60000L
                var lastErr: IOException? = null
                var attempt = 0

                Logger.logDebug(TAG, "retryingLocalSocketProvider: STARTING, socket=$socketName, deadline=${deadlineMs}ms from now")

                while (System.currentTimeMillis() < deadlineMs && !Thread.currentThread().isInterrupted) {
                    attempt++
                    val attemptStart = System.currentTimeMillis()
                    try {
                        Logger.logDebug(TAG, "retryingLocalSocketProvider: attempt $attempt to connect to $socketName")
                        socket = LocalSocket()
                        socket.connect(
                            LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT)
                        )
                        _inputStream = socket.inputStream
                        _outputStream = socket.outputStream
                        val elapsed = System.currentTimeMillis() - startTime
                        Logger.logDebug(TAG, "retryingLocalSocketProvider: CONNECTED to $socketName (attempt $attempt, took ${elapsed}ms)")
                        return
                    } catch (e: IOException) {
                        lastErr = e
                        val elapsed = System.currentTimeMillis() - startTime
                        Logger.logDebug(TAG, "retryingLocalSocketProvider: attempt $attempt FAILED (${e.message}), elapsed=${elapsed}ms, remaining=${deadlineMs - System.currentTimeMillis()}ms")
                    }
                    try {
                        Thread.sleep(100)
                    } catch (_: InterruptedException) {
                        Logger.logDebug(TAG, "retryingLocalSocketProvider: interrupted during sleep")
                        break
                    }
                }

                val totalTime = System.currentTimeMillis() - startTime
                Logger.logError(TAG, "retryingLocalSocketProvider: FAILED to connect to $socketName after $attempt attempts (${totalTime}ms elapsed)")
                throw lastErr ?: IOException("Failed to connect to local socket: $socketName")
            }

            override val inputStream: java.io.InputStream
                get() = _inputStream

            override val outputStream: java.io.OutputStream
                get() = _outputStream

            override fun close() {
                runCatching { socket.close() }
            }
        }
    }

    @Suppress("unused")
    private fun customProvider(socketName: String): StreamConnectionProvider {
        // Alternative: use CustomConnectProvider. Kept for reference.
        return CustomConnectProvider {
            val s = LocalSocket()
            s.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            android.util.Pair(s.inputStream, s.outputStream)
        }
    }
}
