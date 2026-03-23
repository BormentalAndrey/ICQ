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
                cppFlags += "-std=c++17 -fexceptions -frtti -D__linux__ -DANDROID"
                
                // Находим путь к Qt внутри вашего репозитория
                val qtPath = file("${project.rootDir}/Qt/android_arm64_v8a").absolutePath
                
                // Передаем этот путь в CMake
                arguments += "-DQT_ANDROID_PATH=$qtPath"
                arguments += "-DANDROID_STL=c++_shared"
                
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
