package co.edu.unal.reto10.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import co.edu.unal.reto10.R
import co.edu.unal.reto10.data.FilterOptions
import co.edu.unal.reto10.data.NetworkMonitor
import co.edu.unal.reto10.data.SchoolFilter
import co.edu.unal.reto10.databinding.ActivitySearchBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView

/**
 * Pantalla principal: formulario con los parámetros que se envían al servicio web.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding

    /** Muestra u oculta el aviso "Sin conexión" en tiempo real. */
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        networkMonitor = NetworkMonitor(this) { online ->
            binding.bannerOffline.visibility = if (online) View.GONE else View.VISIBLE
        }
        binding.buttonBannerConnect.setOnClickListener { openConnectivitySettings() }

        binding.buttonSearch.setOnClickListener { search() }
        binding.buttonClear.setOnClickListener { clearFilters() }
        binding.editName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(); true
            } else false
        }
    }

    override fun onStart() {
        super.onStart()
        networkMonitor.start()
    }

    override fun onStop() {
        super.onStop()
        networkMonitor.stop()
    }

    override fun onResume() {
        super.onResume()
        // Se cargan (o restablecen al volver de otra pantalla) las opciones de cada filtro,
        // conservando la selección actual del usuario.
        setupDropdown(binding.dropdownProvider, FilterOptions.providers)
        setupDropdown(binding.dropdownType, FilterOptions.types)
        setupDropdown(binding.dropdownSchedule, FilterOptions.schedules)
        setupDropdown(binding.dropdownLevel, FilterOptions.levels)
        setupDropdown(binding.dropdownCalendar, FilterOptions.calendars)
    }

    private fun setupDropdown(view: MaterialAutoCompleteTextView, options: List<Pair<String, String?>>) {
        val current = view.text?.toString().orEmpty()
        view.setSimpleItems(options.map { it.first }.toTypedArray())
        view.setText(current.ifEmpty { FilterOptions.ALL }, false)
    }

    /** Traduce la etiqueta elegida al valor exacto del dataset (null si es "Todos"). */
    private fun selectedValue(view: MaterialAutoCompleteTextView, options: List<Pair<String, String?>>): String? {
        val label = view.text?.toString()
        return options.firstOrNull { it.first == label }?.second
    }

    private fun buildFilter() = SchoolFilter(
        name = binding.editName.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
        provider = selectedValue(binding.dropdownProvider, FilterOptions.providers),
        type = selectedValue(binding.dropdownType, FilterOptions.types),
        schedule = selectedValue(binding.dropdownSchedule, FilterOptions.schedules),
        level = selectedValue(binding.dropdownLevel, FilterOptions.levels),
        calendar = selectedValue(binding.dropdownCalendar, FilterOptions.calendars),
        teachesEnglish = binding.switchEnglish.isChecked
    )

    private fun search() {
        // Sin internet no se abre la pantalla de resultados: se avisa y se ofrece conectarse.
        if (!NetworkMonitor.isOnline(this)) {
            showOfflineDialog()
            return
        }
        startActivity(
            Intent(this, ResultsActivity::class.java)
                .putExtra(ResultsActivity.EXTRA_FILTER, buildFilter())
        )
    }

    private fun showOfflineDialog() {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_wifi_off)
            .setTitle(R.string.offline_title)
            .setMessage(R.string.offline_message)
            .setPositiveButton(R.string.action_connect) { _, _ -> openConnectivitySettings() }
            .setNeutralButton(R.string.action_retry) { _, _ -> search() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun openConnectivitySettings() {
        try {
            startActivity(NetworkMonitor.connectivitySettingsIntent())
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun clearFilters() {
        binding.editName.setText("")
        listOf(
            binding.dropdownProvider,
            binding.dropdownType,
            binding.dropdownSchedule,
            binding.dropdownLevel,
            binding.dropdownCalendar
        ).forEach { it.setText(FilterOptions.ALL, false) }
        binding.switchEnglish.isChecked = false
    }
}
