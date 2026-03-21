#pragma once

#include <string>
#include <vector>
#include <memory>

namespace core
{
    using coll_ptr = std::shared_ptr<void>;  // заглушка
    using event_props_type = std::vector<std::pair<std::string, std::string>>;

    class icore_interface
    {
    public:
        virtual ~icore_interface() = default;
        virtual void receive_variable(const std::string& _name, coll_ptr _value) = 0;
        virtual void receive_package(const std::string& _name, coll_ptr _value) = 0;
        virtual void on_voip_proto_msg(const std::string& _account, const std::vector<char>& _data) = 0;
        virtual void send_statistic_event(const std::string& _event, const event_props_type& _props) = 0;
        virtual std::string get_device_id() = 0;
    };
}
