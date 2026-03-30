package com.neonide.studio.app.home.create

import android.content.Context
import java.io.File

/**
 * Generates Android Gradle projects similar to Android Code Studio templates.
 *
 * Notes:
 * - For BottomNav/NavDrawer/Tabbed templates, we copy their res/ directory from app assets under
 *   app/src/main/assets/templates/<templateId>/res (copied from android-code-studio).
 * - We generate Kotlin/Java sources to match ACS templates closely.
 */
object AndroidProjectGenerator {

    fun generate(
        context: Context,
        template: ProjectTemplate,
        projectDir: File,
        applicationId: String,
        minSdk: Int,
        language: String,
        useKts: Boolean = false,
    ) {
        require(!projectDir.exists()) { "Project directory already exists" }

        val useKotlin = language.equals("Kotlin", ignoreCase = true)
        val name = projectDir.name

        // Template constraints
        if (template.kind == ProjectTemplate.Kind.COMPOSE_ACTIVITY && !useKotlin) {
            throw IllegalArgumentException("Jetpack Compose template requires Kotlin")
        }

        // Create dirs
        val appDir = File(projectDir, "app")
        val srcMain = File(appDir, "src/main")
        val resDir = File(srcMain, "res")
        val codeDir = File(srcMain, if (useKotlin) "kotlin" else "java")

        val valuesDir = File(resDir, "values")
        val valuesNightDir = File(resDir, "values-night")

        codeDir.mkdirs()
        valuesDir.mkdirs()
        valuesNightDir.mkdirs()

        val addAndroidX = template.kind != ProjectTemplate.Kind.NO_ANDROIDX_ACTIVITY && template.kind != ProjectTemplate.Kind.COMPOSE_ACTIVITY

        // Root build files
        writeText(File(projectDir, if (useKts) "settings.gradle.kts" else "settings.gradle"), settingsGradle(projectDir.name, useKts))
        writeText(File(projectDir, if (useKts) "build.gradle.kts" else "build.gradle"), rootBuildGradle(useKts))

        // Copy base root files from ACS templates for 100% identical output.
        copyAsset(context, "acs-templates/base/root/gradle.properties", File(projectDir, "gradle.properties"))

        // Version catalog like Android Code Studio/ATC
        writeLibsVersionsToml(context, projectDir)

        // Module proguard rules (in this repo we put it at root historically; keep writing it for compatibility)
        copyAsset(context, "acs-templates/base/module/android/proguard-rules.pro", File(projectDir, "proguard-rules.pro"))

        // Gradle wrapper: copy the same scripts/files used by Android Code Studio (ATC assets)
        // so generated projects behave correctly.
        val wrapperDir = File(projectDir, "gradle/wrapper")
        wrapperDir.mkdirs()
        copyAsset(context, "gradle/wrapper/gradle-wrapper.jar", File(wrapperDir, "gradle-wrapper.jar"))
        copyAsset(context, "gradle/wrapper/gradle-wrapper.properties", File(wrapperDir, "gradle-wrapper.properties"))

        // gradlew scripts: copy ATC scripts (not minimal placeholders)
        copyAsset(context, "gradle/gradlew", File(projectDir, "gradlew"))
        File(projectDir, "gradlew").setExecutable(true)
        copyAsset(context, "gradle/gradlew.bat", File(projectDir, "gradlew.bat"))

        // .gitignore from ACS templates
        copyAsset(context, "acs-templates/base/root/gitignore", File(projectDir, ".gitignore"))

        // Copy base resources from ATC (icons/themes/backup rules) for identical output.
        // Individual templates may overwrite/add additional resources later.
        copyAtcBaseRes(context, resDir)
        
        // Ensure app_name matches the actual project name by overriding the base strings.xml
        writeText(File(valuesDir, "strings.xml"), """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">$name</string>
</resources>
""".trimIndent() + "\n")

        // Themes like ACS (Material3 DayNight + optional actionbar)
        // For some templates we still adjust theme parent.
        val actionBar = template.kind == ProjectTemplate.Kind.BOTTOM_NAV_ACTIVITY || template.kind == ProjectTemplate.Kind.NO_ACTIVITY
        val writeThemes = template.kind != ProjectTemplate.Kind.NO_ANDROIDX_ACTIVITY && template.kind != ProjectTemplate.Kind.COMPOSE_ACTIVITY
        if (writeThemes) {
            // Override themes.xml with our selected variant (ATC default is NoActionBar).
            writeText(File(valuesDir, "themes.xml"), simpleMaterial3Theme("AppTheme", actionBar))
            writeText(File(valuesNightDir, "themes.xml"), simpleMaterial3Theme("AppTheme", actionBar))
        }

        // App module build
        writeText(
            File(appDir, if (useKts) "build.gradle.kts" else "build.gradle"),
            appBuildGradle(
                appId = applicationId,
                minSdk = minSdk,
                useKotlin = useKotlin,
                useKts = useKts,
                templateKind = template.kind,
            ),
        )

        // Manifest
        writeText(File(srcMain, "AndroidManifest.xml"), appManifest(applicationId, template.kind, writeThemes, name))

        // Sources + resources per template
        val pkgDir = File(codeDir, applicationId.replace('.', '/')).apply { mkdirs() }

        when (template.kind) {
            ProjectTemplate.Kind.NO_ACTIVITY -> {
                // No MainActivity
            }

            ProjectTemplate.Kind.EMPTY_ACTIVITY -> {
                writeText(File(resDir, "layout/activity_main.xml"), baseLayoutContentMain())
                writeMainActivityEmpty(pkgDir, applicationId, useKotlin)
            }

            ProjectTemplate.Kind.BASIC_ACTIVITY -> {
                writeText(File(resDir, "layout/activity_main.xml"), basicActivityLayout())
                writeText(File(resDir, "layout/content_main.xml"), baseLayoutContentMain())
                writeMainActivityBasic(pkgDir, applicationId, useKotlin)
            }

            ProjectTemplate.Kind.CPP_ACTIVITY -> {
                writeText(File(resDir, "layout/activity_main.xml"), baseLayoutContentMain())
                writeMainActivityWithCpp(pkgDir, applicationId, useKotlin)
                writeCppNdkFiles(appDir, applicationId)
            }

            ProjectTemplate.Kind.NO_ANDROIDX_ACTIVITY -> {
                writeText(File(resDir, "layout/activity_main.xml"), noAndroidXActivityLayout())
                writeMainActivityNoAndroidX(pkgDir, applicationId, useKotlin)
            }

            ProjectTemplate.Kind.BOTTOM_NAV_ACTIVITY -> {
                val navGraphName = "mobile_navigation"
                copyTemplateRes(context, "bottomNav", resDir)
                writeText(File(resDir, "navigation/${navGraphName}.xml"), bottomNavNavigationXml(applicationId, navGraphName))
                mergeStringsXml(valuesDir, bottomNavStringsXml())
                writeMainActivityBottomNav(pkgDir, applicationId, useKotlin)
                writeBottomNavSources(codeDir, applicationId, useKotlin)
            }

            ProjectTemplate.Kind.NAV_DRAWER_ACTIVITY -> {
                val navGraphName = "mobile_navigation"
                copyTemplateRes(context, "navDrawer", resDir)
                writeText(File(resDir, "navigation/${navGraphName}.xml"), navDrawerNavigationXml(applicationId, navGraphName))
                mergeStringsXml(valuesDir, navDrawerStringsXml())
                writeMainActivityNavDrawer(pkgDir, applicationId, useKotlin)
                writeNavDrawerSources(codeDir, applicationId, useKotlin)
            }

            ProjectTemplate.Kind.TABBED_ACTIVITY -> {
                copyTemplateRes(context, "tabbed", resDir)
                mergeStringsXml(valuesDir, tabbedStringsXml())
                writeMainActivityTabbed(pkgDir, applicationId, useKotlin)
                writeTabbedSources(codeDir, applicationId, useKotlin)
            }

            ProjectTemplate.Kind.COMPOSE_ACTIVITY -> {
                writeText(File(valuesDir, "themes.xml"), composeThemesXml())
                writeComposeSources(codeDir, applicationId)
            }
        }
    }

