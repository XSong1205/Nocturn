plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "dev.nocturn.localapi.client"
        compileSdk = 37
        minSdk = 29
    }
    // Runs common client contract tests on the build host; no desktop application.
    jvm("host")
    sourceSets {
        commonMain.dependencies {
            api("io.ktor:ktor-client-core:3.6.1")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.6.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        androidMain.dependencies {
            api(project(":local-api-android"))
            implementation("io.ktor:ktor-client-okhttp:3.6.1")
        }
    }
    jvmToolchain(21)
}
