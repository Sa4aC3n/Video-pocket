package org.videopocket.probe.processing

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.videopocket.probe.core.AppSettings
import org.videopocket.probe.download.ServiceController
import org.videopocket.probe.engine.PocketNotifier

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
        // Acquire foreground service so processing continues if user leaves the screen
        ServiceController.acquire(getApplication())
        viewModelScope.launch {
            try {
                result = withContext(Dispatchers.IO + NonCancellable) {
                    engine.run(request) { progress ->
                        viewModelScope.launch {
                            update = progress
                            // Post a processing notification so the user can track progress
                            val percent = progress.percent?.toInt() ?: -1
                            val stageLabel = when (progress.stage) {
                                "COPYING" -> if (AppSettings.isArabic) "نسخ الملف…" else "Copying file…"
                                "ENCODING" -> if (AppSettings.isArabic) "ترميز الفيديو…" else "Encoding…"
                                "OPTIMIZING" -> if (AppSettings.isArabic) "تحسين الجودة…" else "Optimizing…"
                                "VALIDATING" -> if (AppSettings.isArabic) "التحقق من المخرجات…" else "Validating…"
                                "SAVING" -> if (AppSettings.isArabic) "حفظ في المكتبة…" else "Saving…"
                                else -> progress.stage
                            }
                            val notifTitle = if (AppSettings.isArabic) "معالجة الوسائط" else "Processing media"
                            PocketNotifier.showProcessingProgress(getApplication(), notifTitle, percent, stageLabel)
                        }
                    }
                }
                update = ProcessingUpdate(100f, "COMPLETE")
                PocketNotifier.showProcessingSuccess(
                    getApplication(),
                    if (AppSettings.isArabic) "اكتملت المعالجة" else "Processing complete",
                    request.title
                )
            } catch (failure: Exception) {
                error = (failure as? ProcessingFailure)?.code ?: "PROCESSING_FAILED"
                PocketNotifier.dismissProcessing(getApplication())
            } finally {
                busy = false
                processor = null
                ServiceController.release(getApplication())
            }
        }
    }

    fun cancel() {
        if (processor?.cancel() == true) {
            update = ProcessingUpdate(null, "CANCELLING")
            PocketNotifier.dismissProcessing(getApplication())
        }
    }

    override fun onCleared() {
        processor?.cancel()
        // If the ViewModel is cleared while processing, release the service
        if (busy) ServiceController.release(getApplication())
        super.onCleared()
    }
}
