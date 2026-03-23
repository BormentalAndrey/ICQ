# app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core" LANGUAGES CXX C)

# CMP0074: find_package() использует переменные <PackageName>_ROOT
cmake_policy(SET CMP0074 NEW)

# -----------------------------------------------------------------------------
# Поиск Qt (Принудительно Qt 6.5.0)
# -----------------------------------------------------------------------------

# Пытаемся найти Qt6 версии 6.5.0
# Сначала проверяем QT_ANDROID_PATH (обычно передается из Gradle)
if(QT_ANDROID_PATH)
    set(CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}/lib/cmake")
endif()

# Если задана переменная окружения для Qt6 Android
if(DEFINED ENV{Qt6_ANDROID_DIR})
    list(APPEND CMAKE_PREFIX_PATH "$ENV{Qt6_ANDROID_DIR}/lib/cmake")
endif()

# Ищем именно Qt 6.5.0. Если не найдено, CMake выдаст ошибку конфигурации.
find_package(Qt6 6.5.0 REQUIRED COMPONENTS Core Network)
set(QT_VERSION_MAJOR 6)

message(STATUS "Using Qt${QT_VERSION_MAJOR}: ${Qt6_VERSION}")

# -----------------------------------------------------------------------------
# Настройка путей проекта
# -----------------------------------------------------------------------------

if(NOT ICQ_PROJECT_ROOT)
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

# -----------------------------------------------------------------------------
# Глобальные определения
# -----------------------------------------------------------------------------

add_compile_definitions(
    ANDROID
    PLATFORM_ANDROID
    __ANDROID__
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
    _FORTIFY_SOURCE=2
    QT_CORE_LIB 
    QT_NETWORK_LIB
)

if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_compile_definitions(QT_NO_DEBUG NDEBUG)
endif()

# -----------------------------------------------------------------------------
# Сбор исходных файлов
# -----------------------------------------------------------------------------

file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Исключаем платформозависимый код
list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*_win\\.cpp|.*_mac\\.cpp|.*_ios\\.cpp|.*stdafx\\.cpp"
)

# -----------------------------------------------------------------------------
# Создание библиотеки
# -----------------------------------------------------------------------------

add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# -----------------------------------------------------------------------------
# Пути заголовков
# -----------------------------------------------------------------------------

target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/core/Voip/libvoip/include
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/gui.shared
)

if(EXISTS "${ICQ_ROOT}/boost_1_83_0")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/boost_1_83_0")
endif()

if(EXISTS "${ICQ_ROOT}/rapidjson/include")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/rapidjson/include")
endif()

if(DEFINED ENV{CURL_INCLUDE_PATH})
    target_include_directories(icq_core PRIVATE $ENV{CURL_INCLUDE_PATH})
endif()

# Заголовки Qt6
target_include_directories(icq_core PRIVATE
    ${Qt6Core_INCLUDE_DIRS}
    ${Qt6Network_INCLUDE_DIRS}
)

# -----------------------------------------------------------------------------
# Свойства компиляции
# -----------------------------------------------------------------------------

set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    OUTPUT_NAME "icq_core"
)

# -----------------------------------------------------------------------------
# Флаги компилятора
# -----------------------------------------------------------------------------

target_compile_options(icq_core PRIVATE 
    -fexceptions -frtti -fPIC -Wall -Wextra
    -Wno-unused-parameter -Wno-unused-variable
    -Wno-mismatched-tags -Wno-deprecated-declarations
    -fdata-sections -ffunction-sections
)

# -----------------------------------------------------------------------------
# Линковка
# -----------------------------------------------------------------------------

find_library(log-lib log REQUIRED)
find_library(z-lib z REQUIRED)
find_library(android-lib android REQUIRED)

target_link_libraries(icq_core
    Qt6::Core
    Qt6::Network
    ${log-lib}
    ${z-lib}
    ${android-lib}
    -Wl,--gc-sections
)

# -----------------------------------------------------------------------------
# Android специфика
# -----------------------------------------------------------------------------

if(ANDROID)
    # Гарантируем, что JNI_OnLoad не будет вырезана линковщиком
    target_link_options(icq_core PRIVATE "-Wl,--undefined=JNI_OnLoad")
    
    if(NOT ANDROID_NATIVE_API_LEVEL)
        set(ANDROID_NATIVE_API_LEVEL 24)
    endif()
    target_compile_definitions(icq_core PRIVATE __ANDROID_API__=${ANDROID_NATIVE_API_LEVEL})
    
    # Обязательная команда для Qt6 при сборке под Android
    qt_finalize_target(icq_core)
endif()

# -----------------------------------------------------------------------------
# Итоговый лог
# -----------------------------------------------------------------------------

message(STATUS "")
message(STATUS "========================================")
message(STATUS "ICQ/DZIN Mobile Core Build Configuration")
message(STATUS "========================================")
message(STATUS "  ICQ_ROOT:      ${ICQ_ROOT}")
message(STATUS "  Build Type:    ${CMAKE_BUILD_TYPE}")
message(STATUS "  Qt Version:    ${Qt6_VERSION}")
message(STATUS "  Android API:   ${ANDROID_NATIVE_API_LEVEL}")
message(STATUS "========================================")
