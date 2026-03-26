package com.tcddtakip.ui.main

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tcddtakip.data.api.ApiClient
import com.tcddtakip.data.model.TrainTracking
import kotlinx.coroutines.launch
import android.content.Context

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val api get() = ApiClient.getService(getApplication())
    val deviceId: String = Settings.Secure.getString(
        getApplication<Application>().contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown_device"

    val trackings = MutableLiveData<List<TrainTracking>>(emptyList())
    val checkStatus = MutableLiveData<String>()
    val isLoading = MutableLiveData(false)

    init {
        val prefs = getApplication<Application>()
            .getSharedPreferences("tcdd_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("backend_url", "") ?: ""
        if (url.isNotEmpty()) {
            loadTrackings()
        }
    }

    fun loadTrackings() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val result = api.getTrackings(deviceId)
                trackings.value = result.map { r ->
                    TrainTracking(
                        id = r.id,
                        kalkisIstasyonAdi = r.kalkisIstasyonAdi,
                        varisIstasyonAdi = r.varisIstasyonAdi,
                        seferTarihi = r.seferTarihi,
                        trenNo = r.trenNo,
                        trenAdi = r.trenAdi,
                        kalkisSaati = r.kalkisSaati,
                        varisSaati = r.varisSaati,
                        seatTypes = r.seatTypes,
                        isActive = r.active,
                        lastChecked = r.lastChecked,
                        foundSeat = r.lastResult.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                checkStatus.value = "Bağlantı hatası: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun deleteTracking(tracking: TrainTracking) {
        viewModelScope.launch {
            try {
                api.deleteTracking(tracking.id)
                loadTrackings()
            } catch (e: Exception) {
                checkStatus.value = "Silme hatası: ${e.message}"
            }
        }
    }

    fun toggleTracking(tracking: TrainTracking, active: Boolean) {
        viewModelScope.launch {
            try {
                api.toggleTracking(tracking.id, active)
                loadTrackings()
            } catch (e: Exception) {
                checkStatus.value = "Güncelleme hatası: ${e.message}"
            }
        }
    }

    fun checkNow(tracking: TrainTracking) {
        viewModelScope.launch {
            try {
                checkStatus.value = "${tracking.trenAdi} kontrol ediliyor..."
                api.checkNow()
                kotlinx.coroutines.delay(2000)
                loadTrackings()
                checkStatus.value = "Kontrol tamamlandı."
            } catch (e: Exception) {
                checkStatus.value = "Kontrol hatası: ${e.message}"
            }
        }
    }

    fun checkAllNow() {
        viewModelScope.launch {
            try {
                isLoading.value = true
                api.checkNow()
                kotlinx.coroutines.delay(2000)
                loadTrackings()
                checkStatus.value = "Tüm trenler kontrol edildi."
            } catch (e: Exception) {
                checkStatus.value = "Hata: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }
}
