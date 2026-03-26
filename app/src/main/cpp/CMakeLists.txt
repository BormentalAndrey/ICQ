cmake_minimum_required(VERSION 3.22.1)

project("icq_mobile_core" LANGUAGES CXX C)

# Устанавливаем политику для корректной обработки префиксов (важно для Qt6)
cmake_policy(SET CMP0074 NEW)

# ---
# Настройка корня проекта
# ---
if(NOT ICQ_PROJECT_ROOT)
    # Из app/src/main/cpp поднимаемся на 4 уровня вверх до корня репозитория
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

message(STATUS "[ICQ] Root directory: ${ICQ_ROOT}")

# ---
# Поиск Qt6 для Android (Production логика кросс-компиляции)
# ---

# 1. Определяем путь к Host-инструментам
if(NOT QT_HOST_PATH)
    set(QT_HOST_PATH "${ICQ_ROOT}/Qt/gcc_64" CACHE PATH "Path to host Qt installation" FORCE)
endif()

# 2. Определяем путь к Android SDK (Target)
if(NOT QT_ANDROID_PATH)
    set(QT_ANDROID_PATH "${ICQ_ROOT}/Qt/android_arm64_v8a" CACHE PATH "Path to target Qt" FORCE)
endif()

# 3. Настройка путей поиска
list(APPEND CMAKE_FIND_ROOT_PATH "${QT_ANDROID_PATH}")
list(APPEND CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}")

