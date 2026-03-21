// app/src/main/cpp/jni_bridge.cpp
#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>
#include <android/log.h>

#include "corelib/core_face.h"
#include "common.shared/common_defs.h"

#define LOG_TAG "IcqCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

JavaVM* g_jvm = nullptr;
static jobject g_event_callback_obj = nullptr;
static std::shared_ptr<core::icore_interface> g_gui_callback;
static std::mutex g_core_mutex;

class AndroidGuiCallback : public core::icore_interface {
public:
    core::iconnector* get_core_connector() override { return nullptr; }
    core::iconnector* get_gui_connector() override { return nullptr; }
    core::icore_factory* get_factory() override { return nullptr; }
    
    void notify_java_event(const std::string& _event_name) {
        if (!g_jvm || !g_event_callback_obj) return;

        JNIEnv* env = nullptr;
        bool attached = false;
        jint res = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        
        if (res == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                LOGE("Failed to attach thread to JVM");
                return;
            }
            attached = true;
        }

        jclass callbackClass = env->GetObjectClass(g_event_callback_obj);
        jmethodID methodId = env->GetMethodID(callbackClass, "onCoreEvent", "(Ljava/lang/String;)V");
        
        if (methodId) {
            jstring jName = env->NewStringUTF(_event_name.c_str());
            env->CallVoidMethod(g_event_callback_obj, methodId, jName);
            env->DeleteLocalRef(jName);
        }
        
        env->DeleteLocalRef(callbackClass);
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    }
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeInit(JNIEnv *env, jobject thiz, jstring data_path, jstring cache_path, jobject callback) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (g_event_callback_obj) {
        env->DeleteGlobalRef(g_event_callback_obj);
    }
    g_event_callback_obj = env->NewGlobalRef(callback);
    
    g_gui_callback = std::make_shared<AndroidGuiCallback>();

    const char *data_path_cstr = env->GetStringUTFChars(data_path, nullptr);
    std::string path_str(data_path_cstr);
    env->ReleaseStringUTFChars(data_path, data_path_cstr);

    common::core_gui_settings settings;
    settings.os_version_ = "Android";
    settings.locale_ = "ru_RU";
    settings.recents_avatars_size_ = 96;
    
    // Внимание: для Android здесь потребуется инициализация без использования Qt, 
    // например, прямым вызовом фабрики ядра:
    // auto factory = core::get_core_factory();
    // core_instance = factory->create_core();
    // core_instance->link_gui(g_gui_callback.get(), settings);
    
    LOGI("Core Engine initialized. Path: %s", path_str.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeShutdown(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    // Отключение ядра
    // core_instance->unlink_gui();
    
    if (g_event_callback_obj) {
        env->DeleteGlobalRef(g_event_callback_obj);
        g_event_callback_obj = nullptr;
    }
    
    g_gui_callback.reset();
    LOGI("Core Engine shutdown complete.");
}
