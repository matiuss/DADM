package co.edu.unal.reto9.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.unal.reto9.R
import co.edu.unal.reto9.data.model.PoiCategory
import co.edu.unal.reto9.data.model.PointOfInterest
import co.edu.unal.reto9.data.preferences.AppPreferences
import co.edu.unal.reto9.data.repository.LocationAndPoiRepository
import co.edu.unal.reto9.databinding.ActivityMainBinding
import co.edu.unal.reto9.databinding.DialogCoordinatesBinding
import co.edu.unal.reto9.ui.adapter.PoiAdapter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.util.Locale
import kotlin.math.ln

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appPreferences: AppPreferences
    private lateinit var repository: LocationAndPoiRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var poiAdapter: PoiAdapter

    private var currentLat: Double = AppPreferences.DEFAULT_UNAL_LAT
    private var currentLon: Double = AppPreferences.DEFAULT_UNAL_LON
    private var currentAddress: String = AppPreferences.DEFAULT_UNAL_ADDRESS
    private var selectedCategory: PoiCategory = PoiCategory.ALL
    private var activePoiKeyword: String = ""

    private var currentPois: List<PointOfInterest> = emptyList()
    private var selectedPoiIndex: Int = -1
    private var isListMode: Boolean = false
    private var searchJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val markerMap = mutableMapOf<String, Marker>()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchDeviceGpsLocation(showToastOnFallback = true)
        } else {
            Toast.makeText(
                this,
                "Permiso GPS no concedido. Mostrando ubicación de referencia configurada.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            updateRadiusBadge()
            updateLocationAndLoadPois(currentLat, currentLon, currentAddress, animateZoom = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar osmdroid antes de inflar el MapView
        Configuration.getInstance().apply {
            load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
            userAgentValue = packageName
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPreferences = AppPreferences(this)
        repository = LocationAndPoiRepository(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        currentLat = appPreferences.lastLatitude
        currentLon = appPreferences.lastLongitude
        currentAddress = appPreferences.lastAddress

        setupMapView()
        setupRecyclerView()
        setupListeners()
        updateRadiusBadge()

        // Mostrar inmediatamente la posición guardada/por defecto y solicitar señal GPS
        checkLocationPermissionsAndStart()
    }

    private fun setupMapView() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.5)
            controller.setCenter(GeoPoint(currentLat, currentLon))

            val scaleBarOverlay = ScaleBarOverlay(this).apply {
                setCentred(true)
                setScaleBarOffset(resources.displayMetrics.widthPixels / 2, 24)
            }
            overlays.add(scaleBarOverlay)

            val compassOverlay = CompassOverlay(
                this@MainActivity,
                InternalCompassOrientationProvider(this@MainActivity),
                this
            ).apply {
                enableCompass()
            }
            overlays.add(compassOverlay)
        }
    }

    private fun setupRecyclerView() {
        poiAdapter = PoiAdapter(
            onShowOnMapClick = { poi ->
                switchToMapAndFocusPoi(poi)
            },
            onDirectionsClick = { poi ->
                openExternalDirections(poi)
            }
        )
        binding.rvPois.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = poiAdapter
        }
    }

    private fun setupListeners() {
        // Abrir pantalla de Preferencias para configurar el radio en kilómetros
        binding.btnOpenSettings.setOnClickListener {
            openSettingsScreen()
        }
        binding.chipRadiusPref.setOnClickListener {
            openSettingsScreen()
        }

        // Botón "Mi ubicación GPS"
        binding.btnMyGps.setOnClickListener {
            if (hasLocationPermission()) {
                fetchDeviceGpsLocation(showToastOnFallback = true)
            } else {
                requestLocationPermissions()
            }
        }

        // Botón o encabezado para establecer "Mi Dirección Actual o Coordenadas GPS"
        binding.btnEnterCoordinates.setOnClickListener {
            showSetUserLocationDialog()
        }
        binding.layoutLocationHeader.setOnClickListener {
            showSetUserLocationDialog()
        }

        // Barra de búsqueda de Puntos de Interés cercanos dentro del radio (ej. "Carulla")
        binding.etSearchLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClearSearch.visibility =
                    if (s.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchLocation.setText("")
            if (activePoiKeyword.isNotEmpty()) {
                activePoiKeyword = ""
                updateLocationAndLoadPois(currentLat, currentLon, currentAddress, animateZoom = false)
            }
        }

        binding.btnSearchGo.setOnClickListener {
            performPoiOrAddressSearch()
        }
        binding.etSearchLocation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performPoiOrAddressSearch()
                true
            } else {
                false
            }
        }

        // Filtro por categorías de puntos de interés
        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipCatAll
            selectedCategory = when (checkedId) {
                R.id.chipCatHospital -> PoiCategory.HOSPITAL
                R.id.chipCatTourism -> PoiCategory.TOURISM
                R.id.chipCatShop -> PoiCategory.SHOP
                R.id.chipCatRestaurant -> PoiCategory.RESTAURANT
                R.id.chipCatEducation -> PoiCategory.EDUCATION
                R.id.chipCatServices -> PoiCategory.SERVICES
                else -> PoiCategory.ALL
            }
            updateLocationAndLoadPois(currentLat, currentLon, currentAddress, animateZoom = false)
        }

        // Alternar entre vista de Mapa y vista de Lista
        binding.btnToggleViewMode.setOnClickListener {
            toggleMapAndListView(!isListMode)
        }

        // Controles de la tarjeta inferior de Punto de Interés seleccionado
        binding.btnPrevPoi.setOnClickListener {
            if (currentPois.isNotEmpty()) {
                val nextIndex = if (selectedPoiIndex <= 0) currentPois.lastIndex else selectedPoiIndex - 1
                selectPoiAtIndex(nextIndex, centerMap = true)
            }
        }
        binding.btnNextPoi.setOnClickListener {
            if (currentPois.isNotEmpty()) {
                val nextIndex = if (selectedPoiIndex >= currentPois.lastIndex) 0 else selectedPoiIndex + 1
                selectPoiAtIndex(nextIndex, centerMap = true)
            }
        }
        binding.btnNavigatePoi.setOnClickListener {
            val poi = currentPois.getOrNull(selectedPoiIndex) ?: return@setOnClickListener
            openExternalDirections(poi)
        }
    }

    private fun openSettingsScreen() {
        val intent = Intent(this, SettingsActivity::class.java)
        settingsLauncher.launch(intent)
    }

    private fun updateRadiusBadge() {
        val radiusKm = appPreferences.radiusKm
        binding.chipRadiusPref.text = String.format(Locale.US, "Radio: %.1f km", radiusKm)
    }

    private fun checkLocationPermissionsAndStart() {
        updateLocationAndLoadPois(currentLat, currentLon, currentAddress, animateZoom = true)
        if (!hasLocationPermission()) {
            requestLocationPermissions()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun fetchDeviceGpsLocation(showToastOnFallback: Boolean) {
        setLoadingState(true, "Obteniendo coordenadas GPS...")
        val tokenSource = CancellationTokenSource()
        var resolved = false

        val timeoutRunnable = Runnable {
            if (!resolved) {
                resolved = true
                tokenSource.cancel()
                tryFallbackLocationManager(showToastOnFallback)
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 3500L)

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
            .addOnSuccessListener { location: Location? ->
                if (!resolved) {
                    resolved = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    if (location != null) {
                        onNewDeviceLocation(location)
                    } else {
                        tryFallbackLocationManager(showToastOnFallback)
                    }
                }
            }
            .addOnFailureListener {
                if (!resolved) {
                    resolved = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    tryFallbackLocationManager(showToastOnFallback)
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun tryFallbackLocationManager(showToastOnFallback: Boolean) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    onNewDeviceLocation(lastLoc)
                } else {
                    trySystemLocationProviders(showToastOnFallback)
                }
            }
            .addOnFailureListener {
                trySystemLocationProviders(showToastOnFallback)
            }
    }

    @SuppressLint("MissingPermission")
    private fun trySystemLocationProviders(showToastOnFallback: Boolean) {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val gpsLoc = try {
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            null
        }

        if (gpsLoc != null) {
            onNewDeviceLocation(gpsLoc)
        } else {
            if (showToastOnFallback) {
                Toast.makeText(
                    this,
                    "Sin señal GPS activa; mostrando ubicación configurada (${
                        String.format(Locale.US, "%.4f, %.4f", currentLat, currentLon)
                    }).",
                    Toast.LENGTH_LONG
                ).show()
            }
            updateLocationAndLoadPois(currentLat, currentLon, currentAddress, animateZoom = true)
        }
    }

    private fun onNewDeviceLocation(location: Location) {
        val lat = location.latitude
        val lon = location.longitude
        lifecycleScope.launch {
            val resolvedAddress = repository.reverseGeocode(lat, lon)
            Toast.makeText(
                this@MainActivity,
                String.format(Locale.US, "Posición GPS obtenida: %.5f, %.5f", lat, lon),
                Toast.LENGTH_SHORT
            ).show()
            updateLocationAndLoadPois(lat, lon, resolvedAddress, animateZoom = true)
        }
    }

    /**
     * Si el usuario escribe un nombre de Punto de Interés (ej. "Carulla", "farmacia", "museo"),
     * lo busca DENTRO del radio actual en kilómetros sin mover la posición del usuario.
     * Si escribe explícitamente una dirección con nomenclatura vial (ej. "Carrera 20 #122-55")
     * o coordenadas ("4.7019, -74.0499"), actualiza la posición del usuario a esa dirección.
     */
    private fun performPoiOrAddressSearch() {
        val query = binding.etSearchLocation.text?.toString()?.trim().orEmpty()
        hideKeyboard()

        if (query.isEmpty()) {
            activePoiKeyword = ""
            updateLocationAndLoadPois(currentLat, currentLon, currentAddress, animateZoom = false)
            return
        }

        val isCoordinateInput = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*[,;\s]\s*(-?\d+(?:\.\d+)?)\s*$""")
            .matches(query)
        val isStreetAddressInput = Regex(
            """(?i).*\b(carrera|cra\.?|kr\.?|calle|cll\.?|cl\.?|avenida|av\.?|diagonal|transversal)\s+\d+.*"""
        ).matches(query) && (query.contains("#") || query.contains("-"))

        if (isCoordinateInput || isStreetAddressInput) {
            // El usuario ingresó una dirección vial o coordenadas GPS para fijar su posición
            activePoiKeyword = ""
            binding.etSearchLocation.setText("")
            setLoadingState(true, "Ubicando dirección \"$query\"...")
            lifecycleScope.launch {
                val resolved = repository.resolveUserAddressOrCoordinates(query)
                if (resolved != null) {
                    updateLocationAndLoadPois(
                        resolved.latitude,
                        resolved.longitude,
                        resolved.addressName,
                        animateZoom = true
                    )
                } else {
                    setLoadingState(false, "")
                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo ubicar la dirección \"$query\".",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            // Búsqueda de Punto de Interés cercano dentro del radio actual (ej. "Carulla")
            activePoiKeyword = query
            updateLocationAndLoadPois(currentLat, currentLon, currentAddress, animateZoom = true)
        }
    }

    /**
     * Diálogo para cambiar la posición actual del usuario ya sea por Dirección
     * (ej. "Carrera 20 #122-55, Bogotá") o por Coordenadas GPS (Latitud y Longitud).
     */
    private fun showSetUserLocationDialog() {
        val dialogBinding = DialogCoordinatesBinding.inflate(layoutInflater)
        dialogBinding.etDialogAddress.setText(currentAddress)
        dialogBinding.etDialogLat.setText(String.format(Locale.US, "%.6f", currentLat))
        dialogBinding.etDialogLon.setText(String.format(Locale.US, "%.6f", currentLon))

        // Si el usuario escribe en el campo de dirección, limpiar el foco de coordenadas
        var editedAddressLast = false
        dialogBinding.etDialogAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                editedAddressLast = true
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val coordWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                editedAddressLast = false
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        dialogBinding.etDialogLat.addTextChangedListener(coordWatcher)
        dialogBinding.etDialogLon.addTextChangedListener(coordWatcher)

        dialogBinding.chipCra20Calle122.setOnClickListener {
            dialogBinding.etDialogAddress.setText("Carrera 20 #122-55, Bogotá")
            dialogBinding.etDialogLat.setText("4.701906")
            dialogBinding.etDialogLon.setText("-74.049873")
            editedAddressLast = false
        }
        dialogBinding.chipUnalBogota.setOnClickListener {
            dialogBinding.etDialogAddress.setText("Universidad Nacional de Colombia, Bogotá")
            dialogBinding.etDialogLat.setText("4.638194")
            dialogBinding.etDialogLon.setText("-74.084046")
            editedAddressLast = false
        }
        dialogBinding.chipCentroBogota.setOnClickListener {
            dialogBinding.etDialogAddress.setText("Plaza de Bolívar, Bogotá")
            dialogBinding.etDialogLat.setText("4.598120")
            dialogBinding.etDialogLon.setText("-74.076044")
            editedAddressLast = false
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Establecer Mi Posición Actual")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Fijar Posición") { _, _ ->
                val addressText = dialogBinding.etDialogAddress.text?.toString()?.trim().orEmpty()
                val latStr = dialogBinding.etDialogLat.text?.toString()?.trim()?.replace(',', '.') ?: ""
                val lonStr = dialogBinding.etDialogLon.text?.toString()?.trim()?.replace(',', '.') ?: ""

                // Si el usuario modificó la dirección (o escribió una dirección nueva distinta a la actual)
                if (editedAddressLast && addressText.isNotEmpty() && addressText != currentAddress) {
                    lifecycleScope.launch {
                        setLoadingState(true, "Ubicando dirección \"$addressText\"...")
                        val resolved = repository.resolveUserAddressOrCoordinates(addressText)
                        if (resolved != null) {
                            updateLocationAndLoadPois(
                                resolved.latitude,
                                resolved.longitude,
                                resolved.addressName,
                                animateZoom = true
                            )
                        } else {
                            setLoadingState(false, "")
                            Toast.makeText(
                                this@MainActivity,
                                "No se encontró la dirección \"$addressText\".",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    return@setPositiveButton
                }

                val lat = latStr.toDoubleOrNull()
                val lon = lonStr.toDoubleOrNull()
                if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    Toast.makeText(
                        this,
                        "Coordenadas inválidas. Verifica Latitud (-90 a 90) y Longitud (-180 a 180).",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    setLoadingState(true, "Resolviendo posición...")
                    val label = if (addressText.isNotBlank() && addressText != currentAddress) {
                        addressText
                    } else {
                        repository.reverseGeocode(lat, lon)
                    }
                    updateLocationAndLoadPois(lat, lon, label, animateZoom = true)
                }
            }
            .show()
    }

    private fun updateLocationAndLoadPois(
        latitude: Double,
        longitude: Double,
        address: String,
        animateZoom: Boolean
    ) {
        currentLat = latitude
        currentLon = longitude
        currentAddress = address

        appPreferences.lastLatitude = latitude
        appPreferences.lastLongitude = longitude
        appPreferences.lastAddress = address

        val radiusKm = appPreferences.radiusKm
        updateRadiusBadge()

        binding.tvCurrentLocationInfo.text = String.format(
            Locale.US,
            "📍 %s (%.4f, %.4f)",
            address,
            latitude,
            longitude
        )

        if (animateZoom) {
            val targetZoom = calculateZoomForRadius(radiusKm)
            binding.mapView.controller.setZoom(targetZoom)
            binding.mapView.controller.animateTo(GeoPoint(latitude, longitude))
        }

        renderMapOverlays(emptyList())

        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val statusMsg = if (activePoiKeyword.isNotBlank()) {
                String.format(Locale.US, "Buscando \"%s\" en %.1f km...", activePoiKeyword, radiusKm)
            } else {
                String.format(Locale.US, "Buscando POIs en %.1f km...", radiusKm)
            }
            setLoadingState(true, statusMsg)

            val pois = repository.fetchNearbyPois(
                latitude = latitude,
                longitude = longitude,
                radiusKm = radiusKm,
                category = selectedCategory,
                maxResults = appPreferences.maxResults,
                keywordQuery = activePoiKeyword
            )
            currentPois = pois
            poiAdapter.submitList(pois)

            renderMapOverlays(pois)
            updateViewCountersAndSelection(pois)
            setLoadingState(false, "")
        }
    }

    private fun renderMapOverlays(pois: List<PointOfInterest>) {
        val map = binding.mapView
        InfoWindow.closeAllInfoWindowsOn(map)

        map.overlays.removeAll { overlay ->
            overlay !is ScaleBarOverlay && overlay !is CompassOverlay
        }
        markerMap.clear()

        val userPoint = GeoPoint(currentLat, currentLon)
        val radiusKm = appPreferences.radiusKm
        val radiusMeters = radiusKm * 1000.0

        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                InfoWindow.closeAllInfoWindowsOn(map)
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    lifecycleScope.launch {
                        setLoadingState(true, "Actualizando posición seleccionada...")
                        val addr = repository.reverseGeocode(p.latitude, p.longitude)
                        Toast.makeText(
                            this@MainActivity,
                            "Nueva posición fijada en el mapa",
                            Toast.LENGTH_SHORT
                        ).show()
                        updateLocationAndLoadPois(p.latitude, p.longitude, addr, animateZoom = false)
                    }
                    return true
                }
                return false
            }
        })
        map.overlays.add(eventsOverlay)

        val circlePolygon = Polygon(map).apply {
            points = Polygon.pointsAsCircle(userPoint, radiusMeters)
            fillPaint.color = Color.argb(36, 26, 115, 232)
            outlinePaint.color = Color.rgb(26, 115, 232)
            outlinePaint.strokeWidth = 4.5f
            title = String.format(Locale.US, "Radio de búsqueda: %.1f km", radiusKm)
            infoWindow = null
        }
        map.overlays.add(circlePolygon)

        val showLabelsOnMap = appPreferences.showPoiLabelsOnMap
        pois.forEachIndexed { index, poi ->
            val poiMarker = Marker(map).apply {
                position = GeoPoint(poi.latitude, poi.longitude)
                title = poi.name
                snippet = String.format(
                    Locale.US,
                    "%s • a %.2f km",
                    poi.subType,
                    poi.distanceKm
                )
                subDescription = poi.address
                icon = createPoiMarkerDrawable(
                    name = poi.name,
                    color = poi.category.badgeColorHex,
                    includeNameLabel = showLabelsOnMap
                )
                if (showLabelsOnMap) {
                    setAnchor(Marker.ANCHOR_CENTER, 0.92f)
                } else {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                setOnMarkerClickListener { marker, _ ->
                    InfoWindow.closeAllInfoWindowsOn(map)
                    marker.showInfoWindow()
                    selectPoiAtIndex(index, centerMap = false)
                    true
                }
            }
            map.overlays.add(poiMarker)
            markerMap[poi.id] = poiMarker
        }

        val userMarker = Marker(map).apply {
            position = userPoint
            title = "Tu Posición Actual"
            snippet = currentAddress
            subDescription = String.format(
                Locale.US,
                "Lat: %.5f, Lon: %.5f | Radio: %.1f km",
                currentLat,
                currentLon,
                radiusKm
            )
            icon = createUserLocationDrawable()
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { marker, _ ->
                InfoWindow.closeAllInfoWindowsOn(map)
                marker.showInfoWindow()
                true
            }
        }
        map.overlays.add(userMarker)

        map.invalidate()
    }

    private fun updateViewCountersAndSelection(pois: List<PointOfInterest>) {
        val radiusKm = appPreferences.radiusKm
        binding.tvListHeaderSummary.text = if (activePoiKeyword.isNotBlank()) {
            String.format(
                Locale.US,
                "%d resultados de \"%s\" en %.1f km",
                pois.size,
                activePoiKeyword,
                radiusKm
            )
        } else {
            String.format(
                Locale.US,
                "%d puntos de interés en un radio de %.1f km",
                pois.size,
                radiusKm
            )
        }

        updateToggleViewButtonText()

        if (pois.isNotEmpty()) {
            selectPoiAtIndex(0, centerMap = false)
            val nearestPoi = pois[0]
            InfoWindow.closeAllInfoWindowsOn(binding.mapView)
            markerMap[nearestPoi.id]?.showInfoWindow()
        } else {
            selectedPoiIndex = -1
            binding.cardSelectedPoi.visibility = View.GONE
            val msg = if (activePoiKeyword.isNotBlank()) {
                String.format(
                    Locale.US,
                    "No se encontró \"%s\" dentro de %.1f km alrededor de tu posición. Aumenta el radio en Preferencias.",
                    activePoiKeyword,
                    radiusKm
                )
            } else {
                String.format(
                    Locale.US,
                    "No se encontraron puntos de interés en %.1f km. Aumenta el radio en Preferencias.",
                    radiusKm
                )
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun selectPoiAtIndex(index: Int, centerMap: Boolean) {
        if (index !in currentPois.indices) return
        selectedPoiIndex = index
        val poi = currentPois[index]

        binding.cardSelectedPoi.visibility = if (isListMode) View.GONE else View.VISIBLE
        binding.tvSelectedPoiName.text = poi.name
        binding.tvSelectedPoiCategory.text = poi.subType
        binding.tvSelectedPoiCategory.setTextColor(poi.category.badgeColorHex)
        binding.tvSelectedPoiCategory.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(poi.category.badgeBgHex)
        }
        binding.tvSelectedPoiDistance.text = String.format(
            Locale.US,
            "a %.2f km de tu posición",
            poi.distanceKm
        )
        binding.tvPoiCounterBadge.text = "${index + 1} / ${currentPois.size}"
        binding.tvSelectedPoiAddress.text = String.format(
            Locale.US,
            "%s • (%.4f, %.4f)",
            poi.address,
            poi.latitude,
            poi.longitude
        )

        if (centerMap) {
            val point = GeoPoint(poi.latitude, poi.longitude)
            binding.mapView.controller.animateTo(point)
            InfoWindow.closeAllInfoWindowsOn(binding.mapView)
            markerMap[poi.id]?.showInfoWindow()
        }
    }

    private fun switchToMapAndFocusPoi(poi: PointOfInterest) {
        toggleMapAndListView(false)
        val idx = currentPois.indexOfFirst { it.id == poi.id }
        if (idx >= 0) {
            selectPoiAtIndex(idx, centerMap = true)
        }
    }

    private fun toggleMapAndListView(showList: Boolean) {
        isListMode = showList
        if (showList) {
            binding.layoutListContainer.visibility = View.VISIBLE
            binding.cardSelectedPoi.visibility = View.GONE
        } else {
            binding.layoutListContainer.visibility = View.GONE
            binding.cardSelectedPoi.visibility =
                if (currentPois.isNotEmpty()) View.VISIBLE else View.GONE
        }
        updateToggleViewButtonText()
    }

    private fun updateToggleViewButtonText() {
        if (isListMode) {
            binding.btnToggleViewMode.text = "Ver Mapa"
            binding.btnToggleViewMode.setIconResource(R.drawable.ic_map)
        } else {
            binding.btnToggleViewMode.text = "Ver Lista (${currentPois.size})"
            binding.btnToggleViewMode.setIconResource(R.drawable.ic_list)
        }
    }

    private fun openExternalDirections(poi: PointOfInterest) {
        val encodedName = Uri.encode(poi.name)
        val geoUri = Uri.parse("geo:${poi.latitude},${poi.longitude}?q=${poi.latitude},${poi.longitude}($encodedName)")
        val intent = Intent(Intent.ACTION_VIEW, geoUri)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            val webUrl = Uri.parse(
                "https://www.openstreetmap.org/?mlat=${poi.latitude}&mlon=${poi.longitude}#map=17/${poi.latitude}/${poi.longitude}"
            )
            startActivity(Intent(Intent.ACTION_VIEW, webUrl))
        }
    }

    private fun createPoiMarkerDrawable(
        name: String,
        color: Int,
        includeNameLabel: Boolean
    ): BitmapDrawable {
        val density = resources.displayMetrics.density
        val shortName = if (name.length > 24) name.take(22) + "…" else name

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.parseColor("#202124")
            textSize = 11f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val pinRadius = 11f * density
        val pinTipHeight = 8f * density
        val pinTotalHeight = (pinRadius * 2f) + pinTipHeight

        if (!includeNameLabel) {
            val width = (pinRadius * 2.4f).toInt().coerceAtLeast(1)
            val height = (pinTotalHeight + 4f * density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawPinIcon(canvas, width / 2f, pinRadius + 2f * density, pinRadius, pinTipHeight, color, density)
            return BitmapDrawable(resources, bitmap)
        }

        val textPaddingH = 7f * density
        val textPaddingV = 4f * density
        val textWidth = textPaint.measureText(shortName)
        val pillWidth = textWidth + textPaddingH * 2f
        val pillHeight = (14f * density) + textPaddingV * 2f
        val spacing = 3f * density

        val totalWidth = pillWidth.coerceAtLeast(pinRadius * 2.5f).toInt() + (6 * density).toInt()
        val totalHeight = (pillHeight + spacing + pinTotalHeight + 4f * density).toInt()

        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pillLeft = (totalWidth - pillWidth) / 2f
        val pillRect = RectF(pillLeft, 2f * density, pillLeft + pillWidth, 2f * density + pillHeight)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(242, 255, 255, 255)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
        }

        canvas.drawRoundRect(pillRect, 8f * density, 8f * density, bgPaint)
        canvas.drawRoundRect(pillRect, 8f * density, 8f * density, borderPaint)

        val fontMetrics = textPaint.fontMetrics
        val textBaseline = pillRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(shortName, pillLeft + textPaddingH, textBaseline, textPaint)

        val pinCenterX = totalWidth / 2f
        val pinCenterY = pillRect.bottom + spacing + pinRadius
        drawPinIcon(canvas, pinCenterX, pinCenterY, pinRadius, pinTipHeight, color, density)

        return BitmapDrawable(resources, bitmap)
    }

    private fun drawPinIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        tipHeight: Float,
        color: Int,
        density: Float
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }

        val path = Path().apply {
            moveTo(cx - radius * 0.65f, cy + radius * 0.55f)
            lineTo(cx, cy + radius + tipHeight)
            lineTo(cx + radius * 0.65f, cy + radius * 0.55f)
            close()
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)

        val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius * 0.38f, innerDotPaint)
    }

    private fun createUserLocationDrawable(): BitmapDrawable {
        val density = resources.displayMetrics.density
        val size = (32f * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(75, 26, 115, 232)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(26, 115, 232)
            style = Paint.Style.FILL
        }

        canvas.drawCircle(center, center, 15f * density, haloPaint)
        canvas.drawCircle(center, center, 9.5f * density, borderPaint)
        canvas.drawCircle(center, center, 7f * density, corePaint)

        return BitmapDrawable(resources, bitmap)
    }

    private fun calculateZoomForRadius(radiusKm: Float): Double {
        val safeRadius = radiusKm.coerceIn(0.5f, 50.0f).toDouble()
        val zoom = 16.0 - (ln(safeRadius) / ln(2.0))
        return zoom.coerceIn(10.5, 17.5)
    }

    private fun setLoadingState(isLoading: Boolean, message: String) {
        binding.cardLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (message.isNotBlank()) {
            binding.tvLoadingStatus.text = message
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let { view ->
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
