#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <android/log.h>

// Добавляем определение im_assert перед включением заголовков ICQ
#ifndef im_assert
#include <cstdlib>
#define im_assert(condition) do { \
    if (!(condition)) { \
        __android_log_print(ANDROID_LOG_ERROR, "ICQCore", \
            "Assertion failed: %s:%d: %s", __FILE__, __LINE__, #condition); \
        abort(); \
    } \
} while(0)
#endif

// Предполагаемые заголовки из ICQ Desktop core
#include "core.h"
#include "core_dispatcher.h"
#include "gui_interface.h" // Оригинальный интерфейс связи Core -> GUI

#define LOG_TAG "IcqCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Глобальный указатель на JVM для фоновых потоков
JavaVM* g_jvm = nullptr;
static std::unique_ptr<core::core_dispatcher> g_core;
static jobject g_event_callback_obj = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    LOGI("JNI_OnLoad: JVM успешно закэширована");
    return JNI_VERSION_1_6;
}

// Реализация интерфейса ядра для отправки событий в Kotlin
class AndroidGuiCallback : public core::icore_interface {
public:
    void post_message_to_gui(const char* message) override {
        if (!g_jvm || !g_event_callback_obj) return;

        JNIEnv* env = nullptr;
        bool attached = false;
        
        // Подключаем фоновый поток (network/voip) к JVM
        if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
            g_jvm->AttachCurrentThread(&env, nullptr);
            attached = true;
        }

        jclass callbackClass = env->GetObjectClass(g_event_callback_obj);
        jmethodID methodId = env->GetMethodID(callbackClass, "onCoreEvent", "(Ljava/lang/String;)V");
        
        jstring jMessage = env->NewStringUTF(message);
        env->CallVoidMethod(g_event_callback_obj, methodId, jMessage);
        
        env->DeleteLocalRef(jMessage);
        env->DeleteLocalRef(callbackClass);

        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    }
};

static std::shared_ptr<AndroidGuiCallback> g_gui_callback;

extern "C" JNIEXPORT void JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeInit(JNIEnv *env, jobject thiz, jstring data_path, jstring cache_path, jobject callback) {
    const char *data_path_cstr = env->GetStringUTFChars(data_path, nullptr);
    const char *cache_path_cstr = env->GetStringUTFChars(cache_path, nullptr);
    
    // Создаем глобальную ссылку на Kotlin-объект коллбэка
    g_event_callback_obj = env->NewGlobalRef(callback);
    g_gui_callback = std::make_shared<AndroidGuiCallback>();

    common::core_gui_settings settings;
    settings.os_version_ = "Android";
    settings.locale_ = "ru_RU";
    // Устанавливаем пути для БД и кэша
    settings.profile_path_ = std::wstring(data_path_cstr, data_path_cstr + strlen(data_path_cstr));
    
    g_core = std::make_unique<core::core_dispatcher>();
    
    // Линкуем ядро с нашим Android JNI интерфейсом
    g_core->link_gui(g_gui_callback, settings);
    
    LOGI("Ядро ICQ запущено. Путь БД: %s", data_path_cstr);

    env->ReleaseStringUTFChars(data_path, data_path_cstr);
    env->ReleaseStringUTFChars(cache_path, cache_path_cstr);
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
    LOGI("Ядро ICQ безопасно остановлено");
}
