package com.neonide.studio.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.system.Os;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.neonide.studio.R;
import com.neonide.studio.shared.shell.command.ExecutionCommand.Runner;
import com.neonide.studio.shared.shell.command.ExecutionCommand.ShellCreateMode;
import com.neonide.studio.shared.termux.TermuxConstants;
import com.neonide.studio.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Utility to run app bundled dev-kit setup script in a Termux terminal session. */
public final class DevKitSetup {

    private static final String SETUP_SCRIPT_ASSET_PATH = "setup.sh";
    private static final String SETUP_SCRIPT_FILE_NAME = "setup.sh";

    private DevKitSetup() {
    }

    public static void startSetup(@NonNull Activity activity) {
        // Ensure bootstrap exists; then run setup.sh in a fresh terminal session.
        TermuxInstaller.setupBootstrapIfNeeded(activity, () -> {
            try {
                File homeDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
                //noinspection ResultOfMethodCallIgnored
                homeDir.mkdirs();

                File setupScriptFile = new File(homeDir, SETUP_SCRIPT_FILE_NAME);
                copyAssetToFile(activity, SETUP_SCRIPT_ASSET_PATH, setupScriptFile);

                // Prefer explicit chmod to allow direct invocation if needed.
                try {
                    //noinspection OctalInteger
                    Os.chmod(setupScriptFile.getAbsolutePath(), 0700);
                } catch (Throwable ignored) {
                    // Ignore if chmod fails on some devices/ROMs.
                }

                runScriptInNewTerminalSession(activity, setupScriptFile.getAbsolutePath());

                // Bring terminal UI to foreground since we are currently in a foreground activity.
                activity.startActivity(new Intent(activity, TermuxActivity.class));

            } catch (Exception e) {
                Toast.makeText(activity, "Failed to start setup: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void runScriptInNewTerminalSession(@NonNull Activity activity, @NonNull String scriptAbsolutePath) {
        String bashPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash";

        Uri executableUri = new Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(bashPath)
            .build();

        Intent execIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri);
        execIntent.setClass(activity, TermuxService.class);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[]{scriptAbsolutePath});
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, TermuxConstants.TERMUX_HOME_DIR_PATH);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.TERMINAL_SESSION.getName());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION,
            String.valueOf(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY));
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ShellCreateMode.ALWAYS.getMode());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, "setup-development-kit");
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, activity.getString(R.string.acs_setup_development_kit));
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_DESCRIPTION, activity.getString(R.string.acs_setup_development_kit_summary));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(execIntent);
        } else {
            activity.startService(execIntent);
        }
    }

    private static void copyAssetToFile(@NonNull Activity activity, @NonNull String assetPath, @NonNull File destinationFile) throws IOException {
        try (InputStream in = activity.getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(destinationFile, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

}
