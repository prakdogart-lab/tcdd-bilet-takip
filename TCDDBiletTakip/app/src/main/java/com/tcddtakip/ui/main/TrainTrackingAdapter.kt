package com.tcddtakip.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.tcddtakip.data.model.SeatType
import com.tcddtakip.data.model.TrainTracking
import com.tcddtakip.databinding.ItemTrackingBinding
import java.text.SimpleDateFormat
import java.util.*

class TrainTrackingAdapter(
    private val onToggle: (TrainTracking, Boolean) -> Unit,
    private val onDelete: (TrainTracking) -> Unit,
    private val onRefresh: (TrainTracking) -> Unit
) : ListAdapter<TrainTracking, TrainTrackingAdapter.VH>(DIFF) {

    inner class VH(val b: ItemTrackingBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTrackingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = getItem(position)
        val b = holder.b

        b.tvTrenAdi.text = "🚆 ${t.trenAdi} (${t.trenNo})"
        b.tvRoute.text = "${t.kalkisIstasyonAdi}  →  ${t.varisIstasyonAdi}"
        b.tvDateTime.text = "${formatDate(t.seferTarihi)}  ·  ${t.kalkisSaati} → ${t.varisSaati}"

        b.tvFoundBadge.isVisible = t.foundSeat
        b.switchActive.isChecked = t.isActive

        b.chipGroupSeatTypes.removeAllViews()
        t.seatTypes.forEach { key ->
            val st = SeatType.fromKey(key)
            val chip = Chip(b.root.context).apply {
                text = "${st?.emoji ?: ""} ${st?.displayName ?: key}"
                isClickable = false
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    if (t.foundSeat) 0xFF2E7D32.toInt() else 0xFF1565C0.toInt()
                )
                setTextColor(0xFFFFFFFF.toInt())
            }
            b.chipGroupSeatTypes.addView(chip)
        }

        b.tvLastChecked.text = if (t.lastChecked > 0) {
            "Son kontrol: ${formatTime(t.lastChecked)}"
        } else {
            "Henüz kontrol edilmedi"
        }

        b.switchActive.setOnCheckedChangeListener(null)
        b.switchActive.setOnCheckedChangeListener { _, checked -> onToggle(t, checked) }
        b.btnDelete.setOnClickListener { onDelete(t) }
        b.btnRefresh.setOnClickListener { onRefresh(t) }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val out = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
            out.format(sdf.parse(dateStr)!!)
        } catch (e: Exception) { dateStr }
    }

    private fun formatTime(epochSecs: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(epochSecs * 1000))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TrainTracking>() {
            override fun areItemsTheSame(a: TrainTracking, b: TrainTracking) = a.id == b.id
            override fun areContentsTheSame(a: TrainTracking, b: TrainTracking) = a == b
        }
    }
}
