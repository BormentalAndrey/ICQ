#include "stdafx.h"
#include "antivirus_types.h"

#include "../json_unserialize_helpers.h"
#include "../json_helper.h"
#include <cassert>

#ifndef im_assert
#define im_assert(x) assert(x)
#endif

namespace core::antivirus
{
    std::string_view check::mode_to_string(mode _mode)
    {
        switch (_mode)
        {
        case mode::async:
            return "async";
        case mode::sync:
            return "sync";
        default:
            im_assert(!"wrorg antivirus check mode");
            return mode_to_string(mode::async);
        }
    }

    check::mode check::mode_from_string(std::string_view _mode)
    {
        if ("async" == _mode)
            return mode::async;
        if ("sync" == _mode)
            return mode::sync;
        im_assert(!"wrorg antivirus check mode");
        return mode::async;
    }

    std::string_view check::result_to_string(result _result)
    {
        switch (_result)
        {
        case result::unchecked:
            return "unchecked";
        case result::safe:
            return "safe";
        case result::unsafe:
            return "unsafe";
        case result::unknown:
            return "unknown";
        default:
            im_assert(!"wrorg antivirus check result");
            return result_to_string(result::unchecked);
        }
    }

    check::result check::result_from_string(std::string_view _result)
    {
        if ("unchecked" == _result)
            return result::unchecked;
        if ("safe" == _result)
            return result::safe;
        if ("unsafe" == _result)
            return result::unsafe;
        if ("unknown" == _result)
            return result::unknown;
        im_assert(!"wrorg antivirus check result");
        return result::unchecked;
    }

    void check::serialize(rapidjson::Value& _node, rapidjson_allocator& _a) const
    {
        _node.AddMember("check_mode", common::json::make_string_ref(mode_to_string(mode_)), _a);
        _node.AddMember("check_result", common::json::make_string_ref(result_to_string(result_)), _a);
    }

    void check::unserialize(const rapidjson::Value& _node)
    {
        if (auto check_mode = common::json::get_value<std::string_view>(_node, "check_mode"))
            mode_ = mode_from_string(*check_mode);
        
        if (auto check_result = common::json::get_value<std::string_view>(_node, "check_result"))
            result_ = result_from_string(*check_result);
    }
} // namespace core::antivirus
