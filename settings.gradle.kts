pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
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
