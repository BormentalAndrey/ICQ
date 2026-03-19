// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
    plugins {
        id("com.android.application") version "8.6.0"
        id("com.android.library") version "8.6.0"
        id("org.jetbrains.kotlin.android") version "1.9.23"
        id("com.google.gms.google-services") version "4.4.2"
        id("com.google.devtools.ksp") version "1.9.23-1.0.20"
        id("com.google.firebase.crashlytics") version "2.9.9"
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android" || requested.id.id.startsWith("androidx.")) {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.google.com") }
        // Зеркала для ускорения
        maven {
            name = "Aliyun Mirror"
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "ICQ"

// Включение всех модулей
include(":app")
include(":core")
include(":corelib")
include(":gui")
include(":gui.shared")
include(":common.shared")
include(":libomicron")
include(":unittests")

// Настройка build directories
buildCache {
    local {
        isEnabled = true
        directory = File(rootDir, "build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}
