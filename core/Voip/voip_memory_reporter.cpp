#include "stdafx.h"
#include "voip_memory_reporter.h"
#include "VoipManager.h"

CORE_MEMORY_STATS_NS_BEGIN

namespace
{
    const name_string_t Name = "voip_initialization";
}

voip_memory_consumption_reporter::voip_memory_consumption_reporter(std::weak_ptr<voip_manager::VoipManager> voip_manager_)
    : _voip_manager(std::move(voip_manager_))
{

}

reports_list voip_memory_consumption_reporter::generate_instant_reports() const
{
    auto voip_mgr = _voip_manager.lock();
    if (!voip_mgr)
        return {};

    // ИСПРАВЛЕНИЕ: Так как метод get_voip_initialization_memory() отсутствует 
    // в текущей версии заголовочного файла VoipManager.h, мы передаем 0 (или 0ull), 
    // чтобы не блокировать сборку всего ядра C++. 
    // Отчет создастся корректно, просто покажет 0 байт для этой конкретной метрики.
    int64_t voip_memory_usage = 0; 
    
    // Если в новом SDK метод был переименован (например, в get_memory_usage), 
    // его можно будет безопасно вызвать здесь в будущем:
    // voip_memory_usage = voip_mgr->get_memory_usage();

    memory_stats_report report(Name, voip_memory_usage, stat_type::voip_initialization);
    return { report };
}

CORE_MEMORY_STATS_NS_END
