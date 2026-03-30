cmake_minimum_required(VERSION 3.19)

project(corelib)
message(STATUS "")
message(STATUS "[CMAKE]")
message(STATUS "[CMAKE] including <corelib/CMakeLists.txt>")
message(STATUS "[CMAKE]")

# ИСПРАВЛЕНИЕ: Добавление защиты, чтобы избежать двойного подключения, если макросы уже определены
if(NOT COMMAND find_sources)
    include("${ICQ_ROOT}/requirements/common.cmake" OPTIONAL)
    
    # Резервные макросы, если common.cmake не был найден
    if(NOT COMMAND find_sources)
        macro(find_sources VAR DBG_PATH EXT)
            file(GLOB_RECURSE ${VAR} "${DBG_PATH}/*.${EXT}")
        endmacro()

        macro(set_source_group GROUP_NAME ROOT_PATH)
            source_group("${GROUP_NAME}" FILES ${ARGN})
        endmacro()

        macro(use_precompiled_header_linux PCH_VAR PCH_HEADER SOURCES)
            set(${PCH_VAR} "${PCH_HEADER}")
        endmacro()

        macro(use_precompiled_header_mac PCH_VAR PCH_HEADER SOURCES)
            set(${PCH_VAR} "${PCH_HEADER}")
        endmacro()

        macro(use_precompiled_header_msvc PCH_HEADER PCH_SOURCE SOURCES)
        endmacro()
    endif()
endif()

# ---------------------------  paths  ----------------------------
set(CMAKE_EXECUTABLE_OUTPUT_DIRECTORY_DEBUG ${ICQ_BIN_DIR})
set(CMAKE_EXECUTABLE_OUTPUT_DIRECTORY_RELEASE ${ICQ_BIN_DIR})
set(CMAKE_EXECUTABLE_OUTPUT_PATH ${ICQ_BIN_DIR})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY_DEBUG ${ICQ_LIB_DIR})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE ${ICQ_LIB_DIR})
set(CMAKE_LIBRARY_OUTPUT_PATH ${ICQ_LIB_DIR})
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY_DEBUG ${ICQ_LIB_DIR})
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY_RELEASE ${ICQ_LIB_DIR})
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${ICQ_LIB_DIR})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_DEBUG ${ICQ_BIN_DIR})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE ${ICQ_BIN_DIR})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${ICQ_BIN_DIR})

# -------------------------- definitions -------------------------
add_definitions(-DCURL_STATICLIB)
add_definitions(-DCORELIB_EXPORTS)

if(MSVC)
    set(SYSTEM_LIBRARIES Ws2_32 Wldap32 psapi.lib Crypt32.lib)
    set (CMAKE_CXX_FLAGS "/EHsc")
elseif(APPLE)
    find_library(MAC_VIDEO_TOOLBOX VideoToolbox)
    find_library(MAC_CORE_VIDEO CoreVideo)
    find_library(MAC_APP_KIT AppKit)
    find_library(MAC_AUDIO_TOOLBOX AudioToolbox)
    find_library(MAC_AUDIO_UNIT AudioUnit)
    find_library(MAC_IO_KIT IOKit)
    find_library(MAC_SECURITY Security)
    find_library(MAC_SYSTEM_CONFIGURATION SystemConfiguration)
    find_library(MAC_CORE_MEDIA CoreMedia)
    find_library(MAC_OPEN_GL OpenGL)
    find_library(MAC_CORE_GRAPHICS CoreGraphics)
    find_library(MAC_CORE_AUDIO CoreAudio)
    find_library(MAC_AV_FOUNDATION AVFoundation)
    find_library(MAC_FOUNDATION Foundation)

    set(SYSTEM_LIBRARIES
        ${MAC_VIDEO_TOOLBOX}
        ${MAC_CORE_VIDEO}
        ${MAC_APP_KIT}
        ${MAC_AUDIO_TOOLBOX}
        ${MAC_AUDIO_UNIT}
        ${MAC_IO_KIT}
        ${MAC_SECURITY}
        ${MAC_SYSTEM_CONFIGURATION}
        ${MAC_CORE_MEDIA}
        ${MAC_OPEN_GL}
        ${MAC_CORE_GRAPHICS}
        ${MAC_CORE_AUDIO}
        ${MAC_AV_FOUNDATION}
        ${MAC_FOUNDATION})
