import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

configurations.all {
    exclude(mapOf("group" to "com.google.guava", "module" to "listenablefuture"))
}

fun sha256Of(f: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(f).use { fis ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { b -> "%02x".format(b) }
}

val bootstrapZipFile = file("$projectDir/src/main/cpp/bootstrap-aarch64.zip")
val bootstrapStampFile = file("$projectDir/src/main/cpp/generated/bootstrap-stamp.S")
val bootstrapZipSha256 = if (bootstrapZipFile.exists()) sha256Of(bootstrapZipFile) else "missing"
val bootstrapZipSize = if (bootstrapZipFile.exists()) bootstrapZipFile.length() else -1L

android {
    ndkVersion = "28.2.13676358"
    namespace = "com.neonide.studio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.neonide.studio"
        minSdk = 23
        targetSdk = 28
        versionName = "0.1-beta"

        buildConfigField("String", "TERMUX_APP__BOOTSTRAP_SHA256", "\"$bootstrapZipSha256\"")
        buildConfigField("long", "TERMUX_APP__BOOTSTRAP_SIZE", "${bootstrapZipSize}L")

        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = "com.neonide.studio"
        manifestPlaceholders["TERMUX_APP_NAME"] = "NeonIDE Studio"
        manifestPlaceholders["TERMUX_API_APP_NAME"] = "NeonIDE Studio:API"
        manifestPlaceholders["TERMUX_BOOT_APP_NAME"] = "NeonIDE Studio:Boot"
        manifestPlaceholders["TERMUX_FLOAT_APP_NAME"] = "NeonIDE Studio:Float"
        manifestPlaceholders["TERMUX_STYLING_APP_NAME"] = "NeonIDE Studio:Styling"
        manifestPlaceholders["TERMUX_TASKER_APP_NAME"] = "NeonIDE Studio:Tasker"
        manifestPlaceholders["TERMUX_WIDGET_APP_NAME"] = "NeonIDE Studio:Widget"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("testkey_untrusted.jks")
            keyAlias = "alias"
            storePassword = "xrj45yWGLbsO7W0v"
            keyPassword = "xrj45yWGLbsO7W0v"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }

    composeOptions {
    }

    lint {
        disable.add("ProtectedPermissions")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.annotation:annotation:1.10.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core:1.18.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.viewpager:viewpager:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.window:window:1.5.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.google.guava:guava:33.5.0-jre") {
        exclude(group = "org.codehaus.mojo", module = "animal-sniffer-annotations")
        // Avoid duplicate class with com.google.guava:listenablefuture:1.0
        exclude(group = "com.google.guava", module = "listenablefuture")
    }
    implementation("commons-io:commons-io:2.21.0")
    val markwonVersion = project.properties["markwonVersion"] as String
    implementation("io.noties.markwon:core:$markwonVersion")
    implementation("io.noties.markwon:ext-strikethrough:$markwonVersion")
    implementation("io.noties.markwon:linkify:$markwonVersion")
    implementation("io.noties.markwon:recycler:$markwonVersion")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("com.termux:termux-am-library:2.0.0")

    // Java -> XHTML syntax highlighting (for preview/export)
    implementation("org.codelibs:jhighlight:2.0.0")

    // --- Sora Editor (Maven) ---
    // BOM - Manages versions for all sora-editor modules
    implementation(platform("io.github.rosemoe:editor-bom:0.24.4"))

    // Core editor (replaces all embedded io.github.rosemoe.sora.* source)
    implementation("io.github.rosemoe:editor")

    // Language support modules
    implementation("io.github.rosemoe:language-java")
    implementation("io.github.rosemoe:language-textmate")
    implementation("io.github.rosemoe:language-treesitter")
    implementation("io.github.rosemoe:language-monarch")

    // Native regex engine for TextMate (replaces embedded Oniguruma C code)
    implementation("io.github.rosemoe:oniguruma-native")

    // LSP support (requires minSdk 26+; we use reflection-based factory for API 23)
    implementation("io.github.rosemoe:editor-lsp")

    // LSP4J (needed by embedded LSP utils that reference org.eclipse.lsp4j directly)
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0")

    // Gson (used by embedded Monarch/TextMate code)
    implementation("com.google.code.gson:gson:2.13.2")

    // Monarch language definitions (used by app's MonarchGrammarRegistry loading)
    implementation("io.github.dingyi222666.monarch:monarch-language-pack:1.0.2")

    // Tree-sitter language bindings (separate from Sora's treesitter module)
    // https://github.com/AndroidIDEOfficial/android-tree-sitter
    implementation("com.itsaky.androidide.treesitter:android-tree-sitter:4.3.2")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-java:4.3.2")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-kotlin:4.3.2")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-xml:4.3.2")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-json:4.3.2")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-python:4.3.2")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-c:4.3.2")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-cpp:4.3.2")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-aidl:4.3.2")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-properties:4.3.2")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-log:4.3.2")

    // Required by android-tree-sitter (Java annotation processor generating headers).
    annotationProcessor("com.squareup:javapoet:1.13.0")

    // Coroutines (used by LSP & async loading)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Local jars for demo language server(s)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

