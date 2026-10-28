package co.edu.unal.reto9.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    var radiusKm: Float
        get() = prefs.getFloat(KEY_RADIUS_KM, DEFAULT_RADIUS_KM).coerceIn(0.2f, 100.0f)
        set(value) {
            prefs.edit().putFloat(KEY_RADIUS_KM, value.coerceIn(0.2f, 100.0f)).apply()
        }

    var showPoiLabelsOnMap: Boolean
        get() = prefs.getBoolean(KEY_SHOW_POI_LABELS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_POI_LABELS, value).apply()
        }

    var maxResults: Int
        get() = prefs.getInt(KEY_MAX_RESULTS, DEFAULT_MAX_RESULTS).coerceIn(5, 200)
        set(value) {
            prefs.edit().putInt(KEY_MAX_RESULTS, value.coerceIn(5, 200)).apply()
        }

    var lastLatitude: Double
        get() = prefs.getString(KEY_LAST_LAT, null)?.toDoubleOrNull() ?: DEFAULT_UNAL_LAT
        set(value) {
            prefs.edit().putString(KEY_LAST_LAT, value.toString()).apply()
        }

    var lastLongitude: Double
        get() = prefs.getString(KEY_LAST_LON, null)?.toDoubleOrNull() ?: DEFAULT_UNAL_LON
        set(value) {
            prefs.edit().putString(KEY_LAST_LON, value.toString()).apply()
        }

    var lastAddress: String
        get() = prefs.getString(KEY_LAST_ADDRESS, DEFAULT_UNAL_ADDRESS) ?: DEFAULT_UNAL_ADDRESS
        set(value) {
            prefs.edit().putString(KEY_LAST_ADDRESS, value).apply()
        }

    companion object {
        const val KEY_RADIUS_KM = "pref_radius_km"
        const val KEY_SHOW_POI_LABELS = "pref_show_poi_labels"
        const val KEY_MAX_RESULTS = "pref_max_results"
        const val KEY_LAST_LAT = "pref_last_lat"
        const val KEY_LAST_LON = "pref_last_lon"
        const val KEY_LAST_ADDRESS = "pref_last_address"

        const val DEFAULT_RADIUS_KM = 2.0f
        const val DEFAULT_MAX_RESULTS = 40

        // Coordenadas iniciales por defecto: Universidad Nacional de Colombia, Sede Bogotá
        const val DEFAULT_UNAL_LAT = 4.638194
        const val DEFAULT_UNAL_LON = -74.084046
        const val DEFAULT_UNAL_ADDRESS = "Universidad Nacional de Colombia, Bogotá"
    }
}
