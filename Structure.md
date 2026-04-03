# NeonIDE Studio - Project Structure

NeonIDE Studio (`com.neonide.studio`) is an Android IDE application that combines a code editor, terminal emulator, Gradle build system, and LSP support. It runs on Android devices.

## Tech Stack

- Kotlin 2.2.21 (JVM 17)
- Java 17
- Android Gradle Plugin 8.13.0
- Gradle 9.0.0
- Compile SDK 34, Min SDK 21, Target SDK 34
- NDK 29.0.14206865 (arm64-v8a only)

---

## Project Root

```
NeonIDE-Studio/
├── .gitignore
├── build.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── local.properties
├── README.md
├── settings.gradle
├── .github/workflows/android.yml
├── gradle/
│   ├── wrapper/
│   └── libs.versions.toml
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   ├── testkey_untrusted.jks
│   ├── libs/EmmyLua-LS-all.jar
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       ├── cpp/
│       ├── java/
│       └── res/
├── build/
├── .git/
├── .gradle/
└── .kotlin/
```

### Root Files

| File | Purpose |
|------|---------|
| `build.gradle` | Root build config, applies Android app/library plugins and Kotlin 2.2.21 to all subprojects |
| `settings.gradle` | Declares the `:app` module as the only subproject, configures version catalog |
| `gradle.properties` | Enables AndroidX, non-transitive R class, sets JVM memory args for Gradle daemon |
| `gradlew` / `gradlew.bat` | Gradle wrapper scripts for Unix and Windows |
| `local.properties` | Stores local paths to Android SDK and NDK on the device |
| `.gitignore` | Excludes build outputs, IDE files, bootstrap zips, LSP bundles from version control |
| `.github/workflows/android.yml` | CI workflow that builds debug APK on tag push or manual trigger using JDK 17 |
| `gradle/libs.versions.toml` | Version catalog listing all library versions used across the project |

---

## app/src/main/java/

### com.neonide.studio.app/ — Main Activities & Core Logic

| File/Package | Purpose |
|--------------|---------|
| `ACSHomeActivity.kt` | Home/launch activity, requests storage and notification permissions |
| `SoraEditorActivityK.kt` | Main code editor activity (2252 lines) with file tree, bottom sheets, LSP, Gradle runner |
| `TermuxActivity.java` | Terminal emulator activity (995 lines) with session management and extra keys |
| `IdeConfigActivity.kt` | IDE configuration settings activity |
| `IdeConfigPreferencesFragment.kt` | IDE preferences UI fragment |
| `MainMenuActivity.java` | Main navigation menu activity |
| `TermuxApplication.java` | Application class, initializes crash handler, shell manager, theme, file providers |
| `TermuxService.java` | Background service that manages terminal sessions |
| `TermuxInstaller.java` | Downloads and installs the Termux bootstrap environment |
| `TermuxOpenReceiver.java` | Broadcast receiver for external app file open intents |
| `RunCommandService.java` | Service for handling RUN_COMMAND permission requests |
| `DevKitSetup.java` | Development kit configuration utilities |
| `EditorViewModel.kt` | ViewModel holding editor state (open files, cursor position, etc.) |
| `FileTreeNodeViewHolder.java` | ViewHolder for file tree node display |

### com.neonide.studio.app.bottomsheet/ — Bottom Panel UI

| File | Purpose |
|------|---------|
| `EditorBottomSheetTabAdapter.kt` | Adapter for bottom sheet tab pages |
| `NavigationResultsAdapter.kt` | Adapter displaying search/navigation result items |
| `SimpleStringAdapter.kt` | Generic single-column string list adapter |
| `fragments/AppLogsFragment.kt` | Displays application-level log output |
| `fragments/BuildOutputFragment.kt` | Displays Gradle build output in real time |
| `fragments/DiagnosticsFragment.kt` | Displays LSP diagnostics (errors, warnings) |
| `fragments/IDELogsFragment.kt` | Displays IDE internal logs |
| `fragments/NavigationResultsFragment.kt` | Displays file search/navigation results |
| `fragments/SearchFragment.kt` | Search-in-files panel with results |
| `model/BottomSheetViewModel.kt` | ViewModel managing bottom sheet tab state |

