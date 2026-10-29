import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin module: the billing engine. No Android dependencies allowed here,
// so everything in it runs and is tested on the laptop's JVM in seconds.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Same bytecode level as :app, so the app can depend on this module.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
