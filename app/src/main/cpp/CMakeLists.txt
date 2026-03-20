cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

# --- Настройка путей ---
# Мы находимся в: /app/src/main/cpp
# Поднимаемся:
# 1. .. -> /app/src/main
# 2. ../.. -> /app/src
# 3. ../../.. -> /app
# 4. ../../../.. -> КОРЕНЬ РЕПОЗИТОРИЯ (где лежат core, common.shared и т.д.)
set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../")

# --- Глобальные дефайны (Production набор) ---
add_compile_definitions(
    LINUX
    __linux__
    ANDROID
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
    # Добавляем стандартные для Android NDK флаги
    _FORTIFY_SOURCE=2
)

# --- Поиск исходников ---
# Собираем все .cpp файлы из ключевых модулей
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
    "${ICQ_ROOT}/gui.shared/*.cpp"
)

# Исключаем файлы других платформ и тесты, чтобы избежать конфликтов при линковке
list(FILTER CORE_SOURCES EXCLUDE REGEX 
    ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*|.*test.*|.*stdafx\\.cpp|.*unittests.*|.*_mac\\.cpp|.*_ios\\.cpp|.*/gui/.*|.*/internal/.*"
)

# Создаем библиотеку
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Настройка путей инклудов (Production Style) ---
# КОРРЕКТНО: Добавляем ICQ_ROOT, чтобы компилятор видел папку "core/" как префикс
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}                                # Теперь #include "core/..." сработает!
    ${ICQ_ROOT}/core                           # Для старых инклудов без префикса
    ${ICQ_ROOT}/core/Voip/libvoip/include
    ${ICQ_ROOT}/core/Voip/libvoip/src
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/gui.shared
    ${ICQ_ROOT}/boost_1_83_0
    ${ICQ_ROOT}/rapidjson/include
    ${ICQ_ROOT}/external/curl/include
)

# --- Свойства таргета ---
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
    # Скрытие символов (уменьшает размер .so и ускоряет загрузку)
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
    -fdata-sections 
    -ffunction-sections
)

# --- Линковка системных библиотек Android ---
find_library(log-lib log)
find_library(z-lib z)
find_library(android-lib android)

target_link_libraries(icq_core
    ${log-lib}
    ${z-lib}
    ${android-lib}
    # -Wl,--gc-sections — удаляет неиспользуемый код при линковке
    -Wl,--gc-sections
)

# Отладочный вывод для GitHub Actions / Android Studio
message(STATUS "Build Configuration:")
message(STATUS "  ICQ_ROOT: ${ICQ_ROOT}")
message(STATUS "  Target: icq_core")