### com.neonide.studio.app.buildoutput/

| File | Purpose |
|------|---------|
| `BuildOutputBuffer.kt` | Buffer that captures and streams Gradle build output to the UI |

### com.neonide.studio.app.editor/ — Editor Language Support

| File/Package | Purpose |
|--------------|---------|
| `LanguageProvider.kt` | Interface for language-specific features |
| `SoraLanguageProvider.kt` | Sora editor language provider implementation |
| `completion/UnifiedCompletionProvider.kt` | Unified code completion combining LSP and local completions |
| `xml/` | Android XML editor enhancements: tag auto-completion, color preview highlights, tree-sitter diagnostics |

### com.neonide.studio.app.gradle/ — In-App Build System

| File | Purpose |
|------|---------|
| `GradleRunner.kt` | Executes `./gradlew` commands within open projects, streams output to build panel |
| `GradleDiagnostics.kt` | Parses Gradle build output for errors and warnings |
| `GradleProjectActions.kt` | High-level Gradle project actions (sync, clean, rebuild) |
| `AndroidSdkUtils.kt` | Utilities for locating and configuring the Android SDK on device |
| `ApkInstallUtils.kt` | Handles installing built APKs onto the device after build |
| `ElfInspector.kt` | Inspects ELF binary files (shared libraries, executables) for metadata |

### com.neonide.studio.app.home/ — Welcome/Home Screen

| File/Package | Purpose |
|--------------|---------|
| `HomeFragment.kt` | Welcome screen shown on app launch with recent projects and quick actions |
| `HomeAction.kt` | Data class defining home screen action items |
| `HomeActionsAdapter.kt` | RecyclerView adapter for home screen action list |
| `clone/CloneRepositoryDialogFragment.kt` | Dialog for cloning Git repositories into the IDE |
| `create/AndroidProjectGenerator.kt` | Generates new Android project structure from templates |
| `create/ProjectTemplateRegistry.kt` | Registry of available project templates (Empty, Compose, etc.) |
| `create/ProjectValidators.kt` | Validates project name, package name, and paths during creation |
| `create/TemplateGridAdapter.kt` | Grid adapter for displaying project template thumbnails |
| `open/OpenProjectBottomSheet.kt` | Bottom sheet dialog for browsing and opening existing projects |
| `open/ProjectsListAdapter.kt` | Adapter listing previously opened projects |
| `preferences/WizardPreferences.kt` | Initial setup wizard for SDK/NDK configuration |

### com.neonide.studio.app.lsp/ — Language Server Protocol

| File/Package | Purpose |
|--------------|---------|
| `EditorLspController.kt` | Abstract LSP controller interface for editor integration |
| `EditorLspControllerFactory.kt` | Factory that creates appropriate LSP controller per language |
| `LspClient.java` | Core LSP client that communicates with language server processes |
| `LspManager.kt` | Manages lifecycle of multiple LSP server instances |
| `LspStatus.kt` | Enum representing LSP connection states (connecting, ready, error) |
| `LspUtils.kt` | Utility methods for LSP URI/path conversions |
| `LspCompletionItem.kt` | Wrapper converting LSP completion items to editor format |
| `NoopEditorLspController.kt` | No-op implementation used on API < 26 where LSP is unsupported |
| `impl/SoraEditorLspController.kt` | Concrete LSP implementation wired to Sora editor APIs |
| `server/JavaLanguageServer.kt` | Java language server process launcher |
| `server/JavaLanguageServerService.kt` | Android Service wrapper for Java language server |
| `server/KotlinLanguageServer.kt` | Kotlin language server process launcher |
| `server/KotlinLanguageServerService.kt` | Android Service wrapper for Kotlin language server |
| `server/XMLLanguageServer.kt` | XML language server process launcher |
| `server/XMLLanguageServerService.kt` | Android Service wrapper for XML language server |

### com.neonide.studio.app.terminal/ — Terminal Activity Helpers

