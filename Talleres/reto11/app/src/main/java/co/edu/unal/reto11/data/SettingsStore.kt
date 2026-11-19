package co.edu.unal.reto11.data

import android.content.Context
import co.edu.unal.reto11.BuildConfig

/**
 * Ajustes del chatbot guardados en SharedPreferences.
 *
 * La API key y el modelo se toman primero de lo que el usuario haya ingresado en la app y, si no
 * hay nada, de los valores compilados desde local.properties (BuildConfig).
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("reto11_settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()

    var persona: Persona
        get() = runCatching { Persona.valueOf(prefs.getString(KEY_PERSONA, null)!!) }
            .getOrDefault(Persona.ASSISTANT)
        set(value) = prefs.edit().putString(KEY_PERSONA, value.name).apply()

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_PERSONA = "persona"

        /**
         * Modelos sugeridos si no se puede consultar la lista real de la API (se puede escribir
         * cualquier otro). Con API key, Ajustes muestra los modelos que reporta la API.
         */
        val SUGGESTED_MODELS = listOf("gemini-flash-lite-latest", "gemini-flash-latest")
    }
}
