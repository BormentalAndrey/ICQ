# app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

# --- Настройка путей ---
set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../")

# --- Поиск Qt для Android ---
# Жестко прописываем пути для GitHub Actions, так как Gradle (AGP) 
# изолирует пользовательские переменные окружения.
set(CI_QT_ANDROID "/home/runner/work/ICQ/Qt/6.5.0/android_arm64_v8a")
set(CI_QT_HOST "/home/runner/work/ICQ/Qt/6.5.0/gcc_64")

if(EXISTS "${CI_QT_ANDROID}" AND EXISTS "${CI_QT_HOST}")
    # Для кросс-компиляции Qt6 под Android ОБЯЗАТЕЛЬНО нужен QT_HOST_PATH
    set(QT_HOST_PATH "${CI_QT_HOST}")
    set(Qt6_DIR "${CI_QT_ANDROID}/lib/cmake/Qt6")
    set(CMAKE_PREFIX_PATH "${CI_QT_ANDROID}/lib/cmake")
    message(STATUS "✅ Using GitHub Actions Qt paths. Host: ${QT_HOST_PATH}")
elseif(DEFINED ENV{Qt6_ANDROID_DIR})
    set(QT_HOST_PATH "$ENV{Qt6_DIR}")
    set(Qt6_DIR "$ENV{Qt6_ANDROID_DIR}/lib/cmake/Qt6")
    set(CMAKE_PREFIX_PATH "$ENV{Qt6_ANDROID_DIR}/lib/cmake")
endif()

find_package(Qt6 CONFIG QUIET COMPONENTS Core Network)
if(Qt6_FOUND)
    set(QT_FOUND TRUE)
    message(STATUS "✅ Qt6 found at: ${Qt6_DIR}")
else()
    message(WARNING "⚠️ Qt6 not found! Fallback to stub mode (limited functionality)")
    set(QT_FOUND FALSE)
endif()

# --- Глобальные дефайны ---
add_compile_definitions(
    LINUX
    __linux__
    ANDROID
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
    _FORTIFY_SOURCE=2
)

# Добавляем Qt дефайны если Qt найден
if(QT_FOUND)
    add_compile_definitions(
        QT_CORE_LIB
        QT_NETWORK_LIB
        QT_NO_DEBUG
    )
endif()

# --- Поиск исходников ---
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Исключаем лишнее - только файлы, которые точно не нужны на Android
list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*|.*test.*|.*stdafx\\.cpp|.*unittests.*|.*_mac\\.cpp|.*_ios\\.cpp"
)

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

# Добавляем Qt инклуды если Qt найден
if(QT_FOUND)
    target_include_directories(icq_core PRIVATE
        ${Qt6Core_INCLUDE_DIRS}
        ${Qt6Network_INCLUDE_DIRS}
    )
endif()

# --- Свойства таргета ---
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# --- Опции компиляции ---
target_compile_options(icq_core PRIVATE 
    -fexceptions
    -frtti
    -fPIC
    -Wall 
    -Wno-unused-parameter
    -Wno-unused-variable
    -Wno-mismatched-tags
    -fdata-sections
    -ffunction-sections
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
    -Wl,--gc-sections
)

# Линкуем Qt если найден
if(QT_FOUND)
    target_link_libraries(icq_core
        Qt6::Core
        Qt6::Network
    )
    
    # Добавляем специфичные для Android флаги
    if(ANDROID)
        target_link_options(icq_core PRIVATE -u JNI_OnLoad)
    endif()
endif()

# --- Опциональные оптимизации ---
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(icq_core PRIVATE -flto)
    target_link_options(icq_core PRIVATE -flto)
endif()

# --- Вывод информации о сборке ---
message(STATUS "")
message(STATUS "========================================")
message(STATUS "ICQ Mobile Core Build Configuration")
message(STATUS "========================================")
message(STATUS "  ICQ_ROOT: ${ICQ_ROOT}")
message(STATUS "  Build Type: ${CMAKE_BUILD_TYPE}")
message(STATUS "  Qt Found: ${QT_FOUND}")
if(QT_FOUND)
    message(STATUS "  Qt Version: ${Qt6_VERSION}")
    message(STATUS "  Qt Core: ${Qt6Core_INCLUDE_DIRS}")
endif()
message(STATUS "  Boost: ${ICQ_ROOT}/boost_1_83_0")
message(STATUS "  RapidJSON: ${ICQ_ROOT}/rapidjson/include")
message(STATUS "  Curl Include: $ENV{CURL_INCLUDE_PATH}")
message(STATUS "========================================")

# --- Для Android добавляем флаги совместимости ---
if(ANDROID)
    set_property(TARGET icq_core PROPERTY CXX_STANDARD 17)
    target_compile_options(icq_core PRIVATE 
        -D__ANDROID_API__=24
        -DANDROID_PLATFORM=24
    )
endif()
