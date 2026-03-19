plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.icq.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.icq.mobile"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        externalNativeBuild {
            cmake {
                val boostRoot = System.getenv("BOOST_ROOT") ?: ""
                val rapidjsonRoot = System.getenv("RAPIDJSON_ROOT") ?: ""
                val root = "${project.projectDir}/.."

                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DBOOST_ROOT=$boostRoot",
                    "-DBoost_INCLUDE_DIR=$boostRoot"
                )

                cppFlags += listOf(
                    "-std=c++17",
                    "-fexceptions",
                    "-frtti",
                    "-D__linux__",
                    "-DNDEBUG",
                    "-O3",
                    "-flto",
                    // Пути поиска (Include Directories)
                    "-I$root",
                    "-I$root/core",
                    "-I$root/corelib",
                    "-I$root/common.shared",
                    "-I$root/core/Voip",
                    "-I$root/gui.shared",
                    "-I$boostRoot",
                    "-I$rapidjsonRoot"
                ).filter { it.isNotBlank() }.joinToString(" ")

                abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            pickFirsts.add("lib/**/libc++_shared.so")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("io.github.webrtc-sdk:android:137.7151.05")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
