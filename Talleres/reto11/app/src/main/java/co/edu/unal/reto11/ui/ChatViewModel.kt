package co.edu.unal.reto11.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unal.reto11.data.AiProvider
import co.edu.unal.reto11.data.ChatMessage
import co.edu.unal.reto11.data.GeminiClient
import co.edu.unal.reto11.data.GeminiException
import co.edu.unal.reto11.data.LocalLlmClient
import co.edu.unal.reto11.data.LocalModel
import co.edu.unal.reto11.data.LocalModelStore
import co.edu.unal.reto11.data.NetworkMonitor
import co.edu.unal.reto11.data.Persona
import co.edu.unal.reto11.data.Role
import co.edu.unal.reto11.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

data class ChatUiState(
    val persona: Persona,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false
)

/** Estado de la descarga del modelo local. */
sealed interface DownloadState {
    /** Sin descarga en curso; [bytesOnDisk] cambia al terminar, pausar o borrar, para refrescar la UI. */
    data class Idle(val bytesOnDisk: Long) : DownloadState
    data class Running(val downloaded: Long, val total: Long) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** Mantiene la conversación (sobrevive a rotaciones) y orquesta las llamadas a Gemini. */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsStore(application)
    private val client = GeminiClient()
    val modelStore = LocalModelStore(application)
    private val localClient = LocalLlmClient(application, modelStore.file)

