pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
        maven {
            name = "Aliyun Mirror"
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
    
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
            if (requested.id.id == "com.android.library") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
        maven {
            name = "Aliyun Mirror"
            url = uri("https://maven.aliyun.com/repository/public")
        }
        maven {
            name = "Google Maven"
            url = uri("https://maven.google.com")
        }
    }
    
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "ICQ"

// Include all modules
include(":app")
include(":core")
include(":corelib")
include(":gui")
include(":gui.shared")
include(":common.shared")
include(":libomicron")
include(":unittests")

// Project structure configuration
project(":app").projectDir = file("app")
project(":core").projectDir = file("core")
project(":corelib").projectDir = file("corelib")
project(":gui").projectDir = file("gui")
project(":gui.shared").projectDir = file("gui.shared")
project(":common.shared").projectDir = file("common.shared")
project(":libomicron").projectDir = file("libomicron")
project(":unittests").projectDir = file("unittests")

// Build cache configuration
buildCache {
    local {
        isEnabled = true
        directory = File(rootDir, "build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}

// Validate project structure
gradle.beforeSettings {
    val requiredDirs = listOf(
        "app",
        "core",
        "corelib", 
        "gui",
        "gui.shared",
        "common.shared",
        "libomicron",
        "unittests"
    )
    
    requiredDirs.forEach { dirName ->
        val dir = file(dirName)
        if (!dir.exists()) {
            logger.warn("Warning: Directory '$dirName' does not exist")
        }
    }
}
