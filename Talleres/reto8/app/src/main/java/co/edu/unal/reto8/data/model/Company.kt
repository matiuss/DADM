package co.edu.unal.reto8.data.model

import java.io.Serializable

data class Company(
    val id: Long = 0L,
    val name: String,
    val website: String,
    val phone: String,
    val email: String,
    val productsServices: String,
    val classifications: List<String>
) : Serializable {

    fun getClassificationsAsString(): String {
        return classifications.joinToString(", ")
    }

    companion object {
        const val CLASSIFICATION_CONSULTORIA = "Consultoría"
        const val CLASSIFICATION_DESARROLLO = "Desarrollo a la medida"
        const val CLASSIFICATION_FABRICA = "Fábrica de software"

        fun parseClassifications(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
