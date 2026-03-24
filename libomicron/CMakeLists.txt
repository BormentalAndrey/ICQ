cmake_minimum_required(VERSION 3.19)

# ---------------------------  paths  ----------------------------
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY_DEBUG ${ICQ_LIB_DIR})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE ${ICQ_LIB_DIR})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${ICQ_LIB_DIR})
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY_DEBUG ${ICQ_LIB_DIR})
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY_RELEASE ${ICQ_LIB_DIR})
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${ICQ_LIB_DIR})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_DEBUG ${ICQ_BIN_DIR})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE ${ICQ_BIN_DIR})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${ICQ_BIN_DIR})

set(LIB_MAJOR_VERSION "0")
set(LIB_MINOR_VERSION "1")
set(LIB_PATCH_VERSION "0")
set(LIB_VERSION_STRING "${LIB_MAJOR_VERSION}.${LIB_MINOR_VERSION}.${LIB_PATCH_VERSION}")

project(libomicron
    VERSION ${LIB_VERSION_STRING}
    LANGUAGES CXX)

message(STATUS "")
message(STATUS "[CMAKE]")
message(STATUS "[CMAKE] including <libomicron/CMakeLists.txt>")
message(STATUS "[CMAKE]")
message(STATUS "[INFO] SUBPROJECT is ${CMAKE_PROJECT_NAME}")

option(OMICRON_USE_LIBCURL "Use libcurl for downloading." ON)
option(OMICRON_BUILD_TESTS "Build libomicron tests." OFF)

# --------------------------   libomicron   -------------------------
if(NOT ICQ_ROOT)
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/.." ABSOLUTE)
endif()

set(SUBPROJECT_ROOT "${ICQ_ROOT}/libomicron")

# Используем стандартный CMake GLOB_RECURSE вместо кастомного макроса find_sources
file(GLOB_RECURSE SUBPROJECT_SOURCES "${SUBPROJECT_ROOT}/src/*.cpp")
if(APPLE)
    file(GLOB_RECURSE SUBPROJECT_MM_SOURCES "${SUBPROJECT_ROOT}/src/*.mm")
endif()
file(GLOB_RECURSE SUBPROJECT_HEADERS_MAIN "${SUBPROJECT_ROOT}/include/*.h")
file(GLOB_RECURSE SUBPROJECT_HEADERS_ADD "${SUBPROJECT_ROOT}/src/*.h")
set(SUBPROJECT_HEADERS ${SUBPROJECT_HEADERS_MAIN} ${SUBPROJECT_HEADERS_ADD})

source_group("sources" FILES ${SUBPROJECT_SOURCES} ${SUBPROJECT_MM_SOURCES} ${SUBPROJECT_HEADERS})

include_directories(${SUBPROJECT_ROOT}/include)
include_directories(${SUBPROJECT_ROOT}/src)

# -------------------------- rapidjson ----------------------------------------
add_definitions(-DRAPIDJSON_HAS_STDSTRING=1)
if(EXISTS "${ICQ_ROOT}/rapidjson/include")
    include_directories("${ICQ_ROOT}/rapidjson/include")
endif()

if(OMICRON_USE_LIBCURL)
    add_definitions(-DOMICRON_USE_LIBCURL)
endif()

add_library(${PROJECT_NAME} STATIC
    ${SUBPROJECT_SOURCES}
    ${SUBPROJECT_MM_SOURCES}
    ${SUBPROJECT_HEADERS}
)

# Используем современный стандартный механизм предкомпилированных заголовков
if(EXISTS "${SUBPROJECT_ROOT}/src/stdafx.h")
    target_precompile_headers(${PROJECT_NAME} PRIVATE "${SUBPROJECT_ROOT}/src/stdafx.h")
endif()

if(OMICRON_BUILD_TESTS)
    add_subdirectory(test)
endif()
