// app/src/main/cpp/jni_bridge.cpp
#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <android/log.h>

#include "core/stdafx.h"
#include "gui/core_dispatcher.h"

#define LOG_TAG "IcqCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Глобальные переменные
JavaVM* g_jvm = nullptr;
static jclass g_callback_class = nullptr;
static jmethodID g_on_event_method = nullptr;

static std::unique_ptr<Ui::core_dispatcher> g_core;
static jobject g_event_callback_obj = nullptr;
static std::shared_ptr<core::icore_interface> g_gui_callback;
static std::mutex g_core_mutex;

/**
 * Получение JNIEnv с автоматическим присоединением потока
 */
static JNIEnv* getJniEnv(bool& mustDetach) {
    mustDetach = false;
    JNIEnv* env = nullptr;
    jint res = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            mustDetach = true;
        } else {
            LOGE("Failed to attach thread to JVM");
        }
    }
    return env;
}

/**
 * Реализация интерфейса обратного вызова из Ядра в Android
 */
class AndroidGuiCallback : public core::icore_interface {
public:
    virtual int32_t addref() override { return 1; }
    virtual int32_t release() override { return 0; }
    
    void receive_variable(const std::string& _name, core::coll_ptr _value) override {
        notify_java_event(_name);
    }

    void receive_package(const std::string& _name, core::coll_ptr _value) override {
        notify_java_event(_name);
    }

    void on_voip_proto_msg(const std::string& _account, const std::vector<char>& _data) override {
        notify_java_event("voip_proto_msg");
    }

    void send_statistic_event(const std::string& _event, const core::event_props_type& _props) override {
        LOGI("Core Statistic Event: %s", _event.c_str());
    }

    std::string get_device_id() override {
        // В продакшене это значение должно приходить из Java
        return "android_device_id_stable";
    }

    core::iconnector* get_core_connector() override { 
        return g_core ? g_core->get_core_connector() : nullptr;
    }

    core::iconnector* get_gui_connector() override { 
        return g_core ? g_core->get_gui_connector() : nullptr;
    }

    core::icore_factory* get_factory() override { 
        return g_core ? g_core->get_factory() : nullptr;
    }

private:
    void notify_java_event(const std::string& _event_name) {
        if (!g_event_callback_obj || !g_on_event_method) return;

        bool mustDetach = false;
        JNIEnv* env = getJniEnv(mustDetach);
        if (!env) return;

        jstring jName = env->NewStringUTF(_event_name.c_str());
        env->CallVoidMethod(g_event_callback_obj, g_on_event_method, jName);
        env->DeleteLocalRef(jName);
        
        if (mustDetach) {
            g_jvm->DetachCurrentThread();
        }
    }
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    
    // Кэшируем класс и метод для производительности
    jclass localClass = env->FindClass("com/icq/mobile/core/IcqCoreEngine");
    if (localClass) {
        g_callback_class = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        g_on_event_method = env->GetMethodID(g_callback_class, "onCoreEvent", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(localClass);
    } else {
        LOGE("Failed to find IcqCoreEngine class");
        return JNI_ERR;
    }
    
    return JNI_VERSION_1_6;
}

/**
 * Инициализация Движка
 */
extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeInit(JNIEnv *env, jobject thiz, 
                                                   jstring data_path, 
                                                   jstring cache_path, 
                                                   jobject callback) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (g_core) {
        LOGI("Core Engine already running.");
        return;
    }

    // Сохраняем callback
    if (g_event_callback_obj) {
        env->DeleteGlobalRef(g_event_callback_obj);
    }
    g_event_callback_obj = env->NewGlobalRef(callback);
    
    // Создаем callback-объект
    auto callback_impl = std::make_shared<AndroidGuiCallback>();
    g_gui_callback = std::static_pointer_cast<core::icore_interface>(callback_impl);

    // Извлекаем пути
    const char *data_path_cstr = env->GetStringUTFChars(data_path, nullptr);
    const char *cache_path_cstr = env->GetStringUTFChars(cache_path, nullptr);
    std::string internal_data_path(data_path_cstr);
    std::string internal_cache_path(cache_path_cstr);
    env->ReleaseStringUTFChars(data_path, data_path_cstr);
    env->ReleaseStringUTFChars(cache_path, cache_path_cstr);

    // Настройка параметров
    common::core_gui_settings settings;
    settings.os_version_ = "Android";
    settings.locale_ = "ru_RU";
    settings.recents_avatars_size_ = 120;
    
    // Создаем и линкуем ядро
    g_core = std::make_unique<Ui::core_dispatcher>();
    g_core->link_gui(g_gui_callback, settings);
    
    LOGI("Core Engine started successfully. Data at: %s, Cache at: %s", 
         internal_data_path.c_str(), internal_cache_path.c_str());
}

/**
 * Остановка Движка
 */
extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeShutdown(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (g_core) {
        g_core->unlink_gui();
        g_core.reset();
        LOGI("Core Engine destroyed.");
    }
    
    if (g_event_callback_obj) {
        env->DeleteGlobalRef(g_event_callback_obj);
        g_event_callback_obj = nullptr;
    }
    
    g_gui_callback.reset();
}
