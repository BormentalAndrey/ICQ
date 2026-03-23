cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core" LANGUAGES CXX C)

cmake_policy(SET CMP0074 NEW)

# -----------------------------------------------------------------------------
# Поиск Qt6 для Android
# -----------------------------------------------------------------------------

# Указываем путь к инструментам сборки (Host Qt), они нужны для moc, rcc и т.д.
if(NOT QT_HOST_PATH)
    set(QT_HOST_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../../../Qt/gcc_64")
endif()

# Указываем префикс для поиска Android-библиотек
if(QT_ANDROID_PATH)
    set(CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}/lib/cmake")
    # Дополнительно помогаем CMake найти конкретные модули
    list(APPEND CMAKE_PREFIX_PATH 
        "${QT_ANDROID_PATH}/lib/cmake/Qt6Core"
        "${QT_ANDROID_PATH}/lib/cmake/Qt6Network"
    )
endif()

# Ищем Qt6 6.5.0
find_package(Qt6 6.5.0 REQUIRED COMPONENTS Core Network)

message(STATUS "Using Qt6: ${Qt6_VERSION}")
message(STATUS "Qt6 Host Path: ${QT_HOST_PATH}")

# -----------------------------------------------------------------------------
# Настройка путей проекта
# -----------------------------------------------------------------------------

if(NOT ICQ_PROJECT_ROOT)
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

# -----------------------------------------------------------------------------
# Глобальные определения
# -----------------------------------------------------------------------------

add_compile_definitions(
    ANDROID
    PLATFORM_ANDROID
    __ANDROID__
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
    _FORTIFY_SOURCE=2
    QT_CORE_LIB
    QT_NETWORK_LIB
)

if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_compile_definitions(QT_NO_DEBUG NDEBUG)
endif()

# -----------------------------------------------------------------------------
# Сбор исходных файлов
# -----------------------------------------------------------------------------

file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Исключаем платформозависимый код
list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*_win\\.cpp|.*_mac\\.cpp|.*_ios\\.cpp|.*stdafx\\.cpp"
)

# -----------------------------------------------------------------------------
# Создание библиотеки
# -----------------------------------------------------------------------------

add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# -----------------------------------------------------------------------------
# Пути заголовков
# -----------------------------------------------------------------------------

target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/core/Voip/libvoip/include
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/gui.shared
)

# Подключаем заголовки Qt6
target_include_directories(icq_core PRIVATE
    ${Qt6Core_INCLUDE_DIRS}
    ${Qt6Network_INCLUDE_DIRS}
)

# Внешние зависимости (Boost, RapidJSON)
if(EXISTS "${ICQ_ROOT}/boost_1_83_0")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/boost_1_83_0")
endif()

if(EXISTS "${ICQ_ROOT}/rapidjson/include")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/rapidjson/include")
endif()

# -----------------------------------------------------------------------------
# Свойства и Флаги
# -----------------------------------------------------------------------------

set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    OUTPUT_NAME "icq_core"
)

target_compile_options(icq_core PRIVATE 
    -fexceptions -frtti -fPIC -Wall -Wextra
    -Wno-unused-parameter -Wno-unused-variable
    -Wno-mismatched-tags -Wno-deprecated-declarations
    -fdata-sections -ffunction-sections
)

# -----------------------------------------------------------------------------
# Линковка
# -----------------------------------------------------------------------------

find_library(log-lib log REQUIRED)
find_library(z-lib z REQUIRED)
find_library(android-lib android REQUIRED)

target_link_libraries(icq_core
    Qt6::Core
    Qt6::Network
    ${log-lib}
    ${z-lib}
    ${android-lib}
    -Wl,--gc-sections
)

# -----------------------------------------------------------------------------
# Финализация (критично для Android в Qt6)
# -----------------------------------------------------------------------------

if(ANDROID)
    target_link_options(icq_core PRIVATE "-Wl,--undefined=JNI_OnLoad")
    
    if(NOT ANDROID_NATIVE_API_LEVEL)
        set(ANDROID_NATIVE_API_LEVEL 24)
    endif()
    
    # Эта команда генерирует нужные json-файлы для развертывания
    qt_finalize_target(icq_core)
endif()

# -----------------------------------------------------------------------------
# Логирование конфигурации
# -----------------------------------------------------------------------------

message(STATUS "========================================")
message(STATUS "ICQ/DZIN CORE BUILD CONFIG")
message(STATUS "  Qt6 Version: ${Qt6_VERSION}")
message(STATUS "  Target ABI:  ${ANDROID_ABI}")
message(STATUS "  Host Qt:     ${QT_HOST_PATH}")
message(STATUS "========================================")
