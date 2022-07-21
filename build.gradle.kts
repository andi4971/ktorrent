import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    application
}

group = "org.azauner"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.openfeign:feign-core:11.8")
    implementation("io.github.openfeign:feign-okhttp:11.8")
    val ktor_version = "2.0.3"
    implementation("io.ktor:ktor-network:$ktor_version")
    implementation("io.ktor:ktor-network-tls:$ktor_version")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

application {
    mainClass.set("MainKt")
}

