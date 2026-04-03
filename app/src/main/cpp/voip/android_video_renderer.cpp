// app/src/main/cpp/voip/android_video_renderer.cpp
#include <jni.h>
#include <android/log.h>
#include <api/video/video_frame.h>
#include <api/video/i420_buffer.h>
#include <api/media_stream_interface.h>

extern JavaVM* g_jvm; // Берем из jni_bridge.cpp

// Адаптер: берет сырые кадры WebRTC VideoFrame и конвертирует их в JavaI420Buffer
class AndroidVideoSinkWrapper : public rtc::VideoSinkInterface<webrtc::VideoFrame> {
private:
    jobject java_video_sink_;

public:
    AndroidVideoSinkWrapper(JNIEnv* env, jobject video_sink) {
        java_video_sink_ = env->NewGlobalRef(video_sink);
    }

    ~AndroidVideoSinkWrapper() override {
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

    // Метод, вызываемый ядром WebRTC при поступлении декодированного кадра
    void OnFrame(const webrtc::VideoFrame& frame) override {
        JNIEnv* env = nullptr;
        bool attached = false;
        if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
            g_jvm->AttachCurrentThread(&env, nullptr);
            attached = true;
        }

        rtc::scoped_refptr<webrtc::I420BufferInterface> buffer = frame.video_frame_buffer()->ToI420();
        int width = buffer->width();
        int height = buffer->height();

        // Находим классы WebRTC
        jclass jni_i420_buffer_class = env->FindClass("org/webrtc/JavaI420Buffer");
        jmethodID allocate_mid = env->GetStaticMethodID(jni_i420_buffer_class, "allocate", "(II)Lorg/webrtc/JavaI420Buffer;");
        
        // Аллоцируем буфер на стороне Java
        jobject j_buffer = env->CallStaticObjectMethod(jni_i420_buffer_class, allocate_mid, width, height);

        // Получаем ByteBuffer'ы для Y, U, V
        jmethodID get_data_y_mid = env->GetMethodID(jni_i420_buffer_class, "getDataY", "()Ljava/nio/ByteBuffer;");
        jobject j_data_y = env->CallObjectMethod(j_buffer, get_data_y_mid);
        uint8_t* y_data_ptr = static_cast<uint8_t*>(env->GetDirectBufferAddress(j_data_y));
        memcpy(y_data_ptr, buffer->DataY(), height * buffer->StrideY());

        // Копируем U плоскость
        jmethodID get_data_u_mid = env->GetMethodID(jni_i420_buffer_class, "getDataU", "()Ljava/nio/ByteBuffer;");
        jobject j_data_u = env->CallObjectMethod(j_buffer, get_data_u_mid);
        uint8_t* u_data_ptr = static_cast<uint8_t*>(env->GetDirectBufferAddress(j_data_u));
        memcpy(u_data_ptr, buffer->DataU(), ((height + 1) / 2) * buffer->StrideU());

        // Копируем V плоскость
        jmethodID get_data_v_mid = env->GetMethodID(jni_i420_buffer_class, "getDataV", "()Ljava/nio/ByteBuffer;");
        jobject j_data_v = env->CallObjectMethod(j_buffer, get_data_v_mid);
        uint8_t* v_data_ptr = static_cast<uint8_t*>(env->GetDirectBufferAddress(j_data_v));
        memcpy(v_data_ptr, buffer->DataV(), ((height + 1) / 2) * buffer->StrideV());

        // Создаем VideoFrame и отправляем в Java
        jclass video_frame_class = env->FindClass("org/webrtc/VideoFrame");
        jmethodID video_frame_ctor = env->GetMethodID(video_frame_class, "<init>", "(Lorg/webrtc/VideoFrame$Buffer;IJ)V");
        
        // timestamp в наносекундах
        jlong timestamp_ns = frame.timestamp_us() * 1000; 
        jobject j_video_frame = env->NewObject(video_frame_class, video_frame_ctor, j_buffer, frame.rotation(), timestamp_ns);

        // Вызываем VideoSink.onFrame(frame)
        jclass sink_class = env->GetObjectClass(java_video_sink_);
        jmethodID on_frame_mid = env->GetMethodID(sink_class, "onFrame", "(Lorg/webrtc/VideoFrame;)V");
        env->CallVoidMethod(java_video_sink_, on_frame_mid, j_video_frame);

        // Очистка JNI ссылок
        env->DeleteLocalRef(j_video_frame);
        env->DeleteLocalRef(j_buffer);
        
        if (attached) g_jvm->DetachCurrentThread();
    }
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_icq_mobile_core_IcqCoreEngine_nativeAttachVideoSink(JNIEnv *env, jobject thiz, jobject video_sink) {
    auto* wrapper = new AndroidVideoSinkWrapper(env, video_sink);
    return reinterpret_cast<jlong>(wrapper);
}
