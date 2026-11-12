package co.edu.unal.reto10.data

import java.io.Serializable

/**
 * Parámetros que el usuario envía al servicio web.
 * Un valor null significa "Todos" (no se filtra por ese campo).
 */
data class SchoolFilter(
    val name: String? = null,
    val provider: String? = null,
    val type: String? = null,
    val schedule: String? = null,
    val level: String? = null,
    val calendar: String? = null,
    val teachesEnglish: Boolean = false
) : Serializable {

    /**
     * Construye la cláusula SoQL `$where` del API Socrata (SODA 2.0).
     * - Igualdad exacta para campos de un solo valor (prestador, tipo, calendario).
     * - `like '%X%'` para campos que guardan listas separadas por comas (jornada, niveles, idiomas).
     * Las comillas simples se duplican para evitar romper la consulta.
     */
    fun toWhereClause(): String? {
        val conditions = mutableListOf<String>()
        name?.takeIf { it.isNotBlank() }?.let {
            conditions += "upper(nombreestablecimiento) like '%${it.trim().uppercase(SPANISH).soql()}%'"
        }
        provider?.let { conditions += "prestador_de_servicio = '${it.soql()}'" }
        type?.let { conditions += "tipo_establecimiento = '${it.soql()}'" }
        calendar?.let { conditions += "calendario = '${it.soql()}'" }
        schedule?.let { conditions += "jornada like '%${it.soql()}%'" }
        level?.let { conditions += "niveles like '%${it.soql()}%'" }
        if (teachesEnglish) conditions += "idiomas like '%INGLÉS%'"
        return conditions.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
    }

    /** Descripción corta de los filtros activos para mostrar en la pantalla de resultados. */
    fun describe(): String {
        val parts = mutableListOf<String>()
        name?.takeIf { it.isNotBlank() }?.let { parts += "\"${it.trim()}\"" }
        provider?.let { parts += it.toTitleCase() }
        type?.let { parts += it.toTitleCase() }
        schedule?.let { parts += "Jornada ${it.toTitleCase()}" }
        level?.let { parts += it.toTitleCase() }
        calendar?.let { parts += "Calendario $it" }
        if (teachesEnglish) parts += "Inglés"
        return if (parts.isEmpty()) "Sin filtros: todos los colegios" else parts.joinToString(" · ")
    }

    private fun String.soql(): String = replace("'", "''")
}

/** Opciones de cada filtro: etiqueta visible -> valor exacto en el dataset (null = Todos). */
object FilterOptions {
    const val ALL = "Todos"

    val providers = listOf(
        ALL to null,
        "Oficial" to "OFICIAL",
        "Persona natural" to "PERSONA NATURAL",
        "Comunidad religiosa" to "COMUNIDAD RELIGIOSA",
        "Fundación o corporación" to "FUNDACION O CORPORACIONES",
        "Federaciones" to "FEDERACIONES",
        "Régimen especial" to "REGIMEN ESPECIAL",
        "Cooperativo" to "COOPERATIVO",
        "Comunidad" to "COMUNIDAD",
        "Caja de compensación" to "CAJA DE COMPENSACIÓN",
        "Sociedad" to "SOCIEDAD"
    )

    val types = listOf(
        ALL to null,
        "Institución educativa" to "INSTITUCION EDUCATIVA",
        "Centro educativo" to "CENTRO EDUCATIVO"
    )

    val schedules = listOf(
        ALL to null,
        "Mañana" to "MAÑANA",
        "Tarde" to "TARDE",
        "Completa" to "COMPLETA",
        "Única" to "ÚNICA",
        "Nocturna" to "NOCTURNA",
        "Fin de semana" to "FIN DE SEMANA"
    )

    val levels = listOf(
        ALL to null,
        "Preescolar" to "PREESCOLAR",
        "Básica primaria" to "BÁSICA PRIMARIA",
        "Básica secundaria" to "BÁSICA SECUNDARIA",
        "Media" to "MEDIA"
    )

    val calendars = listOf(
        ALL to null,
        "Calendario A" to "A",
        "Calendario B" to "B",
        "Otro" to "OTRO"
    )
}
