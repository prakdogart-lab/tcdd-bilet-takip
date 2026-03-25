package com.tcddtakip.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tcddtakip.R
import com.tcddtakip.databinding.ActivityMainBinding
import com.tcddtakip.ui.addtrain.AddTrainActivity
import com.tcddtakip.ui.settings.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private lateinit var adapter: TrainTrackingAdapter

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showSnack("⚠️ Bildirim izni verilmedi. Ayarlardan açabilirsiniz.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        requestNotifPermission()
        setupRecycler()
        setupFab()
        observe()
    }

    override fun onResume() {
        super.onResume()
        vm.loadTrackings()
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setupRecycler() {
        adapter = TrainTrackingAdapter(
            onToggle = { t, active -> vm.toggleTracking(t, active) },
            onDelete = { t ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Takibi Sil")
                    .setMessage("\"${t.trenAdi}\" takibini silmek istiyor musunuz?")
                    .setPositiveButton("Sil") { _, _ -> vm.deleteTracking(t) }
                    .setNegativeButton("İptal", null)
                    .show()
            },
            onRefresh = { t -> vm.checkNow(t) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddTrainActivity::class.java))
        }
    }

    private fun observe() {
        vm.trackings.observe(this) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }
        vm.checkStatus.observe(this) { msg ->
            if (!msg.isNullOrBlank()) showSnack(msg)
        }
        vm.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_check_all -> { vm.checkAllNow(); true }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java)); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showSnack(msg: String) =
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
}
