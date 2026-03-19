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
                // Production-флаги для C++ ядра
                cppFlags += listOf(
                    "-std=c++17", 
                    "-fexceptions", 
                    "-frtti", 
                    "-D__linux__", 
                    "-DNDEBUG",
                    "-O3", // Максимальная оптимизация
                    "-flto" // Link-Time Optimization
                ).joinToString(" ")
                
                // Оставляем только актуальные архитектуры для уменьшения размера APK
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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Разрешаем конфликты, если WebRTC и Core используют разные версии libc++
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Библиотека WebRTC для отрисовки видеокадров и P2P
    implementation("org.webrtc:google-webrtc:1.0.32006")
}
