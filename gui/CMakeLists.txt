cmake_minimum_required(VERSION 3.19)

project(${APP_PROJECT_NAME})

message(STATUS "")
message(STATUS "[CMAKE]")
message(STATUS "[CMAKE] including <gui/CMakeLists.txt>")
message(STATUS "[CMAKE]")

message(STATUS "[INFO] APP_PROJECT_NAME is ${APP_PROJECT_NAME}")

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
if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    add_definitions(-DQT_NO_DEBUG_OUTPUT)
endif()

add_definitions(-DQT_NO_CAST_FROM_ASCII)
add_definitions(-DQT_NO_CAST_TO_ASCII)
add_definitions(-DQT_NO_CAST_FROM_BYTEARRAY)
add_definitions(-DQT_STRICT_ITERATORS)
add_definitions(-DQT_NO_KEYWORDS)

add_definitions(-DRLOTTIE_BUILD)

option(LOTTIE_FRAME_CACHE "Enable lottie frame cache in player" ON)
if (LOTTIE_FRAME_CACHE)
    add_definitions(-DLOTTIE_FRAME_CACHE)
endif()

if(IM_AUTO_TESTING)
    message(STATUS "[INFO] CMAKE_INSTALL_RPATH_USE_LINK_PATH TRUE")
    SET(CMAKE_INSTALL_RPATH_USE_LINK_PATH TRUE)
endif()

message(STATUS "[INFO] ICQ_ROOT is ${ICQ_ROOT}")
if(MSVC)
    add_definitions(/FI"stdafx.h")
    set(SYSTEM_LIBRARIES d3d9 dxguid opengl32 Ws2_32 Imm32 Winmm runtimeobject strmiids secur32 glu32 psapi userenv version dwmapi shell32 rpcrt4 wtsapi32)
elseif(APPLE)
    set(ICQ_MAC_ROOT "${ICQ_ROOT}/mac/ICQ")
    message(STATUS "[INFO] ICQ_MAC_ROOT is ${ICQ_MAC_ROOT}")

    set(IM_SPARKLE "${pkg_sparkle_location}")
    message(STATUS "[INFO] IM_SPARKLE is ${IM_SPARKLE}")

    add_definitions(-include "stdafx.h")
    include_directories(${ICQ_MAC_ROOT})
    find_library(MAC_OPENGL OpenGL)
    find_library(MAC_APP_KIT AppKit)
    find_library(MAC_FOUNDATION Foundation)
    find_library(MAC_AVFOUNDATION AVFoundation)
    find_library(MAC_IOKIT IOKit)
    find_library(MAC_SYSTEM_CONFIGURATION SystemConfiguration)
    find_library(MAC_SECURITY Security)
    find_library(MAC_CORE_MEDIA CoreMedia)
    find_library(MAC_CORE_AUDIO CoreAudio)
    find_library(MAC_AUDIO_TOOLBOX AudioToolbox)
    if (USE_SYSTEM_OPENAL)
        find_library(MAC_OPEN_AL OpenAL)
    endif()
    find_library(MAC_AUDIO_UNIT AudioUnit)
    find_library(MAC_CARBON Carbon)
    find_library(MAC_QUARTZ Quartz)
    find_library(MAC_METAL Metal)
    find_library(MAC_IOSURFACE IOSurface)
    find_library(MAC_GSS GSS)
    find_library(MAC_METAL_KIT MetalKit)
    find_library(MAC_XCTEST XCTest PATHS ${ICQ_PLATFORM_SPECIFIC}/macos_xctest)
    find_library(MAC_SCRIPTING_BRIDGE ScriptingBridge)
    find_library(MAC_VIDEO_TOOLBOX VideoToolbox)
    find_library(MAC_VIDEO_DECODE_ACCELERATION VideoDecodeAcceleration)
    find_library(MAC_SPARKLE Sparkle PATHS ${IM_SPARKLE})

    include_directories(${MAC_SPARKLE})

    set(SYSTEM_LIBRARIES
        ${MAC_OPENGL}
        ${MAC_APP_KIT}
        ${MAC_FOUNDATION}
        ${MAC_AVFOUNDATION}
        ${MAC_IOKIT}
        ${MAC_SYSTEM_CONFIGURATION}
        z
        ${MAC_SECURITY}
        ${MAC_CORE_MEDIA}
        ${MAC_CORE_AUDIO}
        ${MAC_AUDIO_TOOLBOX}
        ${MAC_OPEN_AL}
        ${MAC_AUDIO_UNIT}
        ${MAC_CARBON}
        ${MAC_QUARTZ}
        ${MAC_SCRIPTING_BRIDGE}
        ${MAC_VIDEO_TOOLBOX}
        ${MAC_VIDEO_DECODE_ACCELERATION}
        ${MAC_METAL_KIT}
        ${MAC_METAL}
        ${MAC_IOSURFACE}
        ${MAC_GSS}
        ${VOIP_LIBRARIES}
        libcups.2.dylib
        iconv
        bsm)

    if(ICQ_UPDATE)
        message(STATUS "[INFO] Updates: ON [ICQ_UPDATE]")
        add_definitions(-DUPDATES)
        set(SYSTEM_LIBRARIES ${SYSTEM_LIBRARIES} ${MAC_SPARKLE})
    endif()

    if(IM_AUTO_TESTING)
        set(SYSTEM_LIBRARIES ${SYSTEM_LIBRARIES} ${MAC_XCTEST})
        set(SYSTEM_LIBRARIES ${SYSTEM_LIBRARIES} ${ICQ_QT_LIB_DIR}/libQt5Test.dylib)
        set(CMAKE_STATIC_LINKER_FLAGS "${CMAKE_STATIC_LINKER_FLAGS} -F${ICQ_FRAMEWORKS_BUNDLE_DIR} -weak_framework XCTest")
    endif()

    set(PRODUCT_MAC "${PRODUCT_SPEC_DIR}/mac")
    set(MACOSX_BUNDLE_ICON_FILE ${PRODUCT_MAC}/icons-bundle.icns)
    set_source_files_properties(${MACOSX_BUNDLE_ICON_FILE} PROPERTIES MACOSX_PACKAGE_LOCATION Resources)
    set_source_files_properties(${MACOSX_BUNDLE_ICON_FILE} PROPERTIES XCODE_EXPLICIT_FILE_TYPE "compiled")

