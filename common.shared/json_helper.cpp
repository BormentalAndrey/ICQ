#include "stdafx.h"
#include "json_helper.h"
#include <algorithm>
#include <string_view>

// КРИТИЧЕСКИ ВАЖНО для Android NDK: 
// Определение swap для GenericMember, чтобы std::sort мог переставлять элементы внутри объекта
namespace rapidjson {
    template <typename Encoding, typename Allocator>
    inline void swap(GenericMember<Encoding, Allocator>& a, GenericMember<Encoding, Allocator>& b) noexcept {
        a.name.Swap(b.name);
        a.value.Swap(b.value);
    }
}

namespace core::tools::json_helper
{
    void sort_json_keys_by_name(rapidjson::Value& _node)
    {
        if (!_node.IsObject())
            return;

        // Компаратор через string_view для оптимальной производительности
        auto members_comparator = [](const rapidjson::Value::Member& _lhs, const rapidjson::Value::Member& _rhs) noexcept
        {
            const std::string_view s1(_lhs.name.GetString(), _lhs.name.GetStringLength());
            const std::string_view s2(_rhs.name.GetString(), _rhs.name.GetStringLength());
            return s1 < s2;
        };

        auto members = _node.MemberBegin();
        auto members_end = _node.MemberEnd();
        
        if (members < members_end) {
            // Теперь std::sort сработает корректно благодаря определенному выше swap
            std::sort(members, members_end, members_comparator);
        }

        // Рекурсивный обход вложенных объектов и массивов
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
} // namespace core::tools::json_helper
