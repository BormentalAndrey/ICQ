cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../..")

# --- Глобальные дефайны ---
add_compile_definitions(
    LINUX
    __linux__
    ANDROID
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
)

# --- Поиск исходников (исключаем GUI, чтобы не тянуть Qt) ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
)

list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*|.*test.*|.*stdafx\\.cpp|.*unittests.*|.*_mac\\.cpp|.*_ios\\.cpp|.*/gui/.*"
)

add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Настройка путей инклудов (ПРИОРИТЕТ CORE) ---
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    
    # ПЕРВООЧЕРЕДНЫЕ ПУТИ (Core)
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/core/Voip/libvoip/include
    ${ICQ_ROOT}/core/Voip/libvoip/src
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    
    # ВТОРОСТЕПЕННЫЕ ПУТИ (Зависимости)
    ${ICQ_ROOT}/boost_1_83_0
    ${ICQ_ROOT}/rapidjson/include
    ${ICQ_ROOT}/external/curl/include
    
    # ОСТАВЛЕННЫЕ ПУТИ (GUI в конце, чтобы не конфликтовать)
    ${ICQ_ROOT}/gui.shared
    ${ICQ_ROOT}/gui
)

# Настройки компиляции
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

target_compile_options(icq_core PRIVATE -fexceptions -frtti -fPIC -Wall -Wno-unused-parameter)

# Линковка
find_library(log-lib log)
find_library(z-lib z)
target_link_libraries(icq_core ${log-lib} ${z-lib})

message(STATUS "Build ICQ Core for Android: SUCCESS CONFIGURATION")