| File | Purpose |
|------|---------|
| `TermuxActivityRootView.java` | Custom root view handling keyboard and layout changes for terminal |
| `TermuxSessionsListViewController.java` | Controls the terminal session list sidebar UI |
| `TermuxTerminalSessionActivityClient.java` | Activity-side client for terminal session communication |
| `TermuxTerminalSessionServiceClient.java` | Service-side client managing terminal session lifecycle |
| `TermuxTerminalViewClient.java` | Handles terminal view input events and rendering callbacks |
| `io/FullScreenWorkAround.kt` | Workaround for fullscreen mode edge cases |
| `io/KeyboardShortcut.kt` | Handles hardware keyboard shortcut processing |
| `io/TerminalToolbarViewPager.kt` | ViewPager for terminal extra keys toolbar |
| `io/TermuxTerminalExtraKeys.kt` | Configurable extra keys row for terminal (Ctrl, Alt, arrows, etc.) |

### com.neonide.studio.app.fragments.settings/ — Settings Fragments

Terminal I/O preferences, Terminal View preferences, and debugging preferences for Termux and its plugin apps.

### com.neonide.studio.app.api.file/

| File | Purpose |
|------|---------|
| `FileReceiverActivity.java` | Handles SEND and VIEW intents for external file sharing into the IDE |

---

### com.neonide.studio.shared/ — Shared Utilities & Termux Integration

| Package | Purpose |
|---------|---------|
| `activities/ReportActivity.java` | Displays crash reports and error logs |
| `activities/TextIOActivity.java` | Generic text input/output activity for external intents |
| `android/` | Android platform utilities: permissions, package management, process management, SELinux context handling, user management |
| `crash/` | Global crash handler that catches uncaught exceptions and writes crash reports |
| `file/` | File operation utilities: copy, delete, move, Unix file permissions, file type detection, filesystem abstraction layer |
| `jni/` | JNI result model classes for native method returns |
| `logger/IDEFileLogger.kt` | File-based logger that writes IDE logs to disk |
| `logger/Logger.java` | General logging utility with log levels and tagging |
| `markdown/` | Markdown parsing and rendering utilities |
| `net/` | Local socket communication, URI/URL parsing and normalization utilities |
| `notification/` | Notification builder and channel management utilities |
| `shell/` | Shell command execution, argument tokenizing, environment variable management, Activity Manager socket server |
| `termux/` | TermuxConstants (paths, permissions), TermuxBootstrap (bootstrap management), TermuxUtils, crash handling, extra keys config, file handling, plugin integration, settings, terminal config, theme utilities |
| `theme/` | Theme switching utilities, NightMode detection and application |
| `view/` | General view utilities and helpers |

---

### com.neonide.studio.sora/ — Sora Editor Utilities

| File | Purpose |
|------|---------|
| `StringUtils.kt` | String manipulation utilities (indentation, trimming, etc.) |
| `ToastUtils.kt` | Simplified toast message display utilities |

---

### com.neonide.studio.terminal/ — Terminal Emulator Core

Core terminal implementation written in Java:

| File | Purpose |
|------|---------|
| `ByteQueue.java` | Circular byte queue for terminal I/O buffering |
| `JNI.java` | JNI method declarations for native terminal operations |
| `KeyHandler.java` | Keyboard input encoding (Escape sequences, modifiers) |
| `Logger.java` | Terminal-specific logging |
| `TerminalBuffer.java` | Terminal screen buffer managing visible and scrollback content |
| `TerminalColors.java` | Terminal color palette management |
| `TerminalColorScheme.java` | Color scheme definitions (foreground, background, ANSI colors) |
| `TerminalEmulator.java` | Core VT100/ANSI terminal emulator (escape sequence parsing, cursor movement, screen manipulation) |
| `TerminalOutput.java` | Handles output from terminal processes into the buffer |
| `TerminalRow.java` | Data structure for a single terminal row with style runs |
| `TerminalSession.java` | Manages a single terminal session (pty + process) |
| `TerminalSessionClient.java` | Interface for clients receiving terminal session events |
| `TextStyle.java` | Text attribute encoding (bold, italic, underline, colors) |
| `WcWidth.java` | Unicode character width calculation (for proper cursor placement) |

