# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-dontobfuscate
#-renamesourcefileattribute SourceFile
#-keepattributes SourceFile,LineNumberTable

# Temp fix for androidx.window:window:1.0.0-alpha09 imported by termux-shared
# https://issuetracker.google.com/issues/189001730
# https://android-review.googlesource.com/c/platform/frameworks/support/+/1757630
-keep class androidx.window.** { *; }

# --- LSP integration ---
# SoraEditorLspController is loaded via reflection (EditorLspControllerFactory.IMPL_CLASS).
# R8 may strip it in release builds unless we keep it explicitly.
-keep class com.neonide.studio.app.lsp.impl.SoraEditorLspController { <init>(android.content.Context); *; }

# Keep LSP server bridge services (started by Intent and referenced by name in manifest).
-keep class com.neonide.studio.app.lsp.server.** { *; }

# --- Sora Editor (Maven) ---
-keep class io.github.rosemoe.sora.** { *; }
-keep class io.github.rosemoe.sora.langs.** { *; }
-keep class io.github.rosemoe.sora.widget.** { *; }
-keep class io.github.rosemoe.sora.text.** { *; }
-keep class io.github.rosemoe.sora.lsp.** { *; }
-keep class io.github.rosemoe.oniguruma.** { *; }
-dontwarn io.github.rosemoe.**
