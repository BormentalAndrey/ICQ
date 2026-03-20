cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

# Устанавливаем корень проекта (выходим из app/src/main/cpp в корень репозитория)
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

# --- Поиск исходников ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
)

# Исключаем десктопные платформы, тесты и stdafx (он обычно заголовочный или специфичный для MSVC)
list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*|.*test.*|.*stdafx\\.cpp|.*unittests.*|.*_mac\\.cpp|.*_ios\\.cpp|.*/gui/.*"
)

add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Настройка путей инклудов (Production Style) ---
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}                                # Позволяет делать #include "core/file.h"
    ${ICQ_ROOT}/core/Voip/libvoip/include      # Для VoipController.h
    ${ICQ_ROOT}/core/Voip/libvoip/src          # Для CallStateInternal.h
    ${ICQ_ROOT}/boost_1_83_0
    ${ICQ_ROOT}/rapidjson/include
    ${ICQ_ROOT}/external/curl/include
)

set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
)

target_compile_options(icq_core PRIVATE 
    -fexceptions 
    -frtti 
    -fPIC 
    -Wall 
    -Wno-unused-parameter
)

# Линковка системных библиотек Android
find_library(log-lib log)
find_library(z-lib z)

target_link_libraries(icq_core
    ${log-lib}
    ${z-lib}
)

# Отладочный вывод для проверки путей в CI
message(STATUS "ICQ_ROOT is set to: ${ICQ_ROOT}")
