#include "core_dispatcher.h"

#include "../corelib/core_face.h"
#include "../corelib/collection_helper.h"

#include <QTimer>
#include <QCoreApplication>
#include <QDebug>

#include <unordered_map>
#include <string_view>

namespace Ui
{

// ============================================================================
// Вспомогательный класс gui_connector
// ============================================================================
class gui_connector : public core::iconnector
{
public:
    gui_connector() 
        : ref_count_(1)
        , linked_(false)
        , core_connector_(nullptr)
    {}

    int addref() override 
    { 
        return ++ref_count_; 
    }
    
    int release() override 
    { 
        int32_t res = --ref_count_;
        if (res == 0) {
            delete this;
            return 0;
        }
        return res;
    }

    void link(core::iconnector* _connector, const common::core_gui_settings& /*_settings*/) override
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (core_connector_) {
            core_connector_->release();
        }
        core_connector_ = _connector;
        if (core_connector_) {
            core_connector_->addref();
        }
        linked_ = true;
    }

    void unlink() override
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (core_connector_) {
            core_connector_->release();
            core_connector_ = nullptr;
        }
        linked_ = false;
    }

    void receive(std::string_view _message, int64_t _seq, core::icollection* _collection) override
    {
        // Защита от use-after-free при передаче в другой поток
        if (_collection) {
            _collection->addref();
        }

        QMetaObject::invokeMethod(QCoreApplication::instance(),
            [this, msg = std::string(_message), seq = _seq, coll = _collection]() mutable {
                if (auto* dispatcher = GetDispatcher()) {
                    Q_EMIT dispatcher->coreEvent(QString::fromStdString(msg));

                    if (msg == "login_complete") {
                        Q_EMIT dispatcher->loginComplete();
                    }
                    else if (msg == "im_created") {
                        Q_EMIT dispatcher->im_created();
                    }
                    else if (msg == "my_info") {
                        Q_EMIT dispatcher->myInfo();
                    }
                    else if (msg == "connection_state" && coll) {
                        int state = 0;
                        coll->get_value("state", state);
                        Q_EMIT dispatcher->connectionStateChanged(static_cast<ConnectionState>(state));
                    }
                }

                if (coll) {
                    coll->release();
                }
            }, Qt::QueuedConnection);
    }

    bool is_linked() const 
    { 
        std::lock_guard<std::mutex> lock(mutex_);
        return linked_; 
    }

private:
    std::atomic<int32_t> ref_count_;
    core::iconnector* core_connector_ = nullptr;
    bool linked_ = false;
    mutable std::mutex mutex_;
};

// ============================================================================
// PIMPL
// ============================================================================
class core_dispatcher::Impl
{
public:
    Impl()
        : coreConnector_(nullptr)
        , coreFace_(nullptr)
        , guiConnector_(nullptr)
        , isImCreated_(false)
        , connectionState_(ConnectionState::stateUnknown)
        , lastSeq_(0)
    {
        guiConnector_ = new gui_connector();
    }

    \~Impl()
    {
        if (guiConnector_) {
            guiConnector_->unlink();
            guiConnector_->release();
            guiConnector_ = nullptr;
        }
    }

    core::iconnector* coreConnector_ = nullptr;
    core::icore_interface* coreFace_ = nullptr;
    gui_connector* guiConnector_ = nullptr;

    bool isImCreated_ = false;
    ConnectionState connectionState_;

    std::string deviceId_;
    std::string dataPath_;
    std::string cachePath_;

    std::atomic<int64_t> lastSeq_{0};
    std::unordered_map<int64_t, message_processed_callback> callbacks_;
};

static core_dispatcher* g_dispatcher = nullptr;

// ============================================================================
// core_dispatcher
// ============================================================================
core_dispatcher::core_dispatcher()
    : d_(std::make_unique<Impl>())
{
}

core_dispatcher::\~core_dispatcher() = default;

