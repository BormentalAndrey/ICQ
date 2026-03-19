cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../..")

# --- Исправление путей зависимостей ---
set(BOOST_PATH "${ICQ_ROOT}/boost_1_83_0")
set(RAPIDJSON_PATH "${ICQ_ROOT}/rapidjson")
# Путь к libcurl (обычно в external или нужно указать явно)
set(CURL_INCLUDE_DIR "${ICQ_ROOT}/external/curl/include") 

# --- Глобальные дефайны (исправляет im_assert и config_json) ---
add_compile_definitions(
    LINUX
    __linux__
    ANDROID
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
    "im_assert(x)=((void)0)"
)

# --- Поиск всех исходников ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
)

# Исключаем лишнее
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*|.*test.*|.*stdafx.cpp")

add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Настройка путей инклудов ---
# Важно: добавляем корень и основные папки так, чтобы относительные пути в .cpp работали
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/core/voip
    ${BOOST_PATH}
    ${RAPIDJSON_PATH}/include
    ${CURL_INCLUDE_DIR}
)

set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF
)

# Добавляем флаги для обработки исключений и RTTI (нужно для ядра ICQ)
target_compile_options(icq_core PRIVATE -fexceptions -frtti)

# --- Линковка ---
find_library(log-lib log)
find_library(z-lib z)

target_link_libraries(icq_core
    ${log-lib}
    ${z-lib}
    atomic
)
