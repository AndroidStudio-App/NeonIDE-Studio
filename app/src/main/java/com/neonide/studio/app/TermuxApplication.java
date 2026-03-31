package com.neonide.studio.app;

import android.app.Application;
import android.content.Context;

import androidx.annotation.Nullable;

import com.neonide.studio.BuildConfig;
import com.neonide.studio.shared.errors.Error;
import com.neonide.studio.shared.logger.Logger;
import com.neonide.studio.shared.termux.TermuxBootstrap;
import com.neonide.studio.shared.termux.TermuxConstants;
import com.neonide.studio.shared.termux.crash.TermuxCrashUtils;
import com.neonide.studio.shared.termux.file.TermuxFileUtils;
import com.neonide.studio.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.neonide.studio.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.neonide.studio.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.neonide.studio.shared.termux.shell.am.TermuxAmSocketServer;
import com.neonide.studio.shared.termux.shell.TermuxShellManager;
import com.neonide.studio.shared.termux.theme.TermuxThemeUtils;

import java.io.File;

public class TermuxApplication extends Application {

    private static final String LOG_TAG = "TermuxApplication";

    private static volatile Context sApplicationContext;

    /**
     * Returns the Application context if the Application has been created, otherwise {@code null}.
     * This is intentionally "unsafe" and should only be used for best-effort utilities like file logging.
     */
    @Nullable
    public static Context getApplicationContextUnsafe() {
        return sApplicationContext;
    }

    public void onCreate() {
        super.onCreate();

        Context context = getApplicationContext();
        sApplicationContext = context;

        // Init sora-editor file providers
        io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry.getInstance().addFileProvider(new io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver(context.getAssets()));
        io.github.rosemoe.sora.langs.monarch.registry.FileProviderRegistry.INSTANCE.addProvider(new io.github.rosemoe.sora.langs.monarch.registry.provider.AssetsFileResolver(context.getAssets()));

        // Set crash handler for the app
        TermuxCrashUtils.setDefaultCrashHandler(this);

        // Set log config for the app
        setLogConfig(context);

        Logger.logDebug("Starting Application");

        // Init app wide SharedProperties loaded from termux.properties
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.init(context);
        forceEnableAllowExternalApps(context);

        // Init app wide shell manager
        TermuxShellManager shellManager = TermuxShellManager.init(context);

        // Set NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(properties.getNightMode());

        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        Error error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(this, true, true);
        boolean isTermuxFilesDirectoryAccessible = error == null;
        if (isTermuxFilesDirectoryAccessible) {
            Logger.logInfo(LOG_TAG, "Termux files directory is accessible");
            /*
            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true);
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, "Create apps/termux-app directory failed\n" + error);
                return;
            }

            // Setup termux-am-socket server
            TermuxAmSocketServer.setupTermuxAmSocketServer(context);
             */
        } else {
            Logger.logErrorExtended(LOG_TAG, "Termux files directory is not accessible\n" + error);
        }

        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this);

        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this);
        }
    }

    public static void setLogConfig(Context context) {
        Logger.setDefaultLogTag(TermuxConstants.TERMUX_APP_NAME);

        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;
        preferences.setLogLevel(null, preferences.getLogLevel());
    }

    private void forceEnableAllowExternalApps(Context context) {
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.getProperties();
        if (properties != null) {
            if (!properties.shouldAllowExternalApps()) {
                Logger.logInfo(LOG_TAG, "Force enabling allow-external-apps");
                File propsFile = new File(TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE_PATH);
                try {
                    if (!propsFile.exists()) {
                        propsFile.getParentFile().mkdirs();
                        propsFile.createNewFile();
                    }
                    // Simple append
                    java.io.FileWriter writer = new java.io.FileWriter(propsFile, true);
                    writer.write("\nallow-external-apps=true\n");
                    writer.close();
                    // Reload
                    properties.loadTermuxPropertiesFromDisk();
                } catch (java.io.IOException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to force enable allow-external-apps", e);
                }
            }
        }
    }

}
