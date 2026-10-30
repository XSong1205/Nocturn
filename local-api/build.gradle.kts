plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "dev.nocturn.localapi.client"
        compileSdk = 36
        minSdk = 29
    }
    // Runs common client contract tests on the build host; no desktop application.
    jvm("host")
    sourceSets {
        commonMain.dependencies {
            api("io.ktor:ktor-client-core:3.5.2")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.5.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        androidMain.dependencies {
            api(project(":local-api-android"))
            implementation("io.ktor:ktor-client-okhttp:3.5.2")
        }
    }
    jvmToolchain(21)
}
