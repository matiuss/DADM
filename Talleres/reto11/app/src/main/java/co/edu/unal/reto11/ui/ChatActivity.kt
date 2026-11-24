package co.edu.unal.reto11.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.unal.reto11.R
import co.edu.unal.reto11.data.AiProvider
import co.edu.unal.reto11.data.GeminiException
import co.edu.unal.reto11.data.LocalModel
import co.edu.unal.reto11.data.NetworkMonitor
import co.edu.unal.reto11.data.Persona
import co.edu.unal.reto11.data.SettingsStore
import co.edu.unal.reto11.databinding.ActivityChatBinding
import co.edu.unal.reto11.databinding.DialogSettingsBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Pantalla principal: el chatbot. */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: MessageAdapter
    private lateinit var networkMonitor: NetworkMonitor
    private var renderedPersona: Persona? = null

    private companion object {
        const val MB = 1024L * 1024L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = MessageAdapter(
            avatar = { viewModel.state.value.persona.emoji },
            onCopy = ::copyToClipboard,
            onRetry = viewModel::retryLast
        )
        binding.messages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messages.adapter = adapter

        binding.sendButton.setOnClickListener {
            if (viewModel.state.value.isGenerating) viewModel.stop() else sendCurrentInput()
        }
        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentInput(); true
            } else false
        }
        binding.offlineRetry.setOnClickListener { openConnectivitySettings() }

        networkMonitor = NetworkMonitor(this) { online ->
            binding.offlineBanner.visibility = if (online) View.GONE else View.VISIBLE
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }

        // Primera vez sin modelo descargado (o sin API key si se usa Gemini): se abre la configuración.
        val needsSetup = when (viewModel.settings.provider) {
            AiProvider.LOCAL -> !viewModel.activeModelStore.isDownloaded
            AiProvider.GEMINI -> !viewModel.settings.hasApiKey
        }
        if (savedInstanceState == null && needsSetup) showSettingsDialog()
    }

    override fun onStart() {
        super.onStart()
        networkMonitor.start()
    }

    override fun onStop() {
        networkMonitor.stop()
        super.onStop()
    }

    private fun render(state: ChatUiState) {
        if (renderedPersona != state.persona) {
            renderedPersona = state.persona
            renderEmptyState(state.persona)
        }
        updateSubtitle()

        val wasAtBottom = !binding.messages.canScrollVertically(1)
        adapter.submitList(state.messages) {
            if (wasAtBottom && state.messages.isNotEmpty()) {
                binding.messages.scrollToPosition(state.messages.lastIndex)
            }
        }
        binding.emptyState.visibility = if (state.messages.isEmpty()) View.VISIBLE else View.GONE

        binding.sendButton.setImageResource(if (state.isGenerating) R.drawable.ic_stop else R.drawable.ic_send)
        binding.sendButton.contentDescription =
            getString(if (state.isGenerating) R.string.action_stop else R.string.action_send)
        invalidateOptionsMenu()
    }

    private fun renderEmptyState(persona: Persona) {
        binding.emptyEmoji.text = persona.emoji
        binding.emptyTitle.text = persona.title
        binding.emptyGreeting.text = persona.greeting
        binding.suggestions.removeAllViews()
        persona.suggestions.forEach { suggestion ->
            val chip = layoutInflater.inflate(R.layout.item_suggestion, binding.suggestions, false) as Chip
            chip.text = suggestion
            chip.setOnClickListener { viewModel.send(suggestion) }
            binding.suggestions.addView(chip)
        }
    }

    private fun updateSubtitle() {
        val state = viewModel.state.value
        val engine = when (viewModel.settings.provider) {
            AiProvider.LOCAL -> "${viewModel.settings.localModel.displayName} ${getString(R.string.subtitle_local)}" +
                (viewModel.localBackend?.let { " · $it" } ?: "")
            AiProvider.GEMINI -> viewModel.settings.model
        }
        binding.toolbar.subtitle = "${state.persona.emoji} ${state.persona.title} · $engine"
    }

    private fun sendCurrentInput() {
        val text = binding.input.text?.toString().orEmpty()
        if (text.isBlank() || viewModel.state.value.isGenerating) return
        viewModel.send(text)
        binding.input.text?.clear()
        binding.messages.post {
            if (adapter.itemCount > 0) binding.messages.smoothScrollToPosition(adapter.itemCount - 1)
        }
    }

    // ---------------------------------------------------------------- Menú

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_clear)?.isEnabled = viewModel.state.value.messages.isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_persona -> { showPersonaDialog(); true }
        R.id.action_clear -> { confirmClear(); true }
        R.id.action_settings -> { showSettingsDialog(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showPersonaDialog() {
        val personas = Persona.entries
        val labels = personas.map { "${it.emoji}  ${it.title}" }.toTypedArray()
        val current = personas.indexOf(viewModel.state.value.persona)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.persona_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val chosen = personas[which]
                dialog.dismiss()
                if (chosen == viewModel.state.value.persona) return@setSingleChoiceItems
                if (viewModel.state.value.messages.isEmpty()) {
                    viewModel.setPersona(chosen)
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(getString(R.string.persona_change_confirm, chosen.title))
                        .setPositiveButton(R.string.action_continue) { _, _ -> viewModel.setPersona(chosen) }
                        .setNegativeButton(R.string.action_cancel, null)
                        .show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_title)
            .setMessage(R.string.clear_message)
            .setPositiveButton(R.string.action_clear) { _, _ -> viewModel.clear() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showSettingsDialog() {
        val settings = viewModel.settings
        val dialogBinding = DialogSettingsBinding.inflate(LayoutInflater.from(this))
        dialogBinding.apiKey.setText(settings.apiKey)
        dialogBinding.model.setText(settings.model, false)
        dialogBinding.model.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, SettingsStore.SUGGESTED_MODELS)
        )
        // Carga los modelos reales de la API al abrir y cada vez que se termina de editar la key.
        var modelsJob: Job? = null
        fun loadModels() {
            val key = dialogBinding.apiKey.text?.toString()?.trim().orEmpty()
            if (key.isEmpty()) return
            modelsJob?.cancel()
            dialogBinding.modelLayout.helperText = getString(R.string.settings_models_loading)
            modelsJob = lifecycleScope.launch {
                try {
                    val models = viewModel.listModels(key)
                    if (models.isNotEmpty()) {
                        dialogBinding.model.setAdapter(
                            ArrayAdapter(this@ChatActivity, android.R.layout.simple_list_item_1, models)
                        )
                    }
                    val current = dialogBinding.model.text.toString()
                    dialogBinding.modelLayout.helperText = when {
                        models.isEmpty() -> getString(R.string.settings_models_none)
                        current in models -> getString(R.string.settings_models_count, models.size)
                        else -> getString(R.string.settings_model_unavailable, current)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dialogBinding.modelLayout.helperText =
                        (e as? GeminiException)?.message?.substringBefore("\n")
                            ?: getString(R.string.settings_models_error)
                }
            }
        }
        dialogBinding.apiKey.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) loadModels() }
        dialogBinding.model.setOnItemClickListener { _, _, _, _ ->
            dialogBinding.modelLayout.helperText = null
        }
        dialogBinding.temperature.value = settings.temperature
        dialogBinding.getKeyLink.setOnClickListener {
            openUrl("https://aistudio.google.com/apikey")
        }

        // Selector de motor: muestra la sección correspondiente.
        fun showProvider(provider: AiProvider) {
            dialogBinding.localSection.visibility = if (provider == AiProvider.LOCAL) View.VISIBLE else View.GONE
            dialogBinding.geminiSection.visibility = if (provider == AiProvider.GEMINI) View.VISIBLE else View.GONE
            if (provider == AiProvider.GEMINI && modelsJob == null) loadModels()
        }
        dialogBinding.providerGroup.check(
            if (settings.provider == AiProvider.LOCAL) R.id.providerLocal else R.id.providerGemini
        )
        dialogBinding.providerGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) showProvider(if (checkedId == R.id.providerLocal) AiProvider.LOCAL else AiProvider.GEMINI)
        }
        showProvider(settings.provider)

        // Sección del modelo local: selector de modelo y estado de su descarga en vivo. El modelo
        // elegido se guarda al pulsar "Guardar"; descargar o eliminar actúa de inmediato.
        val localModels = LocalModel.entries
        var selectedModel = settings.localModel
        dialogBinding.localModel.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, localModels.map { it.label })
        )
        fun showLocalModel(model: LocalModel) {
            selectedModel = model
            dialogBinding.localModel.setText(model.label, false)
            dialogBinding.localHelp.text = getString(R.string.settings_local_help, model.description, model.sizeLabel)
            renderLocalModel(dialogBinding, viewModel.download.value, model)
        }
        dialogBinding.localModel.setOnItemClickListener { _, _, position, _ -> showLocalModel(localModels[position]) }
        dialogBinding.localDelete.setOnClickListener {
            val model = selectedModel
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.local_delete_title)
                .setMessage(getString(R.string.local_delete_message, model.displayName, model.sizeLabel))
                .setPositiveButton(R.string.local_delete) { _, _ -> viewModel.deleteModel(model) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
        showLocalModel(selectedModel)
        val downloadJob = lifecycleScope.launch {
            viewModel.download.collect { state -> renderLocalModel(dialogBinding, state, selectedModel) }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                settings.provider = if (dialogBinding.providerGroup.checkedButtonId == R.id.providerGemini) {
                    AiProvider.GEMINI
                } else {
                    AiProvider.LOCAL
                }
                settings.localModel = selectedModel
                settings.apiKey = dialogBinding.apiKey.text?.toString().orEmpty()
                settings.model = dialogBinding.model.text?.toString().orEmpty()
                settings.temperature = dialogBinding.temperature.value
                updateSubtitle()
                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .setOnDismissListener {
                modelsJob?.cancel()
                downloadJob.cancel() // la descarga sigue en el ViewModel; solo se deja de mostrar
            }
            .show()
    }

    /** Muestra el estado de [model] (listo, descargando, pausado o con error). */
    private fun renderLocalModel(b: DialogSettingsBinding, state: DownloadState, model: LocalModel) {
        val store = viewModel.modelStore(model)
        val ready = store.isDownloaded
        val partialMb = (store.downloadedBytes / MB).toInt()
        // La descarga en curso (o el error) puede ser de otro modelo distinto al que se está viendo.
        val running = (state as? DownloadState.Running)?.takeIf { it.model == model }
        val failed = (state as? DownloadState.Failed)?.takeIf { it.model == model }
        val otherRunning = (state as? DownloadState.Running)?.takeIf { it.model != model }
        b.localStatus.text = when {
            running != null ->
                getString(R.string.local_status_downloading, (running.downloaded / MB).toInt(), model.sizeLabel)
            ready -> getString(R.string.local_status_ready)
            failed != null -> getString(R.string.local_status_failed, failed.message)
            otherRunning != null ->
                getString(R.string.local_status_other_downloading, otherRunning.model.displayName)
            partialMb > 0 -> getString(R.string.local_status_paused, partialMb, model.sizeLabel)
            else -> getString(R.string.local_status_missing)
        }
        b.localStatus.setTextColor(getColor(if (failed != null && !ready) R.color.error else R.color.on_surface))

        val progress = when {
            running != null -> running.downloaded * 1000 / running.total
            !ready && partialMb > 0 -> store.downloadedBytes * 1000 / model.sizeBytes
            else -> -1
        }
        b.localProgress.visibility = if (progress >= 0) View.VISIBLE else View.GONE
        if (progress >= 0) b.localProgress.setProgressCompat(progress.toInt(), true)

        b.localDownload.visibility = if (ready) View.GONE else View.VISIBLE
        b.localDownload.text = when {
            running != null -> getString(R.string.local_pause)
            partialMb > 0 -> getString(R.string.local_resume)
            else -> getString(R.string.local_download, model.sizeLabel)
        }
        b.localDownload.setOnClickListener {
            if (running != null) viewModel.cancelDownload() else viewModel.startDownload(model)
        }
        b.localDelete.visibility = if (ready || (partialMb > 0 && running == null)) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- Utilidades

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
        // Android 13+ ya muestra su propio aviso al copiar.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show()
        }
    }

    private fun openConnectivitySettings() {
        try {
            startActivity(NetworkMonitor.connectivitySettingsIntent())
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.offline_message, Toast.LENGTH_LONG).show()
        }
    }
}
