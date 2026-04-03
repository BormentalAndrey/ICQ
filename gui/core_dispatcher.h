#pragma once

#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <functional>
#include <atomic>
#include <map>

#include <QObject>
#include <QString>
#include <QByteArray>

// Core interfaces
#include "core/icore_interface.h"

// Forward declarations
namespace core {
    struct iconnector;
    struct icore_factory;
    struct icollection;

    namespace stats {
        using event_props_type = std::map<std::string, std::string>;
        enum class stats_event_names;
        enum class im_stat_event_names;
    }
}

namespace Ui {

enum class ConnectionState {
    stateUnknown = 0,
    stateConnecting = 1,
    stateUpdating = 2,
    stateOnline = 3
};

using message_processed_callback = std::function<void(core::icollection*)>;

/**
 * @brief Основной диспетчер для мобильной версии ICQ/Dzin
 */
class core_dispatcher : public QObject
{
    Q_OBJECT

Q_SIGNALS:
    // Сигналы для JNI / Java
    void coreEvent(const QString& eventName);
    void voipEvent(const QString& account, const QByteArray& data);
    void statisticEvent(const QString& eventName, const QString& props);

    // Сигналы состояния
    void loginComplete();
    void connectionStateChanged(ConnectionState state);
    void im_created();
    void myInfo();
    void contactList(const QString& listData);

public:
    core_dispatcher();
    virtual \~core_dispatcher();

    bool init(const std::string& dataPath, const std::string& cachePath, const std::string& deviceId);
    void unlink_gui();
    bool is_initialized() const;
    std::string get_version() const;

    // Методы, используемые из JNI
    core::iconnector* get_core_connector() const;
    core::iconnector* get_gui_connector() const;
    core::icore_factory* get_factory() const;

    void process_voip_message(const std::string& account, int msg_type, const std::vector<char>& data);
    void send_message(const std::string& contact, const std::string& message);
    void send_statistic_event(const std::string& eventName, const core::stats::event_props_type& props);

    std::string get_device_id() const;

    qint64 post_message_to_core(std::string_view message,
                                core::icollection* collection,
                                const QObject* object = nullptr,
                                message_processed_callback callback = nullptr);

    core::icollection* create_collection() const;

    // Дополнительные высокоуровневые методы
    qint64 get_history(const QString& contact, int early_count = 50, int later_count = 50);
    void set_last_read(const QString& contact, int64_t last_msg_id);
    void send_typing_status(const QString& contact, bool is_typing);
    qint64 load_avatar(const QString& contact, int size);
    void cancel_loading(const QString& url);
    qint64 get_user_info(const QString& aimid);
    qint64 subscribe_status(const std::vector<QString>& contacts);
    void get_active_dialogs();
    qint64 erase_history(const QString& contactAimId);
    qint64 delete_messages(const QString& contactAimId, const std::vector<int64_t>& messageIds);
    qint64 add_reaction(int64_t msgId, const QString& chatId, const QString& reaction);
    qint64 remove_reaction(int64_t msgId, const QString& chatId);

    ConnectionState get_connection_state() const;
    bool is_im_created() const;

private:
    struct Impl;
    std::unique_ptr<Impl> d_;
    mutable std::mutex mutex_;
    std::atomic<bool> initialized_{false};
};

// Глобальные функции
core_dispatcher* GetDispatcher();
void createDispatcher();
void destroyDispatcher();

} // namespace Ui
