#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

// Подключаем заголовки ядра ICQ
#include "core.h"
#include "common_defs.h"

#define LOG_TAG "IcqCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::unique_ptr<core::core_dispatcher> g_core;

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCore_initCore(JNIEnv *env, jobject thiz, jstring data_path, jstring cache_path) {
    const char *data_path_cstr = env->GetStringUTFChars(data_path, nullptr);
    const char *cache_path_cstr = env->GetStringUTFChars(cache_path, nullptr);
    
    LOGI("Инициализация ICQ Core. Data path: %s", data_path_cstr);

    // В Android пути к профилю и кэшу отличаются от десктопных
    // Требуется инъекция путей в ядро перед стартом
    std::wstring w_data_path(data_path_cstr, data_path_cstr + strlen(data_path_cstr));
    
    common::core_gui_settings settings;
    settings.os_version_ = "Android";
    settings.locale_ = "ru_RU";
    
    g_core = std::make_unique<core::core_dispatcher>();
    
    // В реальном проекте необходимо реализовать icore_interface для связи GUI и CORE
    // g_core->link_gui(..., settings); 
    
    env->ReleaseStringUTFChars(data_path, data_path_cstr);
    env->ReleaseStringUTFChars(cache_path, cache_path_cstr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCore_shutdownCore(JNIEnv *env, jobject thiz) {
    if (g_core) {
        g_core->unlink_gui();
        g_core.reset();
        LOGI("ICQ Core остановлен");
    }
}
