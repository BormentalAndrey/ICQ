// gui/core_dispatcher.h
#pragma once

#include <string>
#include <vector>
#include <memory>
#include <mutex>

#include "core/icore_interface.h"

// Forward declarations
namespace core {
    struct iconnector;
    struct icore_factory;
}

namespace Ui {

class core_dispatcher {
public:
    core_dispatcher();
    ~core_dispatcher();

    // Запрещаем копирование
    core_dispatcher(const core_dispatcher&) = delete;
    core_dispatcher& operator=(const core_dispatcher&) = delete;

    // Управление жизненным циклом
    void unlink_gui();
    bool is_linked() const;
    
    // Взаимодействие с ядром из мобильного приложения
    void process_voip_message(const std::string& account, int msg_type, const std::vector<char>& data);
    void send_message(const std::string& contact, const std::string& message);

    // Доступ к внутренним коннекторам
    core::iconnector* get_core_connector() const;
    core::iconnector* get_gui_connector() const;
    core::icore_factory* get_factory() const;

private:
    struct Impl;
    std::unique_ptr<Impl> d;
    mutable std::mutex dispatcher_mutex_;
};

} // namespace Ui
