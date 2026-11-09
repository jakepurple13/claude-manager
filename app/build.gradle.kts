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
 * App version: `-PappVersion=1.2.3`, else the release tag in CI (`v1.2.3` -> `1.2.3`), else 1.1.1.
 * The updater compares this against the version in the GitHub release's latest-*.yml.
 */
// Read through providers so the configuration cache notices a changed -PappVersion or tag;
// findProperty / System.getenv would reuse the version from the cached configuration.
val appVersion: String = providers.gradleProperty("appVersion").orNull
    ?: providers.environmentVariable("GITHUB_REF_NAME").orNull?.takeIf { it.startsWith("v") }?.removePrefix("v")
    ?: "1.1.1"

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
            // DEB requires a maintainer with an email; a GitHub no-reply address keeps a personal one out of the package.
            debMaintainer = "jakepurple13 <2065574+jakepurple13@users.noreply.github.com>"
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

// After a version change, Nucleus's packaging tasks report UP-TO-DATE and electron-builder reuses
// the package.json (which holds the version) left in the output folder, so local builds kept the
// old version. Packaging runs rarely: always configure and run it fresh, without the old metadata.
val composeBinaries = layout.buildDirectory.dir("compose/binaries")
tasks.matching { it.name.startsWith("package") || it.name.endsWith("Distributable") }.configureEach {
    notCompatibleWithConfigurationCache("Package version comes from -PappVersion or the release tag")
    outputs.upToDateWhen { false }
    doFirst {
        fileTree(composeBinaries) { include("*/*/package.json") }.forEach { it.delete() }
    }
}
