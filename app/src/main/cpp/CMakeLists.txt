cmake_minimum_required(VERSION 3.22.1)

project("icq_mobile_core" LANGUAGES CXX C)

cmake_policy(SET CMP0074 NEW)

# =============================================
# Пути проекта
# =============================================
if(NOT ICQ_PROJECT_ROOT)
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

# =============================================
# Настройка Qt6
# =============================================
list(APPEND CMAKE_FIND_ROOT_PATH "${QT_ANDROID_PATH}")
list(APPEND CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}")
set(QT_HOST_PATH "${QT_HOST_PATH}" CACHE PATH "Host Qt tools path" FORCE)

set(Qt6_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6" CACHE PATH "" FORCE)
find_package(Qt6 REQUIRED COMPONENTS Core Network Gui Svg)

# =============================================
# OPENSSL & CURL
# =============================================
set(OPENSSL_INCLUDE_DIR "${ICQ_ROOT}/openssl-android-arm64/ssl_3" CACHE PATH "" FORCE)
set(OPENSSL_CRYPTO_LIBRARY "${ICQ_ROOT}/openssl-android-arm64/arm64-v8a/libcrypto.a" CACHE FILEPATH "" FORCE)
set(OPENSSL_SSL_LIBRARY "${ICQ_ROOT}/openssl-android-arm64/arm64-v8a/libssl.a" CACHE FILEPATH "" FORCE)
set(OPENSSL_USE_STATIC_LIBS TRUE CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(curl GIT_REPOSITORY https://github.com/curl/curl.git GIT_TAG curl-8_4_0)
set(CURL_USE_OPENSSL ON CACHE BOOL "" FORCE)
set(BUILD_CURL_EXE OFF CACHE INTERNAL "")
set(BUILD_SHARED_LIBS OFF CACHE INTERNAL "")
FetchContent_MakeAvailable(curl)

# =============================================
# Глобальные настройки
# =============================================
add_compile_definitions(
    ANDROID PLATFORM_ANDROID _ANDROID_
    STRIP_CRASH_HANDLER SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1 QT_CORE_LIB QT_NETWORK_LIB
    QT_GUI_LIB QT_SVG_LIB CORE_USED_IN_ANDROID
    QT_NO_KEYWORDS
)

# =============================================
# Исходники
# =============================================
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Исключаем специфичные для платформ файлы
list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*_win\\.cpp|.*_mac\\.cpp|.*_ios\\.cpp|.*stdafx\\.cpp|.*main\\.cpp"
)

add_library(icq_core SHARED jni_bridge.cpp ${CORE_SOURCES})

# =============================================
# Include Directories 
# (ВАЖНО: Должны быть объявлены ДО инъекции PCH)
# =============================================
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/gui
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/gui.shared
    ${ICQ_ROOT}/core/Voip/libvoip/include
    ${curl_SOURCE_DIR}/include
)

# Явное подключение Qt инклудов для всех файлов
target_include_directories(icq_core SYSTEM PRIVATE
    ${Qt6Core_INCLUDE_DIRS}
    ${Qt6Gui_INCLUDE_DIRS}
    ${Qt6Network_INCLUDE_DIRS}
    ${Qt6Svg_INCLUDE_DIRS}
)

# Boost & RapidJSON
if(EXISTS "${ICQ_ROOT}/boost_1_83_0")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/boost_1_83_0")
endif()
if(EXISTS "${ICQ_ROOT}/rapidjson/include")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/rapidjson/include")
endif()

# =============================================
# ИСПРАВЛЕНИЕ: Инъекция Precompiled Header (PCH)
# =============================================
# Используем современную команду CMake (target_precompile_headers).
# Это безопасно внедряет stdafx.h, не вызывая конфликтов и учитывая 
# вышеописанные пути Include Directories для rapidjson и boost.
if(EXISTS "${ICQ_ROOT}/core/stdafx.h")
    target_compile_definitions(icq_core PRIVATE -DHAVE_STDAFX_H)
    target_precompile_headers(icq_core PRIVATE "${ICQ_ROOT}/core/stdafx.h")
    message(STATUS "[DZIN_BUILD] Используется главный PCH из Core: ${ICQ_ROOT}/core/stdafx.h")
elseif(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/stdafx.h")
    target_compile_definitions(icq_core PRIVATE -DHAVE_STDAFX_H)
    target_precompile_headers(icq_core PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/stdafx.h")
    message(STATUS "[DZIN_BUILD] Используется локальный PCH: ${CMAKE_CURRENT_SOURCE_DIR}/stdafx.h")
endif()

# =============================================
# Линковка
# =============================================
set_target_properties(icq_core PROPERTIES 
    CXX_STANDARD 17 
    CXX_VISIBILITY_PRESET hidden
)

find_library(log-lib log REQUIRED)
find_library(android-lib android REQUIRED)

target_link_libraries(icq_core
    Qt6::Core Qt6::Network Qt6::Gui Qt6::Svg
    ${log-lib} ${android-lib} libcurl
    -Wl,--gc-sections
)

if(ANDROID)
    target_link_options(icq_core PRIVATE "-Wl,--undefined=JNI_OnLoad")
    qt_finalize_target(icq_core)
endif()
