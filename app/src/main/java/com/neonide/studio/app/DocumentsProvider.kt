package com.neonide.studio.app

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.util.Log
import android.webkit.MimeTypeMap
import com.neonide.studio.R
import com.neonide.studio.shared.termux.TermuxConstants
import java.io.File
import java.io.FileNotFoundException
import java.util.Base64

/**
 * Document provider for the Storage Access Framework.
 * Exposes the files in the $HOME/ directory to other apps.
 *
 * Note: This replaces the legacy ACTION_GET_CONTENT intent approach.
 * "A document provider and ACTION_GET_CONTENT should be considered mutually exclusive."
 */
class DocumentsProvider : DocumentsProvider() {

    private val tag: String = "NeonIDE-DocumentsProvider"

    /** The base directory exposed to other apps. Uses java.io.File for maximum Android compatibility. */
    private val baseDir: File
        get() = TermuxConstants.TERMUX_HOME_DIR

    override fun onCreate(): Boolean = run {
        val dir = baseDir
        val exists = dir.exists() && dir.isDirectory
        Log.i(tag, "DocumentsProvider onCreate: baseDir=${dir.absolutePath}, exists=$exists")
        exists
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        val ctx = context
        if (ctx == null) {
            Log.e(tag, "queryRoots: context is null, returning empty cursor")
            return MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        }

        val effectiveProjection = projection ?: DEFAULT_ROOT_PROJECTION
        val result = MatrixCursor(effectiveProjection)
        val applicationName = ctx.getString(R.string.application_name)
        val base = baseDir

        try {
            result.newRow {
                add(Root.COLUMN_ROOT_ID, docIdForFile(base))
                add(Root.COLUMN_DOCUMENT_ID, docIdForFile(base))
                add(Root.COLUMN_SUMMARY, null)
                add(
                    Root.COLUMN_FLAGS,
                    Root.FLAG_SUPPORTS_CREATE or
                        Root.FLAG_SUPPORTS_SEARCH or
                        Root.FLAG_SUPPORTS_IS_CHILD or
                        Root.FLAG_LOCAL_ONLY,
                )
                add(Root.COLUMN_TITLE, applicationName)
                add(Root.COLUMN_MIME_TYPES, ALL_MIME_TYPES)
                add(Root.COLUMN_AVAILABLE_BYTES, base.freeSpace)
                add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            }
            Log.i(tag, "queryRoots: returning root for ${base.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "queryRoots: failed to build root row", e)
        }

        return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        Log.d(tag, "queryDocument: documentId=$documentId")
        val effectiveProjection = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val result = MatrixCursor(effectiveProjection)
        includeFile(result, documentId, null)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        Log.d(tag, "queryChildDocuments: parentDocumentId=$parentDocumentId, sortOrder=$sortOrder")
        val effectiveProjection = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val result = MatrixCursor(effectiveProjection)

        val parent = fileForDocId(parentDocumentId)

        if (!parent.isDirectory) {
            Log.w(tag, "queryChildDocuments: $parentDocumentId is not a directory")
            setNotificationUri(result, parentDocumentId)
            return result
        }

        val children = parent.listFiles()
        if (children == null) {
            Log.w(tag, "queryChildDocuments: listFiles() returned null for $parentDocumentId")
            setNotificationUri(result, parentDocumentId)
            return result
        }

        Log.d(tag, "queryChildDocuments: found ${children.size} children in ${parent.absolutePath}")

        children.filterNotNull().forEach { child ->
            try {
                includeFile(result, null, child)
            } catch (e: Exception) {
                Log.w(tag, "queryChildDocuments: failed to include child ${child.absolutePath}", e)
            }
        }

        setNotificationUri(result, parentDocumentId)
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        Log.d(tag, "openDocument: documentId=$documentId, mode=$mode")
        val file = fileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?,
    ): AssetFileDescriptor {
        Log.d(tag, "openDocumentThumbnail: documentId=$documentId")
        val file = fileForDocId(documentId)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val fileSize = file.length()
        return AssetFileDescriptor(pfd, 0, fileSize)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        Log.i(
            tag,
            "createDocument: parentId=$parentDocumentId, mimeType=$mimeType, displayName=$displayName",
        )
        val parent = fileForDocId(parentDocumentId)

        // Sanitize display name to prevent path traversal
        val safeName = displayName.replace(Regex("[/\\\\]"), "_")

        var newFile = File(parent, safeName)
        var noConflictId = 2

        while (newFile.exists()) {
            val nameWithoutExt = safeName.substringBeforeLast('.', "")
            val ext = safeName.substringAfterLast('.', "")
            val conflictName = if (ext == safeName || ext.isEmpty()) {
                "$safeName ($noConflictId)"
            } else {
                "$nameWithoutExt ($noConflictId).$ext"
            }
            newFile = File(parent, conflictName)
            noConflictId++
        }

        val succeeded = runCatching {
            if (mimeType == Document.MIME_TYPE_DIR) {
                newFile.mkdirs()
            } else {
                newFile.createNewFile()
            }
        }.getOrElse { e ->
            Log.e(tag, "createDocument: failed to create $safeName", e)
            false
        }

        if (!succeeded) {
            throw FileNotFoundException("Failed to create document: ${newFile.absolutePath}")
        }

        Log.i(tag, "createDocument: successfully created ${newFile.absolutePath}")
        return docIdForFile(newFile)
    }

    override fun deleteDocument(documentId: String) {
        Log.i(tag, "deleteDocument: documentId=$documentId")
        val file = fileForDocId(documentId)
        val deleted = file.deleteRecursively()
        if (!deleted) {
            throw FileNotFoundException("Failed to delete document: $documentId")
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        Log.i(tag, "renameDocument: documentId=$documentId, displayName=$displayName")
        val file = fileForDocId(documentId)

        // Sanitize display name
        val safeName = displayName.replace(Regex("[/\\\\]"), "_")
        val parent = file.parentFile ?: throw FileNotFoundException("Cannot rename root")
        val newFile = File(parent, safeName)

        if (newFile.exists()) {
            throw IllegalArgumentException("A file with name '$displayName' already exists")
        }

        val renamed = file.renameTo(newFile)
        if (!renamed) {
            throw FileNotFoundException("Failed to rename document: $documentId")
        }

        return docIdForFile(newFile)
    }

    override fun getDocumentType(documentId: String): String = fileForDocId(documentId).getMimeType()

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<String>?,
    ): Cursor {
        Log.d(tag, "querySearchDocuments: rootId=$rootId, query=$query")
        val effectiveProjection = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val result = MatrixCursor(effectiveProjection)
        val parent = fileForDocId(rootId)
        val homePath = TermuxConstants.TERMUX_HOME_DIR_PATH

        if (!parent.isDirectory) {
            setNotificationUri(result, rootId)
            return result
        }

        // BFS traversal using java.io.File for reliability
        val pending = ArrayDeque<File>()
        pending.add(parent)

        while (result.count < MAX_SEARCH_RESULTS && pending.isNotEmpty()) {
            val current = pending.removeFirst()

            // Avoid directories outside the $HOME directory (symlink boundary check)
            val canonicalPath = current.canonicalFile.absolutePath
            if (!canonicalPath.startsWith(homePath)) {
                continue
            }

            val children = current.listFiles()
            if (children == null) continue

            for (child in children) {
                if (result.count >= MAX_SEARCH_RESULTS) break

                if (child.isDirectory) {
                    pending.add(child)
                } else if (child.name.contains(query, ignoreCase = true)) {
                    try {
                        includeFile(result, null, child)
                    } catch (e: Exception) {
                        Log.w(
                            tag,
                            "querySearchDocuments: failed to include ${child.absolutePath}",
                            e,
                        )
                    }
                }
            }
        }

        Log.d(tag, "querySearchDocuments: found ${result.count} results for '$query'")
        setNotificationUri(result, rootId)
        return result
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = runCatching {
        val parentFile = fileForDocId(parentDocumentId)
        val childFile = fileForDocId(documentId)
        val parentPath = parentFile.canonicalFile.absolutePath
        val childPath = childFile.canonicalFile.absolutePath
        // Child must start with parent path + separator (or be exactly equal for root edge case)
        childPath == parentPath || childPath.startsWith("$parentPath${File.separatorChar}")
    }.getOrElse { e ->
        Log.w(tag, "isChildDocument: failed for parent=$parentDocumentId, child=$documentId", e)
        false
    }

    /**
     * Encodes a file path to a stable document ID using Base64.
     *
     * This prevents issues with special characters (:/#% etc.) in paths
     * that can confuse the Storage Access Framework.
     *
     * @param file the [File] to encode into a document ID
     * @return the Base64-encoded document ID representing the file path
     */
    private fun docIdForFile(file: File): String {
        val path = file.absolutePath
        return Base64.getEncoder().encodeToString(path.toByteArray(Charsets.UTF_8))
    }

    /**
     * Decodes a document ID back to a [File] object.
     *
     * @param docId the document ID to decode
     * @return the [File] corresponding to the decoded path
     * @throws FileNotFoundException if the decoded file does not exist
     */
    private fun fileForDocId(docId: String): File {
        val path = try {
            String(Base64.getDecoder().decode(docId), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            // Fallback: treat docId as a raw path (legacy compatibility)
            docId
        }

        val file = File(path)
        if (!file.exists()) {
            throw FileNotFoundException("Document not found: $path (docId: $docId)")
        }
        return file
    }

    /**
     * Adds a representation of a file to a cursor.
     *
     * @param result the cursor to modify
     * @param docId the document ID representing the desired file (may be null if given [file])
     * @param file the [File] object representing the desired file (may be null if given [docId])
     */
    private fun includeFile(result: MatrixCursor, docId: String?, file: File?) {
        val resolvedFile = when {
            docId != null -> fileForDocId(docId)
            file != null -> file
            else -> throw IllegalArgumentException("Either docId or file must be provided")
        }

        var flags = 0

        if (resolvedFile.isDirectory) {
            if (resolvedFile.canWrite()) {
                flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
            }
        } else {
            if (resolvedFile.canWrite()) {
                flags = flags or Document.FLAG_SUPPORTS_WRITE
            }
            flags = flags or Document.FLAG_SUPPORTS_DELETE
        }

        val mimeType = resolvedFile.getMimeType()
        if (mimeType.startsWith("image/")) {
            flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        }

        result.newRow {
            add(Document.COLUMN_DOCUMENT_ID, docId ?: docIdForFile(resolvedFile))
            add(Document.COLUMN_DISPLAY_NAME, resolvedFile.name)
            add(Document.COLUMN_SIZE, resolvedFile.length())
            add(Document.COLUMN_MIME_TYPE, mimeType)
            add(Document.COLUMN_LAST_MODIFIED, resolvedFile.lastModified())
            add(Document.COLUMN_FLAGS, flags)
        }
    }

    /** Builds a row using a lambda for cleaner row construction. */
    private inline fun MatrixCursor.newRow(block: MatrixCursor.RowBuilder.() -> Unit) {
        block(newRow())
    }

    /** Sets the notification URI on the cursor so the system knows when to refresh. */
    private fun setNotificationUri(cursor: MatrixCursor, documentId: String) {
        val uri = buildDocumentUri(documentId)
        cursor.setNotificationUri(context?.contentResolver, uri)
    }

    /** Builds a content URI for a document ID. */
    private fun buildDocumentUri(documentId: String): android.net.Uri =
        DocumentsContract.buildDocumentUri(
            "${TermuxConstants.TERMUX_PACKAGE_NAME}.documents",
            documentId,
        )

    /** Returns the MIME type for a file. */
    private fun File.getMimeType(): String = when {
        isDirectory -> Document.MIME_TYPE_DIR
        else -> {
            val name = this.name
            val lastDot = name.lastIndexOf('.')
            if (lastDot > 0 && lastDot < name.length - 1) {
                val extension = name.substring(lastDot + 1).lowercase()
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    ?: "application/octet-stream"
            } else {
                "application/octet-stream"
            }
        }
    }

    companion object {
        private const val ALL_MIME_TYPES = "*/*"
        private const val MAX_SEARCH_RESULTS = 50

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_ICON,
        )
    }
}
