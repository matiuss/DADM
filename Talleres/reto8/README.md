# Reto 8: Directorio de Empresas de Desarrollo de Software (Android SQLite)

**Curso:** Desarrollo de Aplicaciones para Dispositivos Móviles (DADM)  
**Profesor:** Jorge E. Camargo, PhD  
**Lenguaje:** Kotlin  
**Persistencia:** SQLite (`SQLiteOpenHelper` y `SQLiteDatabase`)  
**UI:** Material Design 3 con ViewBinding y RecyclerView  

---

## 📱 Descripción del Proyecto

Aplicación móvil desarrollada en **Android con Kotlin** para gestionar un directorio completo de empresas de desarrollo de software, almacenando toda la información localmente en una base de datos **SQLite**.

---

## 🚀 Características Implementadas

### 1. Modelo de Datos (`Company`)
Cada registro contiene:
* **Nombre de la empresa** (`name`: `TEXT NOT NULL`)
* **URL de la página web** (`website`: `TEXT`)
* **Teléfono de contacto** (`phone`: `TEXT`)
* **Email de contacto** (`email`: `TEXT`)
* **Productos y servicios** (`products_services`: `TEXT`)
* **Clasificación de la empresa** (`classification`: `TEXT NOT NULL`): selección múltiple entre *Consultoría*, *Desarrollo a la medida* y/o *Fábrica de software*.

---

### 2. Operaciones CRUD (SQLite)
* **Create (Crear):** Formulario en `CompanyFormActivity` para registrar nuevas empresas con validaciones de campos obligatorios.
* **Retrieve (Consultar / Listar):** Consulta de registros con orden alfabético y soporte para filtrado dinámico.
* **Update (Modificar):** Edición completa de datos existentes pre-cargados en el formulario.
* **Delete (Eliminar):** Eliminación de registros con **mensaje de confirmación de usuario** mediante `MaterialAlertDialogBuilder` y opción de deshacer (*Undo* con `Snackbar`).

---

### 3. Búsqueda y Filtros Combinados
* **Búsqueda en tiempo real por nombre:** La lista se filtra conforme el usuario escribe en el campo de búsqueda.
* **Filtro por clasificación mediante Chips:** Permite alternar rápidamente entre *Todas*, *Consultoría*, *Desarrollo a la medida* y *Fábrica de software*.
* Búsqueda y filtros trabajan de forma simultánea.

---

### 4. Experiencia de Usuario (UI/UX)
* **Diseño moderno:** Tarjetas (`MaterialCardView`) con chips de colores por tipo de servicio.
* **Acciones interactivas directas:**
  * Toque en el teléfono: Abre el marcador de llamadas (`Intent.ACTION_DIAL`).
  * Toque en el email: Abre el cliente de correo (`Intent.ACTION_SENDTO`).
  * Toque en el sitio web: Abre el navegador web (`Intent.ACTION_VIEW`).
* **Estado vacío:** Vista ilustrada cuando no hay registros o cuando ninguna empresa coincide con los filtros.
* **Datos semilla iniciales:** Al abrir la app por primera vez, se insertan 3 empresas de ejemplo para facilitar la prueba inmediata.

---

## 📂 Estructura del Código

```text
reto8/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/co/edu/unal/reto8/
│   │   │   ├── data/
│   │   │   │   ├── db/
│   │   │   │   │   ├── CompanyDbHelper.kt    # SQLiteOpenHelper y esquema SQL
│   │   │   │   │   └── CompanyDao.kt         # Operaciones CRUD en SQLite
│   │   │   │   └── model/
│   │   │   │       └── Company.kt            # Entidad de dominio
│   │   │   └── ui/
│   │   │       ├── adapter/
│   │   │       │   └── CompanyAdapter.kt     # RecyclerView Adapter con ListAdapter/DiffUtil
│   │   │       ├── CompanyFormActivity.kt    # Formulario para Crear / Editar
│   │   │       └── MainActivity.kt           # Catálogo, Búsqueda, Filtros y Diálogos
│   │   └── res/
│   │       ├── layout/
│   │       │   ├── activity_main.xml
│   │       │   ├── activity_company_form.xml
│   │       │   └── item_company.xml
│   │       └── values/
│   │           ├── colors.xml
│   │           ├── strings.xml
│   │           └── themes.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🛠️ Cómo Abrir y Ejecutar en Android Studio

1. Abre **Android Studio**.
2. Selecciona **File > Open...** y navega a la carpeta:
   `C:\Users\danie\OneDrive\Documentos\GitHub\DADM\Talleres\reto8`
3. Espera a que Gradle sincronice las dependencias del proyecto.
4. Selecciona un emulador o dispositivo físico y presiona **Run ('app')** (Shift + F10).
