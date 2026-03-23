cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core" LANGUAGES CXX C)

# Устанавливаем политику для корректной обработки префиксов (важно для Qt6)
cmake_policy(SET CMP0074 NEW)

# -----------------------------------------------------------------------------
# Поиск Qt6 для Android (Production логика кросс-компиляции)
# -----------------------------------------------------------------------------

# 1. Определяем путь к Host-инструментам (Linux gcc_64)
# Мы ПРИНУДИТЕЛЬНО записываем это в CACHE, чтобы внутренние зависимости Qt увидели путь
if(NOT QT_HOST_PATH)
    get_filename_component(QT_HOST_PATH_AUTO "${CMAKE_CURRENT_SOURCE_DIR}/../../../Qt/gcc_64" ABSOLUTE)
    set(QT_HOST_PATH "${QT_HOST_PATH_AUTO}" CACHE PATH "Path to host Qt installation" FORCE)
else()
    set(QT_HOST_PATH "${QT_HOST_PATH}" CACHE PATH "Path to host Qt installation" FORCE)
endif()

# 2. Определяем путь к Android SDK (Target)
if(NOT QT_ANDROID_PATH)
    get_filename_component(QT_ANDROID_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../../../Qt/android_arm64_v8a" ABSOLUTE)
endif()

# 3. Настройка путей поиска
# CMAKE_PREFIX_PATH должен указывать на корень папки с архитектурой Android
list(APPEND CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}")

# Помогаем CMake найти конкретные конфиги модулей, если они лежат в нестандартных местах
set(Qt6_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6")
set(Qt6Core_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Core")
set(Qt6Network_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Network")

# Ищем Qt6 6.5.0 со всеми компонентами
find_package(Qt6 6.5.0 REQUIRED COMPONENTS Core Network)

message(STATUS "------------------------------------------------------------")
message(STATUS "Qt6 FOUND SUCCESS: ${Qt6_VERSION}")
message(STATUS "Using Host Qt:     ${QT_HOST_PATH}")
message(STATUS "Using Target Qt:   ${QT_ANDROID_PATH}")
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

# Оптимизация для релизных сборок
if(CMAKE_BUILD_TYPE STREQUAL "Release" OR CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
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

# Исключаем платформозависимый код других ОС
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

# Заголовки Qt6 из найденного пакета
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

# Продакшн флаги компилятора Clang (NDK)
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
# Финализация (Критически важно для Android в Qt6)
# -----------------------------------------------------------------------------

if(ANDROID)
    # Гарантируем, что JNI_OnLoad не будет вырезан оптимизатором
    target_link_options(icq_core PRIVATE "-Wl,--undefined=JNI_OnLoad")
    
    # Генерация .json файлов для развертывания (заменяет старый androiddeployqt ручной запуск)
    qt_finalize_target(icq_core)
endif()

# -----------------------------------------------------------------------------
# Итоговый лог
# -----------------------------------------------------------------------------

message(STATUS "============================================================")
message(STATUS "DZIN CORE BUILD CONFIGURATION COMPLETE")
message(STATUS "  Target ABI: ${ANDROID_ABI}")
message(STATUS "  Project:    ${PROJECT_NAME}")
message(STATUS "============================================================")
