package com.icq.mobile.core;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import java.io.File;

public class IcqCoreEngine {
    private static final String TAG = "IcqCoreEngine";

    // Интерфейс для получения событий в UI/ViewModel
    public interface CoreEventListener {
        void onEvent(String eventName);
    }

    private CoreEventListener listener;

    static {
        // Загрузка библиотеки, собранной CMake
        System.loadLibrary("icq_core");
    }

    public void initialize(Context context, CoreEventListener listener) {
        this.listener = listener;

        // Пути к папкам приложения
        String dataPath = context.getFilesDir().getAbsolutePath();
        String cachePath = context.getCacheDir().getAbsolutePath();
        
        // Уникальный ID устройства (важно для сессий)
        String androidId = Settings.Secure.getString(
            context.getContentResolver(), 
            Settings.Secure.ANDROID_ID
        );

        nativeInit(dataPath, cachePath, androidId, this);
    }

    public void shutdown() {
        nativeShutdown();
    }

    /**
     * Этот метод вызывается из C++ через JNI (AndroidGuiCallback::notify_java)
     */
    public void onCoreEvent(String eventName) {
        Log.d(TAG, "Received event from Core: " + eventName);
        if (listener != null) {
            listener.onEvent(eventName);
        }
    }

    // Native методы
    private native void nativeInit(String dataPath, String cachePath, String deviceId, Object callback);
    private native void nativeShutdown();
}
