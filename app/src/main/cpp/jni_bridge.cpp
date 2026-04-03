// app/src/main/cpp/jni_bridge.cpp
#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <vector>
#include <atomic>
#include <android/log.h>

// Qt headers
#include <QTimer>
#include <QString>

// Core headers
#include "core/stdafx.h"
#include "gui/core_dispatcher.h"
#include "core/tools/tlv.h"
#include "core/Voip/voip_proxy.h"

#define LOG_TAG "DzinCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Глобальные переменные JNI (доступны из других файлов)
JavaVM* g_jvm = nullptr;
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
static std::string g_data_path;
static std::string g_cache_path;

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

    int32_t addref() override { return ++ref_count_; }
    
    int32_t release() override {
        int32_t res = --ref_count_;
        if (res == 0) {
            delete this;
        }
        return res;
    }

    void receive_variable(const std::string& _name, core::coll_ptr /*_value*/) override {
        notifyJavaEvent(_name, nullptr);
    }

    void receive_package(const std::string& _name, core::coll_ptr /*_value*/) override {
        notifyJavaEvent(_name, nullptr);
    }

    void on_voip_proto_msg(const std::string& _account, const std::vector<char>& _data) override {
        notifyJavaVoipEvent(_account, _data);
    }

    void send_statistic_event(const std::string& _event, const core::stats::event_props_type& _props) override {
        notifyJavaStatisticEvent(_event, _props);
    }

    std::string get_device_id() override {
        std::lock_guard<std::mutex> lock(g_core_mutex);
        return g_device_id;
    }

    core::iconnector* get_core_connector() override {
        if (!g_core) return nullptr;
        return g_core->get_core_connector();
    }

    core::iconnector* get_gui_connector() override {
        if (!g_core) return nullptr;
        return g_core->get_gui_connector();
    }

    core::icore_factory* get_factory() override {
        if (!g_core) return nullptr;
        return g_core->get_factory();
    }

private:
    std::atomic<int32_t> ref_count_;

    void notifyJavaEvent(const std::string& _name, core::coll_ptr /*value*/) {
        JniThreadGuard guard;
        JNIEnv* env = guard.getEnv();
        if (!env || !g_event_callback_obj || !g_on_event_method) return;

        jstring jname = env->NewStringUTF(_name.c_str());
        env->CallVoidMethod(g_event_callback_obj, g_on_event_method, jname);
        env->DeleteLocalRef(jname);
    }

    void notifyJavaVoipEvent(const std::string& _account, const std::vector<char>& _data) {
        JniThreadGuard guard;
        JNIEnv* env = guard.getEnv();
        if (!env || !g_event_callback_obj || !g_on_voip_event_method) return;

        jstring jaccount = env->NewStringUTF(_account.c_str());
        jbyteArray jdata = env->NewByteArray(_data.size());
        if (jdata) {
            env->SetByteArrayRegion(jdata, 0, _data.size(), 
                                   reinterpret_cast<const jbyte*>(_data.data()));
        }

        env->CallVoidMethod(g_event_callback_obj, g_on_voip_event_method, jaccount, jdata);

        if (jaccount) env->DeleteLocalRef(jaccount);
        if (jdata) env->DeleteLocalRef(jdata);
    }

    void notifyJavaStatisticEvent(const std::string& _event, 
                                  const core::stats::event_props_type& _props) {
        JniThreadGuard guard;
        JNIEnv* env = guard.getEnv();
        if (!env || !g_event_callback_obj || !g_on_statistic_method) return;

        jstring jevent = env->NewStringUTF(_event.c_str());
        
        std::string props_str;
        for (auto it = _props.begin(); it != _props.end(); ++it) {
            props_str += it->first + "=" + it->second;
            if (std::next(it) != _props.end()) {
                props_str += ";";
            }
        }
        jstring jprops = env->NewStringUTF(props_str.c_str());

        env->CallVoidMethod(g_event_callback_obj, g_on_statistic_method, jevent, jprops);

        if (jevent) env->DeleteLocalRef(jevent);
        if (jprops) env->DeleteLocalRef(jprops);
    }
};

// JNI_OnLoad
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_jvm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Failed to get JNIEnv in JNI_OnLoad");
        return JNI_ERR;
    }

    jclass localClass = env->FindClass("com/icq/mobile/core/IcqCoreEngine");
    if (!localClass) {
        LOGE("Failed to find IcqCoreEngine class");
        return JNI_ERR;
    }

    g_callback_class = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    g_on_event_method = env->GetMethodID(g_callback_class, "onCoreEvent", "(Ljava/lang/String;)V");
    g_on_voip_event_method = env->GetMethodID(g_callback_class, "onVoipEvent", "(Ljava/lang/String;[B)V");
    g_on_statistic_method = env->GetMethodID(g_callback_class, "onStatisticEvent", "(Ljava/lang/String;Ljava/lang/String;)V");

    if (!g_on_event_method || !g_on_voip_event_method || !g_on_statistic_method) {
        LOGE("Failed to find methods in IcqCoreEngine class");
        return JNI_ERR;
    }

    env->DeleteLocalRef(localClass);

    LOGI("JNI Bridge initialized successfully");
    return JNI_VERSION_1_6;
}

