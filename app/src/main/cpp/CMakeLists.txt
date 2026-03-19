cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

# --- Настройка путей ---
# Установка корневой директории репозитория ICQ
set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../..")

# Приоритет поиска: 1. Переменные окружения, 2. Локальные папки в репозитории
set(BOOST_PATH $ENV{BOOST_ROOT})
set(RAPIDJSON_PATH $ENV{RAPIDJSON_ROOT})

if(NOT BOOST_PATH)
    if(EXISTS "${ICQ_ROOT}/boost_1_83_0")
        set(BOOST_PATH "${ICQ_ROOT}/boost_1_83_0")
    endif()
endif()

if(NOT RAPIDJSON_PATH)
    if(EXISTS "${ICQ_ROOT}/rapidjson/include")
        set(RAPIDJSON_PATH "${ICQ_ROOT}/rapidjson")
    endif()
endif()

# --- Сбор исходных файлов ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
)

# Исключаем специфичный код других платформ и тесты
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*|.*test.*|.*_mac.*|.*_ios.*|.*stdafx.cpp")

# --- Сборка библиотеки ---
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Настройка компиляции ---
# Использование target_compile_definitions — самый надежный способ передать 
# сложные макросы в Ninja, так как CMake берет на себя экранирование скобок для shell.
target_compile_definitions(icq_core PRIVATE
    LINUX
    __linux__
    ANDROID
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
    "im_assert(x)=((void)0)"
)

# Настройка путей заголовков
target_include_directories(icq_core PRIVATE
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/core/Voip
    ${ICQ_ROOT}/core/voip
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/common.shared/config
    ${ICQ_ROOT}/gui.shared
    ${BOOST_PATH}
    ${RAPIDJSON_PATH}
    ${RAPIDJSON_PATH}/include
)

# Настройка C++ стандарта и параметров компилятора
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF
    POSITION_INDEPENDENT_CODE ON
)

# Дополнительные флаги для стабильности билда
target_compile_options(icq_core PRIVATE
    -fexceptions
    -frtti
    -Wno-deprecated-declarations
)

# --- Линковка зависимостей ---
find_library(log-lib log)
find_library(android-lib android)
find_library(z-lib z)

target_link_libraries(icq_core
    ${log-lib}
    ${android-lib}
    ${z-lib}
    atomic
)

# --- Отладочный вывод ---
message(STATUS "-----------------------------------------")
message(STATUS "  ICQ_ROOT:       ${ICQ_ROOT}")
message(STATUS "  BOOST_PATH:     ${BOOST_PATH}")
message(STATUS "  RAPIDJSON_PATH: ${RAPIDJSON_PATH}")
message(STATUS "-----------------------------------------")
