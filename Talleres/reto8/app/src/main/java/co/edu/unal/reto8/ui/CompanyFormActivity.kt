package co.edu.unal.reto8.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import co.edu.unal.reto8.R
import co.edu.unal.reto8.data.db.CompanyDao
import co.edu.unal.reto8.data.model.Company
import co.edu.unal.reto8.databinding.ActivityCompanyFormBinding

class CompanyFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompanyFormBinding
    private lateinit var companyDao: CompanyDao
    private var editingCompany: Company? = null

    companion object {
        const val EXTRA_COMPANY = "extra_company"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompanyFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        companyDao = CompanyDao(this)

        editingCompany = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_COMPANY, Company::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_COMPANY) as? Company
        }

        setupToolbar()
        if (savedInstanceState == null) {
            populateFormIfEditing()
        }
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        if (editingCompany != null) {
            binding.toolbar.title = getString(R.string.title_edit_company)
            binding.btnSave.text = getString(R.string.title_edit_company)
        } else {
            binding.toolbar.title = getString(R.string.title_add_company)
            binding.btnSave.text = getString(R.string.btn_save)
        }
    }

    private fun populateFormIfEditing() {
        val company = editingCompany ?: return

        binding.etName.setText(company.name)
        binding.etWebsite.setText(company.website)
        binding.etPhone.setText(company.phone)
        binding.etEmail.setText(company.email)
        binding.etProductsServices.setText(company.productsServices)

        binding.cbConsultoria.isChecked = company.classifications.contains(Company.CLASSIFICATION_CONSULTORIA)
        binding.cbDesarrollo.isChecked = company.classifications.contains(Company.CLASSIFICATION_DESARROLLO)
        binding.cbFabrica.isChecked = company.classifications.contains(Company.CLASSIFICATION_FABRICA)
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveCompany()
        }

        // Limpiar errores reactivamente al escribir
        binding.etName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrBlank()) binding.tilName.error = null
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.etEmail.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilEmail.error = null
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Limpiar mensaje de error si marca alguna casilla
        val checkboxListener = View.OnClickListener {
            if (binding.cbConsultoria.isChecked || binding.cbDesarrollo.isChecked || binding.cbFabrica.isChecked) {
                binding.tvClassificationError.visibility = View.GONE
            }
        }
        binding.cbConsultoria.setOnClickListener(checkboxListener)
        binding.cbDesarrollo.setOnClickListener(checkboxListener)
        binding.cbFabrica.setOnClickListener(checkboxListener)
    }

    private fun saveCompany() {
        val name = binding.etName.text?.toString()?.trim() ?: ""
        val website = binding.etWebsite.text?.toString()?.trim() ?: ""
        val phone = binding.etPhone.text?.toString()?.trim() ?: ""
        val email = binding.etEmail.text?.toString()?.trim() ?: ""
        val productsServices = binding.etProductsServices.text?.toString()?.trim() ?: ""

        // Validación de Nombre
        if (name.isEmpty()) {
            binding.tilName.error = getString(R.string.error_name_required)
            binding.etName.requestFocus()
            return
        } else {
            binding.tilName.error = null
        }

        // Validación de formato de Email si se ingresó
        if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Ingresa un correo electrónico válido"
            binding.etEmail.requestFocus()
            return
        } else {
            binding.tilEmail.error = null
        }

        // Validación de Clasificación (al menos una seleccionada)
        val selectedClassifications = mutableListOf<String>()
        if (binding.cbConsultoria.isChecked) selectedClassifications.add(Company.CLASSIFICATION_CONSULTORIA)
        if (binding.cbDesarrollo.isChecked) selectedClassifications.add(Company.CLASSIFICATION_DESARROLLO)
        if (binding.cbFabrica.isChecked) selectedClassifications.add(Company.CLASSIFICATION_FABRICA)

        if (selectedClassifications.isEmpty()) {
            binding.tvClassificationError.visibility = View.VISIBLE
            return
        } else {
            binding.tvClassificationError.visibility = View.GONE
        }

        val currentEditing = editingCompany
        if (currentEditing != null) {
            // Actualizar en SQLite
            val updated = currentEditing.copy(
                name = name,
                website = website,
                phone = phone,
                email = email,
                productsServices = productsServices,
                classifications = selectedClassifications
            )
            val rowsAffected = companyDao.update(updated)
            if (rowsAffected > 0) {
                Toast.makeText(this, getString(R.string.msg_company_updated), Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Error al actualizar en base de datos", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Insertar en SQLite
            val newCompany = Company(
                name = name,
                website = website,
                phone = phone,
                email = email,
                productsServices = productsServices,
                classifications = selectedClassifications
            )
            val newId = companyDao.insert(newCompany)
            if (newId != -1L) {
                Toast.makeText(this, getString(R.string.msg_company_saved), Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Error al guardar en base de datos", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
