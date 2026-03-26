cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core" LANGUAGES CXX C)

cmake_policy(SET CMP0074 NEW)

if(NOT ICQ_PROJECT_ROOT)
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

# Поиск Qt6 для Android (нужен для QPixmap/Svg)
if(NOT QT_ANDROID_PATH)
    set(QT_ANDROID_PATH "${ICQ_ROOT}/Qt/android_arm64_v8a" CACHE PATH "Path to target Qt")
endif()

list(APPEND CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}")
find_package(Qt6 REQUIRED COMPONENTS Core Network Gui Svg)

# Сборка CURL
include(FetchContent)
FetchContent_Declare(
    curl
    GIT_REPOSITORY https://github.com/curl/curl.git
    GIT_TAG curl-8_4_0
)
set(BUILD_CURL_EXE OFF CACHE INTERNAL "")
set(BUILD_SHARED_LIBS OFF CACHE INTERNAL "")
FetchContent_MakeAvailable(curl)

# Определения компиляции
add_compile_definitions(
    ANDROID
    PLATFORM_ANDROID
    STRIP_CRASH_HANDLER
    CORE_USED_IN_ANDROID
    SUPPORT_EXTERNAL_CONFIG
)

# Поиск всех исходников
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/gui/*.cpp"
)

# Исключаем лишнее
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*win32.*|.*macos.*|.*apple.*|.*stdafx\\.cpp")

add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# ИСПРАВЛЕНИЕ ПУТЕЙ: Добавляем корневые папки, чтобы работали инклюды типа "cache/..." и "styles/..."
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}                      # Корень (для доступа к core/..., gui/...)
    ${ICQ_ROOT}/core                 # Путь для core_dispatcher.h и прочего
    ${ICQ_ROOT}/gui                  # Критично для поиска внутри gui без префикса gui/
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/libomicron/include
    ${curl_SOURCE_DIR}/include
)

target_link_libraries(icq_core
    Qt6::Core
    Qt6::Network
    Qt6::Gui
    Qt6::Svg
    log
    z
    android
    libcurl
)

set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

if(ANDROID)
    qt_finalize_target(icq_core)
endif()
