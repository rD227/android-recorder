package io.github.xvsu.daymark.data

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONException
import java.io.IOException
import java.util.concurrent.Executors

/** Keeps a single request alive across rotation, with the last verse available offline. */
class PoetryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PoetryRepository(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var cleared = false
    var state by mutableStateOf(PoetryUiState(poem = repository.cachedPoem()))
        private set

    init { refresh() }

    fun refresh() {
        if (state.isLoading) return
        state = state.copy(isLoading = true, error = null)
        executor.execute {
            try {
                val poem = repository.fetchPoem()
                handler.post {
                    if (!cleared) state = PoetryUiState(poem = poem)
                }
            } catch (exception: IOException) {
                reportFailure(exception)
            } catch (exception: JSONException) {
                reportFailure(exception)
            }
        }
    }

    private fun reportFailure(exception: Exception) {
        Log.w("DaymarkPoetry", "Unable to load poetry: $exception", exception)
        handler.post {
            if (!cleared) state = state.copy(isLoading = false, error = "暂时无法更新诗词")
        }
    }

    override fun onCleared() {
        cleared = true
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onCleared()
    }
}
