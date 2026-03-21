cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

# --- Настройка путей ---
set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../")

# --- Поиск Qt для Android ---
set(CI_QT_ANDROID "/home/runner/work/ICQ/Qt/6.5.0/android_arm64_v8a")
set(CI_QT_HOST "/home/runner/work/ICQ/Qt/6.5.0/gcc_64")

if(EXISTS "${CI_QT_ANDROID}" AND EXISTS "${CI_QT_HOST}")
    set(QT_HOST_PATH "${CI_QT_HOST}")
    set(Qt6_DIR "${CI_QT_ANDROID}/lib/cmake/Qt6")
    set(CMAKE_PREFIX_PATH "${CI_QT_ANDROID}/lib/cmake")
    message(STATUS "✅ Using GitHub Actions Qt paths")
elseif(DEFINED ENV{Qt6_ANDROID_DIR})
    set(QT_HOST_PATH "$ENV{Qt6_DIR}")
    set(Qt6_DIR "$ENV{Qt6_ANDROID_DIR}/lib/cmake/Qt6")
    set(CMAKE_PREFIX_PATH "$ENV{Qt6_ANDROID_DIR}/lib/cmake")
endif()

# Обязательно ищем дополнительные компоненты, необходимые для Android
find_package(Qt6 CONFIG QUIET COMPONENTS Core Network)

if(Qt6_FOUND)
    set(QT_FOUND TRUE)
    # Это макрос Qt, который настраивает необходимые таргеты для Android
    if(ANDROID)
        find_package(Qt6 COMPONENTS Core Network REQUIRED)
    endif()
else()
    message(WARNING "⚠️ Qt6 not found! Fallback to stub mode")
    set(QT_FOUND FALSE)
endif()

# --- Глобальные дефайны ---
add_compile_definitions(
    LINUX __linux__ ANDROID
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
    _FORTIFY_SOURCE=2
)

# --- Поиск исходников ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Оптимизированный фильтр исключений
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*(win32|macos|apple|linux_desktop|test|stdafx\\.cpp|unittests|_mac|_ios).*")

# Создаем библиотеку
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Настройка путей инклудов ---
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}                                
    ${ICQ_ROOT}/core                           
    ${ICQ_ROOT}/core/Voip/libvoip/include
    ${ICQ_ROOT}/core/Voip/libvoip/src
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/gui.shared
    ${ICQ_ROOT}/boost_1_83_0
    ${ICQ_ROOT}/rapidjson/include
    $ENV{CURL_INCLUDE_PATH}
)

# --- Свойства таргета ---
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# --- Опции компиляции (Production Ready) ---
target_compile_options(icq_core PRIVATE 
    -fexceptions -frtti -fPIC 
    -Wall -Wextra -Wno-unused-parameter -Wno-unused-variable -Wno-mismatched-tags
    -fdata-sections -ffunction-sections
)

# --- Поиск системных библиотек ---
find_library(log-lib log)
find_library(z-lib z)
find_library(android-lib android)

# --- Линковка ---
target_link_libraries(icq_core
    ${log-lib}
    ${z-lib}
    ${android-lib}
)

if(QT_FOUND)
    # Важно: Линковка через таргеты Qt сама подтянет нужные инклуды и зависимости
    target_link_libraries(icq_core Qt6::Core Qt6::Network)
    
    # Для Android Qt требует специальной обработки финализации таргета
    if(ANDROID)
        # Этот вызов важен для генерации зависимостей развертывания (deployment)
        qt_finalize_target(icq_core)
        target_link_options(icq_core PRIVATE "-Wl,--undefined=JNI_OnLoad")
    endif()
endif()

# Группировка линковки для оптимизации размера
target_link_options(icq_core PRIVATE "-Wl,--gc-sections")

# --- Опциональные оптимизации ---
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    # Проверка поддержки LTO компилятором
    include(CheckIPOSupported)
    check_ipo_supported(RESULT ipo_supported OUTPUT error)
    if(ipo_supported)
        set_target_properties(icq_core PROPERTIES INTERPROCEDURAL_OPTIMIZATION TRUE)
    endif()
endif()

# --- Для Android добавляем API level ---
if(ANDROID)
    target_compile_definitions(icq_core PRIVATE __ANDROID_API__=24)
endif()
