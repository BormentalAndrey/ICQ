#include "stdafx.h"
#include "json_helper.h"
#include <algorithm>
#include <string_view>

namespace core::tools
{
    void sort_json_keys_by_name(rapidjson::Value& _node)
    {
        if (!_node.IsObject())
            return;

        // Компаратор для Member объектов RapidJSON
        auto members_comparator = [](const rapidjson::Value::Member& _lhs, const rapidjson::Value::Member& _rhs) noexcept
        {
            std::string_view s1(_lhs.name.GetString(), _lhs.name.GetStringLength());
            std::string_view s2(_rhs.name.GetString(), _rhs.name.GetStringLength());
            return s1 < s2;
        };

        // Сортируем поля объекта
        std::sort(_node.MemberBegin(), _node.MemberEnd(), members_comparator);

        // Рекурсивный обход
        for (auto& member : _node.GetObject())
        {
            if (member.value.IsObject())
            {
                sort_json_keys_by_name(member.value);
            }
            else if (member.value.IsArray())
            {
                for (auto& array_member : member.value.GetArray())
                {
                    if (array_member.IsObject())
                        sort_json_keys_by_name(array_member);
                }
            }
        }
    }
}