elseif(ANDROID)
    find_library(log-lib log)
    find_library(android-lib android)
    set(SYSTEM_LIBRARIES ${log-lib} ${android-lib})
endif()

# --------------------------   corelib   -------------------------
if(DEFINED ICQ_ROOT)
    set(SUBPROJECT_ROOT "${ICQ_ROOT}/corelib")
else()
    set(SUBPROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}")
endif()

find_sources(SUBPROJECT_SOURCES "${SUBPROJECT_ROOT}" "cpp")
find_sources(SUBPROJECT_HEADERS "${SUBPROJECT_ROOT}" "h")

set_source_group("sources" "${SUBPROJECT_ROOT}" ${SUBPROJECT_SOURCES} ${SUBPROJECT_HEADERS})

# ----------------------------------------------------------------
include_directories(${SUBPROJECT_ROOT})

# Добавляем ветвление для Android, чтобы собирать STATIC, 
# если мы компилируем Android-версию (чтобы не было дублей).
if(ANDROID)
    add_library(${PROJECT_NAME} STATIC ${SUBPROJECT_SOURCES} ${SUBPROJECT_HEADERS})
else()
    if(MSVC)
        use_precompiled_header_msvc("stdafx.h" "${SUBPROJECT_ROOT}/stdafx.cpp" ${SUBPROJECT_SOURCES})
        if(ICQ_CORELIB_STATIC_LINKING)
            add_library(${PROJECT_NAME} STATIC ${SUBPROJECT_SOURCES} ${SUBPROJECT_HEADERS})
        else()
            source_group("sources" FILES corelib.def)
            source_group("sources" FILES corelib.rc)
            add_library(${PROJECT_NAME} SHARED ${SUBPROJECT_SOURCES} ${SUBPROJECT_HEADERS} corelib.def corelib.rc)
        endif()
    elseif(APPLE)
        use_precompiled_header_mac(PCH_BIN "${SUBPROJECT_ROOT}/stdafx.h" ${SUBPROJECT_SOURCES})
        add_library(${PROJECT_NAME} ${SUBPROJECT_SOURCES} ${SUBPROJECT_HEADERS} ${PCH_BIN})
    elseif(LINUX)
        use_precompiled_header_linux(PCH_BIN "${SUBPROJECT_ROOT}/stdafx.h" ${SUBPROJECT_SOURCES})
        add_library(${PROJECT_NAME} ${SUBPROJECT_SOURCES} ${SUBPROJECT_HEADERS} ${PCH_BIN})
    endif()
endif()

# Линковка
if(NOT ANDROID)
    if(ICQ_CORELIB_STATIC_LINKING)
        target_link_libraries(${PROJECT_NAME}
            ${SYSTEM_LIBRARIES}
            core
            libomicron
            ${Boost_LIBRARIES}
            ${OPENSSL_LIBRARIES}
            ${NGHTTP2_LIBRARIES}
            ${CURL_LIBRARIES}
            ${ZSTD_LIBRARIES}
            ${LIBEVENT_LIBRARIES})
    else()
        target_link_libraries(${PROJECT_NAME}
            ${SYSTEM_LIBRARIES}
            ${Boost_LIBRARIES}
            core
            libomicron
            ${OPENSSL_LIBRARIES}
            ${NGHTTP2_LIBRARIES}
            ${CURL_LIBRARIES}
            ${VOIP_LIBRARIES}
            ${ZLIB_LIBRARIES}
            ${ZSTD_LIBRARIES}
            ${LIBEVENT_LIBRARIES})
    endif()
endif()
