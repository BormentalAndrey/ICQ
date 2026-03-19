// build.gradle.kts (Root project)
import java.io.FileInputStream
import java.util.Properties

// Загрузка конфигурации из gradle.properties
val gradleProperties = Properties().apply {
    val file = rootProject.file("gradle.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}

plugins {
    // Android Application plugin для сборки APK
    id("com.android.application") version "8.6.0" apply false
    
    // Android Library plugin для модулей
    id("com.android.library") version "8.6.0" apply false
    
    // Kotlin Android plugin
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    
    // Google Services plugin для Firebase
    id("com.google.gms.google-services") version "4.4.2" apply false
    
    // Kotlin Symbol Processing plugin
    id("com.google.devtools.ksp") version "1.9.23-1.0.20" apply false
    
    // Performance monitoring (опционально)
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
    
    // SafeArgs для навигации (если используется)
    id("androidx.navigation.safeargs.kotlin") version "2.7.7" apply false
    
    // Hilt для dependency injection (если используется)
    id("com.google.dagger.hilt.android") version "2.50" apply false
    
    // Kotlin serialization
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23" apply false
}

allprojects {
    repositories {
        // Приоритет: сначала локальные репозитории, затем зеркала
        mavenLocal()
        
        // Зеркала Maven Central в России для ускорения
        maven {
            name = "Mirror Maven Central 1"
            url = uri("https://maven.aliyun.com/repository/public")
            content {
                includeGroupByRegex(".*")
            }
        }
        maven {
            name = "Mirror Maven Central 2"
            url = uri("https://repo1.maven.org/maven2")
            content {
                includeGroupByRegex(".*")
            }
        }
        
        // Google Maven с зеркалом
        maven {
            name = "Google Maven"
            url = uri("https://maven.google.com")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
            }
        }
        
        // JitPack для библиотек с GitHub
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github.*")
            }
        }
        
        // Дополнительные репозитории для специфических зависимостей
        maven {
            name = "Sonatype Snapshots"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.toString()
            
            // Оптимизации компиляции
            allWarningsAsErrors = gradleProperties.getProperty("kotlin.allWarningsAsErrors", "false").toBoolean()
            
            // Дополнительные флаги компилятора Kotlin
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-Xjvm-default=all", // Совместимость с Java default methods
                "-Xopt-in=kotlin.RequiresOptIn", // Требовать opt-in для экспериментальных API
                "-Xcontext-receivers", // Включить context receivers (если нужно)
                "-Xinline-classes", // Включить inline классы
                "-Xskip-prerelease-check" // Пропустить проверки предрелизных версий
            )
            
            // Кэширование компиляции
            incremental = true
        }
    }
    
    // Конфигурация тестов для всех подпроектов
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        
        // Настройки для тестов
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
        
        // Параллельный запуск тестов
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        
        // Таймаут для тестов
        timeout = java.time.Duration.ofMinutes(10)
    }
}

tasks.register("cleanAll") {
    description = "Clean all build directories"
    group = "build"
    
    doLast {
        delete(
            rootProject.buildDir,
            *subprojects.map { it.buildDir }.toTypedArray(),
            rootProject.layout.projectDirectory.dir(".gradle"),
            rootProject.layout.projectDirectory.dir("build-native"),
            rootProject.layout.projectDirectory.dir("captures"),
            rootProject.layout.projectDirectory.dir("cxx")
        )
    }
}

tasks.register("printBuildInfo") {
    description = "Print build environment information"
    group = "help"
    
    doLast {
        println("========================================")
        println("ICQ Mobile Build Information")
        println("========================================")
        println("Gradle version: ${gradle.gradleVersion}")
        println("Java version: ${System.getProperty("java.version")}")
        println("Java home: ${System.getProperty("java.home")}")
        println("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
        println("JVM: ${System.getProperty("java.vm.name")}")
        println("Max memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB")
        println("Available processors: ${Runtime.getRuntime().availableProcessors()}")
        println("========================================")
        println("Project structure:")
        subprojects.forEach {
            println("  - ${it.name} (${it.path})")
        }
        println("========================================")
    }
}

// Конфигурация для всех проектов
allprojects {
    // Общие настройки для всех модулей
    configurations.all {
        // Стратегия разрешения конфликтов версий
        resolutionStrategy {
            // Принудительное использование определенных версий
            force(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.23",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.23",
                "org.jetbrains.kotlin:kotlin-reflect:1.9.23",
                "androidx.core:core-ktx:1.12.0",
                "androidx.appcompat:appcompat:1.6.1",
                "com.google.android.material:material:1.11.0"
            )
            
            // Кэширование динамических версий
            cacheDynamicVersionsFor(1, "hours")
            cacheChangingModulesFor(4, "hours")
            
            // Предотвращение дублирования зависимостей
            eachDependency {
                if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
                    useVersion("1.9.23")
                }
            }
        }
        
        // Исключение транзитивных зависимостей
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
        exclude(group = "commons-logging", module = "commons-logging")
    }
}

// Оптимизация для быстрой сборки
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs = options.compilerArgs + listOf(
        "-Xlint:unchecked",
        "-Xlint:deprecation",
        "-Xmaxerrs", "1000"
    )
    options.isFork = true
    options.isIncremental = true
    options.encoding = "UTF-8"
}

// Регистрация задачи для предварительной проверки
tasks.register("preBuildCheck") {
    description = "Check prerequisites before build"
    group = "verification"
    
    doLast {
        // Проверка наличия NDK
        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (androidHome != null) {
            val ndkDir = file("$androidHome/ndk")
            if (ndkDir.exists() && ndkDir.list()?.isNotEmpty() == true) {
                println("✅ NDK found: ${ndkDir.list()?.joinToString()}")
            } else {
                println("⚠️ NDK not found in $androidHome/ndk")
            }
        }
        
        // Проверка CMake
        try {
            val cmakeVersion = "cmake --version".runCommand()
            println("✅ CMake: $cmakeVersion")
        } catch (e: Exception) {
            println("⚠️ CMake not found in PATH")
        }
        
        // Проверка Java версии
        println("✅ Java version: ${System.getProperty("java.version")}")
    }
}

// Вспомогательная функция для выполнения команд
fun String.runCommand(): String {
    val process = ProcessBuilder(*split(" ").toTypedArray())
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()
    process.waitFor()
    
    return if (process.exitValue() == 0) output else error
}
