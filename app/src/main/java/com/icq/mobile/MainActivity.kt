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
import com.icq.mobile.core.IcqCoreEngine
import com.icq.mobile.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    companion object {
        private const val TAG = "MainActivity"
    }

    private val requiredPermissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.VIBRATE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            val denied = permissions.filter { !it.value }.keys
            Log.w(TAG, "Permissions denied: $denied")
            Toast.makeText(
                this, 
                "Required permissions denied: ${denied.joinToString()}", 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions()
        
        // Обновляем статус UI
        updateCoreStatus()
    }

    private fun updateCoreStatus() {
        try {
            val status = getCoreStatus()
            binding.statusTextView.text = status
            Log.d(TAG, "Core status: $status")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get core status: ${e.message}")
            binding.statusTextView.text = "Core status: ERROR"
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isEmpty()) {
            onPermissionsGranted()
        } else {
            Log.d(TAG, "Requesting missing permissions: ${missing.joinToString()}")
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun onPermissionsGranted() {
        Log.d(TAG, "All permissions granted")
        notifyPermissionsReady()
        updateCoreStatus()
        
        // Теперь можно безопасно инициализировать VoIP
        initializeVoip()
    }

    private fun initializeVoip() {
        try {
            // Инициализация VoIP через движок
            val engine = IcqCoreEngine.getInstance()
            // Дополнительная VoIP инициализация если нужна
            Log.d(TAG, "VoIP initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VoIP: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        setNativeAppState(true)
        updateCoreStatus()
    }

    override fun onPause() {
        super.onPause()
        setNativeAppState(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Очистка ресурсов если нужно
    }

    // Нативные методы
    private external fun getCoreStatus(): String
    private external fun notifyPermissionsReady()
    private external fun setNativeAppState(active: Boolean)
}
