#include <jni.h>
#include <android/log.h>
#include <memory>
#include <string>
#include <mutex>

// Благодаря обновленному CMakeLists, эти пути теперь разрешатся корректно
#include "core/stdafx.h"
#include "gui/core_dispatcher.h" 

#define LOG_TAG "DzinCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static std::unique_ptr<Ui::core_dispatcher> g_core;
static std::mutex g_init_mutex;

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeInit(JNIEnv* env, jobject thiz, 
                                                   jstring data_path, 
                                                   jstring cache_path) {
    std::lock_guard<std::mutex> lock(g_init_mutex);
    if (g_core) return;

    const char* d_path = env->GetStringUTFChars(data_path, nullptr);
    const char* c_path = env->GetStringUTFChars(cache_path, nullptr);

    common::core_gui_settings settings;
    settings.data_path_ = d_path;
    settings.cache_path_ = c_path;
    settings.os_version_ = "Android";

    g_core = std::make_unique<Ui::core_dispatcher>();
    // Здесь должна быть логика link_gui с вашим callback-интерфейсом
    
    env->ReleaseStringUTFChars(data_path, d_path);
    env->ReleaseStringUTFChars(cache_path, c_path);
    
    LOGI("Dzin Core Initialized");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeGetVersion(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF("2.0.0-Dzin");
}
