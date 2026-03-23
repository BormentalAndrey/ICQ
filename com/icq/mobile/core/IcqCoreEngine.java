package com.icq.mobile.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/**
 * IcqCoreEngine - Главный мост между Android GUI и C++ Ядром (Dzin Project).
 * Реализован как Singleton для предотвращения множественной инициализации.
 */
public class IcqCoreEngine {
    private static final String TAG = "DzinCoreEngine";
    private static IcqCoreEngine instance;
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private CoreEventListener listener;
    private boolean isLibraryLoaded = false;

    // Интерфейс для слушателей (UI, ViewModel)
    public interface CoreEventListener {
        void onEvent(String eventName);
        void onVoipEvent(String account, byte[] data);
        void onStatisticEvent(String event, String propsJson);
    }

    private IcqCoreEngine() {
        try {
            System.loadLibrary("icq_core");
            isLibraryLoaded = true;
            Log.i(TAG, "Native library 'icq_core' loaded successfully.");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "FATAL: Could not load native library!", e);
        }
    }

    public static synchronized IcqCoreEngine getInstance() {
        if (instance == null) {
            instance = new IcqCoreEngine();
        }
        return instance;
    }

    /**
     * Полная инициализация движка
     */
    public void initialize(Context context, CoreEventListener listener) {
        if (!isLibraryLoaded) return;
        
        this.listener = listener;

        // Подготовка путей
        String dataPath = context.getFilesDir().getAbsolutePath();
        String cachePath = context.getCacheDir().getAbsolutePath();
        
        // Получение уникального ID устройства
        String androidId = Settings.Secure.getString(
            context.getContentResolver(), 
            Settings.Secure.ANDROID_ID
        );

        if (androidId == null) androidId = "android_unknown_device";

        Log.d(TAG, "Initializing native core with deviceId: " + androidId);
        nativeInit(dataPath, cachePath, androidId, this);
    }

    /**
     * Остановка и очистка памяти
     */
    public void shutdown() {
        if (isLibraryLoaded) {
            nativeShutdown();
            this.listener = null;
            Log.i(TAG, "Core engine shut down.");
        }
    }

    // --- Методы, вызываемые из C++ через JNI ---

    public void onCoreEvent(final String eventName) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onEvent(eventName);
            }
        });
    }

    public void onVoipEvent(final String account, final byte[] data) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onVoipEvent(account, data);
            }
        });
    }

    public void onStatisticEvent(final String event, final String propsJson) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onStatisticEvent(event, propsJson);
            }
        });
    }

    // --- Публичные методы для управления ядром из Java ---

    public String getVersion() {
        return isLibraryLoaded ? nativeGetVersion() : "unknown";
    }

    public boolean isInitialized() {
        return isLibraryLoaded && nativeIsInitialized();
    }

    public void sendMessage(String contact, String message) {
        if (isInitialized()) {
            nativeSendMessage(contact, message);
        }
    }

    public void processVoipMsg(String account, int msgType, byte[] data) {
        if (isInitialized()) {
            nativeProcessVoipMsg(account, msgType, data);
        }
    }

    // --- Native методы (сигнатуры соответствуют jni_bridge.cpp) ---

    private native void nativeInit(String dataPath, String cachePath, String deviceId, Object callback);
    private native void nativeShutdown();
    private native String nativeGetVersion();
    private native boolean nativeIsInitialized();
    private native void nativeProcessVoipMsg(String account, int msgType, byte[] data);
    private native void nativeSendMessage(String contact, String message);
}
