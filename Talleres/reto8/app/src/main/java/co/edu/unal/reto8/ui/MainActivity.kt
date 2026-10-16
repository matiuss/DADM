package co.edu.unal.reto8.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import co.edu.unal.reto8.R
import co.edu.unal.reto8.data.db.CompanyDao
import co.edu.unal.reto8.data.model.Company
import co.edu.unal.reto8.databinding.ActivityMainBinding
import co.edu.unal.reto8.ui.adapter.CompanyAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var companyDao: CompanyDao
    private lateinit var adapter: CompanyAdapter

    private var currentFilterClassification: String = "Todas"
    private var currentSearchQuery: String = ""

    private val formLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadCompanies()
        }
    }

    companion object {
        private const val KEY_FILTER = "key_filter_classification"
        private const val KEY_SEARCH = "key_search_query"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        companyDao = CompanyDao(this)

        if (savedInstanceState != null) {
            currentFilterClassification = savedInstanceState.getString(KEY_FILTER, "Todas")
            currentSearchQuery = savedInstanceState.getString(KEY_SEARCH, "")
        }

        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupFab()

        loadCompanies()
    }

    private fun setupRecyclerView() {
        adapter = CompanyAdapter(
            onEditClick = { company -> openEditCompany(company) },
            onDeleteClick = { company -> confirmDeleteCompany(company) },
            onPhoneClick = { phone -> openPhoneDialer(phone) },
            onEmailClick = { email -> openEmailClient(email) },
            onWebsiteClick = { url -> openWebBrowser(url) }
        )

        binding.rvCompanies.layoutManager = LinearLayoutManager(this)
        binding.rvCompanies.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                loadCompanies()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_FILTER, currentFilterClassification)
        outState.putString(KEY_SEARCH, currentSearchQuery)
    }

    private fun setupFilters() {
        // Restaurar estado visual del chip si venimos de rotación
        when (currentFilterClassification) {
            Company.CLASSIFICATION_CONSULTORIA -> binding.chipConsultoria.isChecked = true
            Company.CLASSIFICATION_DESARROLLO -> binding.chipDesarrollo.isChecked = true
            Company.CLASSIFICATION_FABRICA -> binding.chipFabrica.isChecked = true
            else -> binding.chipAll.isChecked = true
        }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) {
                binding.chipAll.isChecked = true
                currentFilterClassification = "Todas"
            } else {
                currentFilterClassification = when (checkedIds.first()) {
                    R.id.chipConsultoria -> Company.CLASSIFICATION_CONSULTORIA
                    R.id.chipDesarrollo -> Company.CLASSIFICATION_DESARROLLO
                    R.id.chipFabrica -> Company.CLASSIFICATION_FABRICA
                    else -> "Todas"
                }
            }
            loadCompanies()
        }
    }

    private fun setupFab() {
        binding.fabAddCompany.setOnClickListener {
            val intent = Intent(this, CompanyFormActivity::class.java)
            formLauncher.launch(intent)
        }
    }

    private fun openEditCompany(company: Company) {
        val intent = Intent(this, CompanyFormActivity::class.java).apply {
            putExtra(CompanyFormActivity.EXTRA_COMPANY, company)
        }
        formLauncher.launch(intent)
    }

    private fun confirmDeleteCompany(company: Company) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, company.name))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                val rows = companyDao.delete(company.id)
                if (rows > 0) {
                    loadCompanies()
                    Snackbar.make(binding.root, R.string.msg_company_deleted, Snackbar.LENGTH_LONG)
                        .setAction("Deshacer") {
                            companyDao.insert(company)
                            loadCompanies()
                        }
                        .show()
                } else {
                    Toast.makeText(this, "Error al eliminar la empresa", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun loadCompanies() {
        val companies = companyDao.filter(currentSearchQuery, currentFilterClassification)
        adapter.submitList(companies)

        // Actualizar contador y estado vacío
        if (companies.isEmpty()) {
            binding.rvCompanies.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.tvResultsCount.visibility = View.GONE

            if (currentSearchQuery.isNotEmpty() || currentFilterClassification != "Todas") {
                binding.tvEmptyTitle.text = getString(R.string.empty_search_title)
                binding.tvEmptySubtitle.text = getString(R.string.empty_search_subtitle)
            } else {
                binding.tvEmptyTitle.text = getString(R.string.empty_companies_title)
                binding.tvEmptySubtitle.text = getString(R.string.empty_companies_subtitle)
            }
        } else {
            binding.rvCompanies.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            binding.tvResultsCount.visibility = View.VISIBLE
            binding.tvResultsCount.text = resources.getQuantityString(
                R.plurals.companies_count,
                companies.size,
                companies.size
            )
        }
    }

    private fun openPhoneDialer(phone: String) {
        if (phone.isBlank()) {
            Toast.makeText(this, "Esta empresa no tiene un teléfono registrado", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${phone.trim()}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se encontró aplicación para realizar llamadas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openEmailClient(email: String) {
        if (email.isBlank()) {
            Toast.makeText(this, "Esta empresa no tiene un email registrado", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${email.trim()}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se encontró aplicación de correo electrónico", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWebBrowser(url: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "Esta empresa no tiene un enlace web registrado", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            var formattedUrl = url.trim()
            if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
                formattedUrl = "https://$formattedUrl"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el enlace en el navegador", Toast.LENGTH_SHORT).show()
        }
    }
}
