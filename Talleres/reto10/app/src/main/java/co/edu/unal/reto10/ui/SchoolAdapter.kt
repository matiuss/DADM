package co.edu.unal.reto10.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.unal.reto10.R
import co.edu.unal.reto10.data.School
import co.edu.unal.reto10.data.toTitleCase
import co.edu.unal.reto10.databinding.ItemFooterBinding
import co.edu.unal.reto10.databinding.ItemSchoolBinding

/**
 * Lista de colegios con un pie que indica el progreso de la paginación
 * ("Mostrando X de Y", cargando más o error para reintentar).
 * Usa [ListAdapter] + [DiffUtil] para actualizar solo los elementos nuevos.
 */
class SchoolAdapter(
    private val onSchoolClick: (School) -> Unit,
    private val onRetryClick: () -> Unit
) : ListAdapter<SchoolAdapter.Row, RecyclerView.ViewHolder>(DIFF) {

    data class Footer(val text: String, val loading: Boolean, val retry: Boolean)

    sealed class Row {
        data class SchoolRow(val school: School) : Row()
        data class FooterRow(val footer: Footer) : Row()
    }

    fun submit(schools: List<School>, footer: Footer?) {
        val rows = schools.map { Row.SchoolRow(it) } + listOfNotNull(footer?.let { Row.FooterRow(it) })
        submitList(rows)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is Row.SchoolRow -> TYPE_SCHOOL
        is Row.FooterRow -> TYPE_FOOTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SCHOOL) {
            SchoolViewHolder(ItemSchoolBinding.inflate(inflater, parent, false))
        } else {
            FooterViewHolder(ItemFooterBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is Row.SchoolRow -> (holder as SchoolViewHolder).bind(row.school)
            is Row.FooterRow -> (holder as FooterViewHolder).bind(row.footer)
        }
    }

    inner class SchoolViewHolder(private val b: ItemSchoolBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(school: School) {
            val ctx = b.root.context
            b.textName.text = school.name.toTitleCase()
            b.textAddress.text = school.address.ifEmpty { ctx.getString(R.string.no_data) }
            b.textSchedule.text = school.pretty(school.schedule).ifEmpty { ctx.getString(R.string.no_data) }

            b.textProvider.visibility = if (school.provider.isEmpty()) View.GONE else View.VISIBLE
            b.textProvider.text = school.provider.toTitleCase()
            val badgeColor = if (school.provider == "OFICIAL") R.color.badge_official else R.color.badge_private
            b.textProvider.setTextColor(ContextCompat.getColor(ctx, badgeColor))

            b.root.setOnClickListener { onSchoolClick(school) }
        }
    }

    inner class FooterViewHolder(private val b: ItemFooterBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(footer: Footer) {
            b.progressFooter.visibility = if (footer.loading) View.VISIBLE else View.GONE
            b.textFooter.text = footer.text
            if (footer.retry) {
                b.root.setOnClickListener { onRetryClick() }
            } else {
                b.root.setOnClickListener(null)
                b.root.isClickable = false
            }
        }
    }

    companion object {
        private const val TYPE_SCHOOL = 0
        private const val TYPE_FOOTER = 1

        private val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean = when {
                oldItem is Row.SchoolRow && newItem is Row.SchoolRow ->
                    oldItem.school.rawJson == newItem.school.rawJson
                oldItem is Row.FooterRow && newItem is Row.FooterRow -> true
                else -> false
            }

            override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean = oldItem == newItem
        }
    }
}
