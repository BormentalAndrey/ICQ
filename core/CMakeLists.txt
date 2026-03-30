cmake_minimum_required(VERSION 3.19)
project(core)

# --- ЗАЩИТА МАКРОСОВ ---
if(NOT COMMAND find_sources)
    include("${ICQ_ROOT}/requirements/common.cmake" OPTIONAL)
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
    endif()
endif()

set(SUBPROJECT_ROOT "${ICQ_ROOT}/core")
find_sources(SUBPROJECT_SOURCES "${SUBPROJECT_ROOT}" "cpp")
find_sources(SUBPROJECT_HEADERS "${SUBPROJECT_ROOT}" "h")

if(APPLE)
    find_sources(SUBPROJECT_MM_SOURCES "${SUBPROJECT_ROOT}" "mm")
endif()

set_source_group("sources" "${SUBPROJECT_ROOT}" ${SUBPROJECT_SOURCES} ${SUBPROJECT_MM_SOURCES} ${SUBPROJECT_HEADERS})

# Отключаем специфичные для десктопа вещи на Android
if(NOT ANDROID)
    if (STRIP_NET_CHANGE_NOTIFY)
        add_definitions(-DSTRIP_NET_CHANGE_NOTIFY)
        # ... (фильтры списков остаются прежними)
    endif()
endif()

include_directories(${SUBPROJECT_ROOT})

# Обработка PCH для разных платформ
if(MSVC)
    use_precompiled_header_msvc("stdafx.h" "${SUBPROJECT_ROOT}/stdafx.cpp" ${SUBPROJECT_SOURCES} ${COMMON_SOURCES})
elseif(APPLE)
    use_precompiled_header_mac(PCH_BIN "${SUBPROJECT_ROOT}/stdafx.h" ${SUBPROJECT_SOURCES} ${COMMON_SOURCES})
elseif(LINUX OR ANDROID) # На Android используем логику Linux для PCH или пропускаем
    use_precompiled_header_linux(PCH_BIN "${SUBPROJECT_ROOT}/stdafx.h" ${SUBPROJECT_SOURCES} ${COMMON_SOURCES})
endif()

add_library(${PROJECT_NAME} STATIC
    ${SUBPROJECT_SOURCES} ${SUBPROJECT_MM_SOURCES} ${SUBPROJECT_HEADERS}
    ${COMMON_SOURCES} ${COMMON_HEADERS}
    ${MINIZIP_SOURCES} ${MINIZIP_HEADERS}
    ${ZSTD_DICTIONARIES_HEADERS} ${ZSTD_DICTIONARIES_SOURCES}
    ${PCH_BIN})

# Пост-сборка только для Windows
if(MSVC)
    set (CMAKE_CXX_FLAGS "/EHsc /bigobj")
    # ... (все команды add_custom_command POST_BUILD для копирования DLL остаются здесь)
endif()
