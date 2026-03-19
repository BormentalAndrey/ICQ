pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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
