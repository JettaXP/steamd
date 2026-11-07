import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(libs.apksig)
    implementation(libs.bouncycastle.pkix)
    testImplementation(libs.junit)
}

tasks.test {
    System.getenv("STEAMD_BUNDLE")?.let { environment("STEAMD_BUNDLE", it) }
    System.getenv("STEAMD_EXPECTED")?.let { environment("STEAMD_EXPECTED", it) }
    maxHeapSize = "2g"
    testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
}
