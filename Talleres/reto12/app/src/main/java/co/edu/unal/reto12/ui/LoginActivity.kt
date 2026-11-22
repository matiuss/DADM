package co.edu.unal.reto12.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.unal.reto12.R
import co.edu.unal.reto12.databinding.ActivityLoginBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textConfigWarning.isVisible = !viewModel.isConfigured
        binding.buttonLogin.setOnClickListener {
            lifecycleScope.launch { viewModel.signIn(this@LoginActivity) }
        }
        binding.buttonLogout.setOnClickListener { viewModel.signOut() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: UiState) = with(binding) {
        groupSignedOut.isVisible = state is UiState.SignedOut
        groupLoading.isVisible = state is UiState.Loading
        groupSignedIn.isVisible = state is UiState.SignedIn

        when (state) {
            is UiState.Loading -> textLoading.text = state.message
            is UiState.SignedIn -> {
                textName.text = state.user.name ?: state.user.email.orEmpty()
                textEmail.text = state.user.email
                textDetails.text = getString(
                    R.string.details_format,
                    state.user.uid,
                    formatDate(state.user.createdAt),
                    formatDate(state.user.lastSignInAt),
                )
                if (state.avatar != null) imageAvatar.setImageBitmap(state.avatar)
                else imageAvatar.setImageResource(R.drawable.ic_person)
            }
            UiState.SignedOut -> Unit
        }
    }

    private fun formatDate(millis: Long?): String =
        if (millis == null || millis == 0L) "—"
        else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    private fun handleEvent(event: UiEvent) {
        when (event) {
            is UiEvent.AuthSucceeded -> showResultDialog(
                success = true,
                message = getString(R.string.auth_success_message, event.user.name ?: event.user.email ?: ""),
            )
            is UiEvent.AuthFailed -> showResultDialog(success = false, message = event.reason)
            is UiEvent.Info -> Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showResultDialog(success: Boolean, message: String) {
        MaterialAlertDialogBuilder(this)
            .setIcon(if (success) R.drawable.ic_check_circle else R.drawable.ic_error)
            .setTitle(if (success) R.string.auth_success_title else R.string.auth_failed_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
