package co.edu.unal.reto10.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.edu.unal.reto10.R
import co.edu.unal.reto10.data.SchoolFilter
import co.edu.unal.reto10.databinding.ActivityResultsBinding
import kotlinx.coroutines.launch

/**
 * Muestra la respuesta del servicio web: total de colegios y lista paginada.
 */
class ResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultsBinding
    private val viewModel: ResultsViewModel by viewModels()

    private val adapter = SchoolAdapter(
        onSchoolClick = { school ->
            startActivity(
                Intent(this, DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_SCHOOL_JSON, school.rawJson)
            )
        },
        onRetryClick = { viewModel.loadNextPage() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val filter = IntentCompat.getSerializableExtra(intent, EXTRA_FILTER, SchoolFilter::class.java)
            ?: SchoolFilter()
        binding.textFilters.text = filter.describe()

        val layoutManager = LinearLayoutManager(this)
        binding.recyclerSchools.layoutManager = layoutManager
        binding.recyclerSchools.adapter = adapter
        binding.recyclerSchools.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Al acercarse al final de la lista se pide la siguiente página.
                // Se difiere con post() para no modificar el adaptador en medio del scroll.
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 5) {
                    recyclerView.post { viewModel.loadNextPage() }
                }
            }
        })

        viewModel.start(filter)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: ResultsState) {
        binding.progress.visibility = if (state.loading) View.VISIBLE else View.GONE

        binding.textCount.text = when {
            state.loading -> getString(R.string.results_title) + "…"
            state.total == 1 -> getString(R.string.results_count_one)
            state.total != null -> getString(R.string.results_count, state.total)
            else -> getString(R.string.results_title)
        }

        val error = state.error
        when {
            error != null -> {
                showMessage(
                    text = when (error) {
                        is ResultsError.Network -> getString(R.string.error_network)
                        is ResultsError.Other -> getString(R.string.error_generic, error.detail)
                    },
                    buttonText = getString(R.string.action_retry),
                    onClick = { viewModel.loadFirstPage() }
                )
                adapter.submit(emptyList(), null)
            }

            !state.loading && state.schools.isEmpty() -> {
                showMessage(
                    text = getString(R.string.results_empty),
                    buttonText = getString(R.string.action_back_to_filters),
                    onClick = { finish() }
                )
                adapter.submit(emptyList(), null)
            }

            else -> {
                binding.layoutMessage.visibility = View.GONE
                val footer = if (state.schools.isEmpty()) null else SchoolAdapter.Footer(
                    text = when {
                        state.loadMoreFailed -> getString(R.string.error_network).lines().first() +
                            " · " + getString(R.string.action_retry)
                        else -> getString(R.string.results_showing, state.schools.size, state.total ?: state.schools.size)
                    },
                    loading = state.loadingMore,
                    retry = state.loadMoreFailed
                )
                adapter.submit(state.schools, footer)
            }
        }
    }

    private fun showMessage(text: String, buttonText: String, onClick: () -> Unit) {
        binding.layoutMessage.visibility = View.VISIBLE
        binding.textMessage.text = text
        binding.buttonMessage.text = buttonText
        binding.buttonMessage.setOnClickListener { onClick() }
    }

    companion object {
        const val EXTRA_FILTER = "extra_filter"
    }
}
