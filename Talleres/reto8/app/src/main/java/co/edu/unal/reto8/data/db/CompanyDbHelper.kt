package co.edu.unal.reto8.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CompanyDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "software_directory.db"
        const val DATABASE_VERSION = 1

        const val TABLE_NAME = "companies"
        const val COLUMN_ID = "_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_WEBSITE = "website"
        const val COLUMN_PHONE = "phone"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_PRODUCTS_SERVICES = "products_services"
        const val COLUMN_CLASSIFICATION = "classification"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_WEBSITE TEXT,
                $COLUMN_PHONE TEXT,
                $COLUMN_EMAIL TEXT,
                $COLUMN_PRODUCTS_SERVICES TEXT,
                $COLUMN_CLASSIFICATION TEXT NOT NULL
            );
        """.trimIndent()
        db.execSQL(createTableQuery)

        // Insertar datos de ejemplo iniciales para facilitar la prueba inmediata
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        val sampleCompanies = listOf(
            ContentValues().apply {
                put(COLUMN_NAME, "SoftSolutions Colombia")
                put(COLUMN_WEBSITE, "https://www.softsolutions.co")
                put(COLUMN_PHONE, "+57 300 123 4567")
                put(COLUMN_EMAIL, "contacto@softsolutions.co")
                put(COLUMN_PRODUCTS_SERVICES, "Desarrollo de aplicaciones móviles Android/iOS, plataformas web y pasarelas de pago.")
                put(COLUMN_CLASSIFICATION, "Desarrollo a la medida, Fábrica de software")
            },
            ContentValues().apply {
                put(COLUMN_NAME, "Cloud & DevOps Advisors")
                put(COLUMN_WEBSITE, "https://www.cloudadvisors.lat")
                put(COLUMN_PHONE, "+57 311 987 6543")
                put(COLUMN_EMAIL, "info@cloudadvisors.lat")
                put(COLUMN_PRODUCTS_SERVICES, "Consultoría en arquitectura cloud (AWS/GCP), auditoría de seguridad y migración de microservicios.")
                put(COLUMN_CLASSIFICATION, "Consultoría")
            },
            ContentValues().apply {
                put(COLUMN_NAME, "Global Byte Factory")
                put(COLUMN_WEBSITE, "https://www.globalbytefactory.com")
                put(COLUMN_PHONE, "+57 315 444 8899")
                put(COLUMN_EMAIL, "ventas@globalbytefactory.com")
                put(COLUMN_PRODUCTS_SERVICES, "Fábrica de software con células ágiles (Scrum), QA automatizado y modernización de sistemas legacy.")
                put(COLUMN_CLASSIFICATION, "Consultoría, Fábrica de software")
            }
        )

        for (values in sampleCompanies) {
            db.insert(TABLE_NAME, null, values)
        }
    }
}