bool core_dispatcher::init(const std::string& dataPath,
                           const std::string& cachePath,
                           const std::string& deviceId)
{
    std::lock_guard<std::mutex> lock(mutex_);
    if (initialized_)
        return true;

    d_->dataPath_ = dataPath;
    d_->cachePath_ = cachePath;
    d_->deviceId_ = deviceId;

    // Реальная инициализация ядра
    core::core_init_params params{};
    params.data_path = dataPath;
    params.cache_path = cachePath;
    params.device_id = deviceId;

    d_->coreFace_ = core::init_core(params);
    if (!d_->coreFace_) {
        qWarning() << "core_dispatcher: Failed to init core library";
        return false;
    }

    d_->coreConnector_ = d_->coreFace_->get_core_connector();
    if (!d_->coreConnector_) {
        qWarning() << "core_dispatcher: Failed to get core connector";
        return false;
    }

    common::core_gui_settings settings;
    if (d_->guiConnector_) {
        d_->guiConnector_->link(d_->coreConnector_, settings);
    }

    initialized_ = true;
    qInfo() << "core_dispatcher initialized successfully. Data path:" << QString::fromStdString(dataPath);
    return true;
}

void core_dispatcher::unlink_gui()
{
    std::lock_guard<std::mutex> lock(mutex_);
    if (d_->guiConnector_)
        d_->guiConnector_->unlink();
}

bool core_dispatcher::is_initialized() const
{
    return initialized_.load();
}

std::string core_dispatcher::get_version() const
{
    return "2.0.0-Dzin-Mobile";
}

core::iconnector* core_dispatcher::get_core_connector() const
{
    std::lock_guard<std::mutex> lock(mutex_);
    return d_->coreConnector_;
}

core::iconnector* core_dispatcher::get_gui_connector() const
{
    std::lock_guard<std::mutex> lock(mutex_);
    return d_->guiConnector_;
}

core::icore_factory* core_dispatcher::get_factory() const
{
    std::lock_guard<std::mutex> lock(mutex_);
    return d_->coreFace_ ? d_->coreFace_->get_factory() : nullptr;
}

std::string core_dispatcher::get_device_id() const
{
    std::lock_guard<std::mutex> lock(mutex_);
    return d_->deviceId_;
}

// ============================================================================
// Методы для JNI
// ============================================================================
void core_dispatcher::process_voip_message(const std::string& account, 
                                           int msg_type, 
                                           const std::vector<char>& data)
{
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_ || !d_->coreConnector_)
        return;

    auto* collection = create_collection();
    if (!collection)
        return;

    collection->set_value("account", account);
    collection->set_value("msg_type", msg_type);

    // Передача бинарных VoIP данных через stream (рекомендуемый способ)
    if (!data.empty()) {
        if (auto* factory = get_factory()) {
            if (core::ifile* stream = factory->create_memory_stream()) {
                stream->write(reinterpret_cast<const uint8_t*>(data.data()), data.size());
                collection->set_value_as_stream("data", stream);
                stream->release();
            }
        }
    }

    post_message_to_core("process_voip_message", collection);
}

void core_dispatcher::send_message(const std::string& contact, const std::string& message)
{
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_ || !d_->coreConnector_)
        return;

    auto* collection = create_collection();
    if (!collection)
        return;

    collection->set_value("contact", contact);
    collection->set_value("message", message);

    post_message_to_core("send_message", collection);
}

void core_dispatcher::send_statistic_event(const std::string& eventName, 
                                           const core::stats::event_props_type& props)
{
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_ || !d_->coreConnector_)
        return;

    auto* collection = create_collection();
    if (!collection)
        return;

    collection->set_value("event_name", eventName);
    for (const auto& [key, value] : props) {
        collection->set_value(key, value);
    }

    post_message_to_core("send_statistic_event", collection);
}

qint64 core_dispatcher::post_message_to_core(std::string_view message,
                                             core::icollection* collection,
                                             const QObject* /*object*/,
                                             message_processed_callback callback)
{
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_ || !d_->coreConnector_) {
        if (collection) collection->release();
        return -1;
    }

    const qint64 seq = ++d_->lastSeq_;

    if (callback) {
        d_->callbacks_[seq] = std::move(callback);
    }

    // Ядро само управляет счётчиком, если нужно
    d_->coreConnector_->receive(message, seq, collection);

    // Мы больше не владеем коллекцией после передачи
    if (collection) {
        collection->release();
    }

    return seq;
}

core::icollection* core_dispatcher::create_collection() const
{
    std::lock_guard<std::mutex> lock(mutex_);
    if (!d_->coreFace_)
        return nullptr;
    return d_->coreFace_->create_collection();
}