    // ----------------- File helpers -----------------

    private fun writeText(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    /**
     * Merge additional <string> entries into values/strings.xml.
     *
     * This mimics Android Studio template behavior (mergeXml) so templates add their own strings
     * without overwriting the existing app_name we set earlier.
     */
    private fun mergeStringsXml(valuesDir: File, additionalStringsXml: String) {
        val stringsFile = File(valuesDir, "strings.xml")
        if (!stringsFile.exists()) {
            writeText(
                stringsFile,
                """<?xml version="1.0" encoding="utf-8"?>
<resources>
</resources>
""".trimIndent() + "\n",
            )
        }

        val base = stringsFile.readText()
        val baseNames = Regex("""<string\s+name=\"([^\"]+)\"""")
            .findAll(base)
            .map { it.groupValues[1] }
            .toSet()

        // Extract string nodes from the provided snippet (it may or may not include XML header).
        val additionalNodes = Regex("""<string\s+name=\"([^\"]+)\"[^>]*>.*?</string>""", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(additionalStringsXml)
            .map { it.value.trim() }
            .filter { node ->
                val name = Regex("""<string\s+name=\"([^\"]+)\"""").find(node)?.groupValues?.get(1)
                name != null && name !in baseNames
            }
            .toList()

        if (additionalNodes.isEmpty()) return

        // Insert before closing </resources>
        val insertion = additionalNodes.joinToString("\n") { "    $it" } + "\n"
        val merged = if (base.contains("</resources>")) {
            base.replace("</resources>", insertion + "</resources>")
        } else {
            // Fallback: if file is malformed, just append.
            base + "\n" + insertion
        }
        stringsFile.writeText(merged)
    }

    private fun copyAsset(context: Context, assetPath: String, dst: File) {
        dst.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            dst.outputStream().use { out -> input.copyTo(out) }
        }
    }

    private fun copyAtcBaseRes(context: Context, resDir: File) {
        copyAssetsDir(context, "atc/resources", resDir)
    }

    private fun copyTemplateRes(context: Context, templateId: String, resDir: File) {
        val assetRoot = "templates/$templateId/res"
        copyAssetsDir(context, assetRoot, resDir)
    }

    private fun copyAssetsDir(context: Context, assetRoot: String, outRoot: File) {
        // Walk assets recursively by listing directories.
        fun copyDir(path: String) {
            val entries = runCatching { context.assets.list(path)?.toList() ?: emptyList() }.getOrDefault(emptyList())
            for (name in entries) {
                val child = if (path.isBlank()) name else "$path/$name"
                val childEntries = runCatching { context.assets.list(child)?.toList() ?: emptyList() }.getOrDefault(emptyList())
                if (childEntries.isNotEmpty()) {
                    copyDir(child)
                } else {
                    // file
                    val rel = child.removePrefix("$assetRoot/")
                    val out = File(outRoot, rel)
                    out.parentFile?.mkdirs()
                    context.assets.open(child).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }

        copyDir(assetRoot)
    }


    // ----------------- Gradle -----------------

    private fun settingsGradle(projectName: String, useKts: Boolean): String {
        val name = sanitizeName(projectName)

        // Match modern ACS/Android Studio template style: pluginManagement + dependencyResolutionManagement
        // (useKts only changes file extension; content is identical enough for both).
        val reposBlock = if (useKts) {
            """
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
            """.trimIndent()
        } else {
            """
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
            """.trimIndent()
        }

        val depResBlock = if (useKts) {
            """
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
            }
            """.trimIndent()
        } else {
            """
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
            }
            """.trimIndent()
        }

        return if (useKts) {
            """
            pluginManagement {
                $reposBlock
            }

            dependencyResolutionManagement {
                $depResBlock
            }

            rootProject.name = "$name"

            include(":app")
            """.trimIndent() + "\n"
        } else {
            """
            pluginManagement {
                $reposBlock
            }

            dependencyResolutionManagement {
                $depResBlock
            }

            rootProject.name = "$name"

            include(":app")
            """.trimIndent() + "\n"
        }
    }

    private fun rootBuildGradle(useKts: Boolean): String {
        // Match ACS style: use version catalog plugin aliases.
        return if (useKts) {
            """
            // Top-level build file where you can add configuration options common to all sub-projects/modules.
            plugins {
                // Plugins automatically generated by TLGradle.kt
                alias(libs.plugins.android.application) apply false
                alias(libs.plugins.android.library) apply false
                alias(libs.plugins.kotlin.android) apply false
                alias(libs.plugins.kotlin.compose) apply false
            }

            tasks.register<Delete>("clean") {
                delete(rootProject.layout.buildDirectory)
            }
            """.trimIndent() + "\n"
        } else {
            """
            // Top-level build file where you can add configuration options common to all sub-projects/modules.
            plugins {
                // Plugins automatically generated by TLGradle.kt
                alias(libs.plugins.android.application) apply false
                alias(libs.plugins.android.library) apply false
                alias(libs.plugins.kotlin.android) apply false
            }

            tasks.register('clean', Delete) {
                delete rootProject.buildDir
            }
            """.trimIndent() + "\n"
        }
    }

    private fun gradleProperties(addAndroidX: Boolean): String {
        // Match ATC/Android Studio style defaults (trimmed for mobile).
        return buildString {
            appendLine("# Project-wide Gradle settings.")
            appendLine("org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8")
            if (addAndroidX) appendLine("android.useAndroidX=true") else appendLine("android.useAndroidX=false")
            appendLine("android.nonTransitiveRClass=true")
            appendLine("kotlin.code.style=official")
        }.trimEnd() + "\n"
    }

    private fun defaultProguardRules(): String {
        // Same spirit as ATC ProguardRulesPresets.DEFAULT_ANDROID
        return """
            # Add project specific ProGuard rules here.
            # You can control the set of applied configuration files using the
            # proguardFiles setting in build.gradle.
            #
            # For more details, see
            #   http://developer.android.com/guide/developing/tools/proguard.html
            
            # Uncomment this to preserve the line number information for
            # debugging stack traces.
            #-keepattributes SourceFile,LineNumberTable
            
            # If you keep the line number information, uncomment this to
            # hide the original source file name.
            #-renamesourcefileattribute SourceFile
        """.trimIndent() + "\n"
    }

    private fun writeLibsVersionsToml(context: Context, projectDir: File) {
        val gradleDir = File(projectDir, "gradle").apply { mkdirs() }
        val toml = File(gradleDir, "libs.versions.toml")

        // Use the exact same version catalog as Android Code Studio.
        // We vendor it as an asset to keep generated projects identical.
        runCatching {
            copyAsset(context, "acs-templates/libs.versions.toml", toml)
            return
        }

        // Fallback (should not happen): minimal catalog.
        toml.writeText(
            """
            [versions]
            agp = "8.3.2"

            [plugins]
            android-application = { id = "com.android.application", version.ref = "agp" }
            """.trimIndent() + "\n"
        )
    }

    private fun appBuildGradle(
        appId: String,
        minSdk: Int,
        useKotlin: Boolean,
        useKts: Boolean,
        templateKind: ProjectTemplate.Kind,
    ): String {
        val addAndroidX = templateKind != ProjectTemplate.Kind.NO_ANDROIDX_ACTIVITY && templateKind != ProjectTemplate.Kind.COMPOSE_ACTIVITY
        val enableNdkBuild = templateKind == ProjectTemplate.Kind.CPP_ACTIVITY
        val enableCompose = templateKind == ProjectTemplate.Kind.COMPOSE_ACTIVITY

        val deps = mutableListOf<String>()
        if (useKotlin) deps += "implementation 'androidx.core:core-ktx:1.13.1'" else deps += "implementation 'androidx.core:core:1.13.1'"
        if (addAndroidX) deps += "implementation 'androidx.appcompat:appcompat:1.6.1'"
        deps += "implementation 'com.google.android.material:material:1.12.0'"

        when (templateKind) {
            ProjectTemplate.Kind.BOTTOM_NAV_ACTIVITY,
            ProjectTemplate.Kind.NAV_DRAWER_ACTIVITY -> {
                deps += "implementation 'androidx.navigation:navigation-fragment-ktx:2.8.0'"
                deps += "implementation 'androidx.navigation:navigation-ui-ktx:2.8.0'"
                deps += "implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.8.4'"
                deps += "implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4'"
            }

            ProjectTemplate.Kind.TABBED_ACTIVITY -> {
                deps += "implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.8.4'"
                deps += "implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4'"
                deps += "implementation 'androidx.viewpager:viewpager:1.0.0'"
            }

            ProjectTemplate.Kind.EMPTY_ACTIVITY,
            ProjectTemplate.Kind.BASIC_ACTIVITY,
            ProjectTemplate.Kind.CPP_ACTIVITY -> {
                deps += "implementation 'androidx.constraintlayout:constraintlayout:2.1.4'"
            }

            else -> {}
        }

        if (enableCompose) {
            deps += "implementation 'androidx.activity:activity-compose:1.9.2'"
            deps += "implementation 'androidx.compose.material3:material3:1.3.1'"
            deps += "implementation 'androidx.compose.ui:ui-tooling-preview:1.7.3'"
        }

        // Plugins (use version catalog aliases to match ACS style)
        val plugins = if (useKts) {
            buildString {
                appendLine("alias(libs.plugins.android.application)")
                if (useKotlin) {
                    appendLine("alias(libs.plugins.kotlin.android)")
                    if (enableCompose) appendLine("alias(libs.plugins.kotlin.compose)")
                }
            }.trimEnd()
        } else {
            buildString {
                appendLine("alias(libs.plugins.android.application)")
                if (useKotlin) {
                    appendLine("alias(libs.plugins.kotlin.android)")
                    if (enableCompose) appendLine("alias(libs.plugins.kotlin.compose)")
                }
            }.trimEnd()
        }

        val buildFeatures = if (enableCompose) {
            """buildFeatures { compose true }""".trimIndent()
        } else {
            "buildFeatures { viewBinding true }"
        }

        val ndkVersionStr = if (enableNdkBuild) {
            if (useKts) "ndkVersion = \"29.0.14206865\"" else "ndkVersion '29.0.14206865'"
        } else ""

        val ndkBlock = if (enableNdkBuild) {
            if (useKts) {
                "externalNativeBuild { ndkBuild { path = file(\"src/main/jni/Android.mk\") } }"
            } else {
                "externalNativeBuild { ndkBuild { path 'src/main/jni/Android.mk' } }"
            }
        } else ""

        val depsBlock = deps.joinToString("\n    ")

        return if (useKts) {
            """
            plugins {
                $plugins
            }

            android {
                namespace = \"$appId\"
                compileSdk = 35
                $ndkVersionStr

                defaultConfig {
                    applicationId = \"$appId\"
                    minSdk = $minSdk
                    targetSdk = 35
                    versionCode = 1
                    versionName = \"1.0\"
                }

                $buildFeatures

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                ${if (useKotlin) "kotlinOptions { jvmTarget = \"17\" }" else ""}

                $ndkBlock
            }

            dependencies {
                $depsBlock
            }
            """.trimIndent() + "\n"
        } else {
            """
            plugins {
                $plugins
            }

            android {
                namespace '$appId'
                compileSdk 35
                $ndkVersionStr

                defaultConfig {
                    applicationId '$appId'
                    minSdk $minSdk
                    targetSdk 35
                    versionCode 1
                    versionName '1.0'
                }

                $buildFeatures

                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_17
                    targetCompatibility JavaVersion.VERSION_17
                }

                ${if (useKotlin) "kotlinOptions { jvmTarget = '17' }" else ""}

                $ndkBlock
            }

            dependencies {
                $depsBlock
            }
            """.trimIndent() + "\n"
        }
    }

    // ----------------- Manifest -----------------

    private fun appManifest(appId: String, kind: ProjectTemplate.Kind, writeThemes: Boolean, projectName: String): String {
        val themeAttr = if (writeThemes) "@style/AppTheme" else "@android:style/Theme.DeviceDefault"
        
        // For templates that don't have a strings.xml app_name by default, we use the project name directly
        // or rely on the strings.xml we just wrote.
        val appLabel = "@string/app_name"

        val activityBlock = if (kind != ProjectTemplate.Kind.NO_ACTIVITY) {
            """        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>"""
        } else ""

        return """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$appId">

    <uses-permission 
        android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="$themeAttr">
$activityBlock
    </application>

</manifest>
""".trim().plus("\n")
    }

    // ----------------- XML snippets -----------------

    private fun emptyValuesFile() = """<?xml version="1.0" encoding="utf-8"?>
<resources />
""".trimIndent() + "\n"

    private fun simpleMaterial3Theme(themeName: String, actionBar: Boolean): String {
        return """
            <resources xmlns:tools="http://schemas.android.com/tools">
              <style name="Base.$themeName" parent="Theme.Material3.DayNight${if (!actionBar) ".NoActionBar" else ""}">
              </style>
              <style name="$themeName" parent="Base.$themeName" />
            </resources>
        """.trimIndent() + "\n"
    }

    private fun baseLayoutContentMain() = """<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
  xmlns:app="http://schemas.android.com/apk/res-auto"
  android:fitsSystemWindows="true"
  android:layout_width="match_parent"
  android:layout_height="match_parent">

  <TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Hello user!"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
""" + "\n"

    private fun basicActivityLayout() = """<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true"
    tools:context=".MainActivity">

<com.google.android.material.appbar.AppBarLayout
    android:id="@+id/appbar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"/>

</com.google.android.material.appbar.AppBarLayout>

    <include layout="@layout/content_main"/>

<com.google.android.material.floatingactionbutton.FloatingActionButton
    android:id="@+id/fab"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom|end"
    android:layout_margin="16dp"
    app:srcCompat="@android:drawable/ic_dialog_email" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
""" + "\n"

    private fun noAndroidXActivityLayout() = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:text="Hello World!"
        android:gravity="center"/>

</LinearLayout>
""" + "\n"

    // ----------------- Navigation graphs & strings -----------------

    private fun bottomNavNavigationXml(pkg: String, navGraphName: String) = """<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/${navGraphName}"
    app:startDestination="@+id/navigation_home">

    <fragment
        android:id="@+id/navigation_home"
        android:name="$pkg.ui.home.HomeFragment"
        android:label="@string/title_home"
        tools:layout="@layout/fragment_home" />

    <fragment
        android:id="@+id/navigation_dashboard"
        android:name="$pkg.ui.dashboard.DashboardFragment"
        android:label="@string/title_dashboard"
        tools:layout="@layout/fragment_dashboard" />

    <fragment
        android:id="@+id/navigation_notifications"
        android:name="$pkg.ui.notifications.NotificationsFragment"
        android:label="@string/title_notifications"
        tools:layout="@layout/fragment_notifications" />
</navigation>
""" + "\n"

    private fun navDrawerNavigationXml(pkg: String, navGraphName: String) = """<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/${navGraphName}"
    app:startDestination="@+id/nav_home">

    <fragment
        android:id="@+id/nav_home"
        android:name="$pkg.ui.home.HomeFragment"
        android:label="@string/menu_home"
        tools:layout="@layout/fragment_home" />

    <fragment
        android:id="@+id/nav_gallery"
        android:name="$pkg.ui.gallery.GalleryFragment"
        android:label="@string/menu_gallery"
        tools:layout="@layout/fragment_gallery" />

    <fragment
        android:id="@+id/nav_slideshow"
        android:name="$pkg.ui.slideshow.SlideshowFragment"
        android:label="@string/menu_slideshow"
        tools:layout="@layout/fragment_slideshow" />
</navigation>
""" + "\n"

    private fun bottomNavStringsXml() = """<resources>
    <string name="title_home">Home</string>
    <string name="title_dashboard">Dashboard</string>
    <string name="title_notifications">Notifications</string>
</resources>
""" + "\n"

    private fun navDrawerStringsXml() = """<resources>
    <string name="navigation_drawer_open">Open navigation drawer</string>
    <string name="navigation_drawer_close">Close navigation drawer</string>
    <string name="nav_header_title">Android Studio</string>
    <string name="nav_header_subtitle">android.studio@android.com</string>
    <string name="nav_header_desc">Navigation header</string>
    <string name="action_settings">Settings</string>

    <string name="menu_home">Home</string>
    <string name="menu_gallery">Gallery</string>
    <string name="menu_slideshow">Slideshow</string>
</resources>
""" + "\n"

    private fun tabbedStringsXml() = """<resources>
    <string name="tab_text_1">Tab 1</string>
    <string name="tab_text_2">Tab 2</string>
</resources>
""" + "\n"

    private fun composeThemesXml() = """<?xml version="1.0" encoding="utf-8"?>
<resources>
  <style name="AppTheme" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
""" + "\n"

    // ----------------- Activities + sources -----------------

    private fun writeMainActivityEmpty(pkgDir: File, pkg: String, kotlin: Boolean) {
        writeText(File(pkgDir, if (kotlin) "MainActivity.kt" else "MainActivity.java"), if (kotlin) emptyActivityKt(pkg) else emptyActivityJava(pkg))
    }

    private fun writeMainActivityBasic(pkgDir: File, pkg: String, kotlin: Boolean) {
        writeText(File(pkgDir, if (kotlin) "MainActivity.kt" else "MainActivity.java"), if (kotlin) basicActivityKt(pkg) else basicActivityJava(pkg))
    }

    private fun writeMainActivityWithCpp(pkgDir: File, pkg: String, kotlin: Boolean) {
        writeText(File(pkgDir, if (kotlin) "MainActivity.kt" else "MainActivity.java"), if (kotlin) cppActivityKt(pkg) else cppActivityJava(pkg))
    }

    private fun writeMainActivityNoAndroidX(pkgDir: File, pkg: String, kotlin: Boolean) {
        writeText(File(pkgDir, if (kotlin) "MainActivity.kt" else "MainActivity.java"), if (kotlin) noAndroidXActivityKt(pkg) else noAndroidXActivityJava(pkg))
    }

    private fun writeMainActivityBottomNav(pkgDir: File, pkg: String, kotlin: Boolean) {
        writeText(File(pkgDir, if (kotlin) "MainActivity.kt" else "MainActivity.java"), if (kotlin) bottomNavActivityKt(pkg) else bottomNavActivityJava(pkg))
    }

    private fun writeMainActivityNavDrawer(pkgDir: File, pkg: String, kotlin: Boolean) {
        writeText(File(pkgDir, if (kotlin) "MainActivity.kt" else "MainActivity.java"), if (kotlin) navDrawerActivityKt(pkg) else navDrawerActivityJava(pkg))
    }

    private fun writeMainActivityTabbed(pkgDir: File, pkg: String, kotlin: Boolean) {
        writeText(File(pkgDir, if (kotlin) "MainActivity.kt" else "MainActivity.java"), if (kotlin) tabbedActivityKt(pkg) else tabbedActivityJava(pkg))
    }

    private fun writeBottomNavSources(codeDir: File, pkg: String, kotlin: Boolean) {
        if (kotlin) {
            writeKotlin(codeDir, pkg, "ui/home", "HomeViewModel.kt", bottomNavHomeViewModelKt(pkg))
            writeKotlin(codeDir, pkg, "ui/home", "HomeFragment.kt", bottomNavHomeFragmentKt(pkg))
            writeKotlin(codeDir, pkg, "ui/dashboard", "DashboardViewModel.kt", bottomNavDashboardViewModelKt(pkg))
            writeKotlin(codeDir, pkg, "ui/dashboard", "DashboardFragment.kt", bottomNavDashboardFragmentKt(pkg))
            writeKotlin(codeDir, pkg, "ui/notifications", "NotificationsViewModel.kt", bottomNavNotificationsViewModelKt(pkg))
            writeKotlin(codeDir, pkg, "ui/notifications", "NotificationsFragment.kt", bottomNavNotificationsFragmentKt(pkg))
        } else {
            writeJava(codeDir, pkg, "ui/home", "HomeViewModel.java", bottomNavHomeViewModelJava(pkg))
            writeJava(codeDir, pkg, "ui/home", "HomeFragment.java", bottomNavHomeFragmentJava(pkg))
            writeJava(codeDir, pkg, "ui/dashboard", "DashboardViewModel.java", bottomNavDashboardViewModelJava(pkg))
            writeJava(codeDir, pkg, "ui/dashboard", "DashboardFragment.java", bottomNavDashboardFragmentJava(pkg))
            writeJava(codeDir, pkg, "ui/notifications", "NotificationsViewModel.java", bottomNavNotificationsViewModelJava(pkg))
            writeJava(codeDir, pkg, "ui/notifications", "NotificationsFragment.java", bottomNavNotificationsFragmentJava(pkg))
        }
    }

    private fun writeNavDrawerSources(codeDir: File, pkg: String, kotlin: Boolean) {
        if (kotlin) {
            writeKotlin(codeDir, pkg, "ui/home", "HomeViewModel.kt", navDrawerHomeViewModelKt(pkg))
            writeKotlin(codeDir, pkg, "ui/home", "HomeFragment.kt", navDrawerHomeFragmentKt(pkg))
            writeKotlin(codeDir, pkg, "ui/gallery", "GalleryViewModel.kt", navDrawerGalleryViewModelKt(pkg))
            writeKotlin(codeDir, pkg, "ui/gallery", "GalleryFragment.kt", navDrawerGalleryFragmentKt(pkg))
            writeKotlin(codeDir, pkg, "ui/slideshow", "SlideshowViewModel.kt", navDrawerSlideshowViewModelKt(pkg))
            writeKotlin(codeDir, pkg, "ui/slideshow", "SlideshowFragment.kt", navDrawerSlideshowFragmentKt(pkg))
        } else {
            writeJava(codeDir, pkg, "ui/home", "HomeViewModel.java", navDrawerHomeViewModelJava(pkg))
            writeJava(codeDir, pkg, "ui/home", "HomeFragment.java", navDrawerHomeFragmentJava(pkg))
            writeJava(codeDir, pkg, "ui/gallery", "GalleryViewModel.java", navDrawerGalleryViewModelJava(pkg))
            writeJava(codeDir, pkg, "ui/gallery", "GalleryFragment.java", navDrawerGalleryFragmentJava(pkg))
            writeJava(codeDir, pkg, "ui/slideshow", "SlideshowViewModel.java", navDrawerSlideshowViewModelJava(pkg))
            writeJava(codeDir, pkg, "ui/slideshow", "SlideshowFragment.java", navDrawerSlideshowFragmentJava(pkg))
        }
    }

    private fun writeTabbedSources(codeDir: File, pkg: String, kotlin: Boolean) {
        if (kotlin) {
            writeKotlin(codeDir, pkg, "ui/main", "SectionsPagerAdapter.kt", tabbedPagerAdapterKt(pkg))
            writeKotlin(codeDir, pkg, "ui/main", "PageViewModel.kt", tabbedPageViewModelKt(pkg))
            writeKotlin(codeDir, pkg, "ui/main", "PlaceholderFragment.kt", tabbedPlaceholderFragmentKt(pkg))
        } else {
            writeJava(codeDir, pkg, "ui/main", "SectionsPagerAdapter.java", tabbedPagerAdapterJava(pkg))
            writeJava(codeDir, pkg, "ui/main", "PageViewModel.java", tabbedPageViewModelJava(pkg))
            writeJava(codeDir, pkg, "ui/main", "PlaceholderFragment.java", tabbedPlaceholderFragmentJava(pkg))
        }
    }

    private fun writeComposeSources(codeDir: File, pkg: String) {
        writeKotlin(codeDir, pkg, "", "MainActivity.kt", composeActivityKt(pkg))
        writeKotlin(codeDir, pkg, "ui/theme", "Color.kt", composeColorKt(pkg))
        writeKotlin(codeDir, pkg, "ui/theme", "Theme.kt", composeThemeKt(pkg))
        writeKotlin(codeDir, pkg, "ui/theme", "Type.kt", composeTypeKt(pkg))
    }

    private fun writeKotlin(codeDir: File, pkg: String, sub: String, name: String, src: String) {
        val dir = File(codeDir, pkg.replace('.', '/') + if (sub.isNotBlank()) "/$sub" else "")
        dir.mkdirs()
        writeText(File(dir, name), src.trim() + "\n")
    }

    private fun writeJava(codeDir: File, pkg: String, sub: String, name: String, src: String) {
        val dir = File(codeDir, pkg.replace('.', '/') + if (sub.isNotBlank()) "/$sub" else "")
        dir.mkdirs()
        writeText(File(dir, name), src.trim() + "\n")
    }

    // ----------------- C++ template -----------------

    private fun writeCppNdkFiles(appDir: File, pkg: String) {
        val jniDir = File(appDir, "src/main/jni").apply { mkdirs() }
        writeText(File(jniDir, "tomaslib.cpp"), basicJniCppSource(pkg))
        writeText(File(jniDir, "Android.mk"), androidMkFile())
        writeText(File(jniDir, "Application.mk"), applicationMkFile())
    }

    private fun basicJniCppSource(pkg: String): String {
        val prefix = "Java_${pkg.replace("_", "_1").replace('.', '_')}_MainActivity"
        return """#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
${prefix}_sayHello(JNIEnv *env, jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
"""
    }

    private fun androidMkFile(): String = """LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := tomaslib
LOCAL_SRC_FILES := tomaslib.cpp
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
""" + "\n"

    private fun applicationMkFile(): String = """APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
APP_PLATFORM := android-21
APP_STL := c++_shared
""" + "\n"

    // ----------------- Source templates (from ACS) -----------------

    private fun emptyActivityKt(pkg: String) = """
package $pkg

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import $pkg.databinding.ActivityMainBinding

public class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null

    private val binding: ActivityMainBinding
      get() = checkNotNull(_binding) { "Activity has been destroyed" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
"""

    private fun emptyActivityJava(pkg: String) = """
package $pkg;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import $pkg.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.binding = null;
    }
}
"""

    private fun basicActivityKt(pkg: String) = """
package $pkg

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import $pkg.databinding.ActivityMainBinding

public class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null

    private val binding: ActivityMainBinding
      get() = checkNotNull(_binding) { "Activity has been destroyed" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.fab.setOnClickListener {
            Toast.makeText(this@MainActivity, "Replace with your action", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
"""

    private fun basicActivityJava(pkg: String) = """
package $pkg;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import $pkg.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

      private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
            binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

            setSupportActionBar(binding.toolbar);

            binding.fab.setOnClickListener(v ->
          Toast.makeText(MainActivity.this, "Replace with your action", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.binding = null;
    }
}
"""

    private fun cppActivityKt(pkg: String) = """
package $pkg

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import $pkg.databinding.ActivityMainBinding

public class MainActivity : AppCompatActivity() {

    companion object {
        init {
            System.loadLibrary("tomaslib")
        }
    }

    private var _binding: ActivityMainBinding? = null

    private val binding: ActivityMainBinding
      get() = checkNotNull(_binding) { "Activity has been destroyed" }

    external fun sayHello(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val message = sayHello()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
"""

    private fun cppActivityJava(pkg: String) = """
package $pkg;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;
import $pkg.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("tomaslib");
    }

    private ActivityMainBinding binding;

    public native String sayHello();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        String message = sayHello();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.binding = null;
    }
}
"""

    private fun noAndroidXActivityKt(pkg: String) = """
package $pkg

import android.app.Activity
import android.os.Bundle
import $pkg.databinding.ActivityMainBinding

public class MainActivity : Activity() {

    private var _binding: ActivityMainBinding? = null

    private val binding: ActivityMainBinding
      get() = checkNotNull(_binding) { "Activity has been destroyed" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
"""

    private fun noAndroidXActivityJava(pkg: String) = """
package $pkg;

import android.app.Activity;
import android.os.Bundle;
import $pkg.databinding.ActivityMainBinding;

public class MainActivity extends Activity {

      private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
            binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.binding = null;
    }
}
"""

    private fun bottomNavActivityKt(pkg: String) = """
package $pkg

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import $pkg.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)
    }
}
"""

    private fun bottomNavActivityJava(pkg: String) = """
package $pkg;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import $pkg.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);
    }

}
"""

    private fun navDrawerActivityKt(pkg: String) = """
package $pkg

import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import $pkg.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), binding.drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
"""

    private fun navDrawerActivityJava(pkg: String) = """