elseif(LINUX)
    message(STATUS "[INFO] Linux: Set SYSTEM_LIBRARIES")
    set(SYSTEM_LIBRARIES
        corelib
        core
        ${CURL_LIBRARIES}
        ${OPENSSL_LIBRARIES}
        ${VOIP_LIBRARIES}
        -ldl -lstdc++fs -lgpg-error -lfontconfig -lfreetype -luuid
        -lpthread -lm -lrt -lexpat -lz -Wl,-Bstatic -Wl,-Bdynamic
        -lX11 -lX11-xcb -lxcb -lxcb-ewmh -lXrandr -lXcursor -lXext -lXdamage -lXfixes -lXcomposite)

    if(CMAKE_CXX_COMPILER_VERSION VERSION_LESS 10.2)
        execute_process(COMMAND bash "-c" "g++ -print-file-name=libstdc++.a" OUTPUT_VARIABLE LIBSTD OUTPUT_STRIP_TRAILING_WHITESPACE)
        set(SYSTEM_LIBRARIES ${SYSTEM_LIBRARIES} ${LIBSTD})
    endif()

    if(ICQ_DEBUG)
        set(SYSTEM_LIBRARIES ${SYSTEM_LIBRARIES} -ludev)
    endif()

    if(IM_AUTO_TESTING)
        add_definitions(-DQT_CORE_LIB=1 -DQT_GUI_LIB=1 -DQT_WIDGETS_LIB=1 -DQT_TESTLIB_LIB=1)
        set(CMAKE_STATIC_LINKER_FLAGS "${CMAKE_STATIC_LINKER_FLAGS} -Wl -fsanitize=address")
        set(QT_LIBRARIES ${QT_LIBRARIES} ${ICQ_QT_LIB_DIR}/libQt5Test.so)
    endif()

elseif(ANDROID)
    message(STATUS "[INFO] Android: Set SYSTEM_LIBRARIES")
    find_library(log-lib log)
    find_library(android-lib android)
    set(SYSTEM_LIBRARIES
        corelib
        core
        ${CURL_LIBRARIES}
        ${OPENSSL_LIBRARIES}
        ${VOIP_LIBRARIES}
        ${log-lib}
        ${android-lib}
        -ldl -lz -lm)
endif()

