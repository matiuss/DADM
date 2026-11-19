package co.edu.unal.reto11.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unal.reto11.data.ChatMessage
import co.edu.unal.reto11.data.GeminiClient
import co.edu.unal.reto11.data.GeminiException
import co.edu.unal.reto11.data.NetworkMonitor
import co.edu.unal.reto11.data.Persona
import co.edu.unal.reto11.data.Role
import co.edu.unal.reto11.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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

data class ChatUiState(
    val persona: Persona,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false
)

/** Mantiene la conversación (sobrevive a rotaciones) y orquesta las llamadas a Gemini. */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsStore(application)
    private val client = GeminiClient()
    private var nextId = 0L
    private var generationJob: Job? = null

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
                if (!settings.hasApiKey) {
                    throw GeminiException("Falta la API key de Gemini. Configúrala en el menú ⋮ → Ajustes.")
                }
                if (!NetworkMonitor.isOnline(context)) {
                    throw GeminiException("No hay conexión a internet.")
                }
                val persona = _state.value.persona
                client.streamReply(
                    apiKey = settings.apiKey,
                    model = settings.model,
                    systemPrompt = persona.systemPrompt + "\n\n" + currentDateContext(),
                    history = history,
                    temperature = settings.temperature
                ).collect { chunk ->
                    updateMessage(replyId) { it.copy(text = it.text + chunk) }
                }
                updateMessage(replyId) { it.copy(isStreaming = false) }
            } catch (e: CancellationException) {
                // El usuario pulsó "Detener": se conserva lo que alcanzó a generarse.
                updateMessage(replyId) {
                    it.copy(
                        text = it.text.ifEmpty { "(Respuesta detenida)" },
                        isStreaming = false,
                        isError = it.text.isEmpty()
                    )
                }
                throw e
            } catch (e: Exception) {
                updateMessage(replyId) {
                    it.copy(text = friendlyError(e), isStreaming = false, isError = true)
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
        else -> "Error inesperado: ${e.message}"
    }
}
