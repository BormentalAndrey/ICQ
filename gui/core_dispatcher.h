// gui/core_dispatcher.h
#pragma once

#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <functional>
#include <atomic>

// Только необходимые Qt заголовки для мобильной версии
#include <QObject>
#include <QString>

// Core interfaces
#include "../corelib/core_face.h"
#include "../corelib/collection_helper.h"
#include "../corelib/icore_interface.h"

namespace core
{
    struct iconnector;
    struct icore_factory;
    
    namespace stats
    {
        using event_props_type = std::map<std::string, std::string>;
        enum class stats_event_names;
        enum class im_stat_event_names;
    }
}

namespace Ui
{
    enum class ConnectionState
    {
        stateUnknown = 0,
        stateConnecting = 1,
        stateUpdating = 2,
        stateOnline = 3
    };

    using message_processed_callback = std::function<void(core::icollection*)>;

    /**
     * @brief Основной диспетчер для мобильной версии ICQ
     * 
     * Упрощенная версия core_dispatcher для Android/iOS,
     * содержит только необходимые методы для работы через JNI
     */
    class core_dispatcher : public QObject
    {
        Q_OBJECT

    Q_SIGNALS:
        // Сигналы для обратного вызова в Java/Kotlin
        void coreEvent(const QString& eventName);
        void voipEvent(const QString& account, const QByteArray& data);
        void statisticEvent(const QString& eventName, const QString& props);

        // Основные сигналы состояния
        void loginComplete();
        void connectionStateChanged(const Ui::ConnectionState& state);
        void im_created();
        void myInfo();
        void contactList(const QString& listData);

    public:
        /**
         * @brief Конструктор
         */
        core_dispatcher();
        
        /**
         * @brief Деструктор
         */
        virtual ~core_dispatcher();

        /**
         * @brief Инициализация диспетчера
         * @param dataPath Путь для хранения данных
         * @param cachePath Путь для кэша
         * @param deviceId Уникальный ID устройства
         * @return true если инициализация успешна
         */
        bool init(const std::string& dataPath, const std::string& cachePath, const std::string& deviceId);

        /**
         * @brief Очистка ресурсов и отвязка GUI
         */
        void unlink_gui();

        /**
         * @brief Проверка, инициализирован ли диспетчер
         */
        bool is_initialized() const;

        /**
         * @brief Получение версии ядра
         */
        std::string get_version() const;

        // ========== Методы для JNI моста ==========
        
        /**
         * @brief Получение core коннектора
         */
        core::iconnector* get_core_connector() const;

        /**
         * @brief Получение GUI коннектора
         */
        core::iconnector* get_gui_connector() const;

        /**
         * @brief Получение фабрики core
         */
        core::icore_factory* get_factory() const;

        /**
         * @brief Обработка VoIP сообщения
         * @param account Аккаунт пользователя
         * @param msgType Тип сообщения
         * @param data Данные сообщения
         */
        void process_voip_message(const std::string& account, int msg_type, const std::vector<char>& data);

        /**
         * @brief Отправка текстового сообщения
         * @param contact Контакт получателя
         * @param message Текст сообщения
         */
        void send_message(const std::string& contact, const std::string& message);

        /**
         * @brief Отправка статистического события
         * @param eventName Название события
         * @param props Свойства события
         */
        void send_statistic_event(const std::string& eventName, const core::stats::event_props_type& props);

        /**
         * @brief Получение ID устройства
         */
        std::string get_device_id() const;

        /**
         * @brief Отправка сообщения в core
         * @param message Имя сообщения
         * @param collection Коллекция с данными
         * @param callback Callback функция
         * @return ID сообщения
         */
        qint64 post_message_to_core(std::string_view message, 
                                    core::icollection* collection,
                                    const QObject* object = nullptr,
                                    message_processed_callback callback = nullptr);

        /**
         * @brief Создание коллекции для передачи данных
         */
        core::icollection* create_collection() const;

        /**
         * @brief Отправка статистики
         */
        qint64 post_stats_to_core(core::stats::stats_event_names eventName, 
                                  const core::stats::event_props_type& props = {});

        /**
         * @brief Отправка IM статистики
         */
        qint64 post_im_stats_to_core(core::stats::im_stat_event_names eventName,
                                     const core::stats::event_props_type& props = {});

        /**
         * @brief Получение истории сообщений
         */
        qint64 get_history(const QString& contact, int early_count = 50, int later_count = 50);

        /**
         * @brief Установка статуса "прочитано"
         */
        void set_last_read(const QString& contact, int64_t last_msg_id);

        /**
         * @brief Отправка статуса печатания
         */
        void send_typing_status(const QString& contact, bool is_typing);

        /**
         * @brief Загрузка аватара контакта
         */
        qint64 load_avatar(const QString& contact, int size);

        /**
         * @бота Отмена загрузки
         */
        void cancel_loading(const QString& url);

        /**
         * @brief Получение информации о пользователе
         */
        qint64 get_user_info(const QString& aimid);

        /**
         * @brief Получение статуса пользователя
         */
        qint64 subscribe_status(const std::vector<QString>& contacts);

        /**
         * @brief Получение последних активных диалогов
         */
        void get_active_dialogs();

        /**
         * @brief Очистка истории переписки
         */
        qint64 erase_history(const QString& contactAimId);

        /**
         * @brief Удаление сообщений
         */
        qint64 delete_messages(const QString& contactAimId, const std::vector<int64_t>& messageIds);

        /**
         * @бота Отправка реакции на сообщение
         */
        qint64 add_reaction(int64_t msgId, const QString& chatId, const QString& reaction);

        /**
         * @brief Удаление реакции
         */
        qint64 remove_reaction(int64_t msgId, const QString& chatId);

        /**
         * @brief Получение состояния соединения
         */
        ConnectionState get_connection_state() const;

        /**
         * @brief Проверка, создан ли IM
         */
        bool is_im_created() const;

    private:
        struct Impl;
        std::unique_ptr<Impl> d_;
        mutable std::mutex mutex_;
        std::atomic<bool> initialized_{false};
    };

    /**
     * @brief Получение глобального экземпляра диспетчера
     */
    core_dispatcher* GetDispatcher();

    /**
     * @brief Создание глобального диспетчера
     */
    void createDispatcher();

    /**
     * @brief Уничтожение глобального диспетчера
     */
    void destroyDispatcher();
}
