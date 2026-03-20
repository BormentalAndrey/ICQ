#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>
#include <codecvt>
#include <locale>
#include <android/log.h>

// Подключаем заголовки ядра (Core)
#include "core/stdafx.h"
#include "core/core_dispatcher.h"
#include "core/icore_interface.h"

#define LOG_TAG "IcqCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Глобальные переменные для работы с JVM и ядром
JavaVM* g_jvm = nullptr;
static std::unique_ptr<core::core_dispatcher> g_core;
static jobject g_event_callback_obj = nullptr;
static std::shared_ptr<core::icore_interface> g_gui_callback;
static std::mutex g_core_mutex;

/**
 * Реализация интерфейса обратной связи из ядра в Java
 */
class AndroidGuiCallback : public core::icore_interface {
public:
    // Метод получения данных/переменных от ядра
    void receive_variable(const std::string& _name, core::coll_ptr _value) override {
        if (!g_jvm || !g_event_callback_obj) return;

        JNIEnv* env = nullptr;
        bool attached = false;
        jint res = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        
        if (res == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
            attached = true;
        }

        jclass callbackClass = env->GetObjectClass(g_event_callback_obj);
        jmethodID methodId = env->GetMethodID(callbackClass, "onCoreEvent", "(Ljava/lang/String;)V");
        
        if (methodId) {
            jstring jName = env->NewStringUTF(_name.c_str());
            env->CallVoidMethod(g_event_callback_obj, methodId, jName);
            env->DeleteLocalRef(jName);
        }
        
        env->DeleteLocalRef(callbackClass);
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    }

    // Метод для передачи статистических событий
    void send_statistic_event(const std::string& _event, const core::event_props_type& _props) override {
        // В продакшне здесь обычно идет логирование или проброс в аналитику (Firebase/AppMetrica)
        LOGI("Core Statistic Event: %s", _event.c_str());
    }
};

/**
 * Вызывается при загрузке библиотеки
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

/**
 * Инициализация ядра мессенджера
 */
extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeInit(JNIEnv *env, jobject thiz, jstring data_path, jstring cache_path, jobject callback) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (g_core) {
        LOGI("Core Engine is already initialized.");
        return;
    }

    // Сохраняем глобальную ссылку на Java-коллбэк
    if (g_event_callback_obj) {
        env->DeleteGlobalRef(g_event_callback_obj);
    }
    g_event_callback_obj = env->NewGlobalRef(callback);
    
    // Создаем экземпляр нашего коллбэка для ядра
    auto callback_impl = std::make_shared<AndroidGuiCallback>();
    g_gui_callback = std::static_pointer_cast<core::icore_interface>(callback_impl);

    // Получаем путь к данным и конвертируем в wstring (для кроссплатформенного ядра)
    const char *data_path_cstr = env->GetStringUTFChars(data_path, nullptr);
    std::string path_str(data_path_cstr);
    env->ReleaseStringUTFChars(data_path, data_path_cstr);

    common::core_gui_settings settings;
    settings.os_version_ = "Android";
    settings.locale_ = "ru_RU";
    
    // Корректная конвертация UTF-8 в широкую строку (wstring) для путей профиля
    try {
        std::wstring_convert<std::codecvt_utf8<wchar_t>> converter;
        settings.profile_path_ = converter.from_bytes(path_str);
    } catch (...) {
        // Fallback на случай проблем с локалью
        settings.profile_path_ = std::wstring(path_str.begin(), path_str.end());
    }
    
    // Запуск ядра
    g_core = std::make_unique<core::core_dispatcher>();
    g_core->link_gui(g_gui_callback, settings);
    
    LOGI("Core Engine initialized. Path: %s", path_str.c_str());
}

/**
 * Завершение работы ядра
 */
extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeShutdown(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_core_mutex);
    
    if (g_core) {
        g_core->unlink_gui();
        g_core.reset();
        LOGI("Core Engine unlinked and reset.");
    }
    
    if (g_event_callback_obj) {
        env->DeleteGlobalRef(g_event_callback_obj);
        g_event_callback_obj = nullptr;
    }
    
    g_gui_callback.reset();
    LOGI("Core Engine shutdown complete.");
}