# ------------------------- release notes  -----------------------
find_package(PythonInterp 3 REQUIRED)
message(STATUS "[INFO] PYTHON_EXECUTABLE = ${PYTHON_EXECUTABLE}")
execute_process(COMMAND ${PYTHON_EXECUTABLE} buildsystem/update_release_notes.py WORKING_DIRECTORY ${ICQ_ROOT})

# --------------------------    gui      -------------------------
set(SUBPROJECT_ROOT "${ICQ_ROOT}/gui")

include(QrcFunctions.cmake)

message(STATUS "[INFO] SUBPROJECT_ROOT = ${SUBPROJECT_ROOT}")
find_sources(SUBPROJECT_SOURCES "${SUBPROJECT_ROOT}" "cpp")
if(APPLE)
    find_sources(SUBPROJECT_MM_SOURCES "${SUBPROJECT_ROOT}" "mm")
endif()
find_sources(SUBPROJECT_HEADERS "${SUBPROJECT_ROOT}" "h")

if(IM_AUTO_TESTING)
    message(STATUS "[Testing] Webconsole Testing: on")
    add_definitions(-DIM_AUTO_TESTING=1)
    if(LINUX)
        set(CMAKE_CXX_STANDARD_REQUIRED ON)
        set(CMAKE_CXX_EXTENSIONS OFF)
        find_package(Qt5Gui REQUIRED HINTS ${Qt5Tests_PREFIX_PATH})
        find_package(Qt5Widgets REQUIRED)
        find_package(Qt5Test REQUIRED HINTS ${Qt5Tests_PREFIX_PATH})
        if(NOT Qt5Test_FOUND)
            message(STATUS "[INFO] Qt5Test not found, autotests will not be built.")
            return()
        endif()
    endif()
else()
    message(STATUS "[INFO] Webconsole Testing: off")
    list(REMOVE_ITEM SUBPROJECT_HEADERS "${ICQ_ROOT}/gui/webserver/webserver.h")
    list(REMOVE_ITEM SUBPROJECT_SOURCES "${ICQ_ROOT}/gui/webserver/webserver.cpp")
endif()

if (STRIP_VOIP)
    list(FILTER SUBPROJECT_HEADERS EXCLUDE REGEX  "${ICQ_ROOT}/gui/voip/*")
    list(FILTER SUBPROJECT_MM_SOURCES EXCLUDE REGEX  "${ICQ_ROOT}/gui/voip/*")
    list(FILTER SUBPROJECT_SOURCES EXCLUDE REGEX  "${ICQ_ROOT}/gui/voip/*")
    list(REMOVE_ITEM SUBPROJECT_HEADERS "${ICQ_ROOT}/gui/main_window/settings/SettingsVoip.h")
    list(REMOVE_ITEM SUBPROJECT_SOURCES "${ICQ_ROOT}/gui/main_window/settings/SettingsVoip.cpp")
endif()

if (STRIP_AV_MEDIA)
    list(FILTER SUBPROJECT_HEADERS EXCLUDE REGEX  "${ICQ_ROOT}/gui/main_window/mplayer/*")
    list(FILTER SUBPROJECT_MM_SOURCES EXCLUDE REGEX  "${ICQ_ROOT}/gui/main_window/mplayer/*")
    list(FILTER SUBPROJECT_SOURCES EXCLUDE REGEX  "${ICQ_ROOT}/gui/main_window/mplayer/*")
    list(REMOVE_ITEM SUBPROJECT_HEADERS "${ICQ_ROOT}/gui/memory_stats/FFmpegPlayerMemMonitor.h")
    list(REMOVE_ITEM SUBPROJECT_SOURCES "${ICQ_ROOT}/gui/memory_stats/FFmpegPlayerMemMonitor.cpp")
    list(REMOVE_ITEM SUBPROJECT_HEADERS "${ICQ_ROOT}/gui/previewer/FfmpegViewerImpl.h")
    list(REMOVE_ITEM SUBPROJECT_SOURCES "${ICQ_ROOT}/gui/previewer/FfmpegViewerImpl.cpp")
    list(FILTER SUBPROJECT_HEADERS EXCLUDE REGEX  "${ICQ_ROOT}/gui/media/ptt/*")
    list(FILTER SUBPROJECT_MM_SOURCES EXCLUDE REGEX  "${ICQ_ROOT}/gui/media/ptt/*")
    list(FILTER SUBPROJECT_SOURCES EXCLUDE REGEX  "${ICQ_ROOT}/gui/media/ptt/*")
    list(FILTER SUBPROJECT_HEADERS EXCLUDE REGEX  "${ICQ_ROOT}/gui/main_window/input_widget/panels/PanelPtt*")
    list(FILTER SUBPROJECT_MM_SOURCES EXCLUDE REGEX  "${ICQ_ROOT}/gui/main_window/input_widget/panels/PanelPtt*")
    list(FILTER SUBPROJECT_SOURCES EXCLUDE REGEX  "${ICQ_ROOT}/gui/main_window/input_widget/panels/PanelPtt*")
    list(FILTER SUBPROJECT_HEADERS EXCLUDE REGEX  "${ICQ_ROOT}/gui/main_window/sounds/*")
    list(FILTER SUBPROJECT_MM_SOURCES EXCLUDE REGEX  "${ICQ_ROOT}/gui/main_window/sounds/*")
    list(FILTER SUBPROJECT_SOURCES EXCLUDE REGEX  "${ICQ_ROOT}/gui/main_window/sounds/*")
