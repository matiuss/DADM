package co.edu.unal.reto8.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import co.edu.unal.reto8.R
import co.edu.unal.reto8.data.model.Company
import co.edu.unal.reto8.databinding.ItemCompanyBinding

class CompanyAdapter(
    private val onEditClick: (Company) -> Unit,
    private val onDeleteClick: (Company) -> Unit,
    private val onPhoneClick: (String) -> Unit,
    private val onEmailClick: (String) -> Unit,
    private val onWebsiteClick: (String) -> Unit
) : ListAdapter<Company, CompanyAdapter.CompanyViewHolder>(CompanyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompanyViewHolder {
        val binding = ItemCompanyBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CompanyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CompanyViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CompanyViewHolder(
        private val binding: ItemCompanyBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(company: Company) {
            val context = binding.root.context

            binding.tvCompanyName.text = company.name

            // Productos y servicios
            if (company.productsServices.isNotBlank()) {
                binding.layoutProducts.visibility = View.VISIBLE
                binding.tvProductsServices.text = company.productsServices
            } else {
                binding.layoutProducts.visibility = View.GONE
            }

            // Teléfono
            if (company.phone.isNotBlank()) {
                binding.layoutPhone.visibility = View.VISIBLE
                binding.tvPhone.text = company.phone
                binding.layoutPhone.setOnClickListener {
                    onPhoneClick(company.phone)
                }
            } else {
                binding.layoutPhone.visibility = View.GONE
            }

            // Email
            if (company.email.isNotBlank()) {
                binding.layoutEmail.visibility = View.VISIBLE
                binding.tvEmail.text = company.email
                binding.layoutEmail.setOnClickListener {
                    onEmailClick(company.email)
                }
            } else {
                binding.layoutEmail.visibility = View.GONE
            }

            // Sitio Web
            if (company.website.isNotBlank()) {
                binding.layoutWebsite.visibility = View.VISIBLE
                binding.tvWebsite.text = company.website
                binding.layoutWebsite.setOnClickListener {
                    onWebsiteClick(company.website)
                }
            } else {
                binding.layoutWebsite.visibility = View.GONE
            }

            // Clasificaciones (Chips estilizados)
            binding.chipGroupClassifications.removeAllViews()
            for (classification in company.classifications) {
                val chip = Chip(context).apply {
                    text = classification
                    isClickable = false
                    isFocusable = false
                    setEnsureMinTouchTargetSize(false)
                    chipCornerRadius = 24f
                    chipStrokeWidth = 0f
                    textSize = 12f

                    when (classification) {
                        Company.CLASSIFICATION_CONSULTORIA -> {
                            chipBackgroundColor = ColorStateList.valueOf(
                                ContextCompat.getColor(context, R.color.chip_consultoria_bg)
                            )
                            setTextColor(ContextCompat.getColor(context, R.color.chip_consultoria_text))
                        }
                        Company.CLASSIFICATION_DESARROLLO -> {
                            chipBackgroundColor = ColorStateList.valueOf(
                                ContextCompat.getColor(context, R.color.chip_desarrollo_bg)
                            )
                            setTextColor(ContextCompat.getColor(context, R.color.chip_desarrollo_text))
                        }
                        Company.CLASSIFICATION_FABRICA -> {
                            chipBackgroundColor = ColorStateList.valueOf(
                                ContextCompat.getColor(context, R.color.chip_fabrica_bg)
                            )
                            setTextColor(ContextCompat.getColor(context, R.color.chip_fabrica_text))
                        }
                        else -> {
                            chipBackgroundColor = ColorStateList.valueOf(
                                ContextCompat.getColor(context, R.color.chip_consultoria_bg)
                            )
                            setTextColor(ContextCompat.getColor(context, R.color.chip_consultoria_text))
                        }
                    }
                }
                binding.chipGroupClassifications.addView(chip)
            }

            // Botones de acción
            binding.btnEdit.setOnClickListener {
                onEditClick(company)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(company)
            }

            binding.root.setOnClickListener {
                onEditClick(company)
            }
        }
    }

    class CompanyDiffCallback : DiffUtil.ItemCallback<Company>() {
        override fun areItemsTheSame(oldItem: Company, newItem: Company): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Company, newItem: Company): Boolean {
            return oldItem == newItem
        }
    }
}
