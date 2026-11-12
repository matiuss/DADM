package co.edu.unal.reto10.data

import org.json.JSONObject

/**
 * Colegio de Bogotá tal como lo devuelve el servicio web de datos.gov.co
 * (conjunto de datos "LISTADO COLEGIOS BOGOTA", id qijw-htwa).
 *
 * Se construye exclusivamente con [JSONObject] (org.json, incluido en el SDK de Android).
 * Se conserva el JSON original en [rawJson] para pasarlo entre pantallas.
 */
data class School(
    val name: String,
    val year: String,
    val secretaria: String,
    val zone: String,
    val address: String,
    val phone: String,
    val principal: String,
    val type: String,
    val levels: String,
    val schedule: String,
    val grades: String,
    val educationalModels: String,
    val languages: String,
    val campusCount: String,
    val provider: String,
    val buildingOwner: String,
    val contractedEnrollment: String,
    val calendar: String,
    val email: String,
    val rawJson: String
) {

    /** Primer número telefónico utilizable (el campo puede traer varios separados por "/" o espacios). */
    val firstPhone: String?
        get() = PHONE_REGEX.find(phone)?.value

    /** Primer correo válido (el campo puede traer varios separados por espacios). */
    val firstEmail: String?
        get() = email.split(' ', ',', ';', '/')
            .map { it.trim() }
            .firstOrNull { it.contains('@') && it.contains('.') }

    /** Separa listas como "MAÑANA,TARDE" en "Mañana · Tarde". */
    fun pretty(value: String): String =
        value.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ") { it.toTitleCase() }

    companion object {
        private val PHONE_REGEX = Regex("\\d{7,10}")

        fun fromJson(json: JSONObject): School = School(
            name = json.clean("nombreestablecimiento"),
            year = json.clean("a_o"),
            secretaria = json.clean("secretaria"),
            zone = json.clean("zona"),
            address = json.clean("direccion"),
            phone = json.clean("telefono"),
            principal = json.clean("codigo_etc"),
            type = json.clean("tipo_establecimiento"),
            levels = json.clean("niveles"),
            schedule = json.clean("jornada"),
            grades = json.clean("grados"),
            educationalModels = json.clean("modelos_educativos"),
            languages = json.clean("idiomas"),
            campusCount = json.clean("numero_de_sedes"),
            provider = json.clean("prestador_de_servicio"),
            buildingOwner = json.clean("propiedad_planta_fisica"),
            contractedEnrollment = json.clean("matricula_contratada"),
            calendar = json.clean("calendario"),
            email = json.clean("correo_electronico"),
            rawJson = json.toString()
        )

        fun fromJson(raw: String): School = fromJson(JSONObject(raw))

        /** Lee un campo opcional y elimina los espacios sobrantes que trae el dataset. */
        private fun JSONObject.clean(key: String): String =
            optString(key, "").trim().replace(Regex("\\s{2,}"), " ")
    }
}

/**
 * "COLEGIO SAN JOSE DE CASTILLA (IED)" -> "Colegio San Jose de Castilla (IED)".
 * Respeta tildes y la Ñ, deja en minúscula los conectores y conserva las siglas entre paréntesis.
 */
fun String.toTitleCase(): String =
    split(' ').mapIndexed { index, word ->
        val lower = word.lowercase(SPANISH)
        when {
            word.startsWith("(") -> word
            index > 0 && lower in CONNECTORS -> lower
            else -> lower.replaceFirstChar { it.titlecase(SPANISH) }
        }
    }.joinToString(" ")

private val CONNECTORS = setOf("de", "del", "la", "las", "el", "los", "y", "e", "para", "en", "a")

val SPANISH: java.util.Locale = java.util.Locale("es", "CO")