package $pkg;

import android.os.Bundle;
import android.view.View;
import android.view.Menu;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import $pkg.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
"""

    private fun tabbedActivityKt(pkg: String) = """
package $pkg

import android.os.Bundle
import androidx.viewpager.widget.ViewPager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import $pkg.ui.main.SectionsPagerAdapter
import $pkg.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: ViewPager = binding.viewPager
        viewPager.adapter = sectionsPagerAdapter

        val tabs: TabLayout = binding.tabs
        tabs.setupWithViewPager(viewPager)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }
    }
}
"""

    private fun tabbedActivityJava(pkg: String) = """
package $pkg;

import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import $pkg.ui.main.SectionsPagerAdapter;
import $pkg.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
        binding.viewPager.setAdapter(sectionsPagerAdapter);

        binding.tabs.setupWithViewPager(binding.viewPager);

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null)
                        .setAnchorView(R.id.fab).show();
            }
        });
    }
}
"""

    // ---- bottom nav fragments / viewmodels ----

    private fun bottomNavHomeViewModelKt(pkg: String) = """
package $pkg.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    val text: LiveData<String> = _text
}
"""

    private fun bottomNavDashboardViewModelKt(pkg: String) = """
package $pkg.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DashboardViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is dashboard Fragment"
    }
    val text: LiveData<String> = _text
}
"""

    private fun bottomNavNotificationsViewModelKt(pkg: String) = """
