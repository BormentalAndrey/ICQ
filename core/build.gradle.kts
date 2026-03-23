plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.icq.core"
    compileSdk = 34
    
    // Указываем конкретную версию NDK из ваших логов для стабильности CI
    ndkVersion = "26.1.10909125"

    defaultConfig {
        minSdk = 24
        targetSdk = 34

        externalNativeBuild {
            cmake {
                // Основные флаги компиляции
                cppFlags += "-std=c++17 -fexceptions -frtti -D__linux__ -DANDROID"
                
                // Передаем аргументы для корректного поиска Qt, если пути заданы в окружении
                arguments += "-DANDROID_STL=c++_shared"
                
                abiFilters.add("arm64-v8a")
            }
        }
        
        // Настройки для ProGuard в библиотеке
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            // ИСПРАВЛЕНИЕ: указываем путь к реальному файлу в папке App
            // Так как мы находимся в папке 'core', поднимаемся на уровень выше
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
    // Внутренние модули проекта
    implementation(project(":corelib"))
    implementation(project(":common.shared"))
    
    // Основные зависимости
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
