// app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core" LANGUAGES CXX C)

# CMP0074: find_package() использует переменные <PackageName>_ROOT
cmake_policy(SET CMP0074 NEW)

# --- Поиск Qt (Qt5 или Qt6) ---
# Приоритет: Qt6 -> Qt5
if(QT_ANDROID_PATH)
    # Путь из Gradle
    set(CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}/lib/cmake")
    find_package(Qt6 QUIET COMPONENTS Core Network)
    if(NOT Qt6_FOUND)
        find_package(Qt5 REQUIRED COMPONENTS Core Network)
        set(QT_VERSION_MAJOR 5)
    else()
        set(QT_VERSION_MAJOR 6)
    endif()
elseif(DEFINED ENV{Qt5_DIR})
    # Qt5 из бандла
    set(CMAKE_PREFIX_PATH $ENV{Qt5_DIR})
    find_package(Qt5 REQUIRED COMPONENTS Core Network)
    set(QT_VERSION_MAJOR 5)
elseif(DEFINED ENV{Qt6_ANDROID_DIR})
    # Qt6 из окружения
    set(CMAKE_PREFIX_PATH "$ENV{Qt6_ANDROID_DIR}/lib/cmake")
    find_package(Qt6 REQUIRED COMPONENTS Core Network)
    set(QT_VERSION_MAJOR 6)
else()
    # Автоматический поиск
    find_package(Qt6 QUIET COMPONENTS Core Network)
    if(NOT Qt6_FOUND)
        find_package(Qt5 REQUIRED COMPONENTS Core Network)
        set(QT_VERSION_MAJOR 5)
    else()
        set(QT_VERSION_MAJOR 6)
    endif()
endif()

message(STATUS "✅ Using Qt${QT_VERSION_MAJOR}: ${Qt${QT_VERSION_MAJOR}_VERSION}")

# --- Настройка путей проекта ---
if(NOT ICQ_PROJECT_ROOT)
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

# --- Глобальные определения ---
add_compile_definitions(
    ANDROID
    PLATFORM_ANDROID
    __ANDROID__
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
    _FORTIFY_SOURCE=2
)

if(${QT_VERSION_MAJOR} EQUAL 5)
    add_compile_definitions(QT_CORE_LIB QT_NETWORK_LIB)
else()
    add_compile_definitions(QT_CORE_LIB QT_NETWORK_LIB)
endif()

if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_compile_definitions(QT_NO_DEBUG NDEBUG)
endif()

# --- Сбор исходных файлов ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Фильтр: исключаем платформозависимый код Windows и macOS
list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*_win\\.cpp|.*_mac\\.cpp|.*_ios\\.cpp|.*stdafx\\.cpp"
)

# --- Создание библиотеки ---
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Пути заголовков ---
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/core/Voip/libvoip/include
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/gui.shared
)

# Boost (опционально)
if(EXISTS "${ICQ_ROOT}/boost_1_83_0")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/boost_1_83_0")
endif()

# RapidJSON (опционально)
if(EXISTS "${ICQ_ROOT}/rapidjson/include")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/rapidjson/include")
endif()

# CURL (опционально)
if(DEFINED ENV{CURL_INCLUDE_PATH})
    target_include_directories(icq_core PRIVATE $ENV{CURL_INCLUDE_PATH})
endif()

# Qt инклуды
if(${QT_VERSION_MAJOR} EQUAL 5)
    target_include_directories(icq_core PRIVATE
        ${Qt5Core_INCLUDE_DIRS}
        ${Qt5Network_INCLUDE_DIRS}
    )
else()
    target_include_directories(icq_core PRIVATE
        ${Qt6Core_INCLUDE_DIRS}
        ${Qt6Network_INCLUDE_DIRS}
    )
endif()

# --- Свойства компиляции ---
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    OUTPUT_NAME "icq_core"
)

# --- Флаги компилятора ---
target_compile_options(icq_core PRIVATE 
    -fexceptions -frtti -fPIC -Wall -Wextra
    -Wno-unused-parameter -Wno-unused-variable
    -Wno-mismatched-tags -Wno-deprecated-declarations
    -fdata-sections -ffunction-sections
)

# --- Линковка ---
find_library(log-lib log REQUIRED)
find_library(z-lib z REQUIRED)
find_library(android-lib android REQUIRED)

if(${QT_VERSION_MAJOR} EQUAL 5)
    target_link_libraries(icq_core
        Qt5::Core
        Qt5::Network
        ${log-lib}
        ${z-lib}
        ${android-lib}
        -Wl,--gc-sections
    )
else()
    target_link_libraries(icq_core
        Qt6::Core
        Qt6::Network
        ${log-lib}
        ${z-lib}
        ${android-lib}
        -Wl,--gc-sections
    )
endif()

# --- Android специфика ---
if(ANDROID)
    target_link_options(icq_core PRIVATE "-Wl,--undefined=JNI_OnLoad")
    
    if(NOT ANDROID_NATIVE_API_LEVEL)
        set(ANDROID_NATIVE_API_LEVEL 24)
    endif()
    target_compile_definitions(icq_core PRIVATE __ANDROID_API__=${ANDROID_NATIVE_API_LEVEL})
    
    # Для Qt6
    if(${QT_VERSION_MAJOR} EQUAL 6)
        qt_finalize_target(icq_core)
    endif()
endif()

# --- Итоговый лог ---
message(STATUS "")
message(STATUS "========================================")
message(STATUS "ICQ Mobile Core Build Configuration")
message(STATUS "========================================")
message(STATUS "  ICQ_ROOT:      ${ICQ_ROOT}")
message(STATUS "  Build Type:    ${CMAKE_BUILD_TYPE}")
message(STATUS "  Qt Version:    ${Qt${QT_VERSION_MAJOR}_VERSION}")
message(STATUS "  Android API:   ${ANDROID_NATIVE_API_LEVEL}")
message(STATUS "========================================")
