cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

# Корневая директория проекта
set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../..")

# --- Пути зависимостей ---
set(BOOST_PATH "${ICQ_ROOT}/boost_1_83_0")
set(RAPIDJSON_PATH "${ICQ_ROOT}/rapidjson")
set(CURL_INCLUDE_DIR "${ICQ_ROOT}/external/curl/include")

# --- Поиск libcurl в разных местах ---
find_library(CURL_LIB NAMES curl libcurl PATHS
    /usr/lib
    /usr/local/lib
    /usr/lib/x86_64-linux-gnu
    ${ANDROID_NDK}/sysroot/usr/lib
)

if(NOT CURL_LIB)
    message(WARNING "libcurl not found, network features may be limited")
    set(CURL_LIB "")
endif()

# --- Глобальные дефайны ---
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
    ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*|.*test.*|.*stdafx\\.cpp|.*unittests.*|.*_mac\\.cpp|.*_ios\\.cpp"
)

add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Настройка путей инклудов ---
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}
    
    # Core директории
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/core/voip
    ${ICQ_ROOT}/core/Voip
    ${ICQ_ROOT}/core/archive
    ${ICQ_ROOT}/core/connections
    ${ICQ_ROOT}/core/connections/wim
    ${ICQ_ROOT}/core/connections/wim/async_loader
    ${ICQ_ROOT}/core/curl_tools
    ${ICQ_ROOT}/core/disk_cache
    ${ICQ_ROOT}/core/friendly
    ${ICQ_ROOT}/core/log
    ${ICQ_ROOT}/core/network
    ${ICQ_ROOT}/core/profiling
    ${ICQ_ROOT}/core/smartreply
    ${ICQ_ROOT}/core/stats
    ${ICQ_ROOT}/core/stats/im
    ${ICQ_ROOT}/core/stickers
    ${ICQ_ROOT}/core/subscriptions
    ${ICQ_ROOT}/core/tasks
    ${ICQ_ROOT}/core/themes
    ${ICQ_ROOT}/core/tools
    ${ICQ_ROOT}/core/updater
    ${ICQ_ROOT}/core/zstd_wrap
    
    # Corelib
    ${ICQ_ROOT}/corelib
    
    # Common.shared
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/common.shared/antivirus
    ${ICQ_ROOT}/common.shared/config
    ${ICQ_ROOT}/common.shared/crash_report
    ${ICQ_ROOT}/common.shared/message_processing
    ${ICQ_ROOT}/common.shared/smartreply
    ${ICQ_ROOT}/common.shared/tasks
    ${ICQ_ROOT}/common.shared/threads
    ${ICQ_ROOT}/common.shared/uri_matcher
    
    # Gui.shared
    ${ICQ_ROOT}/gui.shared
    ${ICQ_ROOT}/gui.shared/spellcheck
    
    # Внешние зависимости
    ${BOOST_PATH}
    ${RAPIDJSON_PATH}/include
    ${CURL_INCLUDE_DIR}
)

# --- Добавляем пути к NDK для системных библиотек ---
target_include_directories(icq_core PRIVATE
    ${ANDROID_NDK}/sysroot/usr/include
    ${ANDROID_NDK}/sysroot/usr/include/${ANDROID_ABI}
)

set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF
    POSITION_INDEPENDENT_CODE ON
)

target_compile_options(icq_core PRIVATE
    -fexceptions
    -frtti
    -fPIC
    -Wall
    -Wextra
    -Wno-unused-parameter
    -Wno-missing-field-initializers
)

# --- Линковка системных библиотек (без atomic) ---
find_library(log-lib log)
find_library(z-lib z)

target_link_libraries(icq_core
    ${log-lib}
    ${z-lib}
)

# Условная линковка curl
if(CURL_LIB)
    target_link_libraries(icq_core ${CURL_LIB})
    target_compile_definitions(icq_core PRIVATE HAVE_CURL)
else()
    target_compile_definitions(icq_core PRIVATE NO_CURL)
endif()

# --- Вывод отладочной информации ---
message(STATUS "================== ICQ Core Build Configuration ==================")
message(STATUS "ICQ_ROOT: ${ICQ_ROOT}")
message(STATUS "BOOST_PATH: ${BOOST_PATH}")
message(STATUS "RAPIDJSON_PATH: ${RAPIDJSON_PATH}")
message(STATUS "CURL_LIB: ${CURL_LIB}")
message(STATUS "CURL_INCLUDE_DIR: ${CURL_INCLUDE_DIR}")
message(STATUS "ANDROID_ABI: ${ANDROID_ABI}")
message(STATUS "ANDROID_NDK: ${ANDROID_NDK}")
message(STATUS "CMAKE_CXX_COMPILER: ${CMAKE_CXX_COMPILER}")
message(STATUS "==================================================================")
