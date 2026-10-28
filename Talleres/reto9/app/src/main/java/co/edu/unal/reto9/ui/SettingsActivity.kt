package co.edu.unal.reto9.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import co.edu.unal.reto9.data.preferences.AppPreferences
import co.edu.unal.reto9.databinding.ActivitySettingsBinding
import java.util.Locale
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPreferences = AppPreferences(this)

        loadCurrentPreferences()
        setupListeners()
    }

    private fun loadCurrentPreferences() {
        val currentRadius = appPreferences.radiusKm
        val sliderValue = currentRadius.coerceIn(0.5f, 30.0f)
        // Redondear al múltiplo de 0.5 más cercano para el Slider
        val steppedValue = ((sliderValue * 2f).roundToInt() / 2f).coerceIn(0.5f, 30.0f)

        binding.sliderRadiusKm.value = steppedValue
        binding.etRadiusKm.setText(String.format(Locale.US, "%.1f", currentRadius))
        binding.tvRadiusPreview.text = String.format(Locale.US, "%.1f km", currentRadius)

        binding.switchShowPoiLabels.isChecked = appPreferences.showPoiLabelsOnMap

        val maxResults = appPreferences.maxResults.coerceIn(10, 100)
        val steppedMax = ((maxResults / 10) * 10).coerceIn(10, 100).toFloat()
        binding.sliderMaxResults.value = steppedMax
        binding.tvMaxResultsLabel.text =
            "Número máximo de puntos de interés a mostrar: ${steppedMax.toInt()}"
    }

    private fun setupListeners() {
        binding.btnBackSettings.setOnClickListener {
            finish()
        }

        binding.sliderRadiusKm.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.tvRadiusPreview.text = String.format(Locale.US, "%.1f km", value)
                binding.etRadiusKm.setText(String.format(Locale.US, "%.1f", value))
            }
        }

        binding.chipPreset1Km.setOnClickListener { applyRadiusPreset(1.0f) }
        binding.chipPreset2Km.setOnClickListener { applyRadiusPreset(2.0f) }
        binding.chipPreset5Km.setOnClickListener { applyRadiusPreset(5.0f) }
        binding.chipPreset10Km.setOnClickListener { applyRadiusPreset(10.0f) }
        binding.chipPreset20Km.setOnClickListener { applyRadiusPreset(20.0f) }

        binding.sliderMaxResults.addOnChangeListener { _, value, _ ->
            binding.tvMaxResultsLabel.text =
                "Número máximo de puntos de interés a mostrar: ${value.toInt()}"
        }

        binding.btnSavePreferences.setOnClickListener {
            savePreferencesAndClose()
        }
    }

    private fun applyRadiusPreset(km: Float) {
        binding.sliderRadiusKm.value = km.coerceIn(0.5f, 30.0f)
        binding.etRadiusKm.setText(String.format(Locale.US, "%.1f", km))
        binding.tvRadiusPreview.text = String.format(Locale.US, "%.1f km", km)
    }

    private fun savePreferencesAndClose() {
        val rawInput = binding.etRadiusKm.text?.toString()?.trim()?.replace(',', '.') ?: ""
        val parsedRadius = rawInput.toFloatOrNull() ?: binding.sliderRadiusKm.value

        if (parsedRadius <= 0f || parsedRadius > 100f) {
            binding.etRadiusKm.error = "Ingresa un radio válido entre 0.2 y 100 km"
            return
        }

        appPreferences.radiusKm = parsedRadius
        appPreferences.showPoiLabelsOnMap = binding.switchShowPoiLabels.isChecked
        appPreferences.maxResults = binding.sliderMaxResults.value.toInt()

        Toast.makeText(
            this,
            String.format(Locale.US, "Preferencias guardadas: Radio = %.1f km", parsedRadius),
            Toast.LENGTH_SHORT
        ).show()

        setResult(Activity.RESULT_OK)
        finish()
    }
}
