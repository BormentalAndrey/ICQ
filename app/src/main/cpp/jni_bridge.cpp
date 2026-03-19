#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <thread>
#include <map>
#include <android/log.h>

// 1. ИСПРАВЛЕНИЕ im_assert: 
// Если CMake не прокинул дефайн, определяем его здесь ДО включения других заголовков ядра.
#ifndef im_assert
#   define im_assert(condition) ((void)0)
#endif

// 2. ИСПРАВЛЕНИЕ stats:
// Чтобы не было ошибки "no type named 'event_props_type'", 
// нужно убедиться, что заголовки статистики подключены в правильном порядке.
// Если im_stats.h не найден, проверьте путь в CMakeLists.txt
#include "core/stats/im/im_stats.h"

#define LOG_TAG "IcqCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 3. ПУТИ К ЗАГОЛОВКАМ:
// Если CMake настроен верно (с ICQ_ROOT), используйте прямые пути без ../../../
#include "core.h"
#include "core_dispatcher.h"
#include "gui_interface.h"

// Глобальные ссылки
JavaVM* g_jvm = nullptr;
static std::unique_ptr<core::core_dispatcher> g_core;
static jobject g_event_callback_obj = nullptr;

// Реализация callback-интерфейса
class AndroidGuiCallback : public core::icore_interface {
public:
    // В ядре ICQ этот метод может называться receive_message_from_core или похожим образом
    // Убедитесь, что сигнатура совпадает с icore_interface в core/gui_interface.h
    void post_message_to_gui(const char* message) override {
        if (!g_jvm || !g_event_callback_obj) return;

        JNIEnv* env = nullptr;
        bool attached = false;
        
        jint getEnvRes = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (getEnvRes == JNI_EDETACHED) {
            // Важно для Android: указываем имя потока для отладки
            if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
            attached = true;
        }

        if (env && g_event_callback_obj) {
            jclass callbackClass = env->GetObjectClass(g_event_callback_obj);
            jmethodID methodId = env->GetMethodID(callbackClass, "onCoreEvent", "(Ljava/lang/String;)V");
            
            if (methodId) {
                jstring jMessage = env->NewStringUTF(message);
                env->CallVoidMethod(g_event_callback_obj, methodId, jMessage);
                env->DeleteLocalRef(jMessage);
            }
            env->DeleteLocalRef(callbackClass);
        }

        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    }
};

static std::shared_ptr<AndroidGuiCallback> g_gui_callback;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeInit(JNIEnv *env, jobject thiz, jstring data_path, jstring cache_path, jobject callback) {
    const char *data_path_cstr = env->GetStringUTFChars(data_path, nullptr);
    
    // Сохраняем глобальную ссылку на Java-объект коллбэка
    if (g_event_callback_obj) env->DeleteGlobalRef(g_event_callback_obj);
    g_event_callback_obj = env->NewGlobalRef(callback);
    
    g_gui_callback = std::make_shared<AndroidGuiCallback>();

    // Настройка путей и окружения ядра
    common::core_gui_settings settings;
    settings.os_version_ = "Android";
    settings.locale_ = "ru_RU";
    
    // Конвертация std::string в std::wstring для ядра (если оно требует wstring)
    std::string path_str(data_path_cstr);
    settings.profile_path_ = std::wstring(path_str.begin(), path_str.end());
    
    if (!g_core) {
        g_core = std::make_unique<core::core_dispatcher>();
        // Важно: в некоторых версиях ICQ Core link_gui принимает ссылки или shared_ptr
        g_core->link_gui(g_gui_callback.get(), settings); 
    }
    
    LOGI("Core Engine initialized. Path: %s", data_path_cstr);
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
    LOGI("Core Engine shutdown complete");
}
