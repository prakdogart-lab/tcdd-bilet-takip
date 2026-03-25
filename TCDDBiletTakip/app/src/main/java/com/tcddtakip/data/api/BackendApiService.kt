package com.tcddtakip.data.api

import com.google.gson.annotations.SerializedName
import com.tcddtakip.data.model.SeferDto
import com.tcddtakip.data.model.Station
import retrofit2.http.*

data class TrackingRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("kalkis_istasyon_id") val kalkisIstasyonId: Long,
    @SerializedName("kalkis_istasyon_adi") val kalkisIstasyonAdi: String,
    @SerializedName("varis_istasyon_id") val varisIstasyonId: Long,
    @SerializedName("varis_istasyon_adi") val varisIstasyonAdi: String,
    @SerializedName("sefer_tarihi") val seferTarihi: String,
    @SerializedName("tren_no") val trenNo: String,
    @SerializedName("tren_adi") val trenAdi: String,
    @SerializedName("kalkis_saati") val kalkisSaati: String,
    @SerializedName("varis_saati") val varisSaati: String,
    @SerializedName("seat_types") val seatTypes: List<String>,
    @SerializedName("auto_book") val autoBook: Boolean = false
)

data class TrackingResponse(
    @SerializedName("id") val id: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("kalkis_istasyon_adi") val kalkisIstasyonAdi: String,
    @SerializedName("varis_istasyon_adi") val varisIstasyonAdi: String,
    @SerializedName("sefer_tarihi") val seferTarihi: String,
    @SerializedName("tren_no") val trenNo: String,
    @SerializedName("tren_adi") val trenAdi: String,
    @SerializedName("kalkis_saati") val kalkisSaati: String,
    @SerializedName("varis_saati") val varisSaati: String,
    @SerializedName("seat_types") val seatTypes: List<String>,
    @SerializedName("active") val active: Boolean,
    @SerializedName("last_checked") val lastChecked: Long,
    @SerializedName("last_result") val lastResult: List<String>
)

data class AddTrackingResult(
    @SerializedName("status") val status: String,
    @SerializedName("tracking_id") val trackingId: String
)

data class SearchTrainsRequest(
    @SerializedName("kalkis_istasyon_id") val kalkisIstasyonId: Long,
    @SerializedName("varis_istasyon_id") val varisIstasyonId: Long,
    @SerializedName("tarih") val tarih: String
)

data class SearchTrainsResponse(
    @SerializedName("seferler") val seferler: List<SeferDto>,
    @SerializedName("count") val count: Int
)

interface BackendApiService {
    @GET("stations")
    suspend fun getStations(): List<Station>

    @POST("search-trains")
    suspend fun searchTrains(@Body request: SearchTrainsRequest): SearchTrainsResponse

    @GET("trackings/{device_id}")
    suspend fun getTrackings(@Path("device_id") deviceId: String): List<TrackingResponse>

    @POST("trackings")
    suspend fun addTracking(@Body request: TrackingRequest): AddTrackingResult

    @DELETE("trackings/{tracking_id}")
    suspend fun deleteTracking(@Path("tracking_id") trackingId: String): Map<String, String>

    @PATCH("trackings/{tracking_id}/toggle")
    suspend fun toggleTracking(
        @Path("tracking_id") trackingId: String,
        @Query("active") active: Boolean
    ): Map<String, Any>

    @POST("trackings/check-now")
    suspend fun checkNow(): Map<String, Any>

    @GET("health")
    suspend fun health(): Map<String, Any>
}
