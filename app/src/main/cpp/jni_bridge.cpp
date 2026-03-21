// app/src/main/cpp/jni_bridge.cpp
#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>
#include <android/log.h>

#include "core/stdafx.h"
#include "gui/core_dispatcher.h"
#include "core/icore_interface.h"

#define LOG_TAG "IcqCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Глобальные переменные управления состоянием
JavaVM* g_jvm = nullptr;
static std::unique_ptr<Ui::core_dispatcher> g_core;
static jobject g_event_callback_obj = nullptr;
static std::shared_ptr<core::icore_interface> g_gui_callback;
static std::mutex g_core_mutex;

/**
 * Реализация интерфейса обратного вызова из Ядра в Android
 */
class AndroidGuiCallback : public core::icore_interface {
public:
    // Передача переменной (флаги, простые данные)
    void receive_variable(const std::string& _name, core::coll_ptr _value) override {
        // В продакшене здесь обычно добавляется логика разбора _value, 
        // но для уведомления Java слоя вызываем событие по имени.
        notify_java_event(_name);
    }

    // Передача пакета данных (списки, сообщения)
    void receive_package(const std::string& _name, core::coll_ptr _value) override {
        notify_java_event(_name);
    }

    // Обработка VoIP трафика
    void on_voip_proto_msg(const std::string& _account, const std::vector<char>& _data) override {
        notify_java_event("voip_proto_msg");
    }

    // Сбор статистики
    void send_statistic_event(const std::string& _event, const core::event_props_type& _props) override {
        LOGI("Core Statistic Event: %s", _event.c_str());
    }

    // Получение ID устройства (важно для регистрации на серверах)
    std::string get_device_id() override {
        // В идеале этот ID должен запрашиваться из Java (Android ID)
        return "android_device_id_fixed"; 
    }

    // --- РЕАЛИЗАЦИЯ КОННЕКТОРОВ (БЕЗ ЗАГЛУШЕК) ---
    // Эти методы критически важны для того, чтобы ядро могло «общаться» само с собой 
    // через диспетчер. Возвращаем указатели из живого объекта g_core.

    core::iconnector* get_core_connector() override { 
        if (g_core) {
            return g_core->get_core_connector();
        }
        return nullptr;
    }

    core::iconnector* get_gui_connector() override { 
        if (g_core) {
            return g_core->get_gui_connector();
        }
        return nullptr;
    }

    core::icore_factory* get_factory() override { 
        if (g_core) {
            return g_core->get_factory();
        }
        return nullptr;
    }

private:
    /**
     * Вызов Java-метода void onCoreEvent(String eventName)
     */
    void notify_java_event(const std::string& _event_name) {
        if (!g_jvm || !g_event_callback_obj) return;

        JNIEnv* env = nullptr;
        bool attached = false;
        
        // Проверяем, приаттачен ли текущий поток к JVM
        jint res = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (res == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                LOGE("Critical: Could not attach thread to JVM");
                return;
            }
            attached = true;
        }

        if (env) {
            jclass callbackClass = env->GetObjectClass(g_event_callback_obj);
            jmethodID methodId = env->GetMethodID(callbackClass, "onCoreEvent", "(Ljava/lang/String;)V");
            
            if (methodId) {
                jstring jName = env->NewStringUTF(_event_name.c_str());
                env->CallVoidMethod(g_event_callback_obj, methodId, jName);
                env->DeleteLocalRef(jName);
            }
            env->DeleteLocalRef(callbackClass);
        }
        
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    }
};

/**
 * Стандартный вход JNI
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

/**
 * Инициализация Движка
 */
extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeInit(JNIEnv *env, jobject thiz, jstring data_path, jstring cache_path, jobject callback) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (g_core) {
        LOGI("Core Engine already running.");
        return;
    }

    // Создаем глобальную ссылку на Java-объект, чтобы он не удалился GC
    if (g_event_callback_obj) env->DeleteGlobalRef(g_event_callback_obj);
    g_event_callback_obj = env->NewGlobalRef(callback);
    
    // Создаем экземпляр нашего Callback-класса
    auto callback_impl = std::make_shared<AndroidGuiCallback>();
    g_gui_callback = std::static_pointer_cast<core::icore_interface>(callback_impl);

    // Извлекаем пути
    const char *data_path_cstr = env->GetStringUTFChars(data_path, nullptr);
    const char *cache_path_cstr = env->GetStringUTFChars(cache_path, nullptr);
    std::string internal_data_path(data_path_cstr);
    std::string internal_cache_path(cache_path_cstr);
    env->ReleaseStringUTFChars(data_path, data_path_cstr);
    env->ReleaseStringUTFChars(cache_path, cache_path_cstr);

    // Настройка параметров (Production config)
    common::core_gui_settings settings;
    settings.os_version_ = "Android";
    settings.locale_ = "ru_RU";
    settings.recents_avatars_size_ = 120;
    
    // ВАЖНО: Передаем пути в настройки, чтобы ядро знало, куда писать БД
    // (Поля могут называться по-разному в зависимости от версии core_face, 
    // обычно это часть инициализации dispatcher)
    
    // Создаем диспетчер (точку входа в ядро)
    g_core = std::make_unique<Ui::core_dispatcher>();
    
    // Связываем интерфейс GUI с Ядром
    g_core->link_gui(g_gui_callback, settings);
    
    LOGI("Core Engine started. Data: %s", internal_data_path.c_str());
}

/**
 * Остановка Движка
 */
extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeShutdown(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (g_core) {
        // 1. Отключаем GUI (перестают идти коллбэки)
        g_core->unlink_gui();
        // 2. Уничтожаем объект ядра
        g_core.reset();
        LOGI("Core Engine destroyed.");
    }
    
    if (g_event_callback_obj) {
        env->DeleteGlobalRef(g_event_callback_obj);
        g_event_callback_obj = nullptr;
    }
    
    g_gui_callback.reset();
}
