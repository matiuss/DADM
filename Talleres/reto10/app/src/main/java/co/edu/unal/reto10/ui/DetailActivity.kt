package co.edu.unal.reto10.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import co.edu.unal.reto10.R
import co.edu.unal.reto10.data.School
import co.edu.unal.reto10.data.toTitleCase
import co.edu.unal.reto10.databinding.ActivityDetailBinding
import co.edu.unal.reto10.databinding.ItemDetailFieldBinding

/**
 * Detalle de un colegio. Recibe el JSON original del registro y lo vuelve a
 * procesar con org.json para mostrar todos sus campos.
 */
class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val raw = intent.getStringExtra(EXTRA_SCHOOL_JSON)
        if (raw == null) {
            finish()
            return
        }
        val school = School.fromJson(raw)
        bind(school)
    }

    private fun bind(school: School) {
        binding.textName.text = school.name.toTitleCase()
        binding.textSubtitle.text = listOf(school.type, school.provider)
            .filter { it.isNotEmpty() }
            .joinToString(" · ") { it.toTitleCase() }

        val fields = listOf(
            R.string.field_address to school.address,
            R.string.field_phone to school.phone,
            R.string.field_email to school.email,
            R.string.field_principal to school.principal.toTitleCase(),
            R.string.field_type to school.type.toTitleCase(),
            R.string.field_provider to school.provider.toTitleCase(),
            R.string.field_zone to school.pretty(school.zone),
            R.string.field_calendar to school.calendar,
            R.string.field_schedule to school.pretty(school.schedule),
            R.string.field_levels to school.pretty(school.levels),
            R.string.field_grades to school.grades.replace(",", ", "),
            R.string.field_models to school.pretty(school.educationalModels),
            R.string.field_languages to school.pretty(school.languages),
            R.string.field_campus to school.campusCount,
            R.string.field_building to school.buildingOwner.toTitleCase(),
            R.string.field_contracted to school.contractedEnrollment.toTitleCase(),
            R.string.field_secretaria to school.secretaria.toTitleCase(),
            R.string.field_year to school.year
        )

        binding.containerFields.removeAllViews()
        fields.forEach { (label, value) ->
            val row = ItemDetailFieldBinding.inflate(layoutInflater, binding.containerFields, true)
            row.textLabel.setText(label)
            row.textValue.text = value.ifBlank { getString(R.string.no_data) }
        }

        val phone = school.firstPhone
        binding.buttonCall.isEnabled = phone != null
        binding.buttonCall.setOnClickListener {
            launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
        }

        val email = school.firstEmail
        binding.buttonEmail.isEnabled = email != null
        binding.buttonEmail.setOnClickListener {
            launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
        }

        binding.buttonMap.isEnabled = school.address.isNotEmpty()
        binding.buttonMap.setOnClickListener {
            val query = Uri.encode("${school.address}, Bogotá, Colombia")
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query")))
        }
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_SCHOOL_JSON = "extra_school_json"
    }
}