package $pkg.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class NotificationsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is notifications Fragment"
    }
    val text: LiveData<String> = _text
}
"""

    private fun bottomNavHomeViewModelJava(pkg: String) = """
package $pkg.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public HomeViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is home fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}
"""

    private fun bottomNavDashboardViewModelJava(pkg: String) = """
package $pkg.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class DashboardViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public DashboardViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is dashboard fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}
"""

    private fun bottomNavNotificationsViewModelJava(pkg: String) = """
package $pkg.ui.notifications;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class NotificationsViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public NotificationsViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is notifications fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}
"""

    private fun bottomNavHomeFragmentKt(pkg: String) = """
package $pkg.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import $pkg.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
"""

    private fun bottomNavDashboardFragmentKt(pkg: String) = """
package $pkg.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import $pkg.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textDashboard
        dashboardViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
"""

    private fun bottomNavNotificationsFragmentKt(pkg: String) = """
package $pkg.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import $pkg.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val notificationsViewModel =
            ViewModelProvider(this).get(NotificationsViewModel::class.java)

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textNotifications
        notificationsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
"""

    private fun bottomNavHomeFragmentJava(pkg: String) = """
package $pkg.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import $pkg.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textHome;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
"""

    private fun bottomNavDashboardFragmentJava(pkg: String) = """