endif()

if (NOT (HAS_WEB_ENGINE))
    list(REMOVE_ITEM SUBPROJECT_HEADERS "${ICQ_ROOT}/gui/controls/WebPage.h")
    list(REMOVE_ITEM SUBPROJECT_SOURCES "${ICQ_ROOT}/gui/controls/WebPage.cpp")
    list(REMOVE_ITEM SUBPROJECT_HEADERS "${ICQ_ROOT}/gui/main_window/WebAppPage.h")
    list(REMOVE_ITEM SUBPROJECT_SOURCES "${ICQ_ROOT}/gui/main_window/WebAppPage.cpp")
    list(REMOVE_ITEM SUBPROJECT_HEADERS "${ICQ_ROOT}/gui/main_window/WebChannelProxy.h")
    list(REMOVE_ITEM SUBPROJECT_SOURCES "${ICQ_ROOT}/gui/main_window/WebChannelProxy.cpp")
    list(FILTER SUBPROJECT_HEADERS EXCLUDE REGEX "^${ICQ_ROOT}/gui/main_window/js_bridge/*")
    list(FILTER SUBPROJECT_SOURCES EXCLUDE REGEX "^${ICQ_ROOT}/gui/main_window/js_bridge/*")
endif()
if (NOT (HAS_OAUTH2))
    list(REMOVE_ITEM SUBPROJECT_HEADERS "${ICQ_ROOT}/gui/main_window/OAuth2Dialog.h")
    list(REMOVE_ITEM SUBPROJECT_SOURCES "${ICQ_ROOT}/gui/main_window/OAuth2Dialog.cpp")
endif()

if (NOT (HAS_KEYCHAIN_MANAGER))
    list(REMOVE_ITEM SUBPROJECT_HEADERS "${ICQ_ROOT}/gui/utils/KeychainManager.h")
    list(REMOVE_ITEM SUBPROJECT_SOURCES "${ICQ_ROOT}/gui/utils/KeychainManager.cpp")
endif()

set_source_group("sources" "${SUBPROJECT_ROOT}" ${SUBPROJECT_SOURCES} ${SUBPROJECT_MM_SOURCES} ${SUBPROJECT_HEADERS})

# --------------------------   common    -------------------------
if (NOT LINUX AND NOT ANDROID)
    set(COMMON_ROOT "${ICQ_ROOT}/common.shared")
else()
    set(COMMON_ROOT "${ICQ_ROOT}/common.shared/message_processing")
endif()

find_sources(COMMON_SOURCES "${COMMON_ROOT}" "cpp")
find_sources(COMMON_HEADERS "${COMMON_ROOT}" "h")

if (STRIP_CRASH_HANDLER)
    list(FILTER COMMON_HEADERS EXCLUDE REGEX  "${ICQ_ROOT}/common.shared/crash_report/*")
    list(FILTER COMMON_SOURCES EXCLUDE REGEX  "${ICQ_ROOT}/common.shared/crash_report/*")
endif()

set_source_group("sources" "${COMMON_ROOT}" ${COMMON_SOURCES} ${COMMON_HEADERS})

# -------------------------   gui.shared   -----------------------
set(GUI_SHARED_ROOT "${ICQ_ROOT}/gui.shared")

find_sources(GUI_SHARED_SOURCES "${GUI_SHARED_ROOT}" "cpp")
find_sources(GUI_SHARED_HEADERS "${GUI_SHARED_ROOT}" "h")

