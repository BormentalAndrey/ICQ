plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.icq.corelib"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34

        // ЗАКОММЕНТИРОВАНО: 
        // Исходники corelib собираются в главном CMakeLists.txt (внутри core), 
        // поэтому отдельный нативный билд здесь не нужен и вызывает ошибку CXX1400.
        /*
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-fexceptions", "-frtti", "-D__linux__", "-DANDROID")
                abiFilters.add("arm64-v8a")
            }
        }
        */
    }

    // ЗАКОММЕНТИРОВАНО (Решает ошибку отсутствующего файла)
    /*
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    */

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":common.shared"))
    implementation("androidx.core:core-ktx:1.12.0")
}
