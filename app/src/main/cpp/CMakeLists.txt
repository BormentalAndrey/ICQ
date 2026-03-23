cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core" LANGUAGES CXX C)

# Устанавливаем политику для обработки путей через переменные окружения
cmake_policy(SET CMP0074 NEW)

# -----------------------------------------------------------------------------
# Поиск Qt6 для Android (Исправленная логика)
# -----------------------------------------------------------------------------

# 1. Определяем базовый путь к SDK (если не передан из Gradle)
if(NOT QT_ANDROID_PATH)
    get_filename_component(QT_ANDROID_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../../../Qt/android_arm64_v8a" ABSOLUTE)
endif()

# 2. Указываем путь к Host-инструментам (Linux gcc_64) - обязательно для Qt6
if(NOT QT_HOST_PATH)
    get_filename_component(QT_HOST_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../../../Qt/gcc_64" ABSOLUTE)
endif()

# 3. Настройка путей поиска для CMake
# Мы добавляем как корень, так и специфические пути к модулям для надежности
list(APPEND CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}")
set(Qt6_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6")
set(Qt6Core_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Core")
set(Qt6Network_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Network")

# Поиск пакетов
find_package(Qt6 6.5.0 REQUIRED COMPONENTS Core Network)

message(STATUS "------------------------------------------------------------")
message(STATUS "Qt6 FOUND: ${Qt6_VERSION}")
message(STATUS "Qt6 Path:  ${QT_ANDROID_PATH}")
message(STATUS "Host Path: ${QT_HOST_PATH}")
message(STATUS "------------------------------------------------------------")

# -----------------------------------------------------------------------------
# Настройка путей проекта
# -----------------------------------------------------------------------------

if(NOT ICQ_PROJECT_ROOT)
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

# -----------------------------------------------------------------------------
# Глобальные определения (Production флаги)
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
    CORE_USED_IN_ANDROID
)

if(CMAKE_BUILD_TYPE STREQUAL "Release" OR CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
    add_compile_definitions(QT_NO_DEBUG NDEBUG)
endif()

# -----------------------------------------------------------------------------
# Сбор исходных файлов
# -----------------------------------------------------------------------------

# Рекурсивный сбор всех исходников ядра
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Исключаем специфичные для других ОС файлы, чтобы избежать конфликтов линковки
list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*_win\\.cpp|.*_mac\\.cpp|.*_ios\\.cpp|.*stdafx\\.cpp|.*main\\.cpp"
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

# Подключаем заголовки найденного Qt6
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
# Свойства и Флаги компиляции
# -----------------------------------------------------------------------------

set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    OUTPUT_NAME "icq_core"
)

target_compile_options(icq_core PRIVATE 
    -fexceptions 
    -frtti 
    -fPIC 
    -Wall 
    -Wextra
    -Wno-unused-parameter 
    -Wno-unused-variable
    -Wno-mismatched-tags 
    -Wno-deprecated-declarations
    -fdata-sections 
    -ffunction-sections
)

# -----------------------------------------------------------------------------
# Линковка
# -----------------------------------------------------------------------------

# Поиск системных библиотек Android
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
# Финализация (Критически важно для деплоя Qt6 на Android)
# -----------------------------------------------------------------------------

if(ANDROID)
    # Гарантируем видимость JNI_OnLoad для системы
    target_link_options(icq_core PRIVATE "-Wl,--undefined=JNI_OnLoad")
    
    if(NOT ANDROID_NATIVE_API_LEVEL)
        set(ANDROID_NATIVE_API_LEVEL 24)
    endif()
    
    # Генерация .json описания для androiddeployqt
    qt_finalize_target(icq_core)
endif()

# -----------------------------------------------------------------------------
# Итоговый отчет в лог сборки
# -----------------------------------------------------------------------------

message(STATUS "============================================================")
message(STATUS "DZIN (ICQ) MOBILE CORE BUILD CONFIGURATION")
message(STATUS "  Project Root: ${ICQ_ROOT}")
message(STATUS "  Target ABI:   ${ANDROID_ABI}")
message(STATUS "  Build Type:   ${CMAKE_BUILD_TYPE}")
message(STATUS "  Output Name:  libicq_core.so")
message(STATUS "============================================================")
