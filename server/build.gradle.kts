import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "id.storia.media"
version = "1.0.0"

dependencies {
    api(project(":core"))
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.vlcj)
    implementation(libs.logback)
    implementation(libs.compose.uiToolingPreview)

    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.autohead.response)
    implementation(libs.kotlinx.datetime)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.h2)
    implementation(libs.jaudiotagger)
    implementation(libs.jmdns)
    implementation(libs.imageio.webp)
    implementation(libs.imageio.core)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

compose.desktop {
    application {
        mainClass = "id.storia.media.entrypoint.DesktopAppMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "id.storia.media.server"
            packageVersion = "1.0.0"
            modules("java.sql", "java.naming", "java.desktop", "java.management", "java.xml", "java.logging", "jdk.unsupported")
        }
    }
}
