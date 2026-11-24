package co.edu.unal.reto11.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Modelos de lenguaje que se pueden ejecutar en el celular con LiteRT-LM (se elige en Ajustes).
 *
 * Todos tienen licencia Apache 2.0 y están convertidos al formato `.litertlm` en el repositorio
 * `litert-community` de Hugging Face. Se fija un commit de cada repositorio para que el archivo
 * descargado no cambie si lo vuelven a exportar (y así el tamaño esperado sigue siendo válido).
 */
enum class LocalModel(
    val displayName: String,
    /** Nivel que se muestra en el selector de Ajustes. */
    val tier: String,
    /** Explicación corta de para qué sirve, para ayudar a elegir. */
    val description: String,
    private val repo: String,
    private val revision: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Tokens de contexto (instrucción + historial + respuesta). */
    val contextTokens: Int = 4096
) {
    QWEN3_0_6B(
        displayName = "Qwen3 0.6B",
        tier = "Básico",
        description = "El más pequeño y rápido. Respuestas simples y con errores ocasionales.",
        repo = "litert-community/Qwen3-0.6B",
        revision = "8d42ab5f1db14cad987fac57aeb17d070f9e180d",
        fileName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        sizeBytes = 343_490_560L
    ),
    QWEN3_1_7B(
        displayName = "Qwen3 1.7B",
        tier = "Ligero",
        description = "Buen equilibrio: responde bastante mejor que el básico y sigue siendo rápido.",
        repo = "litert-community/Qwen3-1.7B",
        revision = "c421cc3775575372ac2ca4aa1bebc97300ae36b6",
        fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
        sizeBytes = 977_184_032L
    ),
    GEMMA4_E2B(
        displayName = "Gemma 4 E2B",
        tier = "Avanzado",
        description = "La mejor calidad y el mejor español, pero es más lento y la descarga es grande: " +
            "usa Wi-Fi.",
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        revision = "2e12bfd869b2962984f97a1f2a26807316d534cc",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L
    );

    val url: String get() = "https://huggingface.co/$repo/resolve/$revision/$fileName"

    /** Tamaño legible: "328 MB" o "2.4 GB". */
    val sizeLabel: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb < 1024) "${mb.toInt()} MB" else String.format(java.util.Locale.US, "%.1f GB", mb / 1024)
        }

    /** Texto del selector, p. ej. "Ligero · Qwen3 1.7B (932 MB)". */
    val label: String get() = "$tier · $displayName ($sizeLabel)"
}

/**
 * Descarga y guarda el archivo del modelo en el almacenamiento interno de la app.
 *
 * Hay un almacén por modelo. El archivo no va dentro del APK (pesa cientos de MB o más): se
 * descarga una sola vez. La descarga se escribe en un `.part` y se reanuda desde donde quedó
 * (encabezado HTTP `Range`) si se corta.
 */
class LocalModelStore(context: Context, val model: LocalModel) {

    private val dir = File(context.applicationContext.filesDir, "models")
    val file = File(dir, model.fileName)
    private val partial = File(dir, model.fileName + ".part")

    val isDownloaded: Boolean get() = file.length() == model.sizeBytes

    /** Bytes ya descargados (completos o parciales), para mostrar el progreso al reanudar. */
    val downloadedBytes: Long get() = if (isDownloaded) model.sizeBytes else partial.length()

    fun delete() {
        file.delete()
        partial.delete()
    }

    /**
     * Descarga el modelo (o termina una descarga interrumpida).
     * @param onProgress bytes descargados y total; se llama como máximo unas 10 veces por segundo.
     */
    suspend fun download(onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        if (isDownloaded) return@withContext
        dir.mkdirs()
        val total = model.sizeBytes
        var start = partial.length().takeIf { it < total } ?: 0L
        if (dir.usableSpace < total - start + FREE_SPACE_MARGIN) {
            throw IOException(
                "No hay espacio suficiente: se necesitan unos ${(total - start) / MB + FREE_SPACE_MARGIN / MB} MB libres."
            )
        }

        val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            if (start > 0) setRequestProperty("Range", "bytes=$start-")
        }
        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> Unit
                HttpURLConnection.HTTP_OK -> start = 0 // el servidor ignoró el Range: desde cero
                else -> throw IOException("El servidor respondió HTTP $code al descargar el modelo.")
            }
            FileOutputStream(partial, start > 0).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var done = start
                    var lastReport = 0L
                    onProgress(done, total)
                    while (true) {
                        currentCoroutineContext().ensureActive() // permite cancelar la descarga
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 100) {
                            lastReport = now
                            onProgress(done, total)
                        }
                    }
                    onProgress(done, total)
                }
            }
        } finally {
            connection.disconnect()
        }

        if (partial.length() != total) {
            throw IOException("La descarga quedó incompleta (${partial.length() / MB} de ${total / MB} MB). Reintenta.")
        }
        if (!partial.renameTo(file)) throw IOException("No se pudo guardar el modelo descargado.")
    }

    private companion object {
        const val MB = 1024L * 1024L
        const val FREE_SPACE_MARGIN = 50 * MB
    }
}
