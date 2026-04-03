cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core" LANGUAGES CXX C)
cmake_policy(SET CMP0074 NEW)

if(NOT ICQ_PROJECT_ROOT)
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

# =============================================
# Qt6
# =============================================
list(APPEND CMAKE_FIND_ROOT_PATH "${QT_ANDROID_PATH}")
list(APPEND CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}")
set(Qt6_DIR "${QT_ANDROID_PATH}/lib/cmake/Qt6" CACHE PATH "" FORCE)
find_package(Qt6 REQUIRED COMPONENTS Core Network Gui Svg)

add_compile_definitions(QT_DISABLE_DEPRECATED_BEFORE=0x050000)

# =============================================
# OpenSSL + CURL
# =============================================
set(OPENSSL_INCLUDE_DIR "${ICQ_ROOT}/openssl-android-arm64/ssl_3" CACHE PATH "" FORCE)
set(OPENSSL_CRYPTO_LIBRARY "${ICQ_ROOT}/openssl-android-arm64/arm64-v8a/libcrypto.a" CACHE FILEPATH "" FORCE)
set(OPENSSL_SSL_LIBRARY "${ICQ_ROOT}/openssl-android-arm64/arm64-v8a/libssl.a" CACHE FILEPATH "" FORCE)
set(OPENSSL_USE_STATIC_LIBS TRUE CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(curl 
    GIT_REPOSITORY https://github.com/curl/curl.git 
    GIT_TAG curl-8_4_0
)
set(CURL_USE_OPENSSL ON CACHE BOOL "" FORCE)
set(BUILD_CURL_EXE OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(CURL_DISABLE_TESTS ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(curl)

# =============================================
# Глобальные определения
# =============================================
add_compile_definitions(
    ANDROID PLATFORM_ANDROID _ANDROID_
    STRIP_CRASH_HANDLER SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
    QT_CORE_LIB QT_NETWORK_LIB QT_GUI_LIB QT_SVG_LIB
    CORE_USED_IN_ANDROID QT_NO_KEYWORDS CORELIB_EXPORTS
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

list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*_win\\.cpp|.*_mac\\.cpp|.*_ios\\.cpp"
)

# Критически важные файлы, которые могут быть пропущены GLOB_RECURSE
set(CRITICAL_SOURCES
    "${ICQ_ROOT}/gui/core_dispatcher.cpp"
    "${ICQ_ROOT}/core/connections/wim/events/fetch_event_thread_update.cpp"
    "${ICQ_ROOT}/core/archive/draft_storage.cpp"
    "${ICQ_ROOT}/core/Voip/voip_memory_reporter.cpp"
    "${ICQ_ROOT}/common.shared/message_processing/text_formatting.cpp"
    "${ICQ_ROOT}/common.shared/message_processing/message_tokenizer.cpp"
    "${ICQ_ROOT}/gui/my_info.cpp"
    "${ICQ_ROOT}/gui/types/search_result.cpp"
)

foreach(CRITICAL_SOURCE ${CRITICAL_SOURCES})
    if(EXISTS ${CRITICAL_SOURCE})
        list(APPEND CORE_SOURCES ${CRITICAL_SOURCE})
    endif()
endforeach()

# VoIP рендерер для Android
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/voip/android_video_renderer.cpp")
    list(APPEND CORE_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/voip/android_video_renderer.cpp")
endif()

add_library(icq_core SHARED jni_bridge.cpp ${CORE_SOURCES})

# =============================================
# Include directories
# =============================================
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/gui
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/gui.shared
    ${ICQ_ROOT}/core/Voip
    ${ICQ_ROOT}/core/Voip/libvoip/include
    ${ICQ_ROOT}/core/tools
    ${curl_SOURCE_DIR}/include
)

target_include_directories(icq_core SYSTEM PRIVATE
    ${Qt6Core_INCLUDE_DIRS}
    ${Qt6Gui_INCLUDE_DIRS}
    ${Qt6Network_INCLUDE_DIRS}
    ${Qt6Svg_INCLUDE_DIRS}
)

if(EXISTS "${ICQ_ROOT}/boost_1_83_0")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/boost_1_83_0")
endif()
if(EXISTS "${ICQ_ROOT}/rapidjson/include")
    target_include_directories(icq_core PRIVATE "${ICQ_ROOT}/rapidjson/include")
endif()

# Определение для voip_proxy
if(EXISTS "${ICQ_ROOT}/core/Voip/voip_proxy.h")
    target_compile_definitions(icq_core PRIVATE HAVE_VOIP_PROXY)
endif()

# =============================================
# PCH
# =============================================
if(EXISTS "${ICQ_ROOT}/core/stdafx.h")
    target_compile_definitions(icq_core PRIVATE HAVE_STDAFX_H)
    target_precompile_headers(icq_core PRIVATE "${ICQ_ROOT}/core/stdafx.h")
elseif(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/stdafx.h")
    target_compile_definitions(icq_core PRIVATE HAVE_STDAFX_H)
    target_precompile_headers(icq_core PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/stdafx.h")
endif()

# =============================================
# Свойства
# =============================================
set_target_properties(icq_core PROPERTIES 
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    CXX_VISIBILITY_PRESET hidden
    POSITION_INDEPENDENT_CODE ON
)

if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set_target_properties(icq_core PROPERTIES INTERPROCEDURAL_OPTIMIZATION TRUE)
endif()

# =============================================
# Линковка
# =============================================
find_library(log-lib log REQUIRED)
find_library(android-lib android REQUIRED)
find_library(GLESv2-lib GLESv2 REQUIRED)
find_library(EGL-lib EGL REQUIRED)

target_link_libraries(icq_core
    Qt6::Core
    Qt6::Network
    Qt6::Gui
    Qt6::Svg
    ${log-lib}
    ${android-lib}
    ${GLESv2-lib}
    ${EGL-lib}
    libcurl
    -Wl,--gc-sections
)

if(ANDROID)
    target_link_options(icq_core PRIVATE 
        "-Wl,--undefined=JNI_OnLoad"
        "-Wl,-z,max-page-size=16384"
    )
    qt_finalize_target(icq_core)
endif()

# =============================================
# Оптимизации для разных типов сборки
# =============================================
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(icq_core PRIVATE -O3 -flto -DNDEBUG)
    target_link_options(icq_core PRIVATE -flto)
else()
    target_compile_options(icq_core PRIVATE -g -O0 -fno-omit-frame-pointer)
endif()

# =============================================
# Дополнительные флаги для подавления предупреждений
# =============================================
target_compile_options(icq_core PRIVATE
    -Wno-macro-redefined
    -Wno-unused-function
    -Wno-deprecated-declarations
)