// ============================================================================
// Высокоуровневые методы (оставлены как у тебя, с мелкими улучшениями)
// ============================================================================
qint64 core_dispatcher::get_history(const QString& contact, int early_count, int later_count)
{
    auto* collection = create_collection();
    if (!collection) return -1;

    collection->set_value("contact", contact.toStdString());
    collection->set_value("early_count", early_count);
    collection->set_value("later_count", later_count);

    return post_message_to_core("get_history", collection);
}

void core_dispatcher::set_last_read(const QString& contact, int64_t last_msg_id)
{
    auto* collection = create_collection();
    if (!collection) return;

    collection->set_value("contact", contact.toStdString());
    collection->set_value("last_msg_id", last_msg_id);

    post_message_to_core("set_last_read", collection);
}

void core_dispatcher::send_typing_status(const QString& contact, bool is_typing)
{
    auto* collection = create_collection();
    if (!collection) return;

    collection->set_value("contact", contact.toStdString());
    collection->set_value("is_typing", is_typing);

    post_message_to_core("send_typing_status", collection);
}

qint64 core_dispatcher::load_avatar(const QString& contact, int size)
{
    auto* collection = create_collection();
    if (!collection) return -1;

    collection->set_value("contact", contact.toStdString());
    collection->set_value("size", size);

    return post_message_to_core("load_avatar", collection);
}

void core_dispatcher::cancel_loading(const QString& url)
{
    auto* collection = create_collection();
    if (!collection) return;

    collection->set_value("url", url.toStdString());
    post_message_to_core("cancel_loading", collection);
}

qint64 core_dispatcher::get_user_info(const QString& aimid)
{
    auto* collection = create_collection();
    if (!collection) return -1;

    collection->set_value("aimid", aimid.toStdString());
    return post_message_to_core("get_user_info", collection);
}

qint64 core_dispatcher::subscribe_status(const std::vector<QString>& contacts)
{
    auto* collection = create_collection();
    if (!collection) return -1;

    std::string contactsStr;
    for (const auto& c : contacts) {
        if (!contactsStr.empty()) contactsStr += ",";
        contactsStr += c.toStdString();
    }
    collection->set_value("contacts", contactsStr);

    return post_message_to_core("subscribe_status", collection);
}

void core_dispatcher::get_active_dialogs()
{
    post_message_to_core("get_active_dialogs", nullptr);
}

qint64 core_dispatcher::erase_history(const QString& contactAimId)
{
    auto* collection = create_collection();
    if (!collection) return -1;

    collection->set_value("contact", contactAimId.toStdString());
    return post_message_to_core("erase_history", collection);
}

qint64 core_dispatcher::delete_messages(const QString& contactAimId, const std::vector<int64_t>& messageIds)
{
    auto* collection = create_collection();
    if (!collection) return -1;

    collection->set_value("contact", contactAimId.toStdString());
    for (auto id : messageIds) {
        collection->add_value("message_ids", id);
    }

    return post_message_to_core("delete_messages", collection);
}

qint64 core_dispatcher::add_reaction(int64_t msgId, const QString& chatId, const QString& reaction)
{
    auto* collection = create_collection();
    if (!collection) return -1;

    collection->set_value("msg_id", msgId);
    collection->set_value("chat_id", chatId.toStdString());
    collection->set_value("reaction", reaction.toStdString());

    return post_message_to_core("add_reaction", collection);
}

qint64 core_dispatcher::remove_reaction(int64_t msgId, const QString& chatId)
{
    auto* collection = create_collection();
    if (!collection) return -1;

    collection->set_value("msg_id", msgId);
    collection->set_value("chat_id", chatId.toStdString());

    return post_message_to_core("remove_reaction", collection);
}

ConnectionState core_dispatcher::get_connection_state() const
{
    std::lock_guard<std::mutex> lock(mutex_);
    return d_->connectionState_;
}

bool core_dispatcher::is_im_created() const
{
    std::lock_guard<std::mutex> lock(mutex_);
    return d_->isImCreated_;
}

// ============================================================================
// Глобальные функции
// ============================================================================
core_dispatcher* GetDispatcher()
{
    return g_dispatcher;
}

void createDispatcher()
{
    if (!g_dispatcher)
        g_dispatcher = new core_dispatcher();
}

void destroyDispatcher()
{
    delete g_dispatcher;
    g_dispatcher = nullptr;
}

} // namespace Ui
