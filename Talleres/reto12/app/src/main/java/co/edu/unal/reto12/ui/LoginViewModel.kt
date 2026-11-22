package co.edu.unal.reto12.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unal.reto12.auth.AuthException
import co.edu.unal.reto12.auth.FirebaseGoogleAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/** Datos del usuario que se muestran en pantalla. */
data class UserInfo(
    val uid: String,
    val name: String?,
    val email: String?,
    val photoUrl: String?,
    val createdAt: Long?,
    val lastSignInAt: Long?,
)

sealed interface UiState {
    data object SignedOut : UiState
    data class Loading(val message: String) : UiState
    data class SignedIn(val user: UserInfo, val avatar: Bitmap?) : UiState
}

/** Eventos de una sola vez (diálogos de resultado y avisos). */
sealed interface UiEvent {
    data class AuthSucceeded(val user: UserInfo) : UiEvent
    data class AuthFailed(val reason: String) : UiEvent
    data class Info(val message: String) : UiEvent
}

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = FirebaseGoogleAuth(app)

    private val _state = MutableStateFlow<UiState>(UiState.SignedOut)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val isConfigured: Boolean get() = auth.isConfigured

    init {
        // Firebase recuerda la sesión: si ya había un usuario, se muestra directamente.
        auth.currentUser?.let { viewModelScope.launch { showUser(it.toInfo()) } }
    }

    /** Se llama desde la Activity, porque la ventana de Google necesita una Activity. */
    suspend fun signIn(activityContext: Context) {
        _state.value = UiState.Loading("Esperando a Google…")
        try {
            val user = auth.signIn(activityContext).toInfo()
            showUser(user)
            _events.send(UiEvent.AuthSucceeded(user))
        } catch (e: AuthException) {
            _state.value = UiState.SignedOut
            _events.send(UiEvent.AuthFailed(e.message ?: "Error desconocido."))
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _state.value = UiState.SignedOut
            _events.send(UiEvent.Info("Sesión cerrada."))
        }
    }

    private fun FirebaseUser.toInfo() = UserInfo(
        uid = uid,
        name = displayName,
        email = email,
        photoUrl = photoUrl?.toString(),
        createdAt = metadata?.creationTimestamp,
        lastSignInAt = metadata?.lastSignInTimestamp,
    )

    private suspend fun showUser(user: UserInfo) {
        _state.value = UiState.SignedIn(user, avatar = null)
        val avatar = user.photoUrl?.let { loadBitmap(it) } ?: return
        val current = _state.value
        if (current is UiState.SignedIn && current.user == user) {
            _state.value = current.copy(avatar = avatar)
        }
    }

    private suspend fun loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
    }
}
