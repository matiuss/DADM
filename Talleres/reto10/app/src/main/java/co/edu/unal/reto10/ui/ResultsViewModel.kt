package co.edu.unal.reto10.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unal.reto10.data.ApiException
import co.edu.unal.reto10.data.School
import co.edu.unal.reto10.data.SchoolFilter
import co.edu.unal.reto10.data.SchoolRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.IOException

/** Estado de la pantalla de resultados. */
data class ResultsState(
    val schools: List<School> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: ResultsError? = null,
    val loadMoreFailed: Boolean = false
)

sealed class ResultsError {
    object Network : ResultsError()
    data class Other(val detail: String) : ResultsError()
}

/**
 * Consulta el servicio web y mantiene los resultados al rotar la pantalla.
 * Carga por páginas de [SchoolRepository.PAGE_SIZE] registros usando $limit y $offset.
 */
class ResultsViewModel : ViewModel() {

    private val _state = MutableStateFlow(ResultsState())
    val state: StateFlow<ResultsState> = _state

    private var filter: SchoolFilter? = null

    fun start(filter: SchoolFilter) {
        if (this.filter != null) return
        this.filter = filter
        loadFirstPage()
    }

    fun loadFirstPage() {
        val filter = filter ?: return
        _state.value = ResultsState(loading = true)
        viewModelScope.launch {
            try {
                // El conteo total y la primera página se piden en paralelo.
                // coroutineScope agrupa ambas peticiones: si una falla (por ejemplo, sin internet)
                // la excepción se lanza aquí y la atrapa el catch, en lugar de cerrar la app.
                val (total, page) = coroutineScope {
                    val totalDeferred = async { SchoolRepository.count(filter) }
                    val pageDeferred = async { SchoolRepository.search(filter, offset = 0) }
                    totalDeferred.await() to pageDeferred.await()
                }
                _state.value = ResultsState(
                    schools = page,
                    total = total,
                    endReached = page.size >= total || page.size < SchoolRepository.PAGE_SIZE
                )
            } catch (e: Exception) {
                _state.value = ResultsState(error = e.toResultsError())
            }
        }
    }

    fun loadNextPage() {
        val filter = filter ?: return
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached || current.error != null) return

        _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        viewModelScope.launch {
            try {
                val page = SchoolRepository.search(filter, offset = current.schools.size)
                _state.update {
                    val all = it.schools + page
                    it.copy(
                        schools = all,
                        loadingMore = false,
                        endReached = page.size < SchoolRepository.PAGE_SIZE || all.size >= (it.total ?: Int.MAX_VALUE)
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    private fun Exception.toResultsError(): ResultsError = when (this) {
        is ApiException -> ResultsError.Other(message.orEmpty())
        is IOException -> ResultsError.Network
        is JSONException -> ResultsError.Other("Respuesta JSON inválida: $message")
        else -> ResultsError.Other(message ?: javaClass.simpleName)
    }
}
