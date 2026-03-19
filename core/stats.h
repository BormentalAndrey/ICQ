#pragma once

#include <string>
#include <vector>
#include <map>
#include <chrono>

namespace core
{
    namespace stats
    {
        // Типы событий для статистики
        enum class stats_event_names
        {
            // Добавьте нужные события из вашего проекта
            app_start,
            app_exit,
            message_sent,
            message_received,
            call_started,
            call_ended,
            file_uploaded,
            file_downloaded,
            // ... другие события
        };

        enum class im_stat_event_names
        {
            // События для IM статистики
            im_connected,
            im_disconnected,
            im_error,
            // ... другие события
        };

        // Тип для свойств события
        using event_props_type = std::map<std::string, std::string>;

        // Класс для сбора статистики
        class statistics
        {
        public:
            void insert_event(stats_event_names _event) {}
            void insert_event(stats_event_names _event, const event_props_type& _props) {}
            void insert_event(stats_event_names _event, event_props_type&& _props) {}
            bool is_enabled() const { return false; }
        };

        // Класс для IM статистики
        class im_stats
        {
        public:
            void insert_event(im_stat_event_names _event) {}
            void insert_event(im_stat_event_names _event, const event_props_type& _props) {}
            void insert_event(im_stat_event_names _event, event_props_type&& _props) {}
            bool is_enabled() const { return false; }
        };
    }
}