---

### com.neonide.studio.view/ — Terminal/Editor View Components

| File/Package | Purpose |
|--------------|---------|
| `GestureAndScaleRecognizer.java` | Recognizes touch gestures (tap, double-tap, pinch-to-zoom, scroll) |
| `TerminalRenderer.java` | Renders terminal buffer to Android Canvas with colors and text |
| `TerminalView.java` | Custom Android View displaying the terminal with touch/keyboard input |
| `TerminalViewClient.java` | Interface for terminal view event callbacks |
| `support/` | View compatibility and support utilities |
| `textselection/` | Text selection handles, anchors, and clipboard integration |
| `treeview/` | File browser tree view with expand/collapse, icons, and context menus |

---

### io.github.rosemoe/ — Embedded Sora Code Editor

| Package | Purpose |
|---------|---------|
| `sora/editor/ts/` | Tree-sitter integration: `TsLanguage` (binding), `TsAnalyzeManager` (code analysis), `TsTheme` (syntax theming), bracket pair matching, scoped variable tracking, multi-language support, predicate handling |
| `sora/event/` | Editor event classes: color scheme change events, side icon click events |
| `sora/graphics/` | Text rendering engine, inlay hint renderers, font measurement |
| `sora/lang/` | Language analysis framework, completion comparators, completion filters, completion icon providers |
| `sora/langs/` | Language implementations: TextMate grammar integration, Monarch VS Code-style syntax definitions |
| `sora/lsp/` | LSP protocol integration: completion, hover, go-to-definition, diagnostics, formatting |
| `sora/text/` | Text buffer management, `ContentIO` for file read/write, undo/redo history |
| `sora/util/` | Utility classes including regex backref grammar |
| `sora/widget/` | Core CodeEditor widget, line numbers gutter, scroll handling, magnifier, sticky scroll, soft keyboard |
| `sora/annotations/` | Java annotation definitions for editor configuration |
| `sora/I18nConfig.java` | Internationalization config for editor strings |
| `oniguruma/OnigNative.java` | JNI binding to the Oniguruma regular expression engine |

---

### org.eclipse.tm4e/ — TextMate Grammar Engine

Embedded TextMate grammar parsing and tokenization engine ported for Android. Provides TextMate grammar parsing, scope matching, syntax tokenization, and theme application for the code editor.

---

## app/src/main/cpp/ — Native Code (NDK)

```
cpp/
├── Android.mk                    # NDK build config defining all native modules
├── termux-bootstrap.c            # C code that exposes embedded bootstrap ZIP to Java
├── termux-bootstrap-zip.S        # Assembly file that embeds bootstrap ZIP binary via .incbin
├── bootstrap-aarch64.zip         # Termux bootstrap archive for aarch64 (downloaded during build)
├── generated/bootstrap-stamp.S   # Auto-generated assembly with bootstrap ZIP SHA256 hash
├── oniguruma/
│   ├── oniguruma/                # Oniguruma regex engine full source tree
│   └── binding.cpp               # JNI binding connecting Oniguruma to Java
├── terminal_emulator/
│   └── termux.c                  # JNI native code for terminal (pty creation, I/O, process control)
└── termux_shared/
    └── local-socket.cpp          # C++ local domain socket library for IPC between processes
```

**Libraries built by the NDK:**

| Library | Purpose |
|---------|---------|
| `libtermux-bootstrap` | Embeds the Termux bootstrap ZIP into the APK as a native shared library for extraction at runtime |
| `libtermux` | Terminal emulator JNI — creates pseudo-terminals (pty), manages child processes, handles native I/O |
| `libonig` | Oniguruma regex engine compiled as a static native library |
| `oniguruma-binding` | JNI wrapper that exposes Oniguruma regex features to Java/Kotlin |
| `local-socket` | Native local-domain socket library for IPC between IDE components |

---

## app/src/main/assets/ — Raw Assets

