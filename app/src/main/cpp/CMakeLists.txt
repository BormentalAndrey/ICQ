cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../..")

# --- Пути зависимостей ---
set(BOOST_PATH "${ICQ_ROOT}/boost_1_83_0")
set(RAPIDJSON_PATH "${ICQ_ROOT}/rapidjson")
set(CURL_INCLUDE_DIR "${ICQ_ROOT}/external/curl/include")

# --- Поиск libcurl ---
find_library(CURL_LIB curl)
if(NOT CURL_LIB)
    message(WARNING "libcurl not found, will try to link system library")
endif()

# --- Глобальные дефайны (только простые, не function-like) ---
add_compile_definitions(
    LINUX
    __linux__
    ANDROID
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
)

# --- Поиск всех исходников ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Исключаем лишнее
list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*|.*test.*|.*stdafx\.cpp|.*unittests.*"
)

add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Настройка путей инклудов ---
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/common.shared/config
    ${ICQ_ROOT}/gui.shared
    ${ICQ_ROOT}/core/voip
    ${ICQ_ROOT}/core/Voip
    ${ICQ_ROOT}/core/connections
    ${ICQ_ROOT}/core/connections/wim
    ${ICQ_ROOT}/core/connections/wim/async_loader
    ${ICQ_ROOT}/core/tools
    ${ICQ_ROOT}/core/utils
    ${ICQ_ROOT}/core/archive
    ${ICQ_ROOT}/core/themes
    ${BOOST_PATH}
    ${RAPIDJSON_PATH}/include
    ${CURL_INCLUDE_DIR}
)

set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF
)

target_compile_options(icq_core PRIVATE -fexceptions -frtti)

# --- Линковка ---
find_library(log-lib log)
find_library(z-lib z)

target_link_libraries(icq_core
    ${log-lib}
    ${z-lib}
    ${CURL_LIB}
    atomic
)

message(STATUS "ICQ_ROOT: ${ICQ_ROOT}")
message(STATUS "CURL_LIB: ${CURL_LIB}")
