import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":server"))
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.vlcj)
    implementation(libs.jmdns)

    implementation(libs.logback)
    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "id.storia.media.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "id.storia.media.local"
            packageVersion = "1.0.0"
            modules("java.sql", "java.naming", "java.desktop", "java.management", "java.xml", "java.logging", "jdk.unsupported")
        }
    }
}
