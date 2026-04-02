package com.icq.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.icq.mobile.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Список разрешений из вашего Manifest
    private val requiredPermissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE
    ).apply {
        // Разрешения для Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Разрешения для Bluetooth (звонки) на Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    // Регистрация обработчика разрешений
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            Toast.makeText(this, "Permissions are required for calls and messaging", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация ViewBinding (как включено в build.gradle.kts)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions()
        
        // Пример вызова нативного метода для получения статуса ядра
        binding.root.post {
            val status = getCoreStatus()
            Log.d("ICQ_CORE", "Current Status: $status")
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onPermissionsGranted()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun onPermissionsGranted() {
        // Уведомляем C++ ядро о том, что разрешения получены и можно запускать сетевые службы
        notifyPermissionsReady()
    }

    override fun onResume() {
        super.onResume()
        setNativeAppState(true) // Приложение в фокусе
    }

    override fun onPause() {
        super.onPause()
        setNativeAppState(false) // Приложение в фоне
    }

    // --- Нативные методы (JNI) ---
    
    /**
     * Получает строковый статус от Qt/C++ ядра
     */
    private external fun getCoreStatus(): String

    /**
     * Уведомляет ядро о готовности разрешений
     */
    private external fun notifyPermissionsReady()

    /**
     * Передает состояние жизненного цикла в C++
     * @param active true если на переднем плане
     */
    private external fun setNativeAppState(active: Boolean)
}
