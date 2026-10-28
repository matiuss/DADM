package co.edu.unal.reto9.data.repository

import android.content.Context
import android.location.Geocoder
import android.location.Location
import co.edu.unal.reto9.data.model.PoiCategory
import co.edu.unal.reto9.data.model.PointOfInterest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val addressName: String
)

class LocationAndPoiRepository(private val context: Context) {

    /**
     * Resuelve la DIRECCIÓN DE LA POSICIÓN ACTUAL DEL USUARIO o sus coordenadas GPS:
     * 1) Coordenadas GPS "lat, lon" (ej. "4.7019, -74.0499")
     * 2) Nomenclatura vial colombiana (ej. "Carrera 20 #122-55, Bogotá" -> intersección Carrera 20 con Calle 122)
     * 3) Cualquier dirección o lugar (ej. "Plaza de Bolívar, Bogotá")
     */
    suspend fun resolveUserAddressOrCoordinates(query: String): ResolvedLocation? =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext null

            // 1. Verificar si el usuario escribió coordenadas directamente: "4.6382, -74.0840"
            val coordRegex = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*[,;\s]\s*(-?\d+(?:\.\d+)?)\s*$""")
            val match = coordRegex.matchEntire(trimmed)
            if (match != null) {
                val lat = match.groupValues[1].toDoubleOrNull()
                val lon = match.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    val resolvedAddress = reverseGeocode(lat, lon)
                    return@withContext ResolvedLocation(lat, lon, resolvedAddress)
                }
            }

            // 2. Generar variantes de búsqueda (incluyendo normalización de direcciones colombianas con #)
            val candidates = buildAddressSearchCandidates(trimmed)

            for (candidate in candidates) {
                try {
                    val encoded = URLEncoder.encode(candidate, "UTF-8")
                    val urlStr =
                        "https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded&limit=1&countrycodes=co&accept-language=es"
                    val response = httpGet(urlStr)
                    if (response.isNotBlank()) {
                        val jsonArray = JSONArray(response)
                        if (jsonArray.length() > 0) {
                            val item = jsonArray.getJSONObject(0)
                            val lat = item.getString("lat").toDouble()
                            val lon = item.getString("lon").toDouble()
                            return@withContext ResolvedLocation(lat, lon, trimmed)
                        }
                    }
                } catch (_: Exception) {
                    // Intentar siguiente variante
                }
            }

            // 3. Intentar búsqueda global en Nominatim si no está en Colombia
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val urlStr =
                    "https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded&limit=1&accept-language=es"
                val response = httpGet(urlStr)
                if (response.isNotBlank()) {
                    val jsonArray = JSONArray(response)
                    if (jsonArray.length() > 0) {
                        val item = jsonArray.getJSONObject(0)
                        val lat = item.getString("lat").toDouble()
                        val lon = item.getString("lon").toDouble()
                        val displayName = item.optString("display_name", trimmed)
                        return@withContext ResolvedLocation(lat, lon, shortenAddress(displayName))
                    }
                }
            } catch (_: Exception) {
                // Continuar con Geocoder
            }

            // 4. Fallback a Geocoder nativo de Android
            withTimeoutOrNull(3000L) {
                try {
                    if (Geocoder.isPresent()) {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val results = geocoder.getFromLocationName(trimmed, 1)
                        if (!results.isNullOrEmpty()) {
                            val addr = results[0]
                            val addressLine = addr.getAddressLine(0) ?: trimmed
                            ResolvedLocation(addr.latitude, addr.longitude, addressLine)
                        } else null
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        }

    /**
     * Convierte direcciones colombianas como "Carrera 20 #122-55, Bogotá" o "Calle 85 # 15-20"
     * en la intersección vial que OpenStreetMap Nominatim ubica con precisión exacta
     * ("Carrera 20 Calle 122, Bogotá" / "Calle 85 Carrera 15").
     */
    private fun buildAddressSearchCandidates(rawQuery: String): List<String> {
        val list = mutableListOf<String>()

        // Caso 1: "Carrera 20 # 122-55, Bogotá" o "Cra 20 No. 122 - 55"
        val craRegex = Regex(
            """(?i)\b(?:carrera|cra\.?|kr\.?|ak)\s*(\d+[a-z]?)\s*(?:#|no\.?|n[°º]?|\s+)\s*(\d+[a-z]?)\s*-\s*\d+(.*)"""
        )
        craRegex.find(rawQuery)?.let { m ->
            val craNum = m.groupValues[1]
            val calleNum = m.groupValues[2]
            val rest = m.groupValues[3].trim().let { if (it.isEmpty()) ", Bogotá" else it }
            list.add("Carrera $craNum Calle $calleNum$rest")
            list.add("Calle $calleNum Carrera $craNum$rest")
        }

