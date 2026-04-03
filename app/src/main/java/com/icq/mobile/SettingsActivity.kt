package com.icq.mobile

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.icq.mobile.core.IcqCoreEngine
import com.icq.mobile.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val TAG = "SettingsActivity"
        private const val PREFS_NAME = "icq_settings"

        // Ключи для хранения настроек
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_AUTO_DOWNLOAD_MEDIA = "auto_download_media"
        const val KEY_SAVE_TO_GALLERY = "save_to_gallery"
        const val KEY_QUALITY = "call_quality"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Настройка ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        loadSettings()
        setupListeners()
        loadVersionInfo()
    }

    private fun setupListeners() {
        // Кнопка сохранения
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        // Переключатели
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            onNotificationSettingChanged(isChecked)
        }

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            onSoundSettingChanged(isChecked)
        }

        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            onVibrationSettingChanged(isChecked)
        }

        binding.switchAutoDownloadMedia.setOnCheckedChangeListener { _, isChecked ->
            onAutoDownloadMediaChanged(isChecked)
        }

        binding.switchSaveToGallery.setOnCheckedChangeListener { _, isChecked ->
            onSaveToGalleryChanged(isChecked)
        }

        // Выбор темы
        binding.spinnerTheme.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                onThemeChanged(position)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        // Выбор качества звонков
        binding.spinnerCallQuality.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                onCallQualityChanged(position)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        // Дополнительные кнопки
        binding.btnClearCache.setOnClickListener { clearCache() }
        binding.btnAbout.setOnClickListener { showAboutDialog() }
        binding.btnLogout.setOnClickListener { showLogoutDialog() }
    }

    private fun loadSettings() {
        val notificationsEnabled = sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        val soundEnabled = sharedPreferences.getBoolean(KEY_SOUND_ENABLED, true)
        val vibrationEnabled = sharedPreferences.getBoolean(KEY_VIBRATION_ENABLED, true)
        val darkMode = sharedPreferences.getInt(KEY_DARK_MODE, 0)
        val autoDownloadMedia = sharedPreferences.getBoolean(KEY_AUTO_DOWNLOAD_MEDIA, true)
        val saveToGallery = sharedPreferences.getBoolean(KEY_SAVE_TO_GALLERY, false)
        val callQuality = sharedPreferences.getInt(KEY_QUALITY, 1) // 0=Low, 1=Medium, 2=High

        // Применяем к UI
        binding.switchNotifications.isChecked = notificationsEnabled
        binding.switchSound.isChecked = soundEnabled
        binding.switchVibration.isChecked = vibrationEnabled
        binding.spinnerTheme.setSelection(darkMode)
        binding.switchAutoDownloadMedia.isChecked = autoDownloadMedia
        binding.switchSaveToGallery.isChecked = saveToGallery
        binding.spinnerCallQuality.setSelection(callQuality)

        applyTheme(darkMode)
        applySettingsToNative(notificationsEnabled, soundEnabled, vibrationEnabled, callQuality)

        Log.d(TAG, "Settings loaded successfully")
    }

    private fun saveSettings() {
        val notificationsEnabled = binding.switchNotifications.isChecked
        val soundEnabled = binding.switchSound.isChecked
        val vibrationEnabled = binding.switchVibration.isChecked
        val darkMode = binding.spinnerTheme.selectedItemPosition
        val autoDownloadMedia = binding.switchAutoDownloadMedia.isChecked
        val saveToGallery = binding.switchSaveToGallery.isChecked
        val callQuality = binding.spinnerCallQuality.selectedItemPosition

        sharedPreferences.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, notificationsEnabled)
            .putBoolean(KEY_SOUND_ENABLED, soundEnabled)
            .putBoolean(KEY_VIBRATION_ENABLED, vibrationEnabled)
            .putInt(KEY_DARK_MODE, darkMode)
            .putBoolean(KEY_AUTO_DOWNLOAD_MEDIA, autoDownloadMedia)
            .putBoolean(KEY_SAVE_TO_GALLERY, saveToGallery)
            .putInt(KEY_QUALITY, callQuality)
            .apply()

        applyTheme(darkMode)
        applySettingsToNative(notificationsEnabled, soundEnabled, vibrationEnabled, callQuality)

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Settings saved successfully")

        finish()
    }

    private fun applySettingsToNative(
        notificationsEnabled: Boolean,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        callQuality: Int
    ) {
        try {
            if (IcqCoreEngine.getInstance().isCoreInitialized()) {
                nativeApplySettings(notificationsEnabled, soundEnabled, vibrationEnabled, callQuality)
                Log.d(TAG, "Settings successfully applied to native core")
            } else {
                Log.w(TAG, "Core not initialized yet. Settings will be applied later.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply settings to native core", e)
        }
    }

    private fun applyTheme(darkMode: Int) {
        when (darkMode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun loadVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = packageInfo.longVersionCode

            binding.tvVersionInfo?.text = getString(R.string.version_format, versionName, versionCode)

            if (IcqCoreEngine.getInstance().isCoreInitialized()) {
                val coreVersion = IcqCoreEngine.getInstance().getVersion()
                binding.tvCoreVersion?.text = getString(R.string.core_version_format, coreVersion)
            } else {
                binding.tvCoreVersion?.text = getString(R.string.core_not_initialized)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load version info", e)
            binding.tvVersionInfo?.text = getString(R.string.version_unknown)
            binding.tvCoreVersion?.text = getString(R.string.core_version_unknown)
        }
    }

    private fun clearCache() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_cache_title))
            .setMessage(getString(R.string.clear_cache_message))
            .setPositiveButton(getString(R.string.clear)) { _, _ ->
                try {
                    if (IcqCoreEngine.getInstance().isCoreInitialized()) {
                        nativeClearCache()
                        Toast.makeText(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Cache cleared successfully")
                    } else {
                        Toast.makeText(this, getString(R.string.core_not_initialized), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear cache", e)
                    Toast.makeText(this, getString(R.string.clear_cache_error), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_title))
            .setMessage(getString(R.string.about_message))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showLogoutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout_title))
            .setMessage(getString(R.string.logout_message))
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                performLogout()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performLogout() {
        try {
            if (IcqCoreEngine.getInstance().isCoreInitialized()) {
                nativeLogout()
                Toast.makeText(this, getString(R.string.logout_success), Toast.LENGTH_SHORT).show()
                Log.d(TAG, "User logged out successfully")
                finishAffinity()
                // TODO: Запуск LoginActivity при необходимости
            } else {
                Toast.makeText(this, getString(R.string.core_not_initialized), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform logout", e)
            Toast.makeText(this, getString(R.string.logout_error), Toast.LENGTH_SHORT).show()
        }
    }

    // === Обработчики изменений настроек (вызываются в реальном времени) ===
    private fun onNotificationSettingChanged(enabled: Boolean) {
        Log.d(TAG, "Notifications setting changed: $enabled")
        if (IcqCoreEngine.getInstance().isCoreInitialized()) nativeSetNotificationsEnabled(enabled)
    }

    private fun onSoundSettingChanged(enabled: Boolean) {
        Log.d(TAG, "Sound setting changed: $enabled")
        if (IcqCoreEngine.getInstance().isCoreInitialized()) nativeSetSoundEnabled(enabled)
    }

    private fun onVibrationSettingChanged(enabled: Boolean) {
        Log.d(TAG, "Vibration setting changed: $enabled")
        if (IcqCoreEngine.getInstance().isCoreInitialized()) nativeSetVibrationEnabled(enabled)
    }

    private fun onThemeChanged(position: Int) {
        Log.d(TAG, "Theme changed to position: $position")
        // Тема применяется при сохранении (или сразу, если нужно)
    }

    private fun onSaveToGalleryChanged(enabled: Boolean) {
        Log.d(TAG, "Save to gallery setting changed: $enabled")
        if (IcqCoreEngine.getInstance().isCoreInitialized()) nativeSetSaveToGalleryEnabled(enabled)
    }

    private fun onAutoDownloadMediaChanged(enabled: Boolean) {
        Log.d(TAG, "Auto download media setting changed: $enabled")
        if (IcqCoreEngine.getInstance().isCoreInitialized()) nativeSetAutoDownloadMediaEnabled(enabled)
    }

    private fun onCallQualityChanged(position: Int) {
        Log.d(TAG, "Call quality changed to position: $position")
        // Применяется при сохранении
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ==================== Нативные методы ====================
    private external fun nativeApplySettings(
        notificationsEnabled: Boolean,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        callQuality: Int
    )

    private external fun nativeSetNotificationsEnabled(enabled: Boolean)
    private external fun nativeSetSoundEnabled(enabled: Boolean)
    private external fun nativeSetVibrationEnabled(enabled: Boolean)
    private external fun nativeSetSaveToGalleryEnabled(enabled: Boolean)
    private external fun nativeSetAutoDownloadMediaEnabled(enabled: Boolean)
    private external fun nativeClearCache()
    private external fun nativeLogout()
}
