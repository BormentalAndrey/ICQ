#include "stdafx.h"
#include "config_data.h"
#include "../tools/system.h"
#include "../utils.h"
#include <fstream>
#include <sstream>
#include <android/log.h>

#define LOG_TAG "ICQConfig"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace config
{
    std::string load_config_from_file(const std::string& path)
    {
        std::ifstream file(path);
        if (file.is_open())
        {
            std::stringstream buffer;
            buffer << file.rdbuf();
            return buffer.str();
        }
        return std::string();
    }
    
    std::string config_json()
    {
        // Получаем wstring путь и конвертируем в string
        std::wstring wpath = core::utils::get_product_data_path();
        std::string base_path(wpath.begin(), wpath.end());
        
        // Пытаемся загрузить конфиг из нескольких возможных мест
        std::vector<std::string> possible_paths = {
            base_path + "/products/icq/config.json",
            base_path + "/../products/icq/config.json",
            "/data/data/com.icq.mobile/files/products/icq/config.json",
            "./products/icq/config.json"
        };
        
        for (const auto& path : possible_paths)
        {
            std::string json = load_config_from_file(path);
            if (!json.empty())
            {
                LOGI("Loaded config from: %s", path.c_str());
                return json;
            }
        }
        
        LOGE("Failed to load config from any path");
        return "{}"; // Возвращаем пустой JSON объект как fallback
    }
}