package $pkg.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import $pkg.databinding.FragmentDashboardBinding;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        DashboardViewModel dashboardViewModel =
                new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textDashboard;
        dashboardViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
"""

    private fun bottomNavNotificationsFragmentJava(pkg: String) = """
package $pkg.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import $pkg.databinding.FragmentNotificationsBinding;

public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        NotificationsViewModel notificationsViewModel =
                new ViewModelProvider(this).get(NotificationsViewModel.class);

        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textNotifications;
        notificationsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
"""

    // ---- nav drawer fragments/viewmodels (reuse bottom nav ones with renamed packages/bindings) ----

    private fun navDrawerHomeViewModelKt(pkg: String) = bottomNavHomeViewModelKt(pkg)
    private fun navDrawerHomeViewModelJava(pkg: String) = bottomNavHomeViewModelJava(pkg)
    private fun navDrawerGalleryViewModelKt(pkg: String) = bottomNavDashboardViewModelKt(pkg).replace("ui.dashboard", "ui.gallery").replace("DashboardViewModel", "GalleryViewModel").replace("dashboard", "gallery")
    private fun navDrawerGalleryViewModelJava(pkg: String) = bottomNavDashboardViewModelJava(pkg).replace("ui.dashboard", "ui.gallery").replace("DashboardViewModel", "GalleryViewModel").replace("dashboard", "gallery")
    private fun navDrawerSlideshowViewModelKt(pkg: String) = bottomNavDashboardViewModelKt(pkg).replace("ui.dashboard", "ui.slideshow").replace("DashboardViewModel", "SlideshowViewModel").replace("dashboard", "slideshow")
    private fun navDrawerSlideshowViewModelJava(pkg: String) = bottomNavDashboardViewModelJava(pkg).replace("ui.dashboard", "ui.slideshow").replace("DashboardViewModel", "SlideshowViewModel").replace("dashboard", "slideshow")

    private fun navDrawerHomeFragmentKt(pkg: String) = bottomNavHomeFragmentKt(pkg)
    private fun navDrawerHomeFragmentJava(pkg: String) = bottomNavHomeFragmentJava(pkg)
    private fun navDrawerGalleryFragmentKt(pkg: String) = bottomNavDashboardFragmentKt(pkg).replace("ui.dashboard", "ui.gallery").replace("DashboardFragment", "GalleryFragment").replace("FragmentDashboardBinding", "FragmentGalleryBinding").replace("textDashboard", "textGallery").replace("DashboardViewModel", "GalleryViewModel")
    private fun navDrawerGalleryFragmentJava(pkg: String) = bottomNavDashboardFragmentJava(pkg).replace("ui.dashboard", "ui.gallery").replace("DashboardFragment", "GalleryFragment").replace("FragmentDashboardBinding", "FragmentGalleryBinding").replace("textDashboard", "textGallery").replace("DashboardViewModel", "GalleryViewModel")
    private fun navDrawerSlideshowFragmentKt(pkg: String) = bottomNavDashboardFragmentKt(pkg).replace("ui.dashboard", "ui.slideshow").replace("DashboardFragment", "SlideshowFragment").replace("FragmentDashboardBinding", "FragmentSlideshowBinding").replace("textDashboard", "textSlideshow").replace("DashboardViewModel", "SlideshowViewModel")
    private fun navDrawerSlideshowFragmentJava(pkg: String) = bottomNavDashboardFragmentJava(pkg).replace("ui.dashboard", "ui.slideshow").replace("DashboardFragment", "SlideshowFragment").replace("FragmentDashboardBinding", "FragmentSlideshowBinding").replace("textDashboard", "textSlideshow").replace("DashboardViewModel", "SlideshowViewModel")

    // ---- tabbed sources ----

    private fun tabbedPagerAdapterKt(pkg: String) = """
