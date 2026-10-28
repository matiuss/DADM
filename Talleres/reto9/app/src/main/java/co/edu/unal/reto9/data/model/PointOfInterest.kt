package co.edu.unal.reto9.data.model

data class PointOfInterest(
    val id: String,
    val name: String,
    val category: PoiCategory,
    val subType: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
    val address: String = "",
    val phone: String = "",
    val website: String = ""
)
