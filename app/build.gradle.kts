import java.io.RandomAccessFile
import java.util.Properties
import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

abstract class IncrementAppVersion : DefaultTask() {
    @get:Internal
    abstract val versionFile: RegularFileProperty

    @get:OutputFile
    abstract val snapshotFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    }

    @TaskAction
    fun increment() {
        // Lock the persistent counter so concurrent Gradle invocations reserve distinct versions.
        RandomAccessFile(versionFile.get().asFile, "rw").use { file ->
            file.channel.lock().use {
                val bytes = ByteArray(file.length().toInt())
                file.readFully(bytes)
                val version = Properties().apply { bytes.inputStream().use { load(it) } }
                val code = Math.addExact(version.getProperty("versionCode").toInt(), 1)
                require(code <= 2_100_000_000) { "Android versionCode limit exceeded" }
                val parts = version.getProperty("versionName").split('.').map { it.toInt() }
                require(parts.size == 3 && parts.all { it >= 0 }) { "Expected major.minor.patch versionName" }
                val name = "${parts[0]}.${parts[1]}.${Math.addExact(parts[2], 1)}"
                val content = "versionCode=$code\nversionName=$name\n"
                file.seek(0)
                file.write(content.toByteArray(Charsets.UTF_8))
                file.setLength(file.filePointer)
                snapshotFile.get().asFile.apply {
                    parentFile.mkdirs()
                    writeText(content)
                }
                logger.lifecycle("App version: $name ($code)")
            }
        }
    }
}

val incrementAppVersion = tasks.register<IncrementAppVersion>("incrementAppVersion") {
    versionFile.set(layout.projectDirectory.file("version.properties"))
    snapshotFile.set(layout.buildDirectory.file("generated/app-version/version.properties"))
}

androidComponents {
    onVariants { variant ->
        val packagingRequested = gradle.startParameter.taskNames.any { path ->
            val task = path.substringAfterLast(':')
            val targetsApp = !path.contains(':') || path.substringBeforeLast(':') in listOf("", ":app", "app")
            targetsApp && (task == "build" || task == "buildNeeded" || task == "buildDependents" ||
                task.startsWith("assemble") || task.startsWith("bundle") ||
                task.startsWith("install") || task.startsWith("package"))
        }
        val versionText = if (packagingRequested) {
            incrementAppVersion.flatMap { it.snapshotFile }.map { it.asFile.readText() }
        } else {
            providers.fileContents(layout.projectDirectory.file("version.properties")).asText
        }
        val version = versionText.map { text ->
            Properties().apply { text.reader().use { load(it) } }
        }
        val variantName = variant.name
        variant.outputs.forEach { output ->
            output.versionCode.set(version.map { it.getProperty("versionCode").toInt() })
            output.versionName.set(version.map { it.getProperty("versionName") })
            val abi = output.filters.find {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier ?: "universal"
            val sanitizedAbi = abi.replace('-', '_')
            if (output is VariantOutputImpl) {
                output.outputFileName.set(version.map {
                    "Nocturn_${it.getProperty("versionName")}_${sanitizedAbi}_$variantName.apk"
                })
            }
        }
    }
}

android {
    namespace = "dev.nocturn.app"
    // Compose 1.12.x and Coil 3.6.x publish AAR metadata that requires compiling against API 37.
    // targetSdk stays at 36; runtime behaviour is unchanged.
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.nocturn.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

val robolectricSdk = configurations.create("robolectricSdk") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(project(":local-api"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    // Material 3 Expressive components are only published in the 1.5 line; the BOM still pins 1.4 stable.
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation3:navigation3-runtime:1.2.0")
    implementation("androidx.navigation3:navigation3-ui:1.2.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("com.mocharealm.accompanist:lyrics-ui:2.0.0-rc.3")
    implementation("com.mocharealm.accompanist:lyrics-core:0.5.0")
    implementation("io.github.proify.lyricon:provider:0.1.70")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    // Backdrop / progressive blur materials (HyperOS style). Effects need API 33+ (RuntimeShader);
    // the library turns them off below that and callers fall back to opaque tinted surfaces.
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.ktor:ktor-client-mock:3.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    robolectricSdk("org.robolectric:android-all-instrumented:16-robolectric-13921718-i7")
}

val prepareRobolectricSdk = tasks.register<Sync>("prepareRobolectricSdk") {
    from(robolectricSdk)
    into(layout.buildDirectory.dir("robolectric-sdk"))
}

tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricSdk)
    maxHeapSize = "1g"
    // Resolve pinned SDKs through Gradle, then keep the test execution fully offline.
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-sdk").get().asFile.absolutePath)
    // Robolectric's ApplicationSharedMemory reaches into JDK internals (SharedSecrets / FileDescriptor);
    // on JDK 21 those packages are closed to the unnamed module unless explicitly opened.
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
}
