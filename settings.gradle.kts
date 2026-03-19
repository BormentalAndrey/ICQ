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
        // Добавлено для WebRTC
        maven {
            name = "Google Maven"
            url = uri("https://maven.google.com")
        }
        // JCenter mirror для старых библиотек
        maven {
            name = "JCenter"
            url = uri("https://jcenter.bintray.com")
        }
    }
}

rootProject.name = "ICQ"

include(":app")
include(":core")
include(":corelib")
include(":gui")
include(":gui.shared")
include(":common.shared")
include(":libomicron")
include(":unittests")

project(":app").projectDir = file("app")
project(":core").projectDir = file("core")
project(":corelib").projectDir = file("corelib")
project(":gui").projectDir = file("gui")
project(":gui.shared").projectDir = file("gui.shared")
project(":common.shared").projectDir = file("common.shared")
project(":libomicron").projectDir = file("libomicron")
project(":unittests").projectDir = file("unittests")
