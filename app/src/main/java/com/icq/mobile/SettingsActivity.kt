package com.icq.mobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.icq.mobile.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Поддержка кнопки "Назад" в тулбаре
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        binding.btnSaveSettings.setOnClickListener {
            // TODO: Передача настроек в C++ ядро
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