package $pkg.ui.main

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import $pkg.R

private val TAB_TITLES = arrayOf(
    R.string.tab_text_1,
    R.string.tab_text_2
)

class SectionsPagerAdapter(private val context: Context, fm: FragmentManager) :
    FragmentPagerAdapter(fm) {

    override fun getItem(position: Int): Fragment {
        return PlaceholderFragment.newInstance(position + 1)
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return context.resources.getString(TAB_TITLES[position])
    }

    override fun getCount(): Int {
        // Show 2 total pages.
        return 2
    }
}
"""

    private fun tabbedPagerAdapterJava(pkg: String) = """
package $pkg.ui.main;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import $pkg.R;

public class SectionsPagerAdapter extends FragmentPagerAdapter {

    @StringRes
    private static final int[] TAB_TITLES = new int[]{R.string.tab_text_1, R.string.tab_text_2};
    private final Context mContext;

    public SectionsPagerAdapter(Context context, FragmentManager fm) {
        super(fm);
        mContext = context;
    }

    @Override
    public Fragment getItem(int position) {
        return PlaceholderFragment.newInstance(position + 1);
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return mContext.getResources().getString(TAB_TITLES[position]);
    }

    @Override
    public int getCount() {
        // Show 2 total pages.
        return 2;
    }
}
"""

    private fun tabbedPageViewModelKt(pkg: String) = """
