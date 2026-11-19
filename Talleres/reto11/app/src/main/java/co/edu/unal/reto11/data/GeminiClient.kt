package co.edu.unal.reto11.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Error devuelto por la API de Gemini (o por la red), con un mensaje listo para mostrar. */
class GeminiException(message: String, val httpCode: Int = -1) : IOException(message)

/**
 * Cliente mínimo de la API REST de Google Gemini (Generative Language API).
 *
 * Usa el endpoint `streamGenerateContent?alt=sse`, que devuelve la respuesta por partes como
 * Server-Sent Events: cada línea `data: {...}` trae un fragmento de texto. Así la respuesta del
 * chatbot va apareciendo mientras se genera, igual que en ChatGPT o Gemini.
 *
 * Documentación: https://ai.google.dev/api/generate-content
 */
class GeminiClient {

    /**
     * Envía la conversación completa a Gemini y emite cada fragmento de texto generado.
     *
     * @param history mensajes previos + el nuevo mensaje del usuario (el último).
     * @param systemPrompt instrucción de sistema que define la personalidad del bot.
     */
    fun streamReply(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<ChatMessage>,
        temperature: Float
    ): Flow<String> = flow {
        val url = URL("$BASE_URL/${URLEncoder.encode(model, "UTF-8")}:streamGenerateContent?alt=sse")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            // La clave va en un encabezado (no en la URL) para que no quede en logs.
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            val body = buildRequestBody(systemPrompt, history, temperature).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw GeminiException(parseError(code, errorBody), code)
            }

            var producedText = false
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    currentCoroutineContext().ensureActive() // permite cancelar con "Detener"
                    if (!line.startsWith("data:")) continue
                    val json = JSONObject(line.removePrefix("data:").trim())

                    json.optJSONObject("error")?.let {
                        throw GeminiException(it.optString("message", "Error desconocido"))
                    }
                    val chunk = extractText(json)
                    if (chunk.isNotEmpty()) {
                        producedText = true
                        emit(chunk)
                    }
                    blockReason(json)?.let { throw GeminiException(it) }
                }
            }
            if (!producedText) {
                throw GeminiException("El modelo no devolvió texto. Intenta reformular la pregunta.")
            }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Consulta los modelos Gemini disponibles para la API key que admiten `generateContent`
     * (endpoint `GET /v1beta/models`). Google retira modelos con el tiempo, así que la lista de
     * Ajustes se arma con lo que la API reporta en lugar de nombres fijos.
     */
    suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val models = mutableListOf<String>()
        var pageToken = ""
        do {
            val query = "pageSize=1000" +
                if (pageToken.isNotEmpty()) "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}" else ""
            val connection = (URL("$BASE_URL?$query").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("x-goog-api-key", apiKey)
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw GeminiException(parseError(code, errorBody), code)
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val array = json.optJSONArray("models") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val model = array.optJSONObject(i) ?: continue
                    val methods = model.optJSONArray("supportedGenerationMethods") ?: continue
                    val supportsChat = (0 until methods.length()).any {
                        methods.optString(it) == "generateContent"
                    }
                    val name = model.optString("name").removePrefix("models/")
                    if (supportsChat && name.startsWith("gemini") && !NON_CHAT.containsMatchIn(name)) {
                        models += name
                    }
                }
                pageToken = json.optString("nextPageToken", "")
            } finally {
                connection.disconnect()
            }
        } while (pageToken.isNotEmpty())
        models.distinct().sortedDescending()
    }

    /** Construye el JSON de la petición `generateContent`. */
    private fun buildRequestBody(
        systemPrompt: String,
        history: List<ChatMessage>,
        temperature: Float
    ): JSONObject {
        val contents = JSONArray()
        history.filter { !it.isError && it.text.isNotBlank() }.forEach { message ->
            contents.put(
                JSONObject()
                    .put("role", message.role.apiName)
                    .put("parts", JSONArray().put(JSONObject().put("text", message.text)))
            )
        }
        return JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", temperature.toDouble())
                    .put("maxOutputTokens", 8192) // en Gemini 2.5 incluye los tokens de "razonamiento"
            )
    }

    /** Une el texto de todas las partes del primer candidato de un fragmento SSE. */
    private fun extractText(json: JSONObject): String {
        val parts = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts") ?: return ""
        return buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought", false)) continue // ignora "pensamientos" internos
                append(part.optString("text", ""))
            }
        }
    }

    /** Si la respuesta fue bloqueada por los filtros de seguridad, devuelve un mensaje. */
    private fun blockReason(json: JSONObject): String? {
        json.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let {
            return "La pregunta fue bloqueada por los filtros de seguridad ($it)."
        }
        val finish = json.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason")
        return when (finish) {
            "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII" ->
                "La respuesta fue bloqueada por los filtros de seguridad ($finish)."
            else -> null
        }
    }

    /** Traduce los errores HTTP más comunes de la API a mensajes entendibles. */
    private fun parseError(code: Int, body: String): String {
        val apiMessage = runCatching {
            JSONObject(body).getJSONObject("error").optString("message")
        }.getOrNull().orEmpty()
        val hint = when (code) {
            400 -> if (apiMessage.contains("API key", ignoreCase = true))
                "La API key no es válida. Revísala en Ajustes."
            else "Petición inválida."
            401, 403 -> "La API key no es válida o no tiene permisos. Revísala en Ajustes."
            404 -> "El modelo no existe o no está disponible. Cambia el modelo en Ajustes."
            429 -> "Se superó la cuota de la API para este modelo (algunos modelos, como los Pro, " +
                "no tienen cuota gratuita). Espera un momento o elige otro modelo en Ajustes."
            503 -> "El modelo está saturado en este momento. Intenta de nuevo en un rato o elige " +
                "otro modelo en Ajustes (p. ej. gemini-flash-lite-latest)."
            in 500..599 -> "El servicio de Gemini no está disponible en este momento."
            else -> "Error HTTP $code."
        }
        return if (apiMessage.isNotEmpty()) "$hint\n\nDetalle: $apiMessage" else hint
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        /** Modelos que admiten generateContent pero no sirven para un chat de texto. */
        private val NON_CHAT = Regex(
            "tts|image|banana|embedding|live|audio|transcribe|robotics|computer-use|customtools"
        )
    }
}
