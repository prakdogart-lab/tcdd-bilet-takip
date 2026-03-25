package com.tcddtakip.ui.addtrain

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tcddtakip.data.model.SeferDto
import com.tcddtakip.databinding.ItemTrainSelectBinding

class TrainSelectAdapter(
    private val onSelect: (SeferDto) -> Unit
) : ListAdapter<SeferDto, TrainSelectAdapter.VH>(DIFF) {

    private var selectedPos = -1

    inner class VH(val b: ItemTrainSelectBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTrainSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sefer = getItem(position)
        val b = holder.b
        val isSelected = position == selectedPos

        b.tvTrenNo.text = sefer.trenNo
        b.tvTrenAdi.text = sefer.trenAdi.ifBlank { "—" }
        b.tvKalkisSaati.text = sefer.kalkisSaati()
        b.tvVarisSaati.text = "→ ${sefer.varisSaati()}"
        b.radioTrain.isChecked = isSelected
        b.cardTrain.strokeColor = if (isSelected) 0xFF1565C0.toInt() else 0xFFE0E0E0.toInt()
        b.cardTrain.strokeWidth = if (isSelected) 3 else 1

        b.cardTrain.setOnClickListener {
            val prev = selectedPos
            selectedPos = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPos)
            onSelect(sefer)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SeferDto>() {
            override fun areItemsTheSame(a: SeferDto, b: SeferDto) =
                a.trenNo == b.trenNo && a.binisTarih == b.binisTarih
            override fun areContentsTheSame(a: SeferDto, b: SeferDto) = a == b
        }
    }
}
