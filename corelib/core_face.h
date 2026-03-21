#pragma once

#include "ibase.h"
#include "ivalue.h"
#include "../common.shared/common_defs.h"

#include <string>
#include <vector>
#include <memory>
#include <map>

namespace core
{
    // --- Перечисления состояния (оставляем без изменений) ---
    enum login_error
    {
        le_success = 0,
        le_wrong_login = 1,
        le_network_error = 2,
        le_parse_response = 3,
        le_rate_limit = 4,
        le_unknown_error = 5,
        le_invalid_sms_code = 6,
        le_error_validate_phone = 7,
        le_attach_error_busy_phone = 8,
        le_wrong_login_2x_factor = 9,
    };

    enum class mchat_error
    {
        me_success = 0,
        me_unknown_error = 1,
        me_cannot_add = 2,
    };

    enum avatar_error
    {
        ae_success = 0,
        ae_network_error = 1,
        ae_unknown_error = 2
    };

    // --- Предварительные объявления ---
    class iconnector;
    class icore_factory;
    typedef std::shared_ptr<class icollection> coll_ptr;
    typedef std::map<std::string, std::string> event_props_type;

    /**
     * @brief Основной интерфейс взаимодействия Core -> GUI.
     * В этом файле НЕ должно быть реализации (return nullptr), 
     * только объявление структуры интерфейса (= 0).
     */
    struct icore_interface : public ibase
    {
        virtual ~icore_interface() {}

        // Передача данных в UI
        virtual void receive_variable(const std::string& _name, coll_ptr _value) = 0;
        virtual void receive_package(const std::string& _name, coll_ptr _value) = 0;

        // События VoIP и статистики
        virtual void on_voip_proto_msg(const std::string& _account, const std::vector<char>& _data) = 0;
        virtual void send_statistic_event(const std::string& _event, const event_props_type& _props) = 0;

        // Системные запросы
        virtual std::string get_device_id() = 0;

        // Коннекторы (должны быть реализованы в jni_bridge.cpp, здесь только сигнатуры)
        virtual iconnector* get_core_connector() = 0;
        virtual iconnector* get_gui_connector() = 0;
        virtual icore_factory* get_factory() = 0;
    };

    // --- Вспомогательные структуры (icollection, iarray и т.д.) ---
    struct iarray : ibase
    {
        virtual ivalue* get_at(int32_t index) const = 0;
        virtual int32_t count() const = 0;
        virtual bool empty() const = 0;
    };

    struct icollection : ibase
    {
        virtual bool is_value_exist(std::string_view name) const = 0;

        virtual ivalue* create_value() = 0;
        virtual icollection* create_collection() = 0;
        virtual iarray* create_array() = 0;
        virtual istream* create_stream() = 0;

        virtual void set_value(std::string_view name, ivalue* value) = 0;
        virtual ivalue* get_value(std::string_view name) const = 0;
        virtual ivalue* first() = 0;
        virtual ivalue* next() = 0;
        virtual int32_t count() const = 0;
        virtual bool empty() const = 0;

        virtual const char* log() const = 0;

        virtual ~icollection() {}
    };

    struct iconnector : ibase
    {
        virtual void link(iconnector*, const common::core_gui_settings&) = 0;
        virtual void unlink() = 0;
        virtual void receive(std::string_view, int64_t, core::icollection*) = 0;

        virtual ~iconnector() {}
    };

    struct icore_factory : ibase
    {
        virtual iconnector* create_core() = 0;
        virtual ~icore_factory() {}
    };
}
