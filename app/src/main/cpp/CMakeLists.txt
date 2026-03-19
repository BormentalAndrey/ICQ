cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../..")

# Те же дефайны, что в основном проекте
add_definitions(-DLINUX -D__linux__ -DSTRIP_CRASH_HANDLER -DSUPPORT_EXTERNAL_CONFIG)

# Расширенный список путей поиска
include_directories(
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/core/Voip        # Для доступа к voip/voip3.h (вместе с симлинком)
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/common.shared/config
    ${ICQ_ROOT}/gui.shared
    ${System.getenv("BOOST_ROOT")}
    ${System.getenv("RAPIDJSON_ROOT")}
)

# Вместо GLOB_RECURSE (который может тянуть лишнее), 
# лучше сфокусироваться на необходимых компонентах
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/connections/wim/*.cpp"
    "${ICQ_ROOT}/core/archive/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
)

# Исключаем десктопные платформы
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*")

add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(icq_core
    ${log-lib}
    ${android-lib}
    # Сюда можно будет добавить zstd/openssl, когда подложишь .a файлы
)
