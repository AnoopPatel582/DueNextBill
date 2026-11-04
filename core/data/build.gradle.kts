// Android library: the app's database. Stores cards, entries and statements on the phone,
// and hands them to :core:engine as engine types.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.anooppatel.cardtracker.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // The migration test reads the exported schemas as assets, so Robolectric must see them.
    sourceSets {
        named("test") {
            assets.directories.add("$projectDir/schemas")
        }
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Room writes a JSON description of every database version here. Commit it: future migrations are tested against it.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // `api`: whoever uses :core:data also sees the engine types it returns.
    api(project(":core:engine"))

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Robolectric runs Android code (and so Room on real SQLite) on the laptop's JVM, without the phone.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
