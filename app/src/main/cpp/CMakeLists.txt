cmake_minimum_required(VERSION 3.22.1)
project("icq_mobile_core")

# Установка корневой директории репозитория ICQ
set(ICQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../..")

# --- Секция компиляции и дефайнов ---
# Использование современного метода установки макросов
add_compile_definitions(
    LINUX
    __linux__
    ANDROID
    STRIP_CRASH_HANDLER
    SUPPORT_EXTERNAL_CONFIG
    HAVE_SECURE_GETENV=1
)

# --- Поиск путей заголовков ---
# ВАЖНО: CMake использует $ENV{VAR} для доступа к переменным окружения GitHub Actions
include_directories(
    ${ICQ_ROOT}
    ${ICQ_ROOT}/core
    ${ICQ_ROOT}/core/Voip
    ${ICQ_ROOT}/core/voip        # Путь для симлинка (регистрозависимость)
    ${ICQ_ROOT}/corelib
    ${ICQ_ROOT}/common.shared
    ${ICQ_ROOT}/common.shared/config
    ${ICQ_ROOT}/gui.shared
    $ENV{BOOST_ROOT}             # Исправлено: переменная окружения Boost
    $ENV{RAPIDJSON_ROOT}         # Исправлено: переменная окружения RapidJSON
)

# --- Сбор исходных файлов ---
# Собираем ключевые модули ядра, необходимые для работы core_dispatcher
file(GLOB_RECURSE CORE_SOURCES
    "${ICQ_ROOT}/core/*.cpp"
    "${ICQ_ROOT}/common.shared/*.cpp"
    "${ICQ_ROOT}/corelib/*.cpp"
)

# Исключаем специфичный код других платформ, чтобы не было конфликтов при сборке
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*win32.*|.*macos.*|.*apple.*|.*linux_desktop.*|.*test.*|.*_mac.*|.*_ios.*")

# --- Сборка библиотеки ---
add_library(icq_core SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# --- Линковка зависимостей ---
find_library(log-lib log)
find_library(android-lib android)
find_library(z-lib z) # Часто требуется для работы базовых компонентов

target_link_libraries(icq_core
    ${log-lib}
    ${android-lib}
    ${z-lib}
    # Если Boost используется как static, линкуем здесь (например, boost_system)
    atomic
)

# Настройка C++ стандарта
set_target_properties(icq_core PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)
