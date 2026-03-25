package com.tcddtakip.data.model

import com.google.gson.annotations.SerializedName

data class Station(
    @SerializedName("istasyonAdi") val istasyonAdi: String = "",
    @SerializedName("istasyonId") val istasyonId: Long = 0L,
    @SerializedName("stationName") val stationName: String? = null,
    @SerializedName("stationId") val stationId: Long? = null
) {
    fun displayName() = istasyonAdi.ifBlank { stationName ?: "" }
    fun id() = if (istasyonId != 0L) istasyonId else stationId ?: 0L
    override fun toString() = displayName()
}

data class SeferDto(
    @SerializedName("trenNo") val trenNo: String = "",
    @SerializedName("trenAdi") val trenAdi: String = "",
    @SerializedName("binisTarih") val binisTarih: String = "",
    @SerializedName("inisTarih") val inisTarih: String = "",
    @SerializedName("vagonTipleri") val vagonTipleri: List<VagonTipi>? = null
) {
    fun kalkisSaati(): String =
        binisTarih.substringAfterLast(" ").take(5).ifBlank { binisTarih.take(5) }
    fun varisSaati(): String =
        inisTarih.substringAfterLast(" ").take(5).ifBlank { inisTarih.take(5) }
}

data class VagonTipi(
    @SerializedName("vagonTipId") val vagonTipId: Int = 0,
    @SerializedName("vagonTipAdi") val vagonTipAdi: String = "",
    @SerializedName("bos") val bos: Int = 0,
    @SerializedName("dolu") val dolu: Int = 0,
    @SerializedName("tekerlekliSandalye") val tekerlekliSandalye: Boolean = false
)

enum class SeatType(val displayName: String, val key: String, val emoji: String) {
    EKONOMI("Ekonomi", "EKONOMI", "🪑"),
    BUSINESS("Business", "BUSINESS", "💼"),
    TEKERLEKLI("Tekerlekli Sandalye", "TEKERLEKLI", "♿");
    companion object {
        fun fromKey(key: String) = values().firstOrNull { it.key.equals(key, true) }
    }
}

data class TrainTracking(
    val id: String = "",
    val kalkisIstasyonAdi: String = "",
    val kalkisIstasyonId: Long = 0L,
    val varisIstasyonAdi: String = "",
    val varisIstasyonId: Long = 0L,
    val seferTarihi: String = "",
    val trenNo: String = "",
    val trenAdi: String = "",
    val kalkisSaati: String = "",
    val varisSaati: String = "",
    val seatTypes: List<String> = emptyList(),
    val isActive: Boolean = true,
    val lastChecked: Long = 0L,
    val foundSeat: Boolean = false,
    val autoBook: Boolean = false
)
