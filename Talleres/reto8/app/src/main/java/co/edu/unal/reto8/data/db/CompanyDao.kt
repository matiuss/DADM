package co.edu.unal.reto8.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import co.edu.unal.reto8.data.model.Company

class CompanyDao(context: Context) {

    private val dbHelper = CompanyDbHelper(context)

    fun insert(company: Company): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(CompanyDbHelper.COLUMN_NAME, company.name)
            put(CompanyDbHelper.COLUMN_WEBSITE, company.website)
            put(CompanyDbHelper.COLUMN_PHONE, company.phone)
            put(CompanyDbHelper.COLUMN_EMAIL, company.email)
            put(CompanyDbHelper.COLUMN_PRODUCTS_SERVICES, company.productsServices)
            put(CompanyDbHelper.COLUMN_CLASSIFICATION, company.getClassificationsAsString())
        }
        return db.insert(CompanyDbHelper.TABLE_NAME, null, values)
    }

    fun update(company: Company): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(CompanyDbHelper.COLUMN_NAME, company.name)
            put(CompanyDbHelper.COLUMN_WEBSITE, company.website)
            put(CompanyDbHelper.COLUMN_PHONE, company.phone)
            put(CompanyDbHelper.COLUMN_EMAIL, company.email)
            put(CompanyDbHelper.COLUMN_PRODUCTS_SERVICES, company.productsServices)
            put(CompanyDbHelper.COLUMN_CLASSIFICATION, company.getClassificationsAsString())
        }
        return db.update(
            CompanyDbHelper.TABLE_NAME,
            values,
            "${CompanyDbHelper.COLUMN_ID} = ?",
            arrayOf(company.id.toString())
        )
    }

    fun delete(id: Long): Int {
        val db = dbHelper.writableDatabase
        return db.delete(
            CompanyDbHelper.TABLE_NAME,
            "${CompanyDbHelper.COLUMN_ID} = ?",
            arrayOf(id.toString())
        )
    }

    fun getById(id: Long): Company? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            CompanyDbHelper.TABLE_NAME,
            null,
            "${CompanyDbHelper.COLUMN_ID} = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                cursorToCompany(it)
            } else {
                null
            }
        }
    }

    fun getAll(): List<Company> {
        return filter(null, null)
    }

    fun filter(nameQuery: String?, classificationFilter: String?): List<Company> {
        val db = dbHelper.readableDatabase
        val selectionClauses = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (!nameQuery.isNullOrBlank()) {
            selectionClauses.add("${CompanyDbHelper.COLUMN_NAME} LIKE ?")
            selectionArgs.add("%${nameQuery.trim()}%")
        }

        if (!classificationFilter.isNullOrBlank() && classificationFilter != "Todas") {
            selectionClauses.add("${CompanyDbHelper.COLUMN_CLASSIFICATION} LIKE ?")
            selectionArgs.add("%${classificationFilter.trim()}%")
        }

        val selection = if (selectionClauses.isNotEmpty()) {
            selectionClauses.joinToString(" AND ")
        } else {
            null
        }

        val args = if (selectionArgs.isNotEmpty()) {
            selectionArgs.toTypedArray()
        } else {
            null
        }

        val orderBy = "${CompanyDbHelper.COLUMN_NAME} COLLATE NOCASE ASC"

        val cursor = db.query(
            CompanyDbHelper.TABLE_NAME,
            null,
            selection,
            args,
            null,
            null,
            orderBy
        )

        val companies = mutableListOf<Company>()
        cursor.use {
            while (it.moveToNext()) {
                companies.add(cursorToCompany(it))
            }
        }
        return companies
    }

    private fun cursorToCompany(cursor: Cursor): Company {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(CompanyDbHelper.COLUMN_ID))
        val name = cursor.getString(cursor.getColumnIndexOrThrow(CompanyDbHelper.COLUMN_NAME))
        val website = cursor.getString(cursor.getColumnIndexOrThrow(CompanyDbHelper.COLUMN_WEBSITE)) ?: ""
        val phone = cursor.getString(cursor.getColumnIndexOrThrow(CompanyDbHelper.COLUMN_PHONE)) ?: ""
        val email = cursor.getString(cursor.getColumnIndexOrThrow(CompanyDbHelper.COLUMN_EMAIL)) ?: ""
        val productsServices = cursor.getString(cursor.getColumnIndexOrThrow(CompanyDbHelper.COLUMN_PRODUCTS_SERVICES)) ?: ""
        val rawClassification = cursor.getString(cursor.getColumnIndexOrThrow(CompanyDbHelper.COLUMN_CLASSIFICATION)) ?: ""

        return Company(
            id = id,
            name = name,
            website = website,
            phone = phone,
            email = email,
            productsServices = productsServices,
            classifications = Company.parseClassifications(rawClassification)
        )
    }
}
