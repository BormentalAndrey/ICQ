#include "stdafx.h"
#include "config_data.h"
#include "tools/system.h"
#include "../utils.h"
#include "tools/strings.h"
#include <fstream>
#include <sstream>
#include <vector>
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
    
    std::string_view config_json() noexcept
    {
        static std::string cached_json; // Статическая переменная для удержания памяти
        
        if (!cached_json.empty()) {
            return cached_json;
        }

        std::wstring wpath = core::utils::get_product_data_path();
        std::string base_path = core::tools::from_utf16(wpath);
        
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
                cached_json = json;
                return cached_json;
            }
        }
        
        LOGE("Failed to load config from any path");
        cached_json = "{}"; // Fallback
        return cached_json;
    }
}
