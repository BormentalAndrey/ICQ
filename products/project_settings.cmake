# project_settings.cmake
# Основной файл конфигурации сборки ICQ для всех платформ

#------------------------------------------------------------------------------
# Базовая информация о проекте
#------------------------------------------------------------------------------
set(PROJECT_NAME "ICQ")
set(PROJECT_VERSION_MAJOR 10)
set(PROJECT_VERSION_MINOR 0)
set(PROJECT_VERSION_PATCH 0)
set(PROJECT_VERSION "${PROJECT_VERSION_MAJOR}.${PROJECT_VERSION_MINOR}.${PROJECT_VERSION_PATCH}")
set(PROJECT_DESCRIPTION "ICQ Messenger")
set(PROJECT_VENDOR "ICQ LLC")

#------------------------------------------------------------------------------
# Тип сборки
#------------------------------------------------------------------------------
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE "Release" CACHE STRING "Choose the type of build: Debug Release RelWithDebInfo MinSizeRel" FORCE)
endif()

message(STATUS "[${PROJECT_NAME}] Build type: ${CMAKE_BUILD_TYPE}")

#------------------------------------------------------------------------------
# Опции сборки
#------------------------------------------------------------------------------
option(BUILD_SHARED_LIBS "Build shared libraries" ON)
option(ENABLE_TESTS "Enable unit tests" OFF)
option(ENABLE_BENCHMARKS "Enable benchmarks" OFF)
option(ENABLE_DOCS "Enable documentation generation" OFF)
option(ENABLE_COVERAGE "Enable coverage reporting" OFF)
option(ENABLE_ASAN "Enable AddressSanitizer" OFF)
option(ENABLE_UBSAN "Enable UndefinedBehaviorSanitizer" OFF)
option(ENABLE_LTO "Enable Link Time Optimization" ON)
option(ENABLE_WERROR "Treat warnings as errors" OFF)

#------------------------------------------------------------------------------
# Целевая платформа
#------------------------------------------------------------------------------
if(ANDROID)
    set(PLATFORM_ANDROID TRUE)
    set(PLATFORM_NAME "android")
    add_definitions(-DANDROID)
    add_definitions(-D__ANDROID__)
elseif(IOS)
    set(PLATFORM_IOS TRUE)
    set(PLATFORM_NAME "ios")
    add_definitions(-DIOS)
elseif(WIN32)
    set(PLATFORM_WINDOWS TRUE)
    set(PLATFORM_NAME "windows")
    add_definitions(-DWIN32 -D_WINDOWS)
elseif(APPLE)
    set(PLATFORM_MACOS TRUE)
    set(PLATFORM_NAME "macos")
    add_definitions(-DMACOS)
elseif(UNIX)
    set(PLATFORM_LINUX TRUE)
    set(PLATFORM_NAME "linux")
    add_definitions(-DLINUX)
endif()

message(STATUS "[${PROJECT_NAME}] Platform: ${PLATFORM_NAME}")

#------------------------------------------------------------------------------
# Архитектура
#------------------------------------------------------------------------------
if(CMAKE_SIZEOF_VOID_P EQUAL 8)
    set(ARCH_64 TRUE)
    set(ARCH_NAME "x86_64")
else()
    set(ARCH_32 TRUE)
    set(ARCH_NAME "x86")
endif()

if(ANDROID)
    if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
        set(ARCH_NAME "arm64")
    elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
        set(ARCH_NAME "armv7")
    endif()
endif()

message(STATUS "[${PROJECT_NAME}] Architecture: ${ARCH_NAME}")

#------------------------------------------------------------------------------
# Директории
#------------------------------------------------------------------------------
set(PROJECT_ROOT_DIR "${CMAKE_CURRENT_SOURCE_DIR}")
set(PROJECT_BUILD_DIR "${CMAKE_CURRENT_BINARY_DIR}")
set(PROJECT_THIRD_PARTY_DIR "${PROJECT_ROOT_DIR}/third_party" CACHE PATH "Third party libraries directory")
set(PROJECT_EXTERNAL_DIR "${PROJECT_ROOT_DIR}/external" CACHE PATH "External projects directory")
set(PROJECT_OUTPUT_DIR "${PROJECT_BUILD_DIR}/output" CACHE PATH "Output directory")
set(PROJECT_INSTALL_DIR "${PROJECT_BUILD_DIR}/install" CACHE PATH "Install directory")

