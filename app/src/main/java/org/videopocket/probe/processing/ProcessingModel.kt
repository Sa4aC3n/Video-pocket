package org.videopocket.probe.processing

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

class ProcessingModel(application: Application) : AndroidViewModel(application) {
    var capabilities by mutableStateOf<ProcessingCapabilities?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var checking by mutableStateOf(false)
        private set
    var update by mutableStateOf(ProcessingUpdate(null, "READY"))
        private set
    var result by mutableStateOf<ProcessingResult?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var processor: LocalMediaProcessor? = null

    fun checkCapabilities() {
        if (capabilities != null || checking || busy) return
        checking = true
        error = null
        val engine = LocalMediaProcessor(getApplication())
        processor = engine
        viewModelScope.launch {
            try { capabilities = withContext(Dispatchers.IO) { engine.capabilities() } }
            catch (failure: Exception) { error = (failure as? ProcessingFailure)?.code ?: "ENGINE_UNAVAILABLE" }
            finally { checking = false; processor = null }
        }
    }
    fun start(request: ProcessingRequest) {
        if (busy || checking) return
        busy = true
        error = null
        result = null
        update = ProcessingUpdate(null, "COPYING")
        val engine = LocalMediaProcessor(getApplication())
        processor = engine
        viewModelScope.launch {
            try {
                result = withContext(Dispatchers.IO + NonCancellable) {
                    engine.run(request) { progress -> viewModelScope.launch { update = progress } }
                }
                update = ProcessingUpdate(100f, "COMPLETE")
            } catch (failure: Exception) {
                error = (failure as? ProcessingFailure)?.code ?: "PROCESSING_FAILED"
            } finally { busy = false; processor = null }
        }
    }
    fun cancel() {
        if (processor?.cancel() == true) update = ProcessingUpdate(null, "CANCELLING")
    }
    override fun onCleared() { processor?.cancel(); super.onCleared() }
}
