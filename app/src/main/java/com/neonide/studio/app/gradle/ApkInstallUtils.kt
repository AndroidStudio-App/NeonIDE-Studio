package com.neonide.studio.app.gradle

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.neonide.studio.shared.net.uri.UriUtils
import com.neonide.studio.shared.termux.TermuxConstants
import java.io.File

object ApkInstallUtils {

    /**
     * Launch system package installer UI for an apk.
     * Uses TermuxOpenReceiver.ContentProvider authority: ${TermuxConstants.TERMUX_FILE_SHARE_URI_AUTHORITY}
     */
    fun installApk(context: Context, apkFile: File) {
        val uri: Uri = UriUtils.getContentUri(TermuxConstants.TERMUX_FILE_SHARE_URI_AUTHORITY, apkFile.absolutePath)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