#------------------------------------------------------------------------------
# Компилятор и флаги
#------------------------------------------------------------------------------
if(CMAKE_CXX_COMPILER_ID MATCHES "Clang")
    set(COMPILER_CLANG TRUE)
    set(COMPILER_NAME "clang")
elseif(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
    set(COMPILER_GCC TRUE)
    set(COMPILER_NAME "gcc")
elseif(CMAKE_CXX_COMPILER_ID STREQUAL "MSVC")
    set(COMPILER_MSVC TRUE)
    set(COMPILER_NAME "msvc")
endif()

message(STATUS "[${PROJECT_NAME}] Compiler: ${COMPILER_NAME}")

#------------------------------------------------------------------------------
# Стандарт C++
#------------------------------------------------------------------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

#------------------------------------------------------------------------------
# Пути для модулей CMake
#------------------------------------------------------------------------------
list(APPEND CMAKE_MODULE_PATH "${PROJECT_ROOT_DIR}/cmake")
list(APPEND CMAKE_MODULE_PATH "${PROJECT_ROOT_DIR}/products")

#------------------------------------------------------------------------------
# Поиск пакетов
#------------------------------------------------------------------------------
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)

#------------------------------------------------------------------------------
# Флаги для Android
#------------------------------------------------------------------------------
if(ANDROID)
    # STL настройки для Android
    if(NOT ANDROID_STL)
        set(ANDROID_STL "c++_shared")
    endif()
    
    # Минимальная версия Android
    if(NOT ANDROID_NATIVE_API_LEVEL)
        set(ANDROID_NATIVE_API_LEVEL 24)
    endif()
    
    message(STATUS "[${PROJECT_NAME}] Android ABI: ${CMAKE_ANDROID_ARCH_ABI}")
    message(STATUS "[${PROJECT_NAME}] Android STL: ${ANDROID_STL}")
    message(STATUS "[${PROJECT_NAME}] Android API: ${ANDROID_NATIVE_API_LEVEL}")
endif()

#------------------------------------------------------------------------------
# Флаги оптимизации
#------------------------------------------------------------------------------
if(ENABLE_LTO AND NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    include(CheckIPOSupported)
    check_ipo_supported(RESULT IPO_SUPPORTED OUTPUT IPO_ERROR)
    if(IPO_SUPPORTED)
        set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)
        message(STATUS "[${PROJECT_NAME}] IPO/LTO enabled")
    else()
        message(WARNING "[${PROJECT_NAME}] IPO/LTO not supported: ${IPO_ERROR}")
    endif()
endif()

#------------------------------------------------------------------------------
# Пути для установки
#------------------------------------------------------------------------------
if(ANDROID)
    set(INSTALL_LIB_DIR "lib/${CMAKE_ANDROID_ARCH_ABI}")
else()
    set(INSTALL_LIB_DIR "lib")
endif()

set(INSTALL_BIN_DIR "bin")
set(INSTALL_INCLUDE_DIR "include")
set(INSTALL_SHARE_DIR "share/${PROJECT_NAME}")

#------------------------------------------------------------------------------
# Версия продукта (для разных продуктов ICQ)
#------------------------------------------------------------------------------
set(PRODUCT_NAME "icq" CACHE STRING "Product name: icq, icq10, icq_business")
set(PRODUCT_VERSION "${PROJECT_VERSION}")

if(PRODUCT_NAME STREQUAL "icq_business")
    set(PRODUCT_DISPLAY_NAME "ICQ Business")
    add_definitions(-DBUSINESS_VERSION)
elseif(PRODUCT_NAME STREQUAL "icq10")
    set(PRODUCT_DISPLAY_NAME "ICQ 10")
    add_definitions(-DICQ10_VERSION)
else()
    set(PRODUCT_DISPLAY_NAME "ICQ")
endif()

message(STATUS "[${PROJECT_NAME}] Product: ${PRODUCT_DISPLAY_NAME}")

#------------------------------------------------------------------------------
# Debug/Release специфичные настройки
#------------------------------------------------------------------------------
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_definitions(-D_DEBUG -DDEBUG)
    set(CMAKE_DEBUG_POSTFIX "d" CACHE STRING "Debug library postfix")
else()
    add_definitions(-DNDEBUG)
endif()

#------------------------------------------------------------------------------
# Экспорт настроек для дочерних CMakeLists.txt
#------------------------------------------------------------------------------
set(PROJECT_SETTINGS_LOADED TRUE CACHE BOOL "Project settings loaded" FORCE)
