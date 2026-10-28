package co.edu.unal.reto9.ui.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.unal.reto9.data.model.PointOfInterest
import co.edu.unal.reto9.databinding.ItemPoiBinding
import java.util.Locale

class PoiAdapter(
    private val onShowOnMapClick: (PointOfInterest) -> Unit,
    private val onDirectionsClick: (PointOfInterest) -> Unit
) : ListAdapter<PointOfInterest, PoiAdapter.PoiViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoiViewHolder {
        val binding = ItemPoiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PoiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PoiViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PoiViewHolder(private val binding: ItemPoiBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(poi: PointOfInterest) {
            binding.tvItemName.text = poi.name
            binding.tvItemCategory.text = poi.subType
            binding.tvItemCategory.setTextColor(poi.category.badgeColorHex)
            binding.tvItemCategory.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f
                setColor(poi.category.badgeBgHex)
            }

            binding.tvItemDistance.text = String.format(Locale.US, "%.2f km", poi.distanceKm)
            binding.tvItemAddress.text = poi.address.ifBlank { poi.category.displayName }
            binding.tvItemCoords.text = String.format(
                Locale.US,
                "Coordenadas: %.5f, %.5f",
                poi.latitude,
                poi.longitude
            )

            binding.root.setOnClickListener { onShowOnMapClick(poi) }
            binding.btnItemShowOnMap.setOnClickListener { onShowOnMapClick(poi) }
            binding.btnItemDirections.setOnClickListener { onDirectionsClick(poi) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<PointOfInterest>() {
        override fun areItemsTheSame(oldItem: PointOfInterest, newItem: PointOfInterest): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PointOfInterest, newItem: PointOfInterest): Boolean =
            oldItem == newItem
    }
}