    /** "GPU" o "CPU" según dónde corre el modelo local; null si aún no se ha cargado. */
    val localBackend: String? get() = localClient.backendName
    private var downloadJob: Job? = null

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle(modelStore.downloadedBytes))
    val download: StateFlow<DownloadState> = _download.asStateFlow()
    private var nextId = 0L
    private var generationJob: Job? = null

    /** Modelos disponibles por API key, para elegir respaldos sin consultar la API cada vez. */
    private var modelCache: Pair<String, List<String>>? = null

    private val _state = MutableStateFlow(ChatUiState(persona = settings.persona))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** Envía un mensaje del usuario y va agregando la respuesta del modelo a medida que llega. */
    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _state.value.isGenerating) return

        val userMessage = ChatMessage(nextId++, Role.USER, prompt)
        val history = _state.value.messages.filter { !it.isError } + userMessage
        val replyId = nextId++
        _state.update {
            it.copy(
                messages = it.messages + userMessage +
                    ChatMessage(replyId, Role.MODEL, "", isStreaming = true),
                isGenerating = true
            )
        }

        // LAZY: viewModelScope usa Main.immediate, así que sin esto la corrutina podría terminar
        // (p. ej. por falta de API key) antes de asignar generationJob y el estado no se liberaría.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val context = getApplication<Application>()
            try {
                val systemPrompt = _state.value.persona.systemPrompt + "\n\n" + currentDateContext()
                when (settings.provider) {
                    AiProvider.LOCAL -> streamLocal(replyId, systemPrompt, history)
                    AiProvider.GEMINI -> {
                        if (!settings.hasApiKey) {
                            throw GeminiException("Falta la API key de Gemini. Configúrala en el menú ⋮ → Ajustes.")
                        }
                        if (!NetworkMonitor.isOnline(context)) {
                            throw GeminiException("No hay conexión a internet.")
                        }
                        streamWithFallback(replyId = replyId, systemPrompt = systemPrompt, history = history)
                    }
                }
                updateMessage(replyId) { it.copy(isStreaming = false) }
            } catch (e: CancellationException) {
                // El usuario pulsó "Detener": se conserva lo que alcanzó a generarse.
                updateMessage(replyId) {
                    it.copy(
                        text = it.text.ifEmpty { "(Respuesta detenida)" },
                        isStreaming = false,
                        isError = it.text.isEmpty(),
                        note = null
                    )
                }
                throw e
            } catch (e: Exception) {
                updateMessage(replyId) {
                    it.copy(text = friendlyError(e), isStreaming = false, isError = true, note = null)
                }
            } finally {
                // Solo libera el estado si este trabajo sigue siendo el actual (tras "Borrar" o
                // cambiar de personalidad puede haber empezado ya otra generación).
                if (generationJob === coroutineContext[Job]) {
                    _state.update { it.copy(isGenerating = false) }
                }
            }
        }
        generationJob = job
        job.start()
    }

    /** Genera la respuesta con el modelo que corre en el celular. */
    private suspend fun streamLocal(replyId: Long, systemPrompt: String, history: List<ChatMessage>) {
        if (!modelStore.isDownloaded) {
            throw GeminiException(
                "Primero descarga el modelo ${LocalModel.DISPLAY_NAME} (~330 MB, una sola vez) en el menú ⋮ → Ajustes."
            )
        }
        if (!localClient.isLoaded) {
            updateMessage(replyId) { it.copy(note = "Cargando el modelo en memoria (solo la primera vez)…") }
        }
        localClient.streamReply(systemPrompt, history, settings.temperature).collect { chunk ->
            updateMessage(replyId) { it.copy(text = it.text + chunk, note = null) }
        }
    }

    /** Descarga el modelo local (o reanuda una descarga interrumpida). */
    fun startDownload() {
        if (downloadJob?.isActive == true || modelStore.isDownloaded) return
        _download.value = DownloadState.Running(modelStore.downloadedBytes, LocalModel.SIZE_BYTES)
        downloadJob = viewModelScope.launch {
            try {
                modelStore.download { done, total -> _download.value = DownloadState.Running(done, total) }
                _download.value = DownloadState.Idle(modelStore.downloadedBytes)
            } catch (e: CancellationException) {
                _download.value = DownloadState.Idle(modelStore.downloadedBytes)
                throw e
            } catch (e: Exception) {
                _download.value = DownloadState.Failed(
                    when (e) {
                        is UnknownHostException -> "Sin conexión a internet. Conéctate y vuelve a intentarlo."
                        is SocketTimeoutException -> "La descarga se detuvo por falta de respuesta. Reintenta: continuará donde quedó."
                        else -> e.message ?: "Error desconocido al descargar el modelo."
                    }
                )
            }
        }
    }

    /** Pausa la descarga; lo descargado se conserva y se reanuda con [startDownload]. */
    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** Borra el archivo del modelo para liberar espacio. */
    fun deleteModel() {
        if (_state.value.isGenerating) stop()
        downloadJob?.cancel()
        viewModelScope.launch {
            localClient.unload()
            modelStore.delete()
            _download.value = DownloadState.Idle(modelStore.downloadedBytes)
        }
    }

    override fun onCleared() {
        localClient.close()
        super.onCleared()
    }

    /**
     * Pide la respuesta a Gemini resistiendo la saturación del servicio:
     *
     * 1. Si el modelo elegido responde 503/5xx, se reintenta con espera creciente (1 s, 2 s…).
     * 2. Si sigue saturado, se agota la cuota (429) o tarda demasiado, se prueba con modelos de
     *    respaldo (cada modelo tiene su propia capacidad y cuota).
     * 3. Si responde un modelo de respaldo, se avisa bajo la burbuja cuál fue.
     *
     * Solo se reintenta si aún no llegó texto: una respuesta a medias no se repite.
     */
    private suspend fun streamWithFallback(
        replyId: Long,
        systemPrompt: String,
        history: List<ChatMessage>
    ) {
        val apiKey = settings.apiKey
        val preferred = settings.model
        val models = mutableListOf(preferred)
        var lastError: Exception? = null
        var index = 0
        while (index < models.size) {
            val model = models[index]
            val retries = if (index == 0) RETRIES_PREFERRED else RETRIES_FALLBACK
            for (attempt in 0..retries) {
                var received = false
                try {
                    client.streamReply(apiKey, model, systemPrompt, history, settings.temperature)
                        .collect { chunk ->
                            received = true
                            updateMessage(replyId) { it.copy(text = it.text + chunk, note = null) }
                        }
                    if (model != preferred) {
                        updateMessage(replyId) {
                            it.copy(note = "Respondido con $model porque $preferred estaba saturado.")
                        }
                    }
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (received) throw e
                    val code = (e as? GeminiException)?.httpCode ?: -1
                    // Un modelo de respaldo que no existe o no está permitido para la key se salta.
                    val unusableFallback = index > 0 && (code == 403 || code == 404)
                    val transient = (e as? GeminiException)?.isTransient == true || e is SocketTimeoutException
                    if (!transient && !unusableFallback) throw e
                    if (!unusableFallback) lastError = e
                    // Con la cuota agotada (429) o un timeout no sirve insistir con el mismo modelo.
                    if (attempt == retries || unusableFallback || code == 429 || e is SocketTimeoutException) break
                    updateMessage(replyId) {
                        it.copy(note = "$model está saturado; reintentando (${attempt + 1}/$retries)…")
                    }
                    delay(BACKOFF_MS * (1L shl attempt) + Random.nextLong(500))
                }
            }
            if (index == 0) models += fallbackModels(apiKey, preferred)
            models.getOrNull(index + 1)?.let { next ->
                updateMessage(replyId) { it.copy(note = "$model no responde; probando con $next…") }
            }
            index++
        }
        val error = lastError ?: GeminiException("No se obtuvo respuesta del modelo.")
        if (models.size == 1) throw error
        throw GeminiException(
            "${friendlyError(error)}\n\nModelos probados: ${models.joinToString()}.",
            (error as? GeminiException)?.httpCode ?: -1
        )
    }

    /**
     * Modelos de respaldo: los alias "latest" de Flash y las versiones fijas de Flash más
     * recientes. Si se pudo consultar la lista de la API, solo se usan los que la key tiene.
     */
    private suspend fun fallbackModels(apiKey: String, preferred: String): List<String> {
        val available = modelCache?.takeIf { it.first == apiKey }?.second
            ?: runCatching { client.listModels(apiKey) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.also { modelCache = apiKey to it }
        val pinnedFlash = available.orEmpty()
            .filter { "flash" in it && !UNSTABLE_MODEL.containsMatchIn(it) }
            .take(2)
        return (FALLBACK_MODELS + pinnedFlash)
            .filter { it != preferred && (available == null || it in available) }
            .distinct()
            .take(MAX_FALLBACKS)
    }

    /** Modelos disponibles para la API key (para la lista de Ajustes). */
    suspend fun listModels(apiKey: String): List<String> = client.listModels(apiKey)

    /** Cancela la generación en curso. */
    fun stop() {
        generationJob?.cancel()
    }

    /** Reintenta el último mensaje del usuario (por ejemplo, tras un error de red). */
    fun retryLast() {
        if (_state.value.isGenerating) return
        val messages = _state.value.messages
        val lastUserIndex = messages.indexOfLast { it.role == Role.USER }
        if (lastUserIndex < 0) return
        val lastUserText = messages[lastUserIndex].text
        _state.update { it.copy(messages = messages.subList(0, lastUserIndex)) }
        send(lastUserText)
    }

    /** Borra la conversación. */
    fun clear() {
        stop()
        _state.update { it.copy(messages = emptyList(), isGenerating = false) }
    }

    /** Cambia la personalidad del bot; empieza una conversación nueva. */
    fun setPersona(persona: Persona) {
        if (persona == _state.value.persona) return
        settings.persona = persona
        stop()
        _state.update { ChatUiState(persona = persona) }
    }

    private fun updateMessage(id: Long, transform: (ChatMessage) -> ChatMessage) {
        _state.update { state ->
            state.copy(messages = state.messages.map { if (it.id == id) transform(it) else it })
        }
    }

    /**
     * El modelo no conoce la fecha ni la hora: se le indican las del dispositivo en cada petición
     * (se calculan en el momento del envío, así siguen siendo correctas en conversaciones largas).
     */
    private fun currentDateContext(): String {
        val spanish = Locale("es", "CO")
        val timeZone = TimeZone.getDefault()
        val now = Date()
        val formatted = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, h:mm a", spanish)
            .apply { this.timeZone = timeZone }
            .format(now)
        val zoneName = timeZone.getDisplayName(timeZone.inDaylightTime(now), TimeZone.LONG, spanish)
        return "Fecha y hora actuales del usuario: $formatted (zona horaria: ${timeZone.id}, " +
            "$zoneName). Úsalas cuando te pregunten por la fecha, la hora o el día de la semana, " +
            "o para calcular fechas relativas como \"mañana\" o \"el próximo lunes\"."
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is GeminiException -> e.message.orEmpty()
        is UnknownHostException -> "No se pudo contactar el servidor. Revisa tu conexión a internet."
        is SocketTimeoutException -> "El modelo tardó demasiado en responder (puede estar saturado). " +
            "Intenta de nuevo o elige otro modelo en Ajustes (p. ej. gemini-flash-lite-latest)."
        is IOException -> "Error de red: ${e.message}"
        is IllegalStateException -> e.message.orEmpty()
        else -> "Error del modelo: ${e.message}"
    }

    private companion object {
        /** Reintentos con el modelo elegido y con cada modelo de respaldo. */
        const val RETRIES_PREFERRED = 2
        const val RETRIES_FALLBACK = 1
        const val BACKOFF_MS = 1_000L
        const val MAX_FALLBACKS = 3

        /** Alias que Google mantiene apuntando al Flash / Flash-Lite vigente. */
        val FALLBACK_MODELS = listOf("gemini-flash-lite-latest", "gemini-flash-latest")

        /** Versiones que suelen tener poca capacidad (preview/experimentales) o ya son alias. */
        val UNSTABLE_MODEL = Regex("preview|exp|latest")
    }
}