tasks.register("downloadBootstrapZip") {
    val bootstrapUrl = "https://github.com/AndroidStudio-App/AndroidStudio-App/releases/download/bootstarprap/bootstrap-aarch64.zip"
    val cppDir = File(projectDir, "src/main/cpp")
    val bootstrapZip = File(cppDir, "bootstrap-aarch64.zip")

    inputs.property("bootstrapUrl", bootstrapUrl)
    outputs.file(bootstrapZip)

    doLast {
        if (!bootstrapZip.exists()) {
            logger.quiet("Downloading bootstrap from $bootstrapUrl ...")
            cppDir.mkdirs()
            URL(bootstrapUrl).openStream().use { input ->
                bootstrapZip.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}

tasks.register("generateBootstrapStamp") {
    outputs.file(bootstrapStampFile)
    doLast {
        val sha = if (bootstrapZipFile.exists()) sha256Of(bootstrapZipFile) else "make sure task are below downloadBootstrap"
        val size = if (bootstrapZipFile.exists()) bootstrapZipFile.length() else -1L
        bootstrapStampFile.parentFile.mkdirs()
        bootstrapStampFile.writeText(
            """.ascii "BOOTSTRAP_SHA256=$sha\n"
               |.ascii "BOOTSTRAP_SIZE=$size\n"
            """.trimMargin()
        )
    }
}

tasks.register("generateDebugKeystore") {
    val keystoreFile = file("$projectDir/testkey_untrusted.jks")
    outputs.file(keystoreFile)
    doLast {
        if (!keystoreFile.exists()) {
            logger.quiet("Generating debug keystore...")
            keystoreFile.parentFile.mkdirs()
            val keytoolCmd = listOf(
                "keytool",
                "-genkey",
                "-v",
                "-keystore", keystoreFile.absolutePath,
                "-alias", "alias",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-storepass", "xrj45yWGLbsO7W0v",
                "-keypass", "xrj45yWGLbsO7W0v",
                "-dname", "CN=Debug, OU=Debug, O=Debug, L=Debug, ST=Debug, C=Debug"
            )
            val process = ProcessBuilder(keytoolCmd)
                .inheritIO()
                .start()
            process.waitFor()
        }
    }
}

// Rebuild bootstrap ndk build when there's changes
tasks.register("cleanNativeIfBootstrapChanged") {
    dependsOn(tasks.named("generateBootstrapStamp"))
    val buildDirPath = layout.buildDirectory.get().asFile
    val stateFile = file("$buildDirPath/bootstrap-embedded.sha256")
    outputs.file(stateFile)
    doLast {
        val currentSha = if (bootstrapZipFile.exists()) sha256Of(bootstrapZipFile) else null
        val previousSha = if (stateFile.exists()) stateFile.readText().trim() else null
        if (previousSha == null || previousSha != currentSha) {
            logger.lifecycle("Bootstrap zip changed (old=$previousSha, new=$currentSha). Cleaning native intermediates...")
            delete(
                file("$buildDirPath/intermediates/ndkBuild"),
                file("$buildDirPath/intermediates/cxx"),
                file("$buildDirPath/intermediates/merged_native_libs"),
                file("$buildDirPath/intermediates/stripped_native_libs")
            )
        }
        stateFile.parentFile.mkdirs()
        stateFile.writeText("$currentSha\n")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.named("preBuild") {
    dependsOn(tasks.named("downloadBootstrapZip"))
    dependsOn(tasks.named("generateBootstrapStamp"))
    dependsOn(tasks.named("generateDebugKeystore"))
    dependsOn(tasks.named("cleanNativeIfBootstrapChanged"))
}
