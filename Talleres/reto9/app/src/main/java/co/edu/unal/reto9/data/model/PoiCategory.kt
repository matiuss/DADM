package co.edu.unal.reto9.data.model

import android.graphics.Color

enum class PoiCategory(
    val displayName: String,
    val badgeColorHex: Int,
    val badgeBgHex: Int
) {
    ALL(
        displayName = "Todos",
        badgeColorHex = Color.parseColor("#1A73E8"),
        badgeBgHex = Color.parseColor("#E8F0FE")
    ),
    HOSPITAL(
        displayName = "Hospitales y Salud",
        badgeColorHex = Color.parseColor("#D93025"),
        badgeBgHex = Color.parseColor("#FCE8E6")
    ),
    TOURISM(
        displayName = "Lugares Turísticos",
        badgeColorHex = Color.parseColor("#00897B"),
        badgeBgHex = Color.parseColor("#E0F2F1")
    ),
    SHOP(
        displayName = "Supermercados y Comercios",
        badgeColorHex = Color.parseColor("#C2185B"),
        badgeBgHex = Color.parseColor("#FCE4EC")
    ),
    RESTAURANT(
        displayName = "Restaurantes y Cafés",
        badgeColorHex = Color.parseColor("#E37400"),
        badgeBgHex = Color.parseColor("#FEF7E0")
    ),
    EDUCATION(
        displayName = "Educación y Cultura",
        badgeColorHex = Color.parseColor("#1967D2"),
        badgeBgHex = Color.parseColor("#E8F0FE")
    ),
    SERVICES(
        displayName = "Transporte y Servicios",
        badgeColorHex = Color.parseColor("#8430CE"),
        badgeBgHex = Color.parseColor("#F3E8FD")
    );

    companion object {
        fun fromKey(key: String?): PoiCategory {
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: ALL
        }
    }
}
