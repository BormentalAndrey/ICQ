// app/src/main/cpp/jni_bridge.cpp
#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <vector>
#include <atomic>
#include <android/log.h>

// Основные заголовки проекта (Qt и Core)
#include "core/stdafx.h"
#include "gui/core_dispatcher.h"

#define LOG_TAG "DzinCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Глобальные переменные JNI
static JavaVM* g_jvm = nullptr;
static jclass g_callback_class = nullptr;
static jmethodID g_on_event_method = nullptr;
static jmethodID g_on_voip_event_method = nullptr;
static jmethodID g_on_statistic_method = nullptr;

// Глобальные объекты движка
static std::unique_ptr<Ui::core_dispatcher> g_core;
static jobject g_event_callback_obj = nullptr;
static std::shared_ptr<core::icore_interface> g_gui_callback;
static std::mutex g_core_mutex;
static std::atomic<bool> g_initialized{false};
static std::string g_device_id;

// RAII-обертка для управления JNIEnv в фоновых потоках
class JniThreadGuard {
public:
    JniThreadGuard() : env(nullptr), mustDetach(false) {
        if (!g_jvm) return;
        jint res = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (res == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                mustDetach = true;
            }
        }
    }
    ~JniThreadGuard() {
        if (mustDetach && g_jvm) {
            g_jvm->DetachCurrentThread();
        }
    }
    JNIEnv* getEnv() const { return env; }
    bool isValid() const { return env != nullptr; }

private:
    JNIEnv* env;
    bool mustDetach;
};

// Реализация интерфейса обратного вызова
class AndroidGuiCallback : public core::icore_interface {
public:
    AndroidGuiCallback() : ref_count_(1) {}
    virtual ~AndroidGuiCallback() = default;
    
    // Управление временем жизни (Intrusive pointer pattern)
    int32_t addref() override { return ++ref_count_; }
    int32_t release() override {
        int32_t res = --ref_count_;
        if (res == 0) { /* Объект обычно управляется shared_ptr в jni_bridge */ }
        return res;
    }
    
    void receive_variable(const std::string& _name, core::coll_ptr _value) override {
        notifyJavaEvent(_name, _value);
    }

    void receive_package(const std::string& _name, core::coll_ptr _value) override {
        notifyJavaEvent(_name, _value);
    }

    void on_voip_proto_msg(const std::string& _account, const std::vector<char>& _data) override {
        notifyJavaVoipEvent(_account, _data);
    }

    void send_statistic_event(const std::string& _event, const core::event_props_type& _props) override {
        notifyJavaStatisticEvent(_event, _props);
    }