package $pkg.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.ViewModel

class PageViewModel : ViewModel() {

    private val _index = MutableLiveData<Int>()
    val text: LiveData<String> = _index.map {
        "Hello world from section: ${'$'}it"
    }

    fun setIndex(index: Int) {
        _index.value = index
    }
}
"""

    private fun tabbedPageViewModelJava(pkg: String) = """
package $pkg.ui.main;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class PageViewModel extends ViewModel {

    private MutableLiveData<Integer> mIndex = new MutableLiveData<>();
    private MediatorLiveData<String> mText = new MediatorLiveData<>();

    public PageViewModel() {
        mText.addSource(mIndex, index -> {
            if (index != null) {
                mText.setValue("Hello world from section: " + index);
            }
        });
    }

    public void setIndex(int index) {
        mIndex.setValue(index);
    }

    public LiveData<String> getText() {
        return mText;
    }
}
"""

    private fun tabbedPlaceholderFragmentKt(pkg: String) = """
package $pkg.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import $pkg.databinding.FragmentMainBinding

class PlaceholderFragment : Fragment() {

    private lateinit var pageViewModel: PageViewModel
    private var _binding: FragmentMainBinding? = null

    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProvider(this).get(PageViewModel::class.java).apply {
            setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentMainBinding.inflate(inflater, container, false)
        val root = binding.root

        val textView: TextView = binding.sectionLabel
        pageViewModel.text.observe(viewLifecycleOwner, Observer {
            textView.text = it
        })
        return root
    }

