#pragma once
#include <string>
#include "config.h"

namespace config
{
    std::string config_json();
    std::string load_config_from_file(const std::string& path);
}