| Directory/File | Purpose |
|----------------|---------|
| `acs-templates/` | Android project templates: base module build.gradle, settings.gradle, version catalog, proguard rules used when creating new projects |
| `atc/resources/` | Android template resources: default app icons, theme files, backup rules for generated projects |
| `templates/` | Pre-built Android activity templates: bottom navigation, navigation drawer, tabbed activities |
| `textmate/` | TextMate grammar files for syntax highlighting: Java, Kotlin, Python, JavaScript, HTML, Lua, Markdown. Theme files: ayu-dark, darcula, quietlight |
| `tree-sitter-queries/` | Tree-sitter query files for code analysis in 10 languages: aidl, c, cpp, java, json, kotlin, log, properties, python, xml |
| `samples/` | Sample code files (Java, Kotlin, XML, txt) used for editor demonstrations |
| `testProject/` | Test project with Lua standard library stubs for EmmyLua language server testing |
| `gradle/` + `gradle-wrapper/` | Gradle wrapper files bundled into newly generated projects |
| `JetBrainsMono-Regular.ttf` | Primary monospaced font used in the code editor |
| `Roboto-Regular.ttf` | Standard Android font used in UI elements |
| `Ubuntu-Regular.ttf` | Alternate font used in the terminal |
| `setup.sh` | Shell script for initial environment setup |

---

## app/src/main/res/ — Android Resources

| Directory | Purpose |
|-----------|---------|
| `anim/` | Animation definitions: tooltip window animations, text action popup animations |
| `drawable/` | Light mode drawables: file type icons (java, kotlin, xml, etc.), language icons, action icons, UI shapes |
| `drawable-night/` | Dark mode variants of all light mode drawables |
| `layout/` | XML layouts for all activities, fragments, dialogs, list items, bottom sheets, settings screens |
| `menu/` | Menu definitions: `sora_main` (editor toolbar), `sora_search_options` (search dialog options), `report` (crash report actions), `text_io` (text I/O actions) |
| `mipmap-*/` | App launcher icons at various screen densities (hdpi through xxxhdpi) |
| `raw/` | Raw binary resources |
| `values/colors.xml` | Color definitions for light theme |
| `values/dimens.xml` | Dimension resources (margins, padding, text sizes) |
| `values/strings.xml` | English string resources for all UI text |
| `values/styles.xml` | Style definitions for app and editor UI components |
| `values/themes.xml` | Theme definitions (light and base themes) |
| `values/attrs.xml` | Custom view attributes for editor and terminal components |
| `values-night/` | Dark mode overrides for colors, styles, and themes |
| `values-zh/` | Chinese language translations |
| `xml/` | Preference screen definitions (IDE config, terminal settings, debugging), shortcut definitions, security config (network_security_config.xml) |

---

## Core Features

1. **Code Editor** — Syntax highlighting via TextMate grammars, Monarch definitions, and Tree-sitter parsing. Code completion, search/replace, undo/redo, line numbers, word wrap, magnifier, sticky scroll
2. **Terminal Emulator** — Full VT100/ANSI Linux terminal with session management, configurable extra keys (Ctrl, Alt, arrows), color themes, and font scaling
3. **Gradle Build System** — In-app `./gradlew` execution with real-time output streaming, build log capture, APK installation after build, and ELF binary inspection
4. **LSP Support** — Language Server Protocol integration with servers for Java, Kotlin, and XML providing go-to-definition, find references, hover info, code completion, and diagnostics
5. **Project Management** — Create new Android projects from templates (Empty, Compose, etc.), open existing projects, clone Git repositories
6. **File Tree** — Sidebar file browser with auto-refresh, file-type-specific icons, expand/collapse navigation
7. **Bottom Sheet Panels** — Tabbed bottom panel showing Build Output, Diagnostics, App Logs, IDE Logs, Search Results, and Navigation Results
8. **Android XML Enhancements** — Auto-closing XML tags, inline `#RRGGBB` color preview rendering, tree-sitter XML syntax diagnostics
9. **Settings & Preferences** — Extensive configuration screens for terminal behavior, editor settings, debugging options, and theme customization
10. **External App Integration** — File receiving via SEND/VIEW intents, documents provider, RUN_COMMAND permission support, plugin API integration