// nativeInit
extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeInit(JNIEnv* env, jobject thiz, 
                                                   jstring data_path, 
                                                   jstring cache_path, 
                                                   jstring device_id,
                                                   jobject callback) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (g_initialized) {
        LOGW("Core Engine already initialized");
        return;
    }

    // Сохраняем пути
    if (data_path) {
        const char* d_path = env->GetStringUTFChars(data_path, nullptr);
        if (d_path) {
            g_data_path = d_path;
            env->ReleaseStringUTFChars(data_path, d_path);
        }
    }

    if (cache_path) {
        const char* c_path = env->GetStringUTFChars(cache_path, nullptr);
        if (c_path) {
            g_cache_path = c_path;
            env->ReleaseStringUTFChars(cache_path, c_path);
        }
    }

    // Сохраняем device_id
    if (device_id) {
        const char* d_id = env->GetStringUTFChars(device_id, nullptr);
        if (d_id) {
            g_device_id = d_id;
            env->ReleaseStringUTFChars(device_id, d_id);
        }
    }

    // Сохраняем callback объект
    g_event_callback_obj = env->NewGlobalRef(thiz);
    if (callback) {
        // Сохраняем дополнительный callback если нужен
        jobject callback_ref = env->NewGlobalRef(callback);
        // TODO: сохранить callback_ref если требуется
    }

    // Создаем core_dispatcher
    g_core = std::make_unique<Ui::core_dispatcher>();
    if (!g_core) {
        LOGE("Failed to create core_dispatcher");
        return;
    }

    // Создаем callback интерфейс
    g_gui_callback = std::make_shared<AndroidGuiCallback>();
    if (!g_gui_callback) {
        LOGE("Failed to create AndroidGuiCallback");
        g_core.reset();
        return;
    }

    g_initialized = true;
    LOGI("Core Engine initialized with data_path: %s, cache_path: %s", 
         g_data_path.c_str(), g_cache_path.c_str());
}

// nativeShutdown
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
    LOGI("Core Engine shutdown complete");
}

// nativeGetVersion
extern "C" JNIEXPORT jstring JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeGetVersion(JNIEnv* env, jobject /*thiz*/) {
    return env->NewStringUTF("2.0.0-Dzin");
}

// nativeIsInitialized
extern "C" JNIEXPORT jboolean JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeIsInitialized(JNIEnv* /*env*/, jobject /*thiz*/) {
    return g_initialized ? JNI_TRUE : JNI_FALSE;
}

// nativeProcessVoipMsg
extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeProcessVoipMsg(JNIEnv* env, jobject /*thiz*/,
    jstring account, jint msg_type, jbyteArray data) {

    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (!g_core || !g_initialized) {
        LOGW("Core not initialized, cannot process VoIP message");
        return;
    }

    const char* acc = env->GetStringUTFChars(account, nullptr);
    if (!acc) {
        LOGE("Failed to get account string");
        return;
    }
    
    jsize len = env->GetArrayLength(data);
    jbyte* buf = env->GetByteArrayElements(data, nullptr);
    
    if (!buf) {
        LOGE("Failed to get data buffer");
        env->ReleaseStringUTFChars(account, acc);
        return;
    }

    std::vector<char> v_data(reinterpret_cast<char*>(buf),
                             reinterpret_cast<char*>(buf) + len);

    g_core->process_voip_message(acc, msg_type, v_data);

    env->ReleaseByteArrayElements(data, buf, JNI_ABORT);
    env->ReleaseStringUTFChars(account, acc);
}

// nativeSendMessage
extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeSendMessage(JNIEnv* env, jobject /*thiz*/,
    jstring contact, jstring message) {

    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (!g_core || !g_initialized) {
        LOGW("Core not initialized, cannot send message");
        return;
    }

    const char* c_str = env->GetStringUTFChars(contact, nullptr);
    const char* m_str = env->GetStringUTFChars(message, nullptr);

    if (!c_str || !m_str) {
        LOGE("Failed to get contact or message string");
        if (c_str) env->ReleaseStringUTFChars(contact, c_str);
        if (m_str) env->ReleaseStringUTFChars(message, m_str);
        return;
    }

    g_core->send_message(c_str, m_str);

    env->ReleaseStringUTFChars(contact, c_str);
    env->ReleaseStringUTFChars(message, m_str);
}

// Дополнительные методы для работы с видео
extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeSetVideoRenderers(JNIEnv* env, jobject /*thiz*/,
    jobject localSurface, jobject remoteSurface) {
    
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (!g_core || !g_initialized) {
        LOGW("Core not initialized, cannot set video renderers");
        return;
    }
    
    // TODO: реализация установки video renderers через android_video_renderer.cpp
    LOGI("Video renderers set: local=%p, remote=%p", localSurface, remoteSurface);
}
