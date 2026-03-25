#pragma once

#include <string>
#include <string_view>

namespace config
{
    // Возвращает JSON конфигурации. 
    // string_view гарантированно указывает на валидную память (статический буфер)
    std::string_view config_json() noexcept;
    
    // Вспомогательная функция загрузки
    std::string load_config_from_file(const std::string& _path);
}
