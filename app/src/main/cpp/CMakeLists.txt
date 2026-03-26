cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core" LANGUAGES CXX C)

cmake_policy(SET CMP0074 NEW)

# --- Настройка корня ---
if(NOT ICQ_PROJECT_ROOT)
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

message(STATUS "[ICQ] Root directory: ${ICQ_ROOT}")

# --- Qt6 настройка (ИСПРАВЛЕННЫЙ ПУТЬ) ---
if(NOT QT_HOST_PATH)
    set(QT_HOST_PATH "${ICQ_ROOT}/Qt/gcc_64" CACHE PATH "Path to host Qt installation" FORCE)
endif()

if(NOT QT_ANDROID_PATH)
    set(QT_ANDROID_PATH "${ICQ_ROOT}/Qt/android_arm64_v8a" CACHE PATH "Path to target Qt" FORCE)  # ← Qt с большой буквы!
endif()

list(APPEND CMAKE_FIND_ROOT_PATH "${QT_ANDROID_PATH}")
list(APPEND CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}")

set(Qt6_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6" CACHE PATH "" FORCE)
set(Qt6Core_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Core" CACHE PATH "" FORCE)
set(Qt6Network_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Network" CACHE PATH "" FORCE)
set(Qt6Gui_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Gui" CACHE PATH "" FORCE)
set(Qt6Svg_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Svg" CACHE PATH "" FORCE)

find_package(Qt6 6.5.0 REQUIRED COMPONENTS Core Network Gui Svg)

message(STATUS "Qt6 FOUND: ${Qt6_VERSION}")
message(STATUS "Qt path: ${QT_ANDROID_PATH}")

# --- CURL ---
include(FetchContent)
FetchContent_Declare(
    curl
    GIT_REPOSITORY https://github.com/curl/curl.git
    GIT_TAG curl-8_4_0
)
set(BUILD_CURL_EXE OFF CACHE INTERNAL "")
set(BUILD_SHARED_LIBS OFF CACHE INTERNAL "")
set(HTTP_ONLY ON CACHE INTERNAL "")
FetchContent_MakeAvailable(curl)

# --- libomicron (если нужен) ---
if(EXISTS "${ICQ_ROOT}/libomicron/CMakeLists.txt")
    message(STATUS "Adding libomicron")
    add_subdirectory("${ICQ_ROOT}/libomicron" libomicron_build)
endif()

# --- Определения ---
add_compile_definitions(
    ANDROID
    PLATFORM_ANDROID
    __ANDROID__
    STRIP_CRASH_HANDLER
    CORE_USED_IN_ANDROID
    SUPPORT_EXTERNAL_CONFIG
    QT_NO_KEYWORDS
)

# --- Исходники (выберите нужные пути) ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/gui/*.cpp"           # или gui.shared/ - проверьте структуру!
    "${ICQ_ROOT}/corelib/*.cpp"        # если нужен
)

list(FILTER CORE_SOURCES EXCLUDE REGEX ".*win32.*|.*_win\\..*|.*macos.*|.*_mac\\..*|.*_ios\\..*|.*stdafx\\.cpp")

# --- Библиотека ---
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Пути инклудов ---
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/gui                      # ← важно для styles/ и cache/
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/libomicron/include
    ${curl_SOURCE_DIR}/include
)

target_include_directories(icq_core PRIVATE
    ${Qt6Core_INCLUDE_DIRS}
    ${Qt6Gui_INCLUDE_DIRS}
    ${Qt6Svg_INCLUDE_DIRS}
)

# --- Линковка ---
target_link_libraries(icq_core
    Qt6::Core
    Qt6::Network
    Qt6::Gui
    Qt6::Svg
    libcurl
    log
    z
    android
)

# Если есть libomicron:
# target_link_libraries(icq_core libomicron)

# --- Свойства ---
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# --- Android финализация (ВАЖНО!) ---
if(ANDROID)
    target_link_options(icq_core PRIVATE "-Wl,--undefined=JNI_OnLoad")
    qt_finalize_target(icq_core)
endif()
