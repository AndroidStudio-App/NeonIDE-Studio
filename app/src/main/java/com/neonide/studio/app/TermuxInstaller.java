package com.neonide.studio.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.system.Os;
import android.util.Pair;
import android.view.WindowManager;

import com.neonide.studio.BuildConfig;
import com.neonide.studio.R;
import com.neonide.studio.shared.file.FileUtils;
import com.neonide.studio.shared.shell.command.ExecutionCommand;
import com.neonide.studio.shared.shell.command.runner.app.AppShell;
import com.neonide.studio.shared.termux.TermuxBootstrap;
import com.neonide.studio.shared.termux.crash.TermuxCrashUtils;
import com.neonide.studio.shared.termux.file.TermuxFileUtils;
import com.neonide.studio.shared.interact.MessageDialogUtils;
import com.neonide.studio.shared.logger.Logger;
import com.neonide.studio.shared.markdown.MarkdownUtils;
import com.neonide.studio.shared.errors.Error;
import com.neonide.studio.shared.android.PackageUtils;
import com.neonide.studio.shared.termux.TermuxConstants;
import com.neonide.studio.shared.termux.TermuxUtils;
import com.neonide.studio.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.neonide.studio.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR;
import static com.neonide.studio.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH;
import static com.neonide.studio.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR;
import static com.neonide.studio.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH;

/**
 * Install the Termux bootstrap packages if necessary by following the below steps:
 * <p/>
 * (1) If $PREFIX already exist, assume that it is correct and be done. Note that this relies on that we do not create a
 * broken $PREFIX directory below.
 * <p/>
 * (2) A progress dialog is shown with "Installing..." message and a spinner.
 * <p/>
 * (3) A staging directory, $STAGING_PREFIX, is cleared if left over from broken installation below.
 * <p/>
 * (4) The zip file is loaded from a shared library.
 * <p/>
 * (5) The zip, containing entries relative to the $PREFIX, is is downloaded and extracted by a zip input stream
 * continuously encountering zip file entries:
 * <p/>
 * (5.1) If the zip entry encountered is SYMLINKS.txt, go through it and remember all symlinks to setup.
 * <p/>
 * (5.2) For every other zip entry, extract it into $STAGING_PREFIX and set execute permissions if necessary.
 */
final class TermuxInstaller {

    private static final String LOG_TAG = "TermuxInstaller";

    /** Performs bootstrap setup if necessary. */
    static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        String bootstrapErrorMessage;
        Error filesDirectoryAccessibleError;

        // This will also call Context.getFilesDir(), which should ensure that termux files directory
        // is created if it does not already exist
        filesDirectoryAccessibleError = TermuxFileUtils.isTermuxFilesDirectoryAccessible(activity, true, true);
        boolean isFilesDirectoryAccessible = filesDirectoryAccessibleError == null;

        // Termux can only be run as the primary user (device owner) since only that
        // account has the expected file system paths. Verify that:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !PackageUtils.isCurrentUserThePrimaryUser(activity)) {
            bootstrapErrorMessage = activity.getString(R.string.bootstrap_error_not_primary_user_message,
                MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            Logger.logError(LOG_TAG, "isFilesDirectoryAccessible: " + isFilesDirectoryAccessible);
            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.exitAppWithErrorMessage(activity,
                activity.getString(R.string.bootstrap_error_title),
                bootstrapErrorMessage);
            return;
        }

