plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.icq.core"
    compileSdk = 34
    
    ndkVersion = "26.1.10909125"

    defaultConfig {
        minSdk = 24
        targetSdk = 34

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-fexceptions", "-frtti", "-D__linux__", "-DANDROID")
                
                // Находим пути к Qt относительно корня репозитория (универсально для GitHub Actions и ПК)
                val qtAndroidPath = file("${project.rootDir}/Qt/android_arm64_v8a").absolutePath
                val qtHostPath = file("${project.rootDir}/Qt/gcc_64").absolutePath
                
                // Передаем в CMake ОБА пути для успешной кросс-компиляции Qt6
                arguments(
                    "-DQT_ANDROID_PATH=$qtAndroidPath",
                    "-DQT_HOST_PATH=$qtHostPath",
                    "-DANDROID_STL=c++_shared"
                )
                
                abiFilters.add("arm64-v8a")
            }
        }
        
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("../app/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":corelib"))
    implementation(project(":common.shared"))
    
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
