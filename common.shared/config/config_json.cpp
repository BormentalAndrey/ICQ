#include "stdafx.h"
#include "config_data.h"
#include "config.h"
#include "../tools/system.h"
#include "../utils.h"
#include <fstream>
#include <sstream>

namespace config
{
    std::string config_json()
    {
        // Пытаемся загрузить конфиг из файла products/icq/config.json
        std::string config_path = core::utils::get_product_data_path() + "/products/icq/config.json";
        
        std::ifstream file(config_path);
        if (file.is_open())
        {
            std::stringstream buffer;
            buffer << file.rdbuf();
            return buffer.str();
        }
        
        // Fallback - пустой JSON объект
        return "{}";
    }
}
