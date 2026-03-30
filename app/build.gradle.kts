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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                val boostRoot = project.findProperty("boost.root")?.toString() 
                    ?: System.getenv("BOOST_ROOT") ?: ""
                
                val rapidjsonRoot = project.findProperty("rapidjson.root")?.toString() 
                    ?: System.getenv("RAPIDJSON_ROOT") ?: ""

                val qtAndroidPath = file("${project.rootDir}/Qt/android_arm64_v8a").absolutePath
                val qtHostPath = file("${project.rootDir}/Qt/gcc_64").absolutePath

                val root = "${project.projectDir}/.."

                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DBOOST_ROOT=$boostRoot",
                    "-DBoost_INCLUDE_DIR=$boostRoot",
                    "-DQT_ANDROID_PATH=$qtAndroidPath",
                    "-DQT_HOST_PATH=$qtHostPath",
                    "-DICQ_PROJECT_ROOT=$root",
                    "-DBUILD_TESTING=OFF" // Важно: предотвращает сборку тестов curl
                )

                cppFlags += listOf(
                    "-std=c++17",
                    "-fexceptions",
                    "-frtti",
                    "-D__linux__",
                    "-DANDROID",
                    "-DNDEBUG",
                    "-O3",
                    "-flto",
                    "-Wall",
                    "-I$root",
                    "-I$root/core",
                    "-I$root/gui", 
                    "-I$root/corelib",
                    "-I$root/common.shared",
                    "-I$root/gui.shared",
                    "-I$boostRoot",
                    "-I$rapidjsonRoot"
                ).filter { it.isNotEmpty() }

                abiFilters.addAll(listOf("arm64-v8a"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" } }
        }
        debug {
            externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Debug" } }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs { pickFirsts.add("lib/**/libc++_shared.so") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("io.github.webrtc-sdk:android:137.7151.05")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
