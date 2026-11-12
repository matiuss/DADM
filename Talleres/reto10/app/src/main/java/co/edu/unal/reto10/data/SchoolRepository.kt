package co.edu.unal.reto10.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente del servicio web de datos.gov.co (API Socrata / SODA 2.0).
 *
 * Ejemplo de petición generada:
 * https://www.datos.gov.co/resource/qijw-htwa.json?$where=jornada like '%NOCTURNA%'&$order=nombreestablecimiento&$limit=50&$offset=0
 *
 * La red se consume con [HttpURLConnection] y la respuesta se procesa con
 * [JSONArray] / [JSONObject] del SDK de Android.
 */
object SchoolRepository {

    const val BASE_URL = "https://www.datos.gov.co/resource/qijw-htwa.json"
    const val PAGE_SIZE = 50

    private const val TIMEOUT_MS = 15_000

    /** Consulta una página de colegios que cumplen el filtro, ordenados por nombre. */
    suspend fun search(filter: SchoolFilter, offset: Int, limit: Int = PAGE_SIZE): List<School> {
        val params = linkedMapOf(
            "\$order" to "nombreestablecimiento ASC",
            "\$limit" to limit.toString(),
            "\$offset" to offset.toString()
        )
        filter.toWhereClause()?.let { params["\$where"] = it }

        val array = JSONArray(get(params))
        return (0 until array.length()).map { i -> School.fromJson(array.getJSONObject(i)) }
    }

    /** Cuenta cuántos colegios cumplen el filtro usando `$select=count(*)`. */
    suspend fun count(filter: SchoolFilter): Int {
        val params = linkedMapOf("\$select" to "count(*) AS total")
        filter.toWhereClause()?.let { params["\$where"] = it }

        val array = JSONArray(get(params))
        if (array.length() == 0) return 0
        return array.getJSONObject(0).optString("total", "0").toIntOrNull() ?: 0
    }

    /** Ejecuta la petición GET en un hilo de E/S y devuelve el cuerpo como texto. */
    private suspend fun get(params: Map<String, String>): String = withContext(Dispatchers.IO) {
        val url = URL(buildUrl(params))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw ApiException("El servicio respondió HTTP $code. ${extractMessage(errorBody)}".trim())
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun buildUrl(params: Map<String, String>): String =
        BASE_URL + "?" + params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }

    /** URLEncoder codifica los espacios como "+"; se usa %20 que es lo estándar en la ruta de consulta. */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** Los errores de Socrata llegan como {"message": "..."}; se extrae el mensaje con org.json. */
    private fun extractMessage(body: String): String = try {
        JSONObject(body).optString("message", "")
    } catch (e: JSONException) {
        ""
    }
}

class ApiException(message: String) : IOException(message)
