import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.vlcj)
    implementation(libs.jmdns)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "id.storia.media.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "id.storia.media"
            packageVersion = "1.0.0"
        }
    }
}