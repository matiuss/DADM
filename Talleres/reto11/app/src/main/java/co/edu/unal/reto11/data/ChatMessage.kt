package co.edu.unal.reto11.data

/** Autor de un mensaje. Los nombres coinciden con los roles que espera la API de Gemini. */
enum class Role(val apiName: String) {
    USER("user"),
    MODEL("model")
}

/**
 * Un mensaje de la conversación.
 *
 * @param isStreaming true mientras el modelo sigue generando el texto (se muestra un cursor).
 * @param isError true si el mensaje representa un error (no se envía como historial al modelo).
 * @param note aviso pequeño bajo la burbuja (p. ej. "reintentando…" o qué modelo de respaldo respondió).
 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val note: String? = null
)
