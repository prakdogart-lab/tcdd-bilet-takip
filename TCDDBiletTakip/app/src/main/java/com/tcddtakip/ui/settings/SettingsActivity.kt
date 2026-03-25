package com.tcddtakip.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.tcddtakip.data.api.ApiClient
import com.tcddtakip.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("tcdd_prefs", MODE_PRIVATE)
        val savedUrl = prefs.getString("backend_url", "") ?: ""
        binding.etBackendUrl.setText(savedUrl)

        binding.btnSaveUrl.setOnClickListener {
            val url = binding.etBackendUrl.text.toString().trim()
            if (url.isEmpty()) { showSnack("URL boş olamaz"); return@setOnClickListener }
            prefs.edit().putString("backend_url", url).apply()
            ApiClient.invalidate()
            testConnection()
        }

        binding.btnTestConnection.setOnClickListener { testConnection() }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun testConnection() {
        val api = ApiClient.getService(this)
        binding.tvConnectionStatus.text = "Bağlanıyor..."
        lifecycleScope.launch {
            try {
                val health = api.health()
                val trackings = health["active_trackings"]?.toString() ?: "?"
                binding.tvConnectionStatus.text = "✅ Bağlantı başarılı\nAktif takip: $trackings"
                showSnack("✅ Backend'e bağlandı!")
            } catch (e: Exception) {
                binding.tvConnectionStatus.text = "❌ Bağlantı hatası:\n${e.message}"
                showSnack("❌ Bağlantı başarısız")
            }
        }
    }

    private fun showSnack(msg: String) =
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
}
