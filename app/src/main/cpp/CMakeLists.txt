cmake_minimum_required(VERSION 3.22.1)

# CMP0074: find_package() использует переменные <PackageName>_ROOT
cmake_policy(SET CMP0074 NEW)

project("icq_mobile_core" LANGUAGES CXX C)

# --- Настройка путей проекта ---
if(NOT ICQ_PROJECT_ROOT)
    # Если корень не задан через Gradle, вычисляем его относительно текущего файла
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

# --- Поиск Qt 6 для Android ---
# Принудительно устанавливаем способ поиска для Android
list(APPEND CMAKE_MESSAGE_INDENT "  ")
message(STATUS "Configuring Qt for Android...")

# 1. Приоритет аргументам из Gradle (через -DQT_ANDROID_PATH)
if(QT_ANDROID_PATH)
    set(Qt6_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6")
    set(CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}/lib/cmake")
    message(STATUS "Using Qt from Gradle: ${QT_ANDROID_PATH}")
# 2. Переменные окружения (для CI или локальных скриптов)
elseif(DEFINED ENV{Qt6_ANDROID_DIR})
    set(QT_ANDROID_PATH "$ENV{Qt6_ANDROID_DIR}")
    set(CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}/lib/cmake")
    message(STATUS "Using Qt from Environment: ${QT_ANDROID_PATH}")
# 3. Стандартный путь в GitHub Actions
elseif(EXISTS "/home/runner/work/ICQ/Qt/6.5.0/android_arm64_v8a")
    set(QT_ANDROID_PATH "/home/runner/work/ICQ/Qt/6.5.0/android_arm64_v8a")
    set(QT_HOST_PATH "/home/runner/work/ICQ/Qt/6.5.0/gcc_64")
    set(CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}/lib/cmake")
    message(STATUS "Using GitHub Actions fallback path")
endif()

# Поиск необходимых модулей Qt
find_package(Qt6 REQUIRED COMPONENTS Core Network)
list(POP_BACK CMAKE_MESSAGE_INDENT)

# --- Глобальные определения (Definitions) ---
add_compile_definitions(
    LINUX
    __linux__
    ANDROID
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
    _FORTIFY_SOURCE=2
    QT_CORE_LIB
    QT_NETWORK_LIB
)

# Настройки для специфических сборок
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_compile_definitions(QT_NO_DEBUG NDEBUG)
endif()

# --- Сбор исходных файлов ---
# ВАЖНО: GLOB_RECURSE удобен, но CMake не всегда видит новые файлы без переконфигурации.
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Фильтр: исключаем платформозависимый код других ОС и тесты
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*(win32|macos|apple|linux_desktop|test|stdafx\\.cpp|unittests|_mac|_ios).*")

# --- Создание целевой библиотеки ---
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Настройка путей заголовочных файлов (Includes) ---
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}                                
    ${ICQ_ROOT}/core                           
    ${ICQ_ROOT}/core/Voip/libvoip/include
    ${ICQ_ROOT}/core/Voip/libvoip/src
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/gui.shared
    ${ICQ_ROOT}/boost_1_83_0
    ${ICQ_ROOT}/rapidjson/include
)

# Обработка внешнего пути CURL из окружения
if(DEFINED ENV{CURL_INCLUDE_PATH})
    target_include_directories(icq_core PRIVATE $ENV{CURL_INCLUDE_PATH})
endif()

# Qt инклуды добавляются автоматически через линковку с таргетами Qt6::...
# Но для ясности можно оставить поиск библиотек

# --- Свойства компиляции ---
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
    # Имя библиотеки в APK будет libicq_core.so
    OUTPUT_NAME "icq_core"
)

# --- Флаги компилятора ---
target_compile_options(icq_core PRIVATE 
    -fexceptions 
    -frtti 
    -fPIC 
    -Wall 
    -Wextra
    -Wno-unused-parameter 
    -Wno-unused-variable 
    -Wno-mismatched-tags
    -fdata-sections 
    -ffunction-sections
)

# --- Линковка системных библиотек Android ---
find_library(log-lib log REQUIRED)
find_library(z-lib z REQUIRED)
find_library(android-lib android REQUIRED)

target_link_libraries(icq_core
    Qt6::Core
    Qt6::Network
    ${log-lib}
    ${z-lib}
    ${android-lib}
    # Очистка неиспользуемых секций для уменьшения размера
    -Wl,--gc-sections
)

# --- Специфические настройки для Android & Qt 6 ---
if(ANDROID)
    # 1. Предотвращаем удаление JNI_OnLoad при линковке
    target_link_options(icq_core PRIVATE "-Wl,--undefined=JNI_OnLoad")
    
    # 2. Установка API Level (обычно пробрасывается из Gradle, но подстрахуем)
    if(NOT ANDROID_NATIVE_API_LEVEL)
        set(ANDROID_NATIVE_API_LEVEL 24)
    endif()
    target_compile_definitions(icq_core PRIVATE __ANDROID_API__=${ANDROID_NATIVE_API_LEVEL})

    # 3. КРИТИЧЕСКИ ВАЖНО ДЛЯ Qt 6:
    # Эта команда генерирует deployment-settings.json, без которого Qt библиотеки не попадут в APK.
    # Должна вызываться ПОСЛЕ target_link_libraries.
    qt_finalize_target(icq_core)
endif()

# --- Межпроцедурная оптимизация (LTO/IPO) ---
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    include(CheckIPOSupported)
    check_ipo_supported(RESULT ipo_supported OUTPUT error)
    if(ipo_supported)
        set_property(TARGET icq_core PROPERTY INTERPROCEDURAL_OPTIMIZATION TRUE)
        message(STATUS "✅ LTO (IPO) is enabled")
    endif()
endif()

# --- Итоговый лог конфигурации ---
message(STATUS "---------------------------------------------------------")
message(STATUS "  Configuration Summary:")
message(STATUS "  Project:           ${PROJECT_NAME}")
message(STATUS "  ICQ_ROOT:          ${ICQ_ROOT}")
message(STATUS "  Build Type:        ${CMAKE_BUILD_TYPE}")
message(STATUS "  Qt6 Version:       ${Qt6_VERSION}")
message(STATUS "  Qt6 Path:          ${QT_ANDROID_PATH}")
message(STATUS "  Android API:       ${ANDROID_NATIVE_API_LEVEL}")
message(STATUS "  Compiler:          ${CMAKE_CXX_COMPILER_ID}")
message(STATUS "---------------------------------------------------------")
