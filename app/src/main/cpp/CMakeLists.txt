cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

# Установка корневой директории репозитория ICQ
set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../..")

# --- Секция компиляции и дефайнов ---
# Используем add_definitions с префиксом -D.
# Это заставит CMake передать функцию-макрос im_assert напрямую в компилятор
add_definitions(
    -DLINUX
    -D__linux__
    -DANDROID
    -DSTRIP_CRASH_HANDLER
    -DSUPPORT_EXTERNAL_CONFIG
    -DHAVE_SECURE_GETENV=1
    "-Dim_assert(x)=((void)0)"
)

# --- Поиск путей заголовков ---
include_directories(
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/core/Voip
    ${ICQ_ROOT}/core/voip
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/common.shared/config
    ${ICQ_ROOT}/gui.shared
    $ENV{BOOST_ROOT}
    $ENV{RAPIDJSON_ROOT}
)

# --- Сбор исходных файлов ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
)

# Исключаем специфичный код других платформ
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*|.*test.*|.*_mac.*|.*_ios.*")

# --- Сборка библиотеки ---
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
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

# Настройка C++ стандарта
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)
