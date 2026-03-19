cmake_minimum_required(VERSION 3.22.1)

project("icq_mobile_core")

# Путь к оригинальным исходникам ICQ
set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../..")

# Дефайны для сборки без GUI и десктопных зависимостей
add_definitions(-DLINUX)
add_definitions(-D__linux__)
add_definitions(-DSTRIP_CRASH_HANDLER)

include_directories(
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/common.shared
)

# Собираем все исходники ядра
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
)

# Исключаем специфичные для Mac/Windows сетевые интерфейсы
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*win32.*")
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*macos.*")

add_library(
    icq_core
    SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# Линкуем Android библиотеки для логов и сети
find_library(log-lib log)

target_link_libraries(
    icq_core
    ${log-lib}
    # Здесь в будущем нужно будет слинковать prebuilt-библиотеки:
    # ssl crypto curl zstd
)
