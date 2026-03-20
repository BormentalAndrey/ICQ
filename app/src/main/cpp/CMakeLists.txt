cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

# --- Настройка путей ---
set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../")

# --- Глобальные дефайны (Production набор) ---
add_compile_definitions(
    LINUX
    __linux__
    ANDROID
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
    _FORTIFY_SOURCE=2
)

# --- Поиск исходников ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Исключаем лишнее
list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*|.*test.*|.*stdafx\\.cpp|.*unittests.*|.*_mac\\.cpp|.*_ios\\.cpp|.*/gui/.*|.*/internal/.*"
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
    ${ICQ_ROOT}/core/Voip/libvoip/include
    ${ICQ_ROOT}/core/Voip/libvoip/src
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/gui.shared
    ${ICQ_ROOT}/boost_1_83_0
    ${ICQ_ROOT}/rapidjson/include
    ${CURL_INCLUDE_PATH}                       # Путь из окружения GitHub Actions
)

# --- Свойства таргета ---
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

target_compile_options(icq_core PRIVATE 
    -fexceptions -frtti -fPIC -Wall 
    -Wno-unused-parameter -Wno-unused-variable
    -fdata-sections -ffunction-sections
)

find_library(log-lib log)
find_library(z-lib z)
find_library(android-lib android)

target_link_libraries(icq_core
    ${log-lib}
    ${z-lib}
    ${android-lib}
    -Wl,--gc-sections
)

message(STATUS "Build Configuration:")
message(STATUS "  ICQ_ROOT: ${ICQ_ROOT}")
message(STATUS "  CURL_INC: ${CURL_INCLUDE_PATH}")
