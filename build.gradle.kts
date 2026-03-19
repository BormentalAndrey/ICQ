// Файл: /build.gradle.kts (Root)
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("com.android.library") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    // ИСПРАВЛЕНИЕ: Версия 1.0.20 более доступна в зеркалах и исправляет баги 1.0.19
    id("com.google.devtools.ksp") version "1.9.23-1.0.20" apply false
}
