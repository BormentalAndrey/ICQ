package com.icq.mobile.core;

import android.util.Log;
import org.webrtc.VideoSink;

public class IcqCoreEngine {
    private static final String TAG = "IcqCoreEngine";
    private static volatile IcqCoreEngine instance;
    private CoreEventCallback eventCallback;

    // Интерфейс для передачи событий из C++ в Java
    public interface CoreEventCallback {
        void onEvent(String eventName);
        void onVoipEvent(String account, byte[] data);
        void onStatisticEvent(String event, String props);
    }

    static {
        try {
            System.loadLibrary("icq_core");
            Log.i(TAG, "Native library icq_core loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library icq_core", e);
        }
    }

    private IcqCoreEngine() {
        // Приватный конструктор для Singleton
    }

    public static IcqCoreEngine getInstance() {
        if (instance == null) {
            synchronized (IcqCoreEngine.class) {
                if (instance == null) {
                    instance = new IcqCoreEngine();
                }
            }
        }
        return instance;
    }

    public synchronized void init(String dataPath, String cachePath, String deviceId, CoreEventCallback callback) {
        if (isInitialized()) {
            Log.w(TAG, "Engine is already initialized. Skipping.");
            return;
        }
        this.eventCallback = callback;
        nativeInit(dataPath, cachePath, deviceId, this);
    }

    public synchronized void shutdown() {
        if (!isInitialized()) {
            return;
        }
        nativeShutdown();
        this.eventCallback = null;
    }

    public String getVersion() {
        return nativeGetVersion();
    }

    public boolean isCoreInitialized() {
        return nativeIsInitialized();
    }

    public void processVoipMsg(String account, int msgType, byte[] data) {
        if (isCoreInitialized()) {
            nativeProcessVoipMsg(account, msgType, data);
        } else {
            Log.e(TAG, "Cannot process VOIP message: Core is not initialized");
        }
    }

    public void sendTextMessage(String contact, String message) {
        if (isCoreInitialized()) {
            nativeSendMessage(contact, message);
        } else {
            Log.e(TAG, "Cannot send message: Core is not initialized");
        }
    }

    public long attachVideoSink(VideoSink sink) {
        if (sink == null) {
            Log.e(TAG, "VideoSink is null");
            return 0;
        }
        return nativeAttachVideoSink(sink);
    }

    // =========================================================
    // Методы обратного вызова (вызываются из JNI / C++)
    // =========================================================
    
    @SuppressWarnings("unused") // Вызывается через JNI
    private void onCoreEvent(String eventName) {
        if (eventCallback != null) {
            eventCallback.onEvent(eventName);
        }
    }

    @SuppressWarnings("unused") // Вызывается через JNI
    private void onVoipEvent(String account, byte[] data) {
        if (eventCallback != null) {
            eventCallback.onVoipEvent(account, data);
        }
    }

    @SuppressWarnings("unused") // Вызывается через JNI
    private void onStatisticEvent(String event, String props) {
        if (eventCallback != null) {
            eventCallback.onStatisticEvent(event, props);
        }
    }

    // =========================================================
    // Нативные методы JNI
    // =========================================================
    private native void nativeInit(String dataPath, String cachePath, String deviceId, Object callback);
    private native void nativeShutdown();
    private native String nativeGetVersion();
    private native boolean nativeIsInitialized();
    private native void nativeProcessVoipMsg(String account, int msgType, byte[] data);
    private native void nativeSendMessage(String contact, String message);
    private native long nativeAttachVideoSink(VideoSink sink);
}