set_source_group("sources" "${GUI_SHARED_ROOT}" ${GUI_SHARED_SOURCES} ${GUI_SHARED_HEADERS})

# ----------------------------   mac   ---------------------------
if(APPLE)
    set(IM_ITUNES_ROOT "${pkg_additional_macos_itunes_location}")
    include_directories(${IM_ITUNES_ROOT})
    find_sources(IM_ITUNES_HEADERS "${IM_ITUNES_ROOT}" "h")

    set(IM_PROTOLIB_ROOT "${pkg_additional_macos_protolib_location}")
    include_directories(${IM_PROTOLIB_ROOT})
    find_sources(IM_PROTOLIB_SOURCES "${IM_PROTOLIB_ROOT}" "cpp")
    find_sources(IM_PROTOLIB_MM_SOURCES "${IM_PROTOLIB_ROOT}" "mm")
    find_sources(IM_PROTOLIB_HEADERS "${IM_PROTOLIB_ROOT}" "h")

    set_source_group("protolib" "${IM_PROTOLIB_ROOT}"
        ${IM_PROTOLIB_SOURCES} ${IM_PROTOLIB_MM_SOURCES} ${IM_PROTOLIB_HEADERS})

    set(IM_SSKEYCHAIN_ROOT "${pkg_additional_macos_sskeychain_location}")
    include_directories(${IM_SSKEYCHAIN_ROOT})
    find_sources(IM_SSKEYCHAIN_MM_SOURCES "${IM_SSKEYCHAIN_ROOT}" "mm")
    find_sources(IM_SSKEYCHAIN_HEADERS "${IM_SSKEYCHAIN_ROOT}" "h")

    set_source_group("SSKeychain" "${IM_SSKEYCHAIN_ROOT}"
        ${IM_SSKEYCHAIN_MM_SOURCES} ${IM_SSKEYCHAIN_HEADERS})
endif()

# --------------------------    mocs    --------------------------
generate_mocs(ICQ_MOC_FILES ${SUBPROJECT_HEADERS} ${COMMON_HEADERS} ${GUI_SHARED_HEADERS})

# -------------------------  translations  -----------------------
set(ICQ_LANGUAGES ar cs de en fr pt ru uk zh tr vi es)
set(ICQ_TRANSLATIONS_DIR "${SUBPROJECT_ROOT}/translations")
set(ICQ_LANGUAGE_FILES)
set(ICQ_QM_FILES)

foreach(lang IN ITEMS ${ICQ_LANGUAGES})
    set(lang_file "${ICQ_TRANSLATIONS_DIR}/${lang}.ts")
    set(qm_file "${ICQ_TRANSLATIONS_DIR}/${lang}.qm")
    add_custom_command(OUTPUT "${qm_file}"
        COMMAND ${IM_QT_BIN_LRELEASE} ${lang_file} -qm ${qm_file}
        DEPENDS ${lang_file})
    list(APPEND ICQ_LANGUAGE_FILES "${lang_file}")
    list(APPEND ICQ_QM_FILES "${qm_file}")
endforeach()
source_group("translations" FILES ${ICQ_LANGUAGE_FILES})
source_group("translations" FILES ${ICQ_QM_FILES})

# ----------------------------------------------------------------
set(ICQ_RESOURCES_DIR "${SUBPROJECT_ROOT}/resources")

# ----------------------  check themes json  ---------------------
set(ICQ_THEMES_META_PATH "${PRODUCT_SPEC_DIR}/app/resources/themes/meta.json")
set(ICQ_THEMES_CHECKER "${ICQ_ROOT}/buildsystem/check_json.py")
set(ICQ_THEMES_CHECKER_STAMP "${PROJECT_BINARY_DIR}/json_check.stamp")

execute_process(
        WORKING_DIRECTORY ${ICQ_ROOT}
        COMMAND ${PYTHON_EXECUTABLE} ${ICQ_THEMES_CHECKER} ${ICQ_THEMES_META_PATH}
        RESULT_VARIABLE RETURN_VALUE
        )
if (NOT RETURN_VALUE EQUAL 0)
    message(FATAL_ERROR "Themes JSON check failed, return value ${RETURN_VALUE}")
endif()