        // Caso 2: "Calle 122 # 20-55, Bogotá" o "Cll 122 # 20 - 55"
        val cllRegex = Regex(
            """(?i)\b(?:calle|cll\.?|cl\.?|ac)\s*(\d+[a-z]?)\s*(?:#|no\.?|n[°º]?|\s+)\s*(\d+[a-z]?)\s*-\s*\d+(.*)"""
        )
        cllRegex.find(rawQuery)?.let { m ->
            val cllNum = m.groupValues[1]
            val craNum = m.groupValues[2]
            val rest = m.groupValues[3].trim().let { if (it.isEmpty()) ", Bogotá" else it }
            list.add("Calle $cllNum Carrera $craNum$rest")
            list.add("Carrera $craNum Calle $cllNum$rest")
        }

        list.add(rawQuery)
        return list.distinct()
    }

    /**
     * Obtiene la dirección legible a partir de las coordenadas GPS (latitud y longitud).
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String =
        withContext(Dispatchers.IO) {
            try {
                val urlStr = String.format(
                    Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&accept-language=es",
                    latitude,
                    longitude
                )
                val response = httpGet(urlStr)
                if (response.isNotBlank()) {
                    val obj = JSONObject(response)
                    val displayName = obj.optString("display_name", "")
                    if (displayName.isNotBlank()) {
                        return@withContext shortenAddress(displayName)
                    }
                }
            } catch (_: Exception) {
                // Fallback a Geocoder
            }

            val geocoderAddress = withTimeoutOrNull(2500L) {
                try {
                    if (Geocoder.isPresent()) {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            addresses[0].getAddressLine(0)?.takeIf { it.isNotBlank() }?.let {
                                shortenAddress(it)
                            }
                        } else null
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
            if (geocoderAddress != null) return@withContext geocoderAddress

            String.format(Locale.US, "Posición GPS (%.4f, %.4f)", latitude, longitude)
        }

    /**
     * Consulta los Puntos de Interés 100% REALES de OpenStreetMap dentro del radio
     * expresado en kilómetros alrededor de (latitude, longitude).
     *
     * - Si `keywordQuery` NO está vacío (ej. "Carulla", "droguería", "museo"), busca específicamente
     *   esos puntos de interés DENTRO del radio en kilómetros alrededor de la posición del usuario
     *   (sin cambiar al usuario de ciudad).
     * - Si `keywordQuery` está vacío, consulta las etiquetas de OpenStreetMap correspondientes a
     *   la categoría seleccionada.
     */
    suspend fun fetchNearbyPois(
        latitude: Double,
        longitude: Double,
        radiusKm: Float,
        category: PoiCategory,
        maxResults: Int,
        keywordQuery: String = ""
    ): List<PointOfInterest> = withContext(Dispatchers.IO) {
        // Calcular el cuadro delimitador (viewbox) exacto para el radio en kilómetros
        val latDelta = (radiusKm / 111.0).coerceAtLeast(0.005)
        val cosLat = cos(Math.toRadians(latitude)).coerceAtLeast(0.1)
        val lonDelta = (radiusKm / (111.0 * cosLat)).coerceAtLeast(0.005)

        val west = longitude - lonDelta
        val north = latitude + latDelta
        val east = longitude + lonDelta
        val south = latitude - latDelta

        val trimmedKeyword = keywordQuery.trim()
        val rawResults = mutableListOf<PointOfInterest>()

        if (trimmedKeyword.isNotEmpty()) {
            // Búsqueda por nombre/palabra clave de Punto de Interés DENTRO del radio actual (ej. "Carulla")
            val items = fetchFromNominatimViewbox(
                term = trimmedKeyword,
                defaultCategory = if (category == PoiCategory.ALL) PoiCategory.SHOP else category,
                userLat = latitude,
                userLon = longitude,
                west = west,
                north = north,
                east = east,
                south = south,
                limit = 40
            )
            rawResults.addAll(items)
        } else {
            val tagQueries: List<Pair<String, PoiCategory>> = when (category) {
                PoiCategory.HOSPITAL -> listOf(
                    "[amenity=hospital]" to PoiCategory.HOSPITAL,
                    "[amenity=clinic]" to PoiCategory.HOSPITAL,
                    "[amenity=pharmacy]" to PoiCategory.HOSPITAL
                )
                PoiCategory.TOURISM -> listOf(
                    "[tourism=museum]" to PoiCategory.TOURISM,
                    "[leisure=park]" to PoiCategory.TOURISM,
                    "[tourism=hotel]" to PoiCategory.TOURISM,
                    "[historic=monument]" to PoiCategory.TOURISM
                )
                PoiCategory.SHOP -> listOf(
                    "[shop=supermarket]" to PoiCategory.SHOP,
                    "[shop=mall]" to PoiCategory.SHOP,
                    "[shop=convenience]" to PoiCategory.SHOP
                )
                PoiCategory.RESTAURANT -> listOf(
                    "[amenity=restaurant]" to PoiCategory.RESTAURANT,
                    "[amenity=cafe]" to PoiCategory.RESTAURANT
                )
                PoiCategory.EDUCATION -> listOf(
                    "[amenity=university]" to PoiCategory.EDUCATION,
                    "[amenity=library]" to PoiCategory.EDUCATION,
                    "[amenity=school]" to PoiCategory.EDUCATION
                )
                PoiCategory.SERVICES -> listOf(
                    "[amenity=bank]" to PoiCategory.SERVICES,
                    "[amenity=police]" to PoiCategory.SERVICES,
                    "[amenity=bus_station]" to PoiCategory.SERVICES
                )
                PoiCategory.ALL -> listOf(
                    "[amenity=hospital]" to PoiCategory.HOSPITAL,
                    "[amenity=clinic]" to PoiCategory.HOSPITAL,
                    "[tourism=museum]" to PoiCategory.TOURISM,
                    "[leisure=park]" to PoiCategory.TOURISM,
                    "[shop=supermarket]" to PoiCategory.SHOP,
                    "[shop=mall]" to PoiCategory.SHOP,
                    "[amenity=restaurant]" to PoiCategory.RESTAURANT,
                    "[amenity=university]" to PoiCategory.EDUCATION,
                    "[amenity=bank]" to PoiCategory.SERVICES
                )
            }

            for ((tagQuery, defaultCat) in tagQueries) {
                val items = fetchFromNominatimViewbox(
                    term = tagQuery,
                    defaultCategory = defaultCat,
                    userLat = latitude,
                    userLon = longitude,
                    west = west,
                    north = north,
                    east = east,
                    south = south,
                    limit = 20
                )
                rawResults.addAll(items)
            }
        }

        // Filtrar estrictamente dentro del radio en kilómetros y eliminar duplicados
        val seenKeys = mutableSetOf<String>()
        val filtered = mutableListOf<PointOfInterest>()

        for (poi in rawResults.sortedBy { it.distanceKm }) {
            if (poi.distanceKm > radiusKm.toDouble()) continue
            if (trimmedKeyword.isEmpty() && category != PoiCategory.ALL && poi.category != category) {
                continue
            }

            val normName = poi.name.lowercase(Locale.ROOT).trim()
            val latBucket = (poi.latitude * 500).toInt()
            val lonBucket = (poi.longitude * 500).toInt()
            val key = "${normName}_${latBucket}_${lonBucket}"
            if (seenKeys.add(key)) {
                filtered.add(poi)
            }
        }

        filtered.take(maxResults)
    }

    private fun fetchFromNominatimViewbox(
        term: String,
        defaultCategory: PoiCategory,
        userLat: Double,
        userLon: Double,
        west: Double,
        north: Double,
        east: Double,
        south: Double,
        limit: Int
    ): List<PointOfInterest> {
        val list = mutableListOf<PointOfInterest>()
        try {
            val viewbox = String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", west, north, east, south)
            val encodedTerm = URLEncoder.encode(term, "UTF-8")
            val urlStr =
                "https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encodedTerm&viewbox=$viewbox&bounded=1&limit=$limit&addressdetails=1&accept-language=es"

            val response = httpGet(urlStr)
            if (response.isBlank()) return emptyList()

            val arr = JSONArray(response)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val lat = item.optString("lat", "").toDoubleOrNull() ?: continue
                val lon = item.optString("lon", "").toDoubleOrNull() ?: continue

                val displayName = item.optString("display_name", "")
                val rawName = item.optString("name", "").trim()
                val name = if (rawName.isNotEmpty()) {
                    rawName
                } else {
                    displayName.substringBefore(",").trim()
                }
                if (name.isEmpty()) continue

                val osmClass = item.optString("category", item.optString("class", ""))
                val osmType = item.optString("type", "")
                val (resolvedCat, subTypeLabel) = classifyCategoryAndSubtype(
                    osmClass,
                    osmType,
                    defaultCategory
                )

                val distKm = computeDistanceKm(userLat, userLon, lat, lon)
                val addressObj = item.optJSONObject("address")
                val formattedAddress = formatNominatimAddress(addressObj, displayName, subTypeLabel)

                list.add(
                    PointOfInterest(
                        id = "osm_${item.optLong("place_id", i.toLong())}",
                        name = name,
                        category = resolvedCat,
                        subType = subTypeLabel,
                        latitude = lat,
                        longitude = lon,
                        distanceKm = distKm,
                        address = formattedAddress
                    )
                )
            }
        } catch (_: Exception) {
            // Ignorar fallo individual
        }
        return list
    }

    private fun classifyCategoryAndSubtype(
        osmClass: String,
        osmType: String,
        fallbackCategory: PoiCategory
    ): Pair<PoiCategory, String> {
        val c = osmClass.lowercase(Locale.ROOT)
        val t = osmType.lowercase(Locale.ROOT)

        return when {
            t in listOf("hospital", "clinic", "doctors", "pharmacy", "dentist") || c == "healthcare" -> {
                val label = when (t) {
                    "hospital" -> "Hospital"
                    "clinic" -> "Clínica / IPS"
                    "pharmacy" -> "Farmacia / Droguería"
                    "doctors" -> "Consultorio Médico"
                    else -> "Centro de Salud"
                }
                PoiCategory.HOSPITAL to label
            }
            c == "shop" || t in listOf("supermarket", "mall", "convenience", "department_store", "marketplace") -> {
                val label = when (t) {
                    "supermarket" -> "Supermercado"
                    "mall" -> "Centro Comercial"
                    "convenience" -> "Tienda / Minimercado"
                    else -> "Comercio"
                }
                PoiCategory.SHOP to label
            }
            c in listOf("tourism", "historic", "leisure") || t in listOf(
                "museum", "park", "garden", "hotel", "attraction", "viewpoint", "monument", "memorial", "artwork", "gallery"
            ) -> {
                val label = when (t) {
                    "museum" -> "Museo"
                    "park" -> "Parque"
                    "garden" -> "Jardín"
                    "hotel", "hostel", "guest_house" -> "Hotel / Hospedaje"
                    "viewpoint" -> "Mirador Turístico"
                    "monument", "memorial" -> "Monumento Histórico"
                    "attraction" -> "Atracción Turística"
                    else -> "Lugar Turístico"
                }
                PoiCategory.TOURISM to label
            }
            t in listOf("restaurant", "cafe", "fast_food", "bakery", "bar", "food_court") -> {
                val label = when (t) {
                    "restaurant" -> "Restaurante"
                    "cafe" -> "Café"
                    "fast_food" -> "Comida Rápida"
                    "bakery" -> "Panadería / Pastelería"
                    else -> "Gastronomía"
                }
                PoiCategory.RESTAURANT to label
            }
            t in listOf("university", "college", "school", "library", "theatre", "cinema", "arts_centre") -> {
                val label = when (t) {
                    "university" -> "Universidad"
                    "college" -> "Instituto Superior"
                    "school" -> "Colegio / Escuela"
                    "library" -> "Biblioteca"
                    "theatre" -> "Teatro"
                    "cinema" -> "Cine"
                    else -> "Educación y Cultura"
                }
                PoiCategory.EDUCATION to label
            }
            t in listOf("bank", "atm", "police", "bus_station", "fuel", "post_office", "townhall", "fire_station") -> {
                val label = when (t) {
                    "bank", "atm" -> "Banco / Cajero"
                    "police" -> "Estación de Policía"
                    "bus_station" -> "Estación de Transporte"
                    "fuel" -> "Estación de Servicio"
                    else -> "Servicio Público"
                }
                PoiCategory.SERVICES to label
            }
            else -> {
                val defaultLabel = when (fallbackCategory) {
                    PoiCategory.HOSPITAL -> "Hospital / Salud"
                    PoiCategory.TOURISM -> "Lugar Turístico"
                    PoiCategory.SHOP -> "Supermercado / Comercio"
                    PoiCategory.RESTAURANT -> "Restaurante / Café"
                    PoiCategory.EDUCATION -> "Educación / Cultura"
                    PoiCategory.SERVICES -> "Transporte y Servicios"
                    PoiCategory.ALL -> "Punto de Interés"
                }
                fallbackCategory to defaultLabel
            }
        }
    }

    private fun formatNominatimAddress(
        addressObj: JSONObject?,
        displayName: String,
        subTypeLabel: String
    ): String {
        if (addressObj != null) {
            val road = addressObj.optString("road", addressObj.optString("pedestrian", ""))
            val houseNumber = addressObj.optString("house_number", "")
            val suburb = addressObj.optString(
                "suburb",
                addressObj.optString("neighbourhood", addressObj.optString("quarter", ""))
            )
            val city = addressObj.optString(
                "city",
                addressObj.optString("town", addressObj.optString("municipality", ""))
            )

            val parts = listOfNotNull(
                road.takeIf { it.isNotBlank() }?.let {
                    if (houseNumber.isNotBlank()) "$it #$houseNumber" else it
                },
                suburb.takeIf { it.isNotBlank() },
                city.takeIf { it.isNotBlank() }
            )
            if (parts.isNotEmpty()) {
                return parts.joinToString(", ")
            }
        }

        val split = displayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (split.size > 1) {
            split.drop(1).take(3).joinToString(", ")
        } else {
            subTypeLabel
        }
    }

    fun computeDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return (results[0] / 1000.0)
    }

    private fun shortenAddress(fullAddress: String): String {
        val parts = fullAddress.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size > 3) {
            parts.take(3).joinToString(", ")
        } else {
            fullAddress
        }
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "UNAL-DADM-Reto9/1.0 (https://unal.edu.co)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }
        } finally {
            conn.disconnect()
        }
    }
}
