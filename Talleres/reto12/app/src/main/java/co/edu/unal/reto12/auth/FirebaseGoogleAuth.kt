package co.edu.unal.reto12.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** Error de autenticación con un mensaje listo para mostrar al usuario. */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Autenticación delegada: Firebase Authentication con Google como proveedor.
 *
 * 1. Credential Manager abre la ventana oficial de Google, que pide la cuenta y las credenciales.
 *    La app nunca ve la contraseña.
 * 2. Google devuelve un ID token (JWT firmado) con la identidad del usuario.
 * 3. Ese token se envía a Firebase, que lo valida en su servidor y crea/inicia la sesión.
 *
 * Firebase guarda la sesión por su cuenta (no hay que almacenar nada en la app).
 */
class FirebaseGoogleAuth(context: Context) {

    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(appContext)

    /** Usuario con sesión abierta, o null. Firebase la conserva entre reinicios de la app. */
    val currentUser: FirebaseUser? get() = auth.currentUser

    /**
     * ID de cliente web que el plugin google-services genera desde google-services.json.
     * Solo existe si el proveedor "Google" estaba activado en Firebase al descargar el archivo.
     */
    private val webClientId: String? by lazy {
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        if (id == 0) null else appContext.getString(id)
    }

    val isConfigured: Boolean get() = webClientId != null

    /** @param activityContext debe ser una Activity: la ventana de Google se muestra sobre ella. */
    suspend fun signIn(activityContext: Context): FirebaseUser {
        val clientId = webClientId ?: throw AuthException(
            "El archivo google-services.json no tiene el cliente web. Activa el proveedor Google en " +
                "Firebase Authentication y vuelve a descargar el archivo."
        )

        // Pasos 1 y 2: Google autentica al usuario y entrega un ID token.
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = try {
            credentialManager.getCredential(activityContext, request).credential
        } catch (e: GetCredentialException) {
            throw AuthException(describe(e), e)
        }
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw AuthException("Se recibió un tipo de credencial inesperado.")
        }
        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw AuthException("La respuesta de Google no es válida.", e)
        }

        // Paso 3: Firebase valida el token y abre la sesión.
        return try {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
                ?: throw AuthException("Firebase no devolvió el usuario.")
        } catch (e: FirebaseNetworkException) {
            throw AuthException("Sin conexión a internet. No se pudo verificar la cuenta con Firebase.", e)
        } catch (e: FirebaseAuthInvalidUserException) {
            throw AuthException("Esta cuenta fue deshabilitada en Firebase.", e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw AuthException("Firebase rechazó el token de Google (inválido o expirado).", e)
        } catch (e: FirebaseException) {
            throw AuthException("Firebase respondió: ${e.message}", e)
        }
    }

    /** Cierra la sesión en Firebase y olvida la cuenta elegida en Credential Manager. */
    suspend fun signOut() {
        auth.signOut()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun describe(e: GetCredentialException): String = when (e) {
        is GetCredentialCancellationException -> "Cancelaste el inicio de sesión con Google."
        is NoCredentialException ->
            "No hay cuentas de Google disponibles, o la huella SHA-1 de la app no está registrada en Firebase."
        is GetCredentialProviderConfigurationException ->
            "Los servicios de Google Play no están disponibles o están desactualizados en este dispositivo."
        else -> {
            val msg = e.errorMessage?.toString().orEmpty()
            when {
                // Error típico cuando falta la huella SHA-1 o el paquete no coincide.
                "28444" in msg || "Developer console" in msg ->
                    "La configuración en Firebase no es correcta. Revisa la huella SHA-1 y el nombre del paquete."
                msg.isNotBlank() -> "Google respondió: $msg"
                else -> "Ocurrió un error al iniciar sesión con Google (${e.type})."
            }
        }
    }
}
