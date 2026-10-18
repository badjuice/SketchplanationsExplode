plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
}

group = "org.rdcl.nox"
version = "0.1.0"

// Java 17 bytecode, so the library runs on Android and on any current JVM.
kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
    // The live test only runs when asked: EXPLODE_LIVE=1 ./gradlew test --tests '*LiveTest*'
    System.getenv("EXPLODE_LIVE")?.let { environment("EXPLODE_LIVE", it); outputs.upToDateWhen { false } }
    testLogging { showStandardStreams = System.getenv("EXPLODE_LIVE") == "1" }
}
