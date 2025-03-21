plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
}

group = "io.itch.mattemade.pipeliner"
version = "1.0.0"
application {
    mainClass.set("io.itch.mattemade.pipeliner.ApplicationKt")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=${extra["io.ktor.development"] ?: "false"}")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "io.itch.mattemade.pipeliner.ApplicationKt"
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.netty)
    implementation(libs.google.api.client)
    implementation(libs.google.oauth.client)
    implementation(libs.google.api.sheets)
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test.junit)
}