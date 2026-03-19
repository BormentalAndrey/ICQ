// Root build.gradle.kts for ICQ Mobile
import java.io.ByteArrayOutputStream
import java.util.Properties

// Load local properties
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

// Version information
val versionMajor = 10
val versionMinor = 0
val versionPatch = 0
val versionCode = 1
val versionName = "$versionMajor.$versionMinor.$versionPatch"

plugins {
    // Android plugins
    id("com.android.application") version "8.6.0" apply false
    id("com.android.library") version "8.6.0" apply false
    
    // Kotlin plugins
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.23" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.23" apply false
    
    // Google services
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
    id("com.google.firebase.firebase-perf") version "1.4.2" apply false
    
    // KSP
    id("com.google.devtools.ksp") version "1.9.23-1.0.20" apply false
    
    // Dependency injection
    id("com.google.dagger.hilt.android") version "2.50" apply false
    
    // Navigation
    id("androidx.navigation.safeargs.kotlin") version "2.7.7" apply false
    
    // Serialization
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23" apply false
    
    // Parcelize
    id("org.jetbrains.kotlin.plugin.parcelize") version "1.9.23" apply false
}

// Common configuration for all subprojects
subprojects {
    // Common repositories (if needed, though we use settings repositories)
    configurations.all {
        resolutionStrategy {
            // Force specific versions to avoid conflicts
            force(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.23",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.23",
                "org.jetbrains.kotlin:kotlin-reflect:1.9.23",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3",
                "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
            )
            
            // Cache dynamic versions
            cacheDynamicVersionsFor(1, "hours")
            cacheChangingModulesFor(4, "hours")
        }
    }
    
    // Common Kotlin compilation options
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.toString()
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-Xjsr305=strict",
                "-Xjvm-default=all",
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xskip-prerelease-check"
            )
            allWarningsAsErrors = false
        }
    }
    
    // Common Java compilation options
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs = options.compilerArgs + listOf(
            "-Xlint:unchecked",
            "-Xlint:deprecation"
        )
        options.encoding = "UTF-8"
        options.isFork = true
        options.isIncremental = true
    }
    
    // Test configuration
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        timeout = java.time.Duration.ofMinutes(10)
    }
}

// Custom tasks
tasks.register("cleanAll") {
    description = "Clean all build directories"
    group = "build"
    doLast {
        delete(
            rootProject.buildDir,
            *subprojects.map { it.buildDir }.toTypedArray(),
            rootProject.layout.projectDirectory.dir(".gradle"),
            rootProject.layout.projectDirectory.dir("build-native"),
            rootProject.layout.projectDirectory.dir("captures"),
            rootProject.layout.projectDirectory.dir("cxx")
        )
    }
}

tasks.register("printBuildInfo") {
    description = "Print build environment information"
    group = "help"
    doLast {
        println("========================================")
        println("ICQ Mobile Build Information")
        println("========================================")
        println("Version: $versionName ($versionCode)")
        println("Gradle: ${gradle.gradleVersion}")
        println("Java: ${System.getProperty("java.version")}")
        println("JVM: ${System.getProperty("java.vm.name")}")
        println("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
        println("Max memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB")
        println("Cores: ${Runtime.getRuntime().availableProcessors()}")
        println("========================================")
        println("Modules:")
        subprojects.forEach { println("  - ${it.name} (${it.path})") }
        println("========================================")
    }
}

tasks.register("checkDependencies") {
    description = "Check for dependency updates"
    group = "verification"
    doLast {
        configurations.all {
            resolutionStrategy.eachDependency {
                println("${requested.group}:${requested.name}:${requested.version}")
            }
        }
    }
}

// Version information for subprojects
ext {
    set("versionCode", versionCode)
    set("versionName", versionName)
    set("minSdkVersion", 24)
    set("targetSdkVersion", 34)
    set("compileSdkVersion", 34)
    set("buildToolsVersion", "34.0.0")
    set("ndkVersion", "25.2.9519653")
    set("kotlinVersion", "1.9.23")
    set("coroutinesVersion", "1.7.3")
    set("androidxCoreVersion", "1.12.0")
    set("androidxAppCompatVersion", "1.6.1")
    set("androidxMaterialVersion", "1.11.0")
    set("androidxLifecycleVersion", "2.7.0")
    set("androidxNavigationVersion", "2.7.7")
    set("webrtcVersion", "1.0.32006")
    set("firebaseVersion", "32.7.0")
    set("hiltVersion", "2.50")
}