add_custom_command(
        DEPENDS ${ICQ_THEMES_META_PATH}
        WORKING_DIRECTORY ${ICQ_ROOT}
        COMMAND ${PYTHON_EXECUTABLE} ${ICQ_THEMES_CHECKER} ${ICQ_THEMES_META_PATH}
        COMMAND ${CMAKE_COMMAND} -E touch ${ICQ_THEMES_CHECKER_STAMP}
        OUTPUT ${ICQ_THEMES_CHECKER_STAMP}
        COMMENT "Build time checking themes JSON ${ICQ_THEMES_META_PATH}"
        )

add_custom_target(check_themes_meta DEPENDS ${ICQ_THEMES_CHECKER_STAMP})

# --------------------------  resources  -------------------------

file(GLOB_RECURSE ICQ_GIF_FILES "${SUBPROJECT_ROOT}/*.gif")
file(GLOB_RECURSE ICQ_ICO_FILES "${SUBPROJECT_ROOT}/*.ico")
file(GLOB_RECURSE ICQ_JPG_FILES "${SUBPROJECT_ROOT}/*.jpg")
file(GLOB_RECURSE ICQ_PNG_FILES "${SUBPROJECT_ROOT}/*.png")
file(GLOB_RECURSE ICQ_TTF_FILES "${SUBPROJECT_ROOT}/*.ttf")
file(GLOB_RECURSE ICQ_WAV_FILES "${SUBPROJECT_ROOT}/*.wav")

set_source_group("resources" ${ICQ_RESOURCES_DIR} ${ICQ_GIF_FILES} ${ICQ_ICO_FILES}
    ${ICQ_JPG_FILES} ${ICQ_PNG_FILES} ${ICQ_TTF_FILES} ${ICQ_WAV_FILES})

file(GLOB_RECURSE ICQ_QSS_FILES "${SUBPROJECT_ROOT}/*.qss")
set_source_group("qss" ${SUBPROJECT_ROOT} ${ICQ_QSS_FILES})

set(ICQ_RESOURCE_FILES ${ICQ_QSS_FILES} ${ICQ_GIF_FILES} ${ICQ_ICO_FILES}
    ${ICQ_JPG_FILES} ${ICQ_PNG_FILES} ${ICQ_TTF_FILES} ${ICQ_WAV_FILES})

if(APPLE)
    set_source_files_properties(${ICQ_GIF_FILES} PROPERTIES XCODE_EXPLICIT_FILE_TYPE "compiled" )
    set_source_files_properties(${ICQ_ICO_FILES} PROPERTIES XCODE_EXPLICIT_FILE_TYPE "compiled" )
    set_source_files_properties(${ICQ_JPG_FILES} PROPERTIES XCODE_EXPLICIT_FILE_TYPE "compiled" )
    set_source_files_properties(${ICQ_PNG_FILES} PROPERTIES XCODE_EXPLICIT_FILE_TYPE "compiled" )
    set_source_files_properties(${ICQ_TTF_FILES} PROPERTIES XCODE_EXPLICIT_FILE_TYPE "compiled" )
    set_source_files_properties(${ICQ_WAV_FILES} PROPERTIES XCODE_EXPLICIT_FILE_TYPE "compiled" )
    set_source_files_properties(${ICQ_QSS_FILES} PROPERTIES XCODE_EXPLICIT_FILE_TYPE "compiled" )
endif()

set(ICQ_RESOURCES "${SUBPROJECT_ROOT}/resource.qrc")
set(PRODUCT_RESOURCES "${PRODUCT_SPEC_DIR}/app/resources/product.qrc")
set(QML_RESOURCES "${SUBPROJECT_ROOT}/qml/resource_qml.qrc")

if(APPLE AND NOT ICQ_DEBUG)
    set(ICQ_RESOURCES_OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/qresource.cpp")
    add_custom_command(OUTPUT ${ICQ_RESOURCES_OUTPUT}
        COMMAND ${IM_QT_BIN_RCC} ${ICQ_RESOURCES} ${QML_RESOURCES} ${PRODUCT_RESOURCES} -o ${ICQ_RESOURCES_OUTPUT}
        DEPENDS ${ICQ_QM_FILES} ${ICQ_RESOURCES} ${QML_RESOURCES} ${PRODUCT_RESOURCES} ${ICQ_RESOURCE_FILES})
    set_source_group("resources" ${CMAKE_CURRENT_BINARY_DIR} ${ICQ_RESOURCES_OUTPUT})
    list(APPEND SUBPROJECT_SOURCES ${ICQ_RESOURCES_OUTPUT})
endif()