    std::string get_device_id() override {
        std::lock_guard<std::mutex> lock(g_core_mutex);
        return g_device_id;
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
    std::atomic<int32_t> ref_count_;

    void notifyJavaEvent(const std::string& _name, core::coll_ptr /*_value*/) {
        std::lock_guard<std::mutex> lock(g_core_mutex);
        if (!g_event_callback_obj || !g_on_event_method) return;

        JniThreadGuard guard;
        if (!guard.isValid()) return;

        jstring jName = guard.getEnv()->NewStringUTF(_name.c_str());
        guard.getEnv()->CallVoidMethod(g_event_callback_obj, g_on_event_method, jName);
        guard.getEnv()->DeleteLocalRef(jName);
    }
    
    void notifyJavaVoipEvent(const std::string& _account, const std::vector<char>& _data) {
        std::lock_guard<std::mutex> lock(g_core_mutex);
        if (!g_event_callback_obj || !g_on_voip_event_method) return;

        JniThreadGuard guard;
        if (!guard.isValid()) return;

        jstring jAccount = guard.getEnv()->NewStringUTF(_account.c_str());
        jbyteArray jData = guard.getEnv()->NewByteArray(static_cast<jsize>(_data.size()));
        guard.getEnv()->SetByteArrayRegion(jData, 0, static_cast<jsize>(_data.size()), reinterpret_cast<const jbyte*>(_data.data()));
        
        guard.getEnv()->CallVoidMethod(g_event_callback_obj, g_on_voip_event_method, jAccount, jData);
        
        guard.getEnv()->DeleteLocalRef(jAccount);
        guard.getEnv()->DeleteLocalRef(jData);
    }
    
    void notifyJavaStatisticEvent(const std::string& _event, const core::event_props_type& _props) {
        std::lock_guard<std::mutex> lock(g_core_mutex);
        if (!g_event_callback_obj || !g_on_statistic_method) return;

        JniThreadGuard guard;
        if (!guard.isValid()) return;

        jstring jEvent = guard.getEnv()->NewStringUTF(_event.c_str());
        
        // Сериализация свойств в простой формат "key=val;key2=val2"
        std::string props_str;
        for (const auto& [key, value] : _props) {
            if (!props_str.empty()) props_str += ";";
            props_str += key + "=" + value;
        }
        
        jstring jProps = guard.getEnv()->NewStringUTF(props_str.c_str());
        guard.getEnv()->CallVoidMethod(g_event_callback_obj, g_on_statistic_method, jEvent, jProps);
        
        guard.getEnv()->DeleteLocalRef(jEvent);
        guard.getEnv()->DeleteLocalRef(jProps);
    }
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_jvm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    
    jclass localClass = env->FindClass("com/icq/mobile/core/IcqCoreEngine");
    if (!localClass) return JNI_ERR;
    
    g_callback_class = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    g_on_event_method = env->GetMethodID(g_callback_class, "onCoreEvent", "(Ljava/lang/String;)V");
    g_on_voip_event_method = env->GetMethodID(g_callback_class, "onVoipEvent", "(Ljava/lang/String;[B)V");
    g_on_statistic_method = env->GetMethodID(g_callback_class, "onStatisticEvent", "(Ljava/lang/String;Ljava/lang/String;)V");
    
    env->DeleteLocalRef(localClass);
    LOGI("JNI Bridge initialized for Dzin application");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeInit(JNIEnv* env, jobject /*thiz*/, 
                                                   jstring data_path, 
                                                   jstring cache_path, 
                                                   jstring device_id,
                                                   jobject callback) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (g_initialized) {
        LOGI("Core Engine already running");
        return;
    }

    if (g_event_callback_obj) env->DeleteGlobalRef(g_event_callback_obj);
    g_event_callback_obj = env->NewGlobalRef(callback);
    
    const char* d_id = env->GetStringUTFChars(device_id, nullptr);
    if (d_id) {
        g_device_id = d_id;
        env->ReleaseStringUTFChars(device_id, d_id);
    }
    
    const char* d_path = env->GetStringUTFChars(data_path, nullptr);
    const char* c_path = env->GetStringUTFChars(cache_path, nullptr);
    
    std::string internal_data_path(d_path ? d_path : "");
    std::string internal_cache_path(c_path ? c_path : "");
    
    if (d_path) env->ReleaseStringUTFChars(data_path, d_path);
    if (c_path) env->ReleaseStringUTFChars(cache_path, c_path);

    common::core_gui_settings settings;
    settings.os_version_ = "Android";
    settings.locale_ = "ru_RU";
    settings.data_path_ = internal_data_path;
    settings.cache_path_ = internal_cache_path;
    
    try {
        g_gui_callback = std::make_shared<AndroidGuiCallback>();
        g_core = std::make_unique<Ui::core_dispatcher>();
        g_core->link_gui(g_gui_callback, settings);
        g_initialized = true;
        LOGI("Core Engine started successfully (Dzin Rebrand)");
    } catch (const std::exception& e) {
        LOGE("Core Init Failure: %s", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeShutdown(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (g_core) {
        g_core->unlink_gui();
        g_core.reset();
    }
    
    if (g_event_callback_obj) {
        env->DeleteGlobalRef(g_event_callback_obj);
        g_event_callback_obj = nullptr;
    }
    
    g_gui_callback.reset();
    g_initialized = false;
    LOGI("Core Engine Shutdown complete");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeGetVersion(JNIEnv* env, jobject /*thiz*/) {
    return env->NewStringUTF("2.0.0-Dzin");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeIsInitialized(JNIEnv* /*env*/, jobject /*thiz*/) {
    return g_initialized ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeProcessVoipMsg(JNIEnv* env, jobject /*thiz*/,
                                                             jstring account, jint msg_type,
                                                             jbyteArray data) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    if (!g_core || !g_initialized) return;
    
    const char* acc = env->GetStringUTFChars(account, nullptr);
    jsize len = env->GetArrayLength(data);
    jbyte* buf = env->GetByteArrayElements(data, nullptr);
    
    if (acc && buf) {
        std::vector<char> v_data(reinterpret_cast<char*>(buf), reinterpret_cast<char*>(buf) + len);
        g_core->process_voip_message(acc, msg_type, v_data);
    }
    
    if (buf) env->ReleaseByteArrayElements(data, buf, JNI_ABORT);
    if (acc) env->ReleaseStringUTFChars(account, acc);
}

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeSendMessage(JNIEnv* env, jobject /*thiz*/,
                                                          jstring contact, jstring message) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    if (!g_core || !g_initialized) return;
    
    const char* c_str = env->GetStringUTFChars(contact, nullptr);
    const char* m_str = env->GetStringUTFChars(message, nullptr);
    
    if (c_str && m_str) {
        g_core->send_message(c_str, m_str);
    }
    
    if (c_str) env->ReleaseStringUTFChars(contact, c_str);
    if (m_str) env->ReleaseStringUTFChars(message, m_str);
}