    companion object {
        private const val ARG_SECTION_NUMBER = "section_number"

        @JvmStatic
        fun newInstance(sectionNumber: Int): PlaceholderFragment {
            return PlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
"""

    private fun tabbedPlaceholderFragmentJava(pkg: String) = """
package $pkg.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import $pkg.databinding.FragmentMainBinding;

public class PlaceholderFragment extends Fragment {

    private static final String ARG_SECTION_NUMBER = "section_number";

    private PageViewModel pageViewModel;
    private FragmentMainBinding binding;

    public static PlaceholderFragment newInstance(int index) {
        PlaceholderFragment fragment = new PlaceholderFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_SECTION_NUMBER, index);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pageViewModel = new ViewModelProvider(this).get(PageViewModel.class);
        int index = 1;
        if (getArguments() != null) {
            index = getArguments().getInt(ARG_SECTION_NUMBER);
        }
        pageViewModel.setIndex(index);
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        binding = FragmentMainBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.sectionLabel;
        pageViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
"""

    // ---- compose sources ----

    private fun composeActivityKt(pkg: String) = """
package $pkg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import $pkg.ui.theme.MyComposeApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyComposeApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background) {
                    Greeting("Android")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Hello ${'$'}name!")
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyComposeApplicationTheme {
        Greeting("Android")
    }
}
"""

    private fun composeColorKt(pkg: String) = """
package $pkg.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
"""

    private fun composeThemeKt(pkg: String) = """
package $pkg.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(primary = Purple80, secondary = PurpleGrey80,
        tertiary = Pink80)

private val LightColorScheme =
    lightColorScheme(primary = Purple40, secondary = PurpleGrey40,
        tertiary = Pink40)

@Composable
fun MyComposeApplicationTheme(darkTheme: Boolean = isSystemInDarkTheme(),
                              dynamicColor: Boolean = true,
                              content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(
                context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window,
                view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography,
        content = content)
}
"""

    private fun composeTypeKt(pkg: String) = """
package $pkg.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp,
        letterSpacing = 0.5.sp)
)
"""

    // ----------------- misc -----------------

    private fun gitignore() = """
        .gradle/
        build/
        local.properties
        *.iml
        .idea/
        .DS_Store
    """.trimIndent() + "\n"

    private fun gradlewSh() = """
        #!/usr/bin/env sh
        DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd)"
        exec java -jar "${'$'}DIR/gradle/wrapper/gradle-wrapper.jar" "${'$'}@"
    """.trimIndent() + "\n"

    private fun gradlewBat() = """
        @echo off
        set DIR=%~dp0
        java -jar "%DIR%\\gradle\\wrapper\\gradle-wrapper.jar" %*
    """.trimIndent() + "\n"

    private fun sanitizeName(name: String) = name.ifBlank { "App" }
}
