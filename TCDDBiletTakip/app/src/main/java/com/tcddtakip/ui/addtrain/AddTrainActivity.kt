package com.tcddtakip.ui.addtrain

import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.tcddtakip.data.api.ApiClient
import com.tcddtakip.data.api.SearchTrainsRequest
import com.tcddtakip.data.api.TrackingRequest
import com.tcddtakip.data.model.SeferDto
import com.tcddtakip.data.model.Station
import com.tcddtakip.databinding.ActivityAddTrainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddTrainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTrainBinding
    private val api get() = ApiClient.getService(this)

    private var stations: List<Station> = emptyList()
    private var selectedKalkis: Station? = null
    private var selectedVaris: Station? = null
    private var selectedDate: String = ""
    private var selectedTrain: SeferDto? = null

    private lateinit var trainAdapter: TrainSelectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTrainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupTrainRecycler()
        loadStations()
        setupDatePicker()

        binding.btnSearchTrains.setOnClickListener { searchTrains() }
        binding.btnSave.setOnClickListener { saveTracking() }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun setupTrainRecycler() {
        trainAdapter = TrainSelectAdapter { train ->
            selectedTrain = train
            binding.cardSeatTypes.visibility = View.VISIBLE
            binding.btnSave.visibility = View.VISIBLE
        }
        binding.rvTrains.layoutManager = LinearLayoutManager(this)
        binding.rvTrains.adapter = trainAdapter
    }

    private fun loadStations() {
        lifecycleScope.launch {
            try {
                stations = api.getStations()
                setupStationAutocomplete()
            } catch (e: Exception) {
                showSnack("İstasyon listesi yüklenemedi: ${e.message}")
            }
        }
    }

    private fun setupStationAutocomplete() {
        val names = stations.map { it.displayName() }
        val kalkisAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        val varisAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)

        binding.actvKalkis.setAdapter(kalkisAdapter)
        binding.actvVaris.setAdapter(varisAdapter)

        binding.actvKalkis.setOnItemClickListener { _, _, pos, _ ->
            val name = kalkisAdapter.getItem(pos)
            selectedKalkis = stations.firstOrNull { it.displayName() == name }
        }
        binding.actvVaris.setOnItemClickListener { _, _, pos, _ ->
            val name = varisAdapter.getItem(pos)
            selectedVaris = stations.firstOrNull { it.displayName() == name }
        }
    }

    private fun setupDatePicker() {
        val today = MaterialDatePicker.todayInUtcMilliseconds()
        val constraints = CalendarConstraints.Builder()
            .setStart(today)
            .setValidator(DateValidatorPointForward.now())
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Tarih Seç")
            .setCalendarConstraints(constraints)
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = millis
            val apiFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            selectedDate = apiFormat.format(cal.time)
            binding.etDate.setText(displayFormat.format(cal.time))
        }

        binding.etDate.setOnClickListener { picker.show(supportFragmentManager, "date_picker") }
        binding.tilDate.setEndIconOnClickListener { picker.show(supportFragmentManager, "date_picker") }
    }

    private fun searchTrains() {
        val kalkis = selectedKalkis ?: run { showSnack("Lütfen kalkış istasyonu seçin"); return }
        val varis = selectedVaris ?: run { showSnack("Lütfen varış istasyonu seçin"); return }
        if (selectedDate.isEmpty()) { showSnack("Lütfen tarih seçin"); return }
        if (kalkis.id() == varis.id()) { showSnack("Kalkış ve varış aynı olamaz"); return }

        binding.cardTrainSelect.visibility = View.VISIBLE
        binding.progressTrains.visibility = View.VISIBLE
        binding.rvTrains.visibility = View.GONE
        binding.cardSeatTypes.visibility = View.GONE
        binding.btnSave.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = api.searchTrains(
                    SearchTrainsRequest(kalkis.id(), varis.id(), selectedDate)
                )
                binding.progressTrains.visibility = View.GONE
                if (response.seferler.isEmpty()) {
                    showSnack("Bu tarihte sefer bulunamadı")
                    binding.cardTrainSelect.visibility = View.GONE
                } else {
                    binding.rvTrains.visibility = View.VISIBLE
                    trainAdapter.submitList(response.seferler)
                }
            } catch (e: Exception) {
                binding.progressTrains.visibility = View.GONE
                showSnack("Tren araması başarısız: ${e.message}")
            }
        }
    }

    private fun saveTracking() {
        val train = selectedTrain ?: run { showSnack("Lütfen bir tren seçin"); return }
        val kalkis = selectedKalkis!!
        val varis = selectedVaris!!

        val seatTypes = mutableListOf<String>()
        if (binding.chipEkonomi.isChecked) seatTypes.add("EKONOMI")
        if (binding.chipBusiness.isChecked) seatTypes.add("BUSINESS")
        if (binding.chipTekerlekli.isChecked) seatTypes.add("TEKERLEKLI")

        if (seatTypes.isEmpty()) { showSnack("En az bir koltuk tipi seçin"); return }

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val prefs = getSharedPreferences("tcdd_prefs", MODE_PRIVATE)
        val fcmToken = prefs.getString("fcm_token", "") ?: ""

        val request = TrackingRequest(
            deviceId = deviceId,
            fcmToken = fcmToken,
            kalkisIstasyonId = kalkis.id(),
            kalkisIstasyonAdi = kalkis.displayName(),
            varisIstasyonId = varis.id(),
            varisIstasyonAdi = varis.displayName(),
            seferTarihi = selectedDate,
            trenNo = train.trenNo,
            trenAdi = train.trenAdi.ifBlank { train.trenNo },
            kalkisSaati = train.kalkisSaati(),
            varisSaati = train.varisSaati(),
            seatTypes = seatTypes
        )

        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Kaydediliyor..."

        lifecycleScope.launch {
            try {
                api.addTracking(request)
                showSnack("✅ Takip başlatıldı!")
                finish()
            } catch (e: Exception) {
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Takibi Başlat"
                showSnack("Kaydetme hatası: ${e.message}")
            }
        }
    }

    private fun showSnack(msg: String) =
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
}
