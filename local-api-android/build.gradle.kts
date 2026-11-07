plugins {
    id("com.android.library")
}

val nodeCommand = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"

val installNodeDependencies = tasks.register<Exec>("installNodeDependencies") {
    workingDir(rootProject.file("local-api-node"))
    commandLine(nodeCommand, "ci", "--no-audit", "--no-fund")
    inputs.files(rootProject.file("local-api-node/package.json"), rootProject.file("local-api-node/package-lock.json"))
    outputs.file(rootProject.file("local-api-node/node_modules/.package-lock.json"))
}

val buildNodeAssets = tasks.register<Exec>("buildNodeAssets") {
    dependsOn(installNodeDependencies)
    workingDir(rootProject.file("local-api-node"))
    commandLine(nodeCommand, "run", "build")
    inputs.dir(rootProject.file("local-api-node/src"))
    inputs.dir(rootProject.file("local-api-node/scripts"))
    inputs.files(rootProject.file("local-api-node/package.json"), rootProject.file("local-api-node/package-lock.json"))
    outputs.dir(layout.buildDirectory.dir("generated/nodeAssets"))
}

val prepareNodeRuntime = tasks.register<Exec>("prepareNodeRuntime") {
    workingDir(rootProject.projectDir)
    commandLine("node", "scripts/prepare-node-runtime.mjs")
    inputs.file(rootProject.file("scripts/prepare-node-runtime.mjs"))
    outputs.dir(layout.buildDirectory.dir("node-runtime"))
}

val copyRuntimeNotices = tasks.register<Sync>("copyRuntimeNotices") {
    dependsOn(prepareNodeRuntime)
    from(layout.buildDirectory.dir("node-runtime/licenses"))
    into(layout.buildDirectory.dir("generated/runtimeNotices/node-runtime"))
}

android {
    namespace = "dev.nocturn.localapi"
    compileSdk = 37
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake { arguments += "-DANDROID_STL=c++_shared" }
        }
    }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/nodeAssets").get().asFile)
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/runtimeNotices").get().asFile)
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

tasks.configureEach {
    if (name == "preBuild") dependsOn(buildNodeAssets, prepareNodeRuntime, copyRuntimeNotices)
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) dependsOn(prepareNodeRuntime)
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
