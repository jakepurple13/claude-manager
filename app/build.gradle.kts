import dev.nucleusframework.desktop.application.dsl.ReleaseChannel
import dev.nucleusframework.desktop.application.dsl.ReleaseType
import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.nucleus)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)

    implementation(libs.nucleus.application)
    implementation(libs.nucleus.window.tao)
    implementation(libs.nucleus.window.material3)
    implementation(libs.nucleus.darkmode)
    implementation(libs.nucleus.notifications)
    implementation(libs.nucleus.fs.watcher)
    implementation(libs.nucleus.system.info)
    implementation(libs.nucleus.updater)
    implementation(libs.composenativetray)

    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)

    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.compose.navigation3)

    implementation(libs.room3.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.room3.compiler)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.koin.test)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

/**
 * App version: `-PappVersion=1.2.3`, else the release tag in CI (`v1.2.3` -> `1.2.3`), else 1.0.0.
 * The updater compares this against the version in the GitHub release's latest-*.yml.
 */
val appVersion: String = (findProperty("appVersion") as String?)
    ?: System.getenv("GITHUB_REF_NAME")?.takeIf { it.startsWith("v") }?.removePrefix("v")
    ?: "1.0.0"

nucleus.application {
    mainClass = "com.programmersbox.claudemanager.MainKt"

    nativeDistributions {
        packageName = "Claude Manager"
        packageVersion = appVersion
        description = "Monitor Claude Code sessions, subagents, shells and devices"
        vendor = "programmersbox"
        homepage = "https://github.com/jakepurple13/claude-manager"
        // Formats the Nucleus updater can update in place. The macOS ZIP is what the updater
        // installs; the DMG is for first installs.
        targetFormats(
            TargetFormat.Dmg,
            TargetFormat.Zip,
            TargetFormat.Nsis,
            TargetFormat.Deb,
            TargetFormat.Rpm,
            TargetFormat.AppImage,
        )
        modules("java.sql", "java.net.http", "jdk.unsupported", "jdk.crypto.ec")
        cleanupNativeLibs = true

        macOS {
            bundleID = "com.programmersbox.claudemanager"
            dockName = "Claude Manager"
            appCategory = "public.app-category.developer-tools"
            minimumSystemVersion = "12.0"
            iconFile.set(project.file("icons/app.icns"))
        }
        windows {
            iconFile.set(project.file("icons/app.ico"))
            menuGroup = "Claude Manager"
            shortcut = true
            // Fixed so new versions upgrade the installed app instead of installing beside it.
            upgradeUuid = "6b6a3f0e-6a51-4f8e-9a3d-2c7f1f2e9b41"
        }
        linux {
            iconFile.set(project.file("icons/app.png"))
            menuGroup = "Development"
            shortcut = true
        }

        publish {
            github {
                enabled = true
                owner = "jakepurple13"
                repo = "claude-manager"
                token = System.getenv("GITHUB_TOKEN")
                channel = ReleaseChannel.Latest
                releaseType = ReleaseType.Release
            }
        }
    }
}
