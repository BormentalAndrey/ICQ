#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <thread>
#include <map>
#include <android/log.h>

// Подключаем stdafx.h для im_assert и базовых определений
#include "../../core/stdafx.h"

// НЕ ПЕРЕОПРЕДЕЛЯЕМ event_props_type - оно уже есть в common.h

#define LOG_TAG "IcqCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Теперь включаем заголовки ядра с правильными путями
#include "core.h"
#include "gui/core_dispatcher.h"  // Файл в папке gui/
#include "gui_interface.h"

JavaVM* g_jvm = nullptr;
static std::unique_ptr<core::core_dispatcher> g_core;
static jobject g_event_callback_obj = nullptr;

class AndroidGuiCallback : public core::icore_interface {
public:
    void post_message_to_gui(const char* message) override {
        if (!g_jvm || !g_event_callback_obj) return;

        JNIEnv* env = nullptr;
        bool attached = false;
        
        jint getEnvRes = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (getEnvRes == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
            attached = true;
        }

        jclass callbackClass = env->GetObjectClass(g_event_callback_obj);
        jmethodID methodId = env->GetMethodID(callbackClass, "onCoreEvent", "(Ljava/lang/String;)V");
        
        if (methodId) {
            jstring jMessage = env->NewStringUTF(message);
            env->CallVoidMethod(g_event_callback_obj, methodId, jMessage);
            env->DeleteLocalRef(jMessage);
        }
        
        env->DeleteLocalRef(callbackClass);

        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    }
};

static std::shared_ptr<AndroidGuiCallback> g_gui_callback;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeInit(JNIEnv *env, jobject thiz, jstring data_path, jstring cache_path, jobject callback) {
    const char *data_path_cstr = env->GetStringUTFChars(data_path, nullptr);
    
    if (g_event_callback_obj) {
        env->DeleteGlobalRef(g_event_callback_obj);
    }
    g_event_callback_obj = env->NewGlobalRef(callback);
    g_gui_callback = std::make_shared<AndroidGuiCallback>();

    common::core_gui_settings settings;
    settings.os_version_ = "Android";
    settings.locale_ = "ru_RU";
    
    std::string path_str(data_path_cstr);
    settings.profile_path_ = std::wstring(path_str.begin(), path_str.end());
    
    g_core = std::make_unique<core::core_dispatcher>();
    g_core->link_gui(g_gui_callback, settings);
    
    LOGI("Core Engine started. Base path: %s", data_path_cstr);

    env->ReleaseStringUTFChars(data_path, data_path_cstr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeShutdown(JNIEnv *env, jobject thiz) {
    if (g_core) {
        g_core->unlink_gui();
        g_core.reset();
    }
    if (g_event_callback_obj) {
        env->DeleteGlobalRef(g_event_callback_obj);
        g_event_callback_obj = nullptr;
    }
    g_gui_callback.reset();
    LOGI("Core Engine safely stopped");
}
