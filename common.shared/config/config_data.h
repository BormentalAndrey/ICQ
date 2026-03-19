#pragma once

#include <string>

namespace config
{
    // Функция для получения JSON конфигурации из файла
    std::string config_json();
    
    // Функция для загрузки конфигурации из указанного пути
    std::string load_config_from_file(const std::string& path);
}
