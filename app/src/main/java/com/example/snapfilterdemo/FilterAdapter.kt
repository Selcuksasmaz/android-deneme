package com.example.snapfilterdemo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FilterAdapter(
    private val filterList: List<FilterItem>,
    private val onFilterSelected: (FilterType) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {

    private var selectedPosition = 0

    inner class FilterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val filterIcon: ImageView = itemView.findViewById(R.id.filterIcon)
        val filterName: TextView = itemView.findViewById(R.id.filterName)

        fun bind(filterItem: FilterItem) {
            filterIcon.setImageResource(filterItem.iconResource)
            filterName.text = filterItem.name

            // Seçili öğeyi vurgula
            itemView.isSelected = adapterPosition == selectedPosition

            itemView.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = adapterPosition

                // Önceki ve yeni seçilen öğeleri güncelle
                notifyItemChanged(oldPosition)
                notifyItemChanged(selectedPosition)

                // Filtre seçimini bildir
                onFilterSelected(filterItem.filterType)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.filter_item, parent, false)
        return FilterViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        holder.bind(filterList[position])
    }

    override fun getItemCount() = filterList.size
}

data class FilterItem(
    val filterType: FilterType,
    val iconResource: Int,
    val name: String
)