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
    implementation(libs.composenativetray.app)

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

nucleus.application {
    mainClass = "com.programmersbox.claudemanager.MainKt"

    nativeDistributions {
        packageName = "Claude Manager"
        packageVersion = "1.0.0"
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
    }
}
