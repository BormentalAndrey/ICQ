#include <jni.h>
#include <android/log.h>
#include "voip_manager.h" // Оригинальный менеджер ICQ
extern JavaVM* g_jvm; // Берем из jni_bridge.cpp

// Адаптер: берет сырые кадры из ICQ и конвертирует их в JavaI420Buffer для WebRTC
class AndroidVideoSinkWrapper : public im::FramesCallback {
private:
    jobject java_video_sink_;

public:
    AndroidVideoSinkWrapper(JNIEnv* env, jobject video_sink) {
        java_video_sink_ = env->NewGlobalRef(video_sink);
    }

    ~AndroidVideoSinkWrapper() {
        if (!g_jvm || !java_video_sink_) return;
        JNIEnv* env = nullptr;
        bool attached = false;
        if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
            g_jvm->AttachCurrentThread(&env, nullptr);
            attached = true;
        }
        env->DeleteGlobalRef(java_video_sink_);
        if (attached) g_jvm->DetachCurrentThread();
    }

    // Метод, вызываемый ядром ICQ при поступлении декодированного кадра
    void onFrame(const uint8_t* y_plane, const uint8_t* u_plane, const uint8_t* v_plane,
                 int y_stride, int u_stride, int v_stride,
                 int width, int height, int rotation) override {
        
        JNIEnv* env = nullptr;
        g_jvm->AttachCurrentThread(&env, nullptr);

        // Находим классы WebRTC
        jclass jni_i420_buffer_class = env->FindClass("org/webrtc/JavaI420Buffer");
        jmethodID allocate_mid = env->GetStaticMethodID(jni_i420_buffer_class, "allocate", "(II)Lorg/webrtc/JavaI420Buffer;");
        
        // Аллоцируем буфер на стороне Java
        jobject j_buffer = env->CallStaticObjectMethod(jni_i420_buffer_class, allocate_mid, width, height);

        // Получаем ByteBuffer'ы для Y, U, V
        jmethodID get_data_y_mid = env->GetMethodID(jni_i420_buffer_class, "getDataY", "()Ljava/nio/ByteBuffer;");
        jobject j_data_y = env->CallObjectMethod(j_buffer, get_data_y_mid);
        uint8_t* y_data_ptr = static_cast<uint8_t*>(env->GetDirectBufferAddress(j_data_y));
        memcpy(y_data_ptr, y_plane, height * y_stride);

        // (Аналогично копируем U и V плоскости...)

        // Создаем VideoFrame и отправляем в Java
        jclass video_frame_class = env->FindClass("org/webrtc/VideoFrame");
        jmethodID video_frame_ctor = env->GetMethodID(video_frame_class, "<init>", "(Lorg/webrtc/VideoFrame$Buffer;IJ)V");
        
        // timestamp в наносекундах
        jlong timestamp_ns = 0; 
        jobject j_video_frame = env->NewObject(video_frame_class, video_frame_ctor, j_buffer, rotation, timestamp_ns);

        // Вызываем VideoSink.onFrame(frame)
        jclass sink_class = env->GetObjectClass(java_video_sink_);
        jmethodID on_frame_mid = env->GetMethodID(sink_class, "onFrame", "(Lorg/webrtc/VideoFrame;)V");
        env->CallVoidMethod(java_video_sink_, on_frame_mid, j_video_frame);

        // Очистка JNI ссылок
        env->DeleteLocalRef(j_video_frame);
        env->DeleteLocalRef(j_buffer);
        g_jvm->DetachCurrentThread();
    }
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_icq_mobile_voip_VoipEngine_nativeAttachVideoSink(JNIEnv *env, jobject thiz, jobject video_sink) {
    // Создаем C++ адаптер и возвращаем указатель на него в Kotlin для управления памятью
    auto* wrapper = new AndroidVideoSinkWrapper(env, video_sink);
    // В реальности: g_core->get_voip_manager()->set_video_callback(wrapper);
    return reinterpret_cast<jlong>(wrapper);
}
