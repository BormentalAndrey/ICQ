cmake_minimum_required(VERSION 3.22.1)

# Название проекта соответствует вашему запросу
project("icq_mobile_core" LANGUAGES CXX C)

# Политика для корректной обработки переменных окружения и путей
cmake_policy(SET CMP0074 NEW)

# 1. ОПРЕДЕЛЕНИЕ КОРНЕВОЙ ДИРЕКТОРИИ ПРОЕКТА
# Используем иерархию папок для автоматического поиска корня, если он не задан
if(NOT ICQ_PROJECT_ROOT)
    get_filename_component(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)
else()
    set(ICQ_ROOT "${ICQ_PROJECT_ROOT}")
endif()

message(STATUS "[ICQ] Project root: ${ICQ_ROOT}")

# 2. НАСТРОЙКА QT6 ДЛЯ ANDROID
# Поддержка QPixmap, QSvgRenderer и базовой графики
if(NOT QT_ANDROID_PATH)
    set(QT_ANDROID_PATH "${ICQ_ROOT}/Qt/android_arm64_v8a" CACHE PATH "Path to target Qt")
endif()

list(APPEND CMAKE_PREFIX_PATH "${QT_ANDROID_PATH}")

# Core, Network, Gui (для QPixmap/QColor) и Svg (для рендеринга иконок)
find_package(Qt6 REQUIRED COMPONENTS Core Network Gui Svg)

# 3. СБОРКА И ПОДКЛЮЧЕНИЕ CURL
# Используем FetchContent для гарантии наличия curl нужной версии в статическом виде
include(FetchContent)
FetchContent_Declare(
    curl
    GIT_REPOSITORY https://github.com/curl/curl.git
    GIT_TAG curl-8_4_0
)
set(BUILD_CURL_EXE OFF CACHE INTERNAL "")
set(BUILD_SHARED_LIBS OFF CACHE INTERNAL "")
set(HTTP_ONLY ON CACHE INTERNAL "") # Опционально: ускоряет сборку, если нужны только http/https
FetchContent_MakeAvailable(curl)

# 4. ПОИСК ИСХОДНЫХ ФАЙЛОВ
# Рекурсивно собираем все файлы, исключая платформозависимые части
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/gui/*.cpp"
)

# Фильтр для исключения Windows/Mac/iOS специфичного кода
# Также исключаем stdafx.cpp, так как в Android-сборке обычно не используются precompiled headers в таком виде
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*win32.*|.*_win\\..*|.*macos.*|.*apple.*|.*_mac\\..*|.*_ios\\..*|.*stdafx\\.cpp")

# 5. СОЗДАНИЕ ЦЕЛЕВОЙ БИБЛИОТЕКИ
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# 6. ОПРЕДЕЛЕНИЯ КОМПИЛЯЦИИ (Макросы)
target_compile_definitions(icq_core PRIVATE
    ANDROID
    PLATFORM_ANDROID
    __ANDROID__
    STRIP_CRASH_HANDLER      # Отключаем десктопный обработчик падений
    CORE_USED_IN_ANDROID
    SUPPORT_EXTERNAL_CONFIG
    QT_NO_KEYWORDS           # Избегаем конфликтов с сигналами/слотами в системных хедерах
)

# 7. ИСПРАВЛЕНИЕ ПУТЕЙ ИНКЛЮДОВ (Решение проблем с 'styles/...' и 'cache/...')
# Добавляем пути так, чтобы компилятор видел вложенные структуры без указания префикса папки
target_include_directories(icq_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ICQ_ROOT}                      # Для путей типа "core/..."
    ${ICQ_ROOT}/core                 # Для прямого доступа к файлам внутри core
    ${ICQ_ROOT}/gui                  # Критично! Позволяет находить "styles/ThemeColor.h" и "cache/..."
    ${ICQ_ROOT}/gui/types            # Дополнительный уровень для сложных зависимостей
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/libomicron/include
    ${curl_SOURCE_DIR}/include
)

# 8. ЛИНКОВКА БИБЛИОТЕК
target_link_libraries(icq_core
    Qt6::Core
    Qt6::Network
    Qt6::Gui
    Qt6::Svg
    libcurl
    log      # Системный лог Android
    z        # Сжатие
    android  # Native Android API
)

# 9. СТАНДАРТЫ И СВОЙСТВА
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    C_STANDARD 11
    POSITION_INDEPENDENT_CODE ON
    # Скрываем символы по умолчанию для уменьшения размера и безопасности, 
    # кроме тех, что помечены JNIEXPORT
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# 10. ФИНАЛИЗАЦИЯ (Специфично для Qt6 Android)
if(ANDROID)
    qt_finalize_target(icq_core)
endif()
