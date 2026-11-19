package co.edu.unal.reto11.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Consulta y vigila el estado de la conexión a internet del dispositivo.
 *
 * - [isOnline] indica si hay una red con acceso a internet en este momento.
 * - [start] / [stop] registran un callback que avisa (en el hilo principal) cada vez que
 *   la conexión se pierde o se recupera.
 */
class NetworkMonitor(context: Context, private val onChange: (Boolean) -> Unit) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false
    private var lastState: Boolean? = null

    /**
     * Se usa la información que entrega el propio callback (y no activeNetwork), porque
     * activeNetwork puede tardar en actualizarse cuando la red acaba de conectarse.
     */
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            notifyChange(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))

        override fun onLost(network: Network) = notifyChange(false)
    }

    /** Solo avisa cuando el estado cambia (conectado <-> desconectado), no en cada evento de red. */
    private fun notifyChange(online: Boolean) {
        mainHandler.post {
            if (online != lastState) {
                Log.d(TAG, "Conexión a internet: ${if (online) "disponible" else "perdida"}")
                lastState = online
                onChange(online)
            }
        }
    }

    fun start() {
        if (registered) return
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (e: RuntimeException) {
            // Algunos dispositivos limitan el número de callbacks; en ese caso solo se consulta bajo demanda.
        }
        lastState = isOnline(appContext)
        onChange(lastState == true)
    }

    fun stop() {
        if (!registered) return
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            // El callback ya no estaba registrado.
        }
        registered = false
    }

    companion object {
        private const val TAG = "NetworkMonitor"

        /** true si la red activa ofrece acceso a internet (Wi-Fi, datos móviles, Ethernet…). */
        fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        /**
         * Pantalla para conectarse a internet:
         * en Android 10+ abre el panel flotante de conectividad (Wi-Fi y datos móviles);
         * en versiones anteriores abre los ajustes de redes inalámbricas.
         */
        fun connectivitySettingsIntent(): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            }
    }
}
