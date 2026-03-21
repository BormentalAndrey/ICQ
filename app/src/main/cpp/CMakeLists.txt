cmake_minimum_required(VERSION 3.22.1)

# Указываем политику для корректной обработки путей (важно для новых версий CMake)
cmake_policy(SET CMP0074 NEW)

project("icq_mobile_core")

# --- Настройка путей ---
# ICQ_PROJECT_ROOT может приходить из Gradle (-DICQ_PROJECT_ROOT=...)
if(NOT ICQ_PROJECT_ROOT)
    set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../")
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

# --- Поиск Qt для Android ---
# Приоритет: Аргументы из Gradle -> Переменные окружения -> Хардкод CI
if(QT_ANDROID_PATH AND QT_HOST_PATH)
    set(Qt6_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6")
    set(CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}/lib/cmake")
    set(QT_HOST_PATH "${QT_HOST_PATH}")
elseif(DEFINED ENV{Qt6_ANDROID_DIR})
    set(QT_ANDROID_PATH "$ENV{Qt6_ANDROID_DIR}")
    set(QT_HOST_PATH "$ENV{Qt6_DIR}")
    set(Qt6_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6")
    set(CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}/lib/cmake")
else()
    # Резервный путь для GitHub Actions
    set(QT_ANDROID_PATH "/home/runner/work/ICQ/Qt/6.5.0/android_arm64_v8a")
    set(QT_HOST_PATH "/home/runner/work/ICQ/Qt/6.5.0/gcc_64")
    set(Qt6_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6")
    set(CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}/lib/cmake")
endif()

# Обязательный поиск Qt6 для Android требует наличия хостового пути для инструментов (moc, rcc)
find_package(Qt6 CONFIG REQUIRED COMPONENTS Core Network)

if(Qt6_FOUND)
    set(QT_FOUND TRUE)
    message(STATUS "✅ Qt6 found. Version: ${Qt6_VERSION}")
    message(STATUS "✅ Qt6 Path: ${Qt6_DIR}")
    message(STATUS "✅ Qt6 Host Path: ${QT_HOST_PATH}")
else()
    message(FATAL_ERROR "❌ Qt6 is REQUIRED for this project but was not found.")
endif()

# --- Глобальные дефайны ---
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
    # Отключаем отладочный вывод в релизе
    $<$<CONFIG:Release>:QT_NO_DEBUG>
    $<$<CONFIG:Release>:NDEBUG>
)

# --- Поиск исходников ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Фильтр исключений (убираем всё, что относится к другим ОС и тестам)
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*(win32|macos|apple|linux_desktop|test|stdafx\\.cpp|unittests|_mac|_ios).*")

# --- Создание библиотеки ---
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Настройка инклудов ---
# Используем PRIVATE, так как это конечная библиотека
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
    $ENV{CURL_INCLUDE_PATH}
)

# --- Свойства таргета ---
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
    # Префикс 'lib' добавляется автоматически для Android
)

# --- Опции компиляции ---
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

# --- Поиск системных библиотек Android ---
find_library(log-lib log REQUIRED)
find_library(z-lib z REQUIRED)
find_library(android-lib android REQUIRED)

# --- Линковка ---
target_link_libraries(icq_core
    Qt6::Core
    Qt6::Network
    ${log-lib}
    ${z-lib}
    ${android-lib}
    -Wl,--gc-sections
)

# --- Специфичные настройки для Android & Qt ---
if(ANDROID)
    # Гарантируем, что JNI_OnLoad не будет вырезан оптимизатором
    target_link_options(icq_core PRIVATE "-Wl,--undefined=JNI_OnLoad")
    
    # Финализация таргета Qt (генерация json-описаний зависимостей для развертывания)
    qt_finalize_target(icq_core)
    
    target_compile_definitions(icq_core PRIVATE __ANDROID_API__=24)
endif()

# --- Глобальные оптимизации (LTO) ---
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    include(CheckIPOSupported)
    check_ipo_supported(RESULT ipo_supported OUTPUT error)
    if(ipo_supported)
        set_property(TARGET icq_core PROPERTY INTERPROCEDURAL_OPTIMIZATION TRUE)
        message(STATUS "✅ LTO (IPO) is enabled for release build")
    else()
        message(STATUS "⚠️ LTO not supported: ${error}")
    endif()
endif()

# --- Вывод диагностической информации ---
message(STATUS "")
message(STATUS "---------------------------------------------------------")
message(STATUS "  Project:          ${PROJECT_NAME}")
message(STATUS "  ICQ_ROOT:         ${ICQ_ROOT}")
message(STATUS "  Build Type:       ${CMAKE_BUILD_TYPE}")
message(STATUS "  Android API:      ${ANDROID_NATIVE_API_LEVEL}")
message(STATUS "  Qt6 Path:         ${Qt6_DIR}")
message(STATUS "  LTO Supported:    ${ipo_supported}")
message(STATUS "---------------------------------------------------------")
message(STATUS "")