# Явно указываем пути к конфигам для всех необходимых модулей
set(Qt6_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6" CACHE PATH "" FORCE)
set(Qt6Core_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Core" CACHE PATH "" FORCE)
set(Qt6Network_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Network" CACHE PATH "" FORCE)
set(Qt6Gui_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Gui" CACHE PATH "" FORCE)
set(Qt6Svg_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Svg" CACHE PATH "" FORCE)
set(Qt6Widgets_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6Widgets" CACHE PATH "" FORCE)

# Ищем Qt6 с компонентами
find_package(Qt6 6.5.0 REQUIRED COMPONENTS Core Network Gui Svg)

message(STATUS "---")
message(STATUS "Qt6 FOUND SUCCESS: ${Qt6_VERSION}")
message(STATUS "Using Host Qt: ${QT_HOST_PATH}")
message(STATUS "Using Target Qt: ${QT_ANDROID_PATH}")
message(STATUS "---")

# ---
# Настройка OpenSSL для Android
# ---

# Пути к OpenSSL в проекте (если присутствует)
if(EXISTS "${ICQ_ROOT}/openssl")
    set(OPENSSL_ROOT_DIR "${ICQ_ROOT}/openssl" CACHE PATH "OpenSSL root directory" FORCE)
    set(OPENSSL_INCLUDE_DIR "${OPENSSL_ROOT_DIR}/include" CACHE PATH "OpenSSL include directory" FORCE)
    
    # Определяем ABI-специфичную библиотеку
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(OPENSSL_LIB_DIR "${OPENSSL_ROOT_DIR}/lib/arm64-v8a")
    elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
        set(OPENSSL_LIB_DIR "${OPENSSL_ROOT_DIR}/lib/armeabi-v7a")
    elseif(ANDROID_ABI STREQUAL "x86")
        set(OPENSSL_LIB_DIR "${OPENSSL_ROOT_DIR}/lib/x86")
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(OPENSSL_LIB_DIR "${OPENSSL_ROOT_DIR}/lib/x86_64")
    else()
        set(OPENSSL_LIB_DIR "${OPENSSL_ROOT_DIR}/lib")
    endif()
    
    set(OPENSSL_CRYPTO_LIBRARY "${OPENSSL_LIB_DIR}/libcrypto.a" CACHE FILEPATH "OpenSSL crypto library" FORCE)
    set(OPENSSL_SSL_LIBRARY "${OPENSSL_LIB_DIR}/libssl.a" CACHE FILEPATH "OpenSSL SSL library" FORCE)
    
    if(EXISTS "${OPENSSL_INCLUDE_DIR}/openssl/ssl.h" AND EXISTS "${OPENSSL_CRYPTO_LIBRARY}")
        message(STATUS "OpenSSL found: ${OPENSSL_ROOT_DIR}")
        message(STATUS "OpenSSL ABI: ${ANDROID_ABI}")
        set(OPENSSL_FOUND TRUE)
    else()
        message(WARNING "OpenSSL incomplete at: ${OPENSSL_ROOT_DIR}")
        set(OPENSSL_FOUND FALSE)
    endif()
else()
    message(STATUS "OpenSSL not found in project, will use system OpenSSL or disable SSL")
    set(OPENSSL_FOUND FALSE)
endif()

# ---
# Подготовка зависимостей (CURL)
# ---
include(FetchContent)

# Если OpenSSL не найден, отключаем его поиск полностью
if(NOT OPENSSL_FOUND)
    set(CMAKE_DISABLE_FIND_PACKAGE_OpenSSL TRUE CACHE BOOL "Disable OpenSSL find_package" FORCE)
    message(STATUS "OpenSSL disabled for curl build")
endif()

FetchContent_Declare(
    curl
    GIT_REPOSITORY https://github.com/curl/curl.git
    GIT_TAG curl-8_4_0
)

# Настройки сборки curl
set(BUILD_CURL_EXE OFF CACHE INTERNAL "")
set(BUILD_SHARED_LIBS OFF CACHE INTERNAL "")
set(CURL_DISABLE_TESTS ON CACHE INTERNAL "")
set(HTTP_ONLY OFF CACHE INTERNAL "")  # Включаем полную поддержку протоколов, включая HTTPS

# Настройки SSL для curl
if(OPENSSL_FOUND)
    set(CURL_USE_OPENSSL ON CACHE BOOL "Use OpenSSL for HTTPS" FORCE)
    set(CURL_USE_MBEDTLS OFF CACHE BOOL "" FORCE)
    set(CURL_USE_WOLFSSL OFF CACHE BOOL "" FORCE)
    set(CURL_USE_SCHANNEL OFF CACHE BOOL "" FORCE)
    set(CURL_USE_SECURETRANSPORT OFF CACHE BOOL "" FORCE)
    message(STATUS "Curl will use OpenSSL for HTTPS support")
else()
    set(CURL_USE_OPENSSL OFF CACHE BOOL "" FORCE)
    set(HTTP_ONLY ON CACHE INTERNAL "")  # Только HTTP если нет SSL
    message(STATUS "Curl will use HTTP only (no HTTPS)")
endif()

FetchContent_MakeAvailable(curl)

# ---
# Автогенерация config_data.h
# ---
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/config_data.h.in")
    configure_file(
        "${CMAKE_CURRENT_SOURCE_DIR}/config_data.h.in"
        "${CMAKE_CURRENT_BINARY_DIR}/config_data.h"
        @ONLY
    )
endif()

# ---
# Подключение подпроектов (libomicron)
# ---
if(EXISTS "${ICQ_ROOT}/libomicron/CMakeLists.txt")
    message(STATUS "[CORE] Adding libomicron to build tree")
    add_subdirectory("${ICQ_ROOT}/libomicron" libomicron_build)
endif()

# ---
# Глобальные определения (Production флаги)
# ---
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
    QT_GUI_LIB
    QT_SVG_LIB
    CORE_USED_IN_ANDROID
    TARGET_OS=android
    QT_NO_KEYWORDS
)

# Определения для OpenSSL
if(OPENSSL_FOUND)
    add_compile_definitions(
        HAVE_OPENSSL=1
        USE_OPENSSL=1
    )
endif()

if(CMAKE_BUILD_TYPE STREQUAL "Release" OR CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
    add_compile_definitions(QT_NO_DEBUG NDEBUG)
endif()

# ---
# Сбор исходных файлов
# ---
file(GLOB_RECURSE
    CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Исключаем платформозависимый код других ОС и лишние файлы
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*win32.*|.*macos.*|.*apple.*|.*_win\\.cpp|.*_mac\\.cpp|.*_ios\\.cpp|.*stdafx\\.cpp|.*main\\.cpp")

# ---
# Создание библиотеки
# ---
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# ---
# Пути заголовков (Критически важно для исправления ошибок компиляции)
# ---
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}                      # Для доступа core/..., gui/...
    ${ICQ_ROOT}/core                 # Для прямого доступа к файлам core
    ${ICQ_ROOT}/gui                  # ИСПРАВЛЕНИЕ: Позволяет находить styles/... и cache/... (важно для search_result.h)
    ${ICQ_ROOT}/core/Voip/libvoip/include
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/gui.shared
    ${ICQ_ROOT}/libomicron/include
    ${curl_SOURCE_DIR}/include
    ${CMAKE_CURRENT_BINARY_DIR}       # Для config_data.h
)

# Пути для OpenSSL
if(OPENSSL_FOUND)
    target_include_directories(icq_core PRIVATE
        ${OPENSSL_INCLUDE_DIR}
    )
endif()

# Интеграция инклюдов Qt6
target_include_directories(icq_core PRIVATE
    ${Qt6Core_INCLUDE_DIRS}
    ${Qt6Network_INCLUDE_DIRS}
    ${Qt6Gui_INCLUDE_DIRS}
    ${Qt6Svg_INCLUDE_DIRS}
)

# Внешние зависимости
if(EXISTS "${ICQ_ROOT}/boost_1_83_0")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/boost_1_83_0")
endif()

if(EXISTS "${ICQ_ROOT}/rapidjson/include")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/rapidjson/include")
endif()

# ---
# Свойства и Флаги компиляции
# ---
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    OUTPUT_NAME "icq_core"
    CXX_VISIBILITY_PRESET hidden     # Для продакшена: скрываем внутренние символы
    VISIBILITY_INLINES_HIDDEN ON
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

# ---
# Линковка
# ---
find_library(log-lib log REQUIRED)
find_library(z-lib z REQUIRED)
find_library(android-lib android REQUIRED)

# Формируем список библиотек для линковки
set(LINK_LIBRARIES
    libomicron
    Qt6::Core
    Qt6::Network
    Qt6::Gui
    Qt6::Svg
    ${log-lib}
    ${z-lib}
    ${android-lib}
    libcurl
    -Wl,--gc-sections                # Удаление неиспользуемых секций для оптимизации размера
)

# Добавляем OpenSSL библиотеки если найдены
if(OPENSSL_FOUND)
    target_link_libraries(icq_core
        ${OPENSSL_CRYPTO_LIBRARY}
        ${OPENSSL_SSL_LIBRARY}
    )
    message(STATUS "OpenSSL libraries linked: ${OPENSSL_CRYPTO_LIBRARY}, ${OPENSSL_SSL_LIBRARY}")
endif()

target_link_libraries(icq_core ${LINK_LIBRARIES})

# ---
# Финализация (Критически важно для Android в Qt6)
# ---
if(ANDROID)
    # Гарантируем, что JNI_OnLoad не будет вырезан оптимизатором
    target_link_options(icq_core PRIVATE "-Wl,--undefined=JNI_OnLoad")
    # Генерация метаданных для развертывания
    qt_finalize_target(icq_core)
endif()

# ---
# Итоговый лог
# ---
message(STATUS "===================================================")
message(STATUS "DZIN CORE BUILD CONFIGURATION COMPLETE")
message(STATUS "Target ABI: ${ANDROID_ABI}")
message(STATUS "Project: ${PROJECT_NAME}")
message(STATUS "Qt Modules: Core, Network, Gui, Svg")
message(STATUS "OpenSSL: ${OPENSSL_FOUND}")

if(OPENSSL_FOUND)
    message(STATUS "OpenSSL Path: ${OPENSSL_ROOT_DIR}")
    message(STATUS "OpenSSL ABI: ${ANDROID_ABI}")
endif()

message(STATUS "Curl SSL Support: ${CURL_USE_OPENSSL}")
message(STATUS "===================================================")