        if (!isFilesDirectoryAccessible) {
            bootstrapErrorMessage = Error.getMinimalErrorString(filesDirectoryAccessibleError);
            //noinspection SdCardPath
            if (PackageUtils.isAppInstalledOnExternalStorage(activity) &&
                !TermuxConstants.TERMUX_FILES_DIR_PATH.equals(activity.getFilesDir().getAbsolutePath().replaceAll("^/data/user/0/", "/data/data/"))) {
                bootstrapErrorMessage += "\n\n" + activity.getString(R.string.bootstrap_error_installed_on_portable_sd,
                    MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            }

            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.showMessage(activity,
                activity.getString(R.string.bootstrap_error_title),
                bootstrapErrorMessage, null);
            return;
        }

        if (!checkIfMinOrMaxSdkVersionIsIncompatible(activity,
                BuildConfig.TERMUX_APP__BOOTSTRAP_MIN_SDK, BuildConfig.TERMUX_APP__BOOTSTRAP_MIN_RELEASE,
                BuildConfig.TERMUX_APP__BOOTSTRAP_MAX_SDK, BuildConfig.TERMUX_APP__BOOTSTRAP_MAX_RELEASE)) {
            return;
        }

        // If prefix directory exists, even if its a symlink to a valid directory and symlink is not broken/dangling
        if (FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true)) {
            if (TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
                Logger.logInfo(LOG_TAG, "The termux prefix directory \"" + TERMUX_PREFIX_DIR_PATH + "\" exists but is empty or only contains specific unimportant files.");
            } else {
                // Prefix already exists; run fixups (path patching, mkdirs, dpkg wrapper) for rebranded installs.
                postInstallFixups(activity);
                whenDone.run();
                return;
            }
        } else if (FileUtils.fileExists(TERMUX_PREFIX_DIR_PATH, false)) {
            Logger.logInfo(LOG_TAG, "The termux prefix directory \"" + TERMUX_PREFIX_DIR_PATH + "\" does not exist but another file exists at its destination.");
        }

