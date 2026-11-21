plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core"))
    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.withType<Test>().configureEach {
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
}

tasks.register<JavaExec>("devHost") {
    group = "application"
    description = "Runs the Words of Wisdom LAN party server locally on port 8777 for browser testing."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.wordsofwisdom.server.DevHostKt")
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
}
