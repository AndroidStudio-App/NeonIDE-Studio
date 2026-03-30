package com.neonide.studio.shared.logger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.neonide.studio.shared.termux.TermuxConstants;
import com.neonide.studio.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Simple file logger for cases where users cannot access logcat.
 *
 * Writes logs to: /sdcard/Documents/NeonIDE/logs/ide.log
 *
 * Controlled by the Termux debugging preference: "ide_file_logging_enabled".
 */
public final class IDEFileLogger {

    private static final String LOG_TAG = "IDEFileLogger";

    private static final long MAX_LOG_BYTES = 2L * 1024L * 1024L; // 2MB

    private static final String LOG_FILE_NAME = "ide.log";

    private IDEFileLogger() {
    }

    public static boolean isEnabled(@NonNull Context context) {
        // Read directly from shared preferences to avoid recursion (SharedPreferenceUtils -> Logger -> IDEFileLogger)
        // and to keep this method lightweight as it can be called for every log line.
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
            TermuxPreferenceConstants.TERMUX_APP.KEY_IDE_FILE_LOGGING_ENABLED,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_IDE_FILE_LOGGING_ENABLED
        );
    }

    @Nullable
    public static File getLogFile() {
        File docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File dir = new File(docs, "NeonIDE/logs");
        return new File(dir, LOG_FILE_NAME);
    }

    public static void log(@NonNull Context context, @NonNull String line) {
        if (!isEnabled(context)) return;

        try {
            File docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File dir = new File(docs, "NeonIDE/logs");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();

            File logFile = new File(dir, LOG_FILE_NAME);

            rotateIfNeeded(logFile);

            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            String payload = ts + " " + line + "\n";

            try (FileOutputStream fos = new FileOutputStream(logFile, true)) {
                fos.write(payload.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            // Avoid recursion if Logger calls us.
            try {
                android.util.Log.e(Logger.getFullTag(LOG_TAG), "Failed to write IDE log file", t);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void rotateIfNeeded(@NonNull File logFile) {
        try {
            if (!logFile.exists()) return;
            long len = logFile.length();
            if (len < MAX_LOG_BYTES) return;

            File dir = logFile.getParentFile();
            if (dir == null) return;

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File rotated = new File(dir, "ide_" + ts + ".log");

            //noinspection ResultOfMethodCallIgnored
            logFile.renameTo(rotated);
        } catch (Throwable ignored) {
        }
    }
}
