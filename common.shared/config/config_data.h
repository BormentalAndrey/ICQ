#pragma once
#include <string>
#include <string_view>
#include "config.h"

namespace config
{
    std::string_view config_json() noexcept;
    std::string load_config_from_file(const std::string& path);
}
