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
 * Modelo de lenguaje que se ejecuta en el celular con LiteRT-LM.
 *
 * Qwen3 0.6B (Alibaba, licencia Apache 2.0) convertido por Google al formato `.litertlm`:
 * pesos INT4, contexto de 4096 tokens y ~330 MB. Es de los modelos más pequeños que aún
 * conversan de forma útil en español. Se fija un commit del repositorio para que el archivo
 * descargado no cambie si lo vuelven a exportar.
 */
object LocalModel {
    const val DISPLAY_NAME = "Qwen3 0.6B"
    const val FILE_NAME = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
    const val SIZE_BYTES = 343_490_560L
    private const val REVISION = "8d42ab5f1db14cad987fac57aeb17d070f9e180d"
    const val URL =
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/$REVISION/$FILE_NAME"

    /** Tokens de contexto del modelo (instrucción + historial + respuesta). */
    const val CONTEXT_TOKENS = 4096
}

/**
 * Descarga y guarda el archivo del modelo en el almacenamiento interno de la app.
 *
 * El archivo no va dentro del APK (pesa cientos de MB): se descarga una sola vez. La descarga se
 * escribe en un `.part` y se reanuda desde donde quedó (encabezado HTTP `Range`) si se corta.
 */
class LocalModelStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "models")
    val file = File(dir, LocalModel.FILE_NAME)
    private val partial = File(dir, LocalModel.FILE_NAME + ".part")

    val isDownloaded: Boolean get() = file.length() == LocalModel.SIZE_BYTES

    /** Bytes ya descargados (completos o parciales), para mostrar el progreso al reanudar. */
    val downloadedBytes: Long get() = if (isDownloaded) LocalModel.SIZE_BYTES else partial.length()

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
        val total = LocalModel.SIZE_BYTES
        var start = partial.length().takeIf { it < total } ?: 0L
        if (dir.usableSpace < total - start + FREE_SPACE_MARGIN) {
            throw IOException(
                "No hay espacio suficiente: se necesitan unos ${(total - start) / MB + FREE_SPACE_MARGIN / MB} MB libres."
            )
        }

        val connection = (URL(LocalModel.URL).openConnection() as HttpURLConnection).apply {
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
