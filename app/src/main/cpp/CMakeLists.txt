cmake_minimum_required(VERSION 3.22.1)

# Название проекта
project("icq_mobile_core" LANGUAGES CXX C)

# Политика для корректной обработки переменных и путей
cmake_policy(SET CMP0074 NEW)

# 1. ОПРЕДЕЛЕНИЕ КОРНЯ ПРОЕКТА
# Если ICQ_PROJECT_ROOT не передан извне (например, из Gradle), вычисляем его относительно текущего файла
if(NOT ICQ_PROJECT_ROOT)
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

message(STATUS "[ICQ] Root directory: ${ICQ_ROOT}")

# 2. НАСТРОЙКА QT6 ДЛЯ ANDROID
if(NOT QT_ANDROID_PATH)
    set(QT_ANDROID_PATH "${ICQ_ROOT}/Qt/android_arm64_v8a" CACHE PATH "Path to target Qt")
endif()

list(APPEND CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}")

# Core, Network и Gui/Svg для работы с графикой и QPixmap
find_package(Qt6 REQUIRED COMPONENTS Core Network Gui Svg)

# 3. СБОРКА CURL (FetchContent)
include(FetchContent)
FetchContent_Declare(
    curl
    GIT_REPOSITORY https://github.com/curl/curl.git
    GIT_TAG curl-8_4_0
)
set(BUILD_CURL_EXE OFF CACHE INTERNAL "")
set(BUILD_SHARED_LIBS OFF CACHE INTERNAL "")
set(HTTP_ONLY ON CACHE INTERNAL "") 
FetchContent_MakeAvailable(curl)

# 4. ОПРЕДЕЛЕНИЯ КОМПИЛЯЦИИ
add_compile_definitions(
    ANDROID
    PLATFORM_ANDROID
    __ANDROID__
    STRIP_CRASH_HANDLER
    CORE_USED_IN_ANDROID
    SUPPORT_EXTERNAL_CONFIG
    QT_NO_KEYWORDS
)

# 5. ПОИСК И ФИЛЬТРАЦИЯ ИСХОДНИКОВ
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/gui/*.cpp"
)

# Исключаем файлы, специфичные для других платформ (Windows, Mac, iOS)
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*win32.*|.*_win\\..*|.*macos.*|.*apple.*|.*_mac\\..*|.*_ios\\..*|.*stdafx\\.cpp")

# 6. СОЗДАНИЕ БИБЛИОТЕКИ
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# 7. ИСПРАВЛЕНИЕ ПУТЕЙ ИНКЛЮДОВ (Решение проблем с 'styles/...' и 'cache/...')
# Добавляем пути так, чтобы компилятор видел структуру папок внутри gui и core
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}                      # Корень проекта (для путей "core/...", "gui/...")
    ${ICQ_ROOT}/core                 # Для прямого доступа к файлам core
    ${ICQ_ROOT}/gui                  # Критично для нахождения "styles/ThemeColor.h" и "cache/..."
    ${ICQ_ROOT}/common.shared        # Общие определения
    ${ICQ_ROOT}/libomicron/include   # Omicron
    ${curl_SOURCE_DIR}/include       # Заголовки Curl
)

# 8. ЛИНКОВКА
target_link_libraries(icq_core
    Qt6::Core
    Qt6::Network
    Qt6::Gui
    Qt6::Svg
    libcurl
    log
    z
    android
)

# 9. ФИНАЛЬНЫЕ СВОЙСТВА
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    C_STANDARD 11
    POSITION_INDEPENDENT_CODE ON
    # Скрываем символы по умолчанию для уменьшения размера библиотеки
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# Обязательная финализация для Android в Qt6
if(ANDROID)
    qt_finalize_target(icq_core)
endif()