        final ProgressDialog progress = ProgressDialog.show(activity, null, activity.getString(R.string.bootstrap_installer_body), true, false);
        new Thread() {
            @Override
            public void run() {
                try {
                    Logger.logInfo(LOG_TAG, "Installing " + TermuxConstants.TERMUX_APP_NAME + " bootstrap packages.");

                    Error error;

                    // Delete prefix staging directory or any file at its destination
                    error = FileUtils.deleteFile("termux prefix staging directory", TERMUX_STAGING_PREFIX_DIR_PATH, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    // Delete prefix directory or any file at its destination
                    error = FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    // Create prefix staging directory if it does not already exist and set required permissions
                    error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    // Create prefix directory if it does not already exist and set required permissions
                    error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    Logger.logInfo(LOG_TAG, "Extracting bootstrap zip to prefix staging directory \"" + TERMUX_STAGING_PREFIX_DIR_PATH + "\".");

                    final byte[] buffer = new byte[8096];
                    final List<Pair<String, String>> symlinks = new ArrayList<>(50);

                    try (java.io.InputStream bootstrapStream = openBootstrapZipStream(activity);
                         ZipInputStream zipInput = new ZipInputStream(new java.io.BufferedInputStream(bootstrapStream))) {
                        ZipEntry zipEntry;
                        while ((zipEntry = zipInput.getNextEntry()) != null) {
                            if (zipEntry.getName().equals("SYMLINKS.txt")) {
                                BufferedReader symlinksReader = new BufferedReader(new InputStreamReader(zipInput));
                                String line;
                                while ((line = symlinksReader.readLine()) != null) {
                                    String[] parts = line.split("←");
                                    if (parts.length != 2)
                                        throw new RuntimeException("Malformed symlink line: " + line);
                                    String oldPath = parts[0];
                                    String newPath = TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1];
                                    symlinks.add(Pair.create(oldPath, newPath));

                                    error = ensureDirectoryExists(new File(newPath).getParentFile());
                                    if (error != null) {
                                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                                        return;
                                    }
                                }
                            } else {
                                String zipEntryName = zipEntry.getName();
                                File targetFile = new File(TERMUX_STAGING_PREFIX_DIR_PATH, zipEntryName);
                                boolean isDirectory = zipEntry.isDirectory();

                                error = ensureDirectoryExists(isDirectory ? targetFile : targetFile.getParentFile());
                                if (error != null) {
                                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                                    return;
                                }

                                if (!isDirectory) {
                                    try (FileOutputStream outStream = new FileOutputStream(targetFile)) {
                                        int readBytes;
                                        while ((readBytes = zipInput.read(buffer)) != -1)
                                            outStream.write(buffer, 0, readBytes);
                                    }
                                    if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") ||
                                        zipEntryName.startsWith("lib/apt/apt-helper") || zipEntryName.startsWith("lib/apt/methods") ||
                                        zipEntryName.equals("etc/termux/bootstrap/termux-bootstrap-second-stage.sh")) {
                                        //noinspection OctalInteger
                                        Os.chmod(targetFile.getAbsolutePath(), 0700);
                                    }
                                }
                            }
                        }
                    }

                    if (symlinks.isEmpty())
                        throw new RuntimeException("No SYMLINKS.txt encountered");
                    for (Pair<String, String> symlink : symlinks) {
                        Os.symlink(symlink.first, symlink.second);
                    }

                    Logger.logInfo(LOG_TAG, "Moving termux prefix staging to prefix directory.");

                    if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                        throw new RuntimeException("Moving termux prefix staging to prefix directory failed");
                    }

                    // Some bootstrap builds may still contain hardcoded legacy paths like /data/data/com.termux.
                    // Since this app is rebranded, run fixups (path patching, mkdirs, dpkg wrapper).
                    postInstallFixups(activity);

                    // Run Termux bootstrap second stage.
                    // Newer/custom bootstraps ship the script under:
                    //   $PREFIX/etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh
                    // Older bootstraps used:
                    //   $PREFIX/etc/termux/bootstrap/termux-bootstrap-second-stage.sh
                    // If we run the wrong script, it may contain hardcoded /data/data/com.termux paths.
                    String termuxBootstrapSecondStageFileNew = TERMUX_PREFIX_DIR_PATH + "/etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh";
                    String termuxBootstrapSecondStageFileOld = TERMUX_PREFIX_DIR_PATH + "/etc/termux/bootstrap/termux-bootstrap-second-stage.sh";
                    String termuxBootstrapSecondStageFile = FileUtils.fileExists(termuxBootstrapSecondStageFileNew, false)
                        ? termuxBootstrapSecondStageFileNew
                        : termuxBootstrapSecondStageFileOld;

                    if (!FileUtils.fileExists(termuxBootstrapSecondStageFile, false)) {
                        Logger.logInfo(LOG_TAG, "Not running Termux bootstrap second stage since script not found at \"" + termuxBootstrapSecondStageFile + "\" path.");
                    } else {
                        final String bashPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash";
                        if (!FileUtils.fileExists(bashPath, true)) {
                            Logger.logInfo(LOG_TAG, "Not running Termux bootstrap second stage since bash not found at \"" + bashPath + ".");
                        } else {
                            Logger.logInfo(LOG_TAG, "Running Termux bootstrap second stage.");

                            // Execute the second stage script via bash explicitly instead of executing the
                            // script file directly. On some Android builds/ROMs, execve() on scripts under
                            // app-private storage can fail with EACCES even if the script has +x.
                            ExecutionCommand executionCommand = new ExecutionCommand(-1,
                                    bashPath, new String[]{termuxBootstrapSecondStageFile}, null,
                                    null, ExecutionCommand.Runner.APP_SHELL.getName(), false);
                            executionCommand.commandLabel = "Termux Bootstrap Second Stage Command";
                            executionCommand.backgroundCustomLogLevel = Logger.LOG_LEVEL_NORMAL;
                            AppShell appShell = AppShell.execute(activity, executionCommand, null, new TermuxShellEnvironment(), null, true);
                            if (appShell == null || !executionCommand.isSuccessful() || executionCommand.resultData.exitCode != 0) {
                                // Generate debug report before deleting broken prefix directory to get `stat` info at time of failure.
                                String extraDebug = getBootstrapSecondStageDebugInfo(activity, termuxBootstrapSecondStageFile,
                                        termuxBootstrapSecondStageFileNew, termuxBootstrapSecondStageFileOld);
                                showBootstrapErrorDialog(activity, whenDone,
                                        MarkdownUtils.getMarkdownCodeForString(executionCommand.toString() + "\n\n" + extraDebug, true));

                                // Delete prefix directory as otherwise when app is restarted, the broken prefix directory would be used and logged into.
                                Logger.logInfo(LOG_TAG, "Deleting broken termux prefix.");
                                error = FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                                if (error != null)
                                    Logger.logErrorExtended(LOG_TAG, error.toString());
                                return;
                            }
                        }
                    }

                    Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.");

                    // Recreate env file since termux prefix was wiped earlier
                    TermuxShellEnvironment.writeEnvironmentToFile(activity);

                    activity.runOnUiThread(whenDone);

                } catch (final Exception e) {
                    showBootstrapErrorDialog(activity, whenDone, Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)));

                } finally {
                    activity.runOnUiThread(() -> {
                        try {
                            progress.dismiss();
                        } catch (RuntimeException e) {
                            // Activity already dismissed - ignore.
                        }
                    });
                }
            }
        }.start();
    }

    public static boolean checkIfMinOrMaxSdkVersionIsIncompatible(Activity activity,
                                                                  Integer minSdk, String minRelease,
                                                                  Integer maxSdk, String maxRelease) {
        if (minSdk != null && Build.VERSION.SDK_INT < minSdk) {
            String bootstrapErrorMessage = activity.getString(R.string.bootstrap_error_apk_bootstrap_variant_min_sdk_incompatible,
                    MarkdownUtils.getMarkdownCodeForString(TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT.getName(), false),
                    MarkdownUtils.getMarkdownCodeForString(Build.VERSION.RELEASE, false),
                    Build.VERSION.SDK_INT,
                    MarkdownUtils.getMarkdownCodeForString(minRelease, false),
                    minSdk);
            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.exitAppWithErrorMessage(activity,
                    activity.getString(R.string.bootstrap_error_title),
                    bootstrapErrorMessage);
            return false;
        }

        if (maxSdk != null && Build.VERSION.SDK_INT > maxSdk) {
            String bootstrapErrorMessage = activity.getString(R.string.bootstrap_error_apk_bootstrap_variant_max_sdk_incompatible,
                    MarkdownUtils.getMarkdownCodeForString(TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT.getName(), false),
                    MarkdownUtils.getMarkdownCodeForString(Build.VERSION.RELEASE, false),
                    Build.VERSION.SDK_INT,
                    MarkdownUtils.getMarkdownCodeForString(maxRelease, false),
                    maxSdk);
            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.exitAppWithErrorMessage(activity,
                    activity.getString(R.string.bootstrap_error_title),
                    bootstrapErrorMessage);
            return false;
        }

        return true;
    }

    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        Logger.logErrorExtended(LOG_TAG, "Bootstrap Error:\n" + message);

        // Send a notification with the exception so that the user knows why bootstrap setup failed
        sendBootstrapCrashReportNotification(activity, message);

        activity.runOnUiThread(() -> {
            try {
                new AlertDialog.Builder(activity).setTitle(R.string.bootstrap_error_title).setMessage(R.string.bootstrap_error_body)
                    .setNegativeButton(R.string.bootstrap_error_abort, (dialog, which) -> {
                        dialog.dismiss();
                        activity.finish();
                    })
                    .setPositiveButton(R.string.bootstrap_error_try_again, (dialog, which) -> {
                        dialog.dismiss();
                        FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                        TermuxInstaller.setupBootstrapIfNeeded(activity, whenDone);
                    }).show();
            } catch (WindowManager.BadTokenException e1) {
                // Activity already dismissed - ignore.
            }
        });
    }

    private static void sendBootstrapCrashReportNotification(Activity activity, String message) {
        final String title = TermuxConstants.TERMUX_APP_NAME + " Bootstrap Error";

        // Add info of all install Termux plugin apps as well since their target sdk or installation
        // on external/portable sd card can affect Termux app files directory access or exec.
        TermuxCrashUtils.sendCrashReportNotification(activity, LOG_TAG,
            title, null, "## " + title + "\n\n" + message + "\n\n" +
                TermuxUtils.getTermuxDebugMarkdownString(activity),
            true, false, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES, true);
    }

    static void setupStorageSymlinks(final Context context) {
        final String LOG_TAG = "termux-storage";
        final String title = TermuxConstants.TERMUX_APP_NAME + " Setup Storage Error";

        Logger.logInfo(LOG_TAG, "Setting up storage symlinks.");

        new Thread() {
            public void run() {
                try {
                    Error error;
                    File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;

                    error = FileUtils.clearDirectory("~/storage", storageDir.getAbsolutePath());
                    if (error != null) {
                        Logger.logErrorAndShowToast(context, LOG_TAG, error.getMessage());
                        Logger.logErrorExtended(LOG_TAG, "Setup Storage Error\n" + error.toString());
                        TermuxCrashUtils.sendCrashReportNotification(context, LOG_TAG, title, null,
                            "## " + title + "\n\n" + Error.getErrorMarkdownString(error),
                            true, false, TermuxUtils.AppInfoMode.TERMUX_PACKAGE, true);
                        return;
                    }

                    Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/shared, ~/storage/downloads, ~/storage/dcim, ~/storage/pictures, ~/storage/music and ~/storage/movies for directories in \"" + Environment.getExternalStorageDirectory().getAbsolutePath() + "\".");

                    // Get primary storage root "/storage/emulated/0" symlink
                    File sharedDir = Environment.getExternalStorageDirectory();
                    Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());

                    File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                    Os.symlink(documentsDir.getAbsolutePath(), new File(storageDir, "documents").getAbsolutePath());

                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    Os.symlink(downloadsDir.getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());

                    File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                    Os.symlink(dcimDir.getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());

                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    Os.symlink(picturesDir.getAbsolutePath(), new File(storageDir, "pictures").getAbsolutePath());

                    File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                    Os.symlink(musicDir.getAbsolutePath(), new File(storageDir, "music").getAbsolutePath());

                    File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                    Os.symlink(moviesDir.getAbsolutePath(), new File(storageDir, "movies").getAbsolutePath());

                    File podcastsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS);
                    Os.symlink(podcastsDir.getAbsolutePath(), new File(storageDir, "podcasts").getAbsolutePath());

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        File audiobooksDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS);
                        Os.symlink(audiobooksDir.getAbsolutePath(), new File(storageDir, "audiobooks").getAbsolutePath());
                    }

                    // Dir 0 should ideally be for primary storage
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/app/ContextImpl.java;l=818
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=219
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=181
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/StorageManagerService.java;l=3796
                    // https://cs.android.com/android/platform/superproject/+/android-7.0.0_r36:frameworks/base/services/core/java/com/android/server/MountService.java;l=3053

                    // Create "Android/data/com.neonide.studio" symlinks
                    File[] dirs = context.getExternalFilesDirs(null);
                    if (dirs != null && dirs.length > 0) {
                        for (int i = 0; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null) continue;
                            String symlinkName = "external-" + i;
                            Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/" + symlinkName + " for \"" + dir.getAbsolutePath() + "\".");
                            Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }

                    // Create "Android/media/com.neonide.studio" symlinks
                    dirs = context.getExternalMediaDirs();
                    if (dirs != null && dirs.length > 0) {
                        for (int i = 0; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null) continue;
                            String symlinkName = "media-" + i;
                            Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/" + symlinkName + " for \"" + dir.getAbsolutePath() + "\".");
                            Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }

                    Logger.logInfo(LOG_TAG, "Storage symlinks created successfully.");
                } catch (Exception e) {
                    Logger.logErrorAndShowToast(context, LOG_TAG, e.getMessage());
                    Logger.logStackTraceWithMessage(LOG_TAG, "Setup Storage Error: Error setting up link", e);
                    TermuxCrashUtils.sendCrashReportNotification(context, LOG_TAG, title, null,
                        "## " + title + "\n\n" + Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)),
                        true, false, TermuxUtils.AppInfoMode.TERMUX_PACKAGE, true);
                }
            }
        }.start();
    }

    private static Error ensureDirectoryExists(File directory) {
        return FileUtils.createDirectoryFile(directory.getAbsolutePath());
    }

    private static volatile String sBootstrapSource = "unknown";

    private static void postInstallFixups(Context context) {
        patchHardcodedLegacyPackagePaths(context);
        patchTermuxOpenReceiverComponent(context);
        ensureAptDirsExist(context);
        installDpkgDebPathRewriteWrapper(context);

        try {
            java.io.File marker = new java.io.File(context.getFilesDir(), "post_install_fixups.txt");
            String msg = "done\n";
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(marker, false)) {
                fos.write(msg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String getBootstrapSecondStageDebugInfo(Context context,
                                                          String chosenScriptPath,
                                                          String newScriptPath,
                                                          String oldScriptPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Bootstrap Debug]\n");
        sb.append("bootstrapSource=").append(sBootstrapSource).append("\n");
        sb.append("chosenSecondStageScript=").append(chosenScriptPath).append("\n");
        sb.append("newSecondStageScript=").append(newScriptPath).append("\n");
        sb.append("oldSecondStageScript=").append(oldScriptPath).append("\n");

        try {
            java.io.File chosen = new java.io.File(chosenScriptPath);
            sb.append("chosenExists=").append(chosen.exists()).append(" size=").append(chosen.exists() ? chosen.length() : -1).append("\n");

            // Read a small prefix of the script for debugging (avoid huge output)
            if (chosen.exists()) {
                String content;
                try (java.io.FileInputStream fis = new java.io.FileInputStream(chosen)) {
                    byte[] buf = new byte[4096];
                    int r = fis.read(buf);
                    content = r > 0 ? new String(buf, 0, r, java.nio.charset.StandardCharsets.UTF_8) : "";
                }
                sb.append("scriptHeadContains_com.termux=").append(content.contains("com.termux")).append("\n");
                sb.append("scriptHeadContains_/data/data/com.termux=").append(content.contains("/data/data/com.termux")).append("\n");
                sb.append("scriptHeadContains_com.neonide.studio=").append(content.contains("com.neonide.studio")).append("\n");
            }
        } catch (Throwable t) {
            sb.append("debugError=").append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
        }

        // Also dump the marker file if present
        try {
            java.io.File marker = new java.io.File(context.getFilesDir(), "bootstrap_source.txt");
            if (marker.exists()) {
                sb.append("bootstrap_source.txt=");
                try (java.io.FileInputStream fis = new java.io.FileInputStream(marker)) {
                    byte[] buf = new byte[256];
                    int r = fis.read(buf);
                    sb.append(r > 0 ? new String(buf, 0, r, java.nio.charset.StandardCharsets.UTF_8) : "").append("\n");
                }
            }
        } catch (Throwable ignored) {
        }

        return sb.toString();
    }

    public static String getBootstrapSource() {
        return sBootstrapSource;
    }

    private static void writeBootstrapSourceFile(Context context) {
        try {
            java.io.File f = new java.io.File(context.getFilesDir(), "bootstrap_source.txt");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f, false)) {
                fos.write((sBootstrapSource + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String getBootstrapArchForDevice() {
        String abi = android.os.Build.SUPPORTED_ABIS != null && android.os.Build.SUPPORTED_ABIS.length > 0
            ? android.os.Build.SUPPORTED_ABIS[0]
            : "";

        if ("arm64-v8a".equals(abi)) return "aarch64";
        if ("armeabi-v7a".equals(abi)) return "arm";
        if ("x86".equals(abi)) return "i686";
        if ("x86_64".equals(abi)) return "x86_64";
        // Default
        return "aarch64";
    }

    private static java.io.InputStream openBootstrapZipStream(Context context) throws java.io.IOException {
        // Use JNI embedded bootstrap.
        // NOTE: We intentionally do NOT package bootstrap zips in assets to keep APK size small.
        // (See app/build.gradle: prepareBootstraps is skipped.)
        sBootstrapSource = "jni";
        writeBootstrapSourceFile(context);
        return new java.io.ByteArrayInputStream(loadZipBytes());
    }

    // Kept for fallback mode / compatibility
    public static byte[] loadZipBytes(Context context) {
        if (!BuildConfig.DEBUG) {
            sBootstrapSource = "jni";
            writeBootstrapSourceFile(context);
            return loadZipBytes();
        }

        // Debug builds prefer streaming from assets via openBootstrapZipStream().
        // Keep this method as a fallback to JNI so existing callers (if any) still work.
        sBootstrapSource = "jni";
        writeBootstrapSourceFile(context);
        return loadZipBytes();
    }

    public static byte[] loadZipBytes() {
        // Only load the shared library when necessary to save memory usage.
        System.loadLibrary("termux-bootstrap");
        return getZip();
    }

    public static native byte[] getZip();

    private static void patchTermuxOpenReceiverComponent(Context context) {
        // The bootstrap-provided `termux-open` script is used for opening files/urls.
        // In upstream termux, it broadcasts to `com.termux/com.termux.app.TermuxOpenReceiver`.
        // This project is rebranded, so we must patch it to the current package.
        try {
            final java.io.File termuxOpen = new java.io.File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/termux-open");
            if (!termuxOpen.isFile()) return;

            final String legacyComponent = "com.termux/com.termux.app.TermuxOpenReceiver";
            final String pkg = context.getPackageName();
            final String replacementComponent = pkg + "/" + pkg + ".app.TermuxOpenReceiver";

            byte[] bytes;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(termuxOpen)) {
                bytes = fis.readAllBytes();
            }

            // Quick binary sniff: if it contains NUL, skip.
            for (byte b : bytes) {
                if (b == 0) return;
            }

            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (!content.contains(legacyComponent)) return;

            String updated = content.replace(legacyComponent, replacementComponent);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(termuxOpen, false)) {
                fos.write(updated.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            try {
                //noinspection OctalInteger
                Os.chmod(termuxOpen.getAbsolutePath(), 0700);
            } catch (Throwable ignored) {
            }

            Logger.logInfo(LOG_TAG, "Patched termux-open receiver component to \"" + replacementComponent + "\".");
        } catch (Throwable ignored) {
        }
    }

    private static void ensureAptDirsExist(Context context) {
        // Avoid apt warnings/errors about missing directories.
        try {
            java.io.File dir1 = new java.io.File(TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH + "/apt/apt.conf.d");
            java.io.File dir2 = new java.io.File(TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH + "/apt/preferences.d");
            java.io.File dir3 = new java.io.File(TermuxConstants.TERMUX_VAR_PREFIX_DIR_PATH + "/log/apt");
            dir1.mkdirs();
            dir2.mkdirs();
            dir3.mkdirs();
        } catch (Throwable ignored) {
        }
    }

    private static void installDpkgDebPathRewriteWrapper(Context context) {
        // Install a small dpkg wrapper for compatibility with rebranded installs.
        // The original wrapper logic in this file got corrupted; keep this minimal and safe.
        java.io.File dpkg = new java.io.File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/dpkg");
        java.io.File dpkgReal = new java.io.File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/dpkg.real");

        try {
            if (!dpkg.exists()) return;
            // If already wrapped, do nothing.
            if (dpkgReal.exists()) return;

            // Move original dpkg to dpkg.real
            if (!dpkg.renameTo(dpkgReal)) return;

            String script = "#!/data/data/" + context.getPackageName() + "/files/usr/bin/sh\n" +
                "set -e\n" +
                "exec \"" + dpkgReal.getAbsolutePath() + "\" \"$@\"\n";

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dpkg, false)) {
                fos.write(script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            try { Os.chmod(dpkg.getAbsolutePath(), 0700); } catch (Throwable ignored) {}

        } catch (Throwable ignored) {
            // If wrapper installation fails, try to restore original dpkg
            try {
                if (dpkgReal.exists() && !dpkg.exists()) {
                    dpkgReal.renameTo(dpkg);
                }
            } catch (Throwable ignored2) {
            }
        }
    }

    private static void patchHardcodedLegacyPackagePaths(Context context) {
        // Only patch text-like files (shell scripts, conf). Do NOT touch binaries.
        final String legacy = "/data/data/com.termux";
        final String current = "/data/data/" + context.getPackageName();

        int scanned = 0;
        int patched = 0;

        java.util.ArrayList<java.io.File> roots = new java.util.ArrayList<>();
        // Patch common locations that contain scripts/configs.
        roots.add(new java.io.File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH));
        roots.add(new java.io.File(TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH));
        roots.add(new java.io.File(TermuxConstants.TERMUX_SHARE_PREFIX_DIR_PATH + "/termux"));

        // Also patch known scripts directly (in case dirs are missing)
        java.util.ArrayList<java.io.File> directFiles = new java.util.ArrayList<>();
        directFiles.add(new java.io.File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/pkg"));
        directFiles.add(new java.io.File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/termux-setup-package-manager"));

        java.util.ArrayList<java.io.File> toCheck = new java.util.ArrayList<>();
        toCheck.addAll(directFiles);

        // Recursively walk roots (limit depth by file count and size safeguards).
        for (java.io.File root : roots) {
            try {
                if (root == null || !root.exists()) continue;
                java.util.ArrayDeque<java.io.File> q = new java.util.ArrayDeque<>();
                q.add(root);
                while (!q.isEmpty()) {
                    java.io.File cur = q.removeFirst();
                    java.io.File[] children = cur.listFiles();
                    if (children == null) continue;
                    for (java.io.File child : children) {
                        if (child.isDirectory()) {
                            q.add(child);
                        } else {
                            toCheck.add(child);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        for (java.io.File f : toCheck) {
            try {
                if (f == null || !f.exists() || !f.isFile()) continue;
                scanned++;

                // Skip very large files (likely binaries)
                if (f.length() > (2 * 1024 * 1024)) continue;

                byte[] bytes;
                try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                    bytes = fis.readAllBytes();
                }

                // Quick binary sniff: if it contains NUL, skip.
                for (int i = 0; i < bytes.length; i++) {
                    if (bytes[i] == 0) throw new RuntimeException("binary");
                }

                String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                if (!content.contains(legacy)) continue;

                String updated = content.replace(legacy, current);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f, false)) {
                    fos.write(updated.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                // If executable, try to keep it executable
                try { Os.chmod(f.getAbsolutePath(), 0700); } catch (Throwable ignored) {}

                patched++;
                Logger.logInfo(LOG_TAG, "Patched legacy path in: " + f.getAbsolutePath());
            } catch (Throwable t) {
                // Ignore binaries/permission errors
            }
        }

        // Write a marker so we can confirm patch ran on-device even if logcat is empty.
        try {
            java.io.File marker = new java.io.File(context.getFilesDir(), "legacy_path_patch.txt");
            String msg = "scanned=" + scanned + " patched=" + patched + "\n";
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(marker, false)) {
                fos.write(msg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

}