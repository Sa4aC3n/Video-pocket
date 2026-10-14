package org.videopocket.probe

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.videopocket.probe.core.*
import org.videopocket.probe.download.*
import org.videopocket.probe.engine.PocketNotifier
import org.videopocket.probe.engine.ProbeEngine
import org.videopocket.probe.resolver.*
import org.videopocket.probe.ui.*
import java.io.File
import java.time.Instant
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf("")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.init(this)
        FeatureFlagsManager.init(this)

        sharedText = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else {
            ""
        }

        setContent {
            VideoPocketTheme(themeMode = AppSettings.themeMode) {
                MainContainer(sharedIntent = sharedText)
            }
        }
    }
}

class ProbeModel(application: Application) : AndroidViewModel(application) {
    private val engine = ProbeEngine(application)
    val queueManager = DownloadQueueManager(application, engine)

    var ready by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var info by mutableStateOf<MediaInfo?>(null)
        private set
    var normalizedMetadata by mutableStateOf<NormalizedMetadata?>(null)
        private set
    var resolverError by mutableStateOf<ResolverError?>(null)
        private set
    var problem by mutableStateOf<Problem?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var currentStageText by mutableStateOf("")
        private set
    var results by mutableStateOf(listOf<ProbeResult>())
        private set
    var libraryItems by mutableStateOf(listOf<PocketLibraryItem>())
        private set
    var runtime by mutableStateOf(JSONObject())
        private set
    var initErrorDetail by mutableStateOf<String?>(null)
        private set
    var inspectedUrl: String? = null
        private set

    private val reportFile get() = File(getApplication<Application>().filesDir, "last-report.json")

    init {
        viewModelScope.launch {
            PocketLibraryManager.revisions.collectLatest { refreshLibrary() }
        }
        val cached = ProbeEngine.cachedRuntime
        if (cached != null) {
            runtime = cached
            ready = true
        }
    }

    suspend fun ensureEngineReady(): Boolean {
        if (ready || ProbeEngine.isReady) {
            if (!ready && ProbeEngine.cachedRuntime != null) {
                runtime = ProbeEngine.cachedRuntime!!
                ready = true
            }
            return true
        }
        currentStageText = "Initializing engine..."
        return withContext(Dispatchers.IO) {
            try {
                runtime = engine.initialize()
                ready = true
                true
            } catch (e: Exception) {
                problem = ProbeEngine.classify(e)
                initErrorDetail = e.message ?: e.javaClass.simpleName
                record(ProbeResult(ProbeAction.INSPECT, 0, 0, null, false, "Initialization: ${problem!!.name} - $initErrorDetail"))
                false
            }
        }
    }

    var libraryError by mutableStateOf(false)
        private set
    fun refreshLibrary() {
        viewModelScope.launch {
            try {
                libraryItems = withContext(Dispatchers.IO) { PocketLibraryManager.getItems(getApplication()) }
                libraryError = false
            } catch (_: Exception) { libraryError = true }
        }
    }
    override fun onCleared() {
        engine.cancelCurrent()
        queueManager.close()
        super.onCleared()
    }


    var libraryActionError by mutableStateOf(false)
        private set
    private fun mutateLibrary(action: () -> Unit) {
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() }; libraryActionError = false; refreshLibrary() }
            catch (_: Exception) { libraryActionError = true }
        }
    }
    fun deleteLibraryItem(id: String) = mutateLibrary { PocketLibraryManager.deleteItem(getApplication(), id) }
    fun renameLibraryItem(id: String, title: String) = mutateLibrary { PocketLibraryManager.renameItem(getApplication(), id, title.trim()) }
    fun toggleFavorite(id: String) = mutateLibrary { PocketLibraryManager.toggleFavorite(getApplication(), id) }
    fun updateCollections(id: String, collections: List<String>) = mutateLibrary { PocketLibraryManager.updateCollections(getApplication(), id, collections) }

    fun cancel() {
        if (!busy) return
        if (engine.cancelCurrent()) {
            PocketNotifier.dismiss(getApplication())
            currentStageText = if (AppSettings.isArabic) "جاري الإلغاء…" else "Cancelling…"
            problem = Problem.CANCELED
        }
    }

    fun retryInit() {
        if (busy || ready) return
        val cached = ProbeEngine.cachedRuntime
        if (cached != null) {
            runtime = cached
            ready = true
            return
        }
        busy = true
        problem = null
        initErrorDetail = null
        viewModelScope.launch {
            try {
                runtime = withContext(Dispatchers.IO) { engine.initialize() }
                ready = true
            } catch (e: Exception) {
                problem = ProbeEngine.classify(e)
                initErrorDetail = e.message ?: e.javaClass.simpleName
                record(ProbeResult(ProbeAction.INSPECT, 0, 0, null, false, "Initialization: ${problem!!.name} - $initErrorDetail"))
            } finally {
                busy = false
            }
        }
    }

    fun inspect(url: String, forcePlaylist: Boolean = false) {
        if (busy) return
        busy = true
        problem = null
        info = null
        normalizedMetadata = null
        resolverError = null
        inspectedUrl = null
        currentStageText = "Inspecting link..."

        viewModelScope.launch {
            if (!ensureEngineReady()) {
                busy = false
                currentStageText = ""
                return@launch
            }
            val started = System.nanoTime()
            try {
                val valid = LinkPolicy.validate(url)
                val resolveRes = withContext(Dispatchers.IO) {
                    ProviderRegistry.resolve(valid, engine)
                }
                when (resolveRes) {
                    is MediaResolverResult.Success -> {
                        normalizedMetadata = resolveRes.metadata
                        info = resolveRes.metadata.rawInfo ?: withContext(Dispatchers.IO) { engine.inspect(valid, forcePlaylist) }
                        inspectedUrl = valid
                        val detailMsg = if (info!!.isPlaylist) "Playlist inspected; entries=${info!!.playlistCount}" else "Metadata extracted; formats=${info!!.formats.size}"
                        record(ProbeResult(ProbeAction.INSPECT, (System.nanoTime() - started) / 1_000_000, 0, null, true, detailMsg))
                    }
                    is MediaResolverResult.Failure -> {
                        resolverError = resolveRes.error
                        problem = when (resolveRes.error.type) {
                            ResolverErrorType.INVALID_URL -> Problem.INVALID_URL
                            ResolverErrorType.UNSUPPORTED_SOURCE -> Problem.UNSUPPORTED
                            ResolverErrorType.MEDIA_NOT_FOUND, ResolverErrorType.PUBLIC_MEDIA_UNAVAILABLE -> Problem.REMOVED
                            ResolverErrorType.PRIVATE_MEDIA, ResolverErrorType.LOGIN_REQUIRED -> Problem.LOGIN_REQUIRED
                            ResolverErrorType.GEO_RESTRICTED, ResolverErrorType.RATE_LIMITED, ResolverErrorType.ANTI_BOT_CHALLENGE -> Problem.RESTRICTED
                            ResolverErrorType.DRM_PROTECTED -> Problem.UNSUPPORTED
                            ResolverErrorType.NETWORK_ERROR, ResolverErrorType.TIMEOUT -> Problem.NETWORK
                            ResolverErrorType.CONVERSION_FAILED -> Problem.CONVERSION
                            ResolverErrorType.INSUFFICIENT_STORAGE -> Problem.SPACE
                            ResolverErrorType.CANCELLED -> Problem.CANCELED
                            else -> Problem.ENGINE
                        }
                        record(ProbeResult(ProbeAction.INSPECT, (System.nanoTime() - started) / 1_000_000, 0, null, false, problem!!.name))
                    }
                }
            } catch (e: Exception) {
                problem = ProbeEngine.classify(e)
                record(ProbeResult(ProbeAction.INSPECT, (System.nanoTime() - started) / 1_000_000, 0, null, false, problem!!.name))
            } finally {
                busy = false
                currentStageText = ""
            }
        }
    }

    fun run(action: ProbeAction, height: Int, bitrate: Int, outputFormat: String = AppSettings.defaultVideoFormat) {
        if (busy || !ready) return
        val media = info ?: return
        val url = inspectedUrl ?: return
        busy = true
        problem = null
        progress = 0f
        currentStageText = "Downloading..."
        PocketNotifier.showProgress(getApplication(), percent = 0, stageText = currentStageText, title = media.title)

        viewModelScope.launch {
            val started = System.nanoTime()
            try {
                val result = withContext(Dispatchers.IO) {
                    engine.downloadWithFormat(url, media, action, height, bitrate, outputFormat) { p ->
                        val pInt = p.toInt()
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            progress = p
                            currentStageText = if (p < 0) "Processing & saving…" else "Downloading ($pInt%)"
                        }
                        PocketNotifier.showProgress(getApplication(), percent = pInt, stageText = "Downloading ($pInt%)", title = media.title)
                    }
                }
                record(result)
                if (!result.passed || result.outputUri.isNullOrBlank()) {
                    problem = Problem.ENGINE
                    PocketNotifier.dismiss(getApplication())
                    return@launch
                }
                withContext(Dispatchers.IO + NonCancellable) {
                    LibraryIndexer.index(getApplication(), result, media, url, normalizedMetadata?.platform)
                }
                refreshLibrary()
                PocketNotifier.showSuccess(getApplication(), title = "Download Complete ✓", message = media.title, uriString = result.outputUri)
            } catch (e: Exception) {
                problem = ProbeEngine.classify(e)
                PocketNotifier.dismiss(getApplication())
                record(ProbeResult(action, (System.nanoTime() - started) / 1_000_000, 0, null, false, problem!!.name))
            } finally {
                busy = false
                currentStageText = ""
            }
        }
    }

    fun runClip(clip: ClipRequest) {
        if (busy || !ready) return
        val media = info ?: return
        val url = inspectedUrl ?: return
        busy = true
        problem = null
        progress = 0f
        currentStageText = "Preparing clip download..."
        PocketNotifier.showProgress(getApplication(), percent = 0, stageText = currentStageText, title = media.title)

        viewModelScope.launch {
            val started = System.nanoTime()
            try {
                val result = withContext(Dispatchers.IO) {
                    engine.downloadClip(url, media, clip) { p, stage ->
                        val pInt = p.toInt()
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            progress = p
                            currentStageText = stage
                        }
                        PocketNotifier.showProgress(getApplication(), percent = pInt, stageText = stage, title = media.title)
                    }
                }
                record(result)
                if (!result.passed || result.outputUri.isNullOrBlank()) {
                    problem = Problem.ENGINE
                    PocketNotifier.dismiss(getApplication())
                    return@launch
                }
                withContext(Dispatchers.IO + NonCancellable) {
                    LibraryIndexer.index(getApplication(), result, media, url, normalizedMetadata?.platform, clip)
                }
                refreshLibrary()
                PocketNotifier.showSuccess(getApplication(), title = "Clip Ready ✓", message = "${media.title} (${clip.durationFormatted()})", uriString = result.outputUri)
            } catch (e: Exception) {
                problem = ProbeEngine.classify(e)
                PocketNotifier.dismiss(getApplication())
                record(ProbeResult(ProbeAction.CLIP, (System.nanoTime() - started) / 1_000_000, 0, null, false, problem!!.name))
            } finally {
                busy = false
                currentStageText = ""
            }
        }
    }

    private suspend fun record(result: ProbeResult) {
        results = results + result
        val text = report()
        withContext(Dispatchers.IO) { runCatching { reportFile.writeText(text) } }
    }

    fun report(): String = JSONObject().put("app", "VideoPocket 2.0")
        .put("generatedAt", Instant.now().toString()).put("runtime", runtime)
        .put("scope", "Foreground V2 experience; secure downloads and media management")
        .put("results", JSONArray(results.map { r ->
            JSONObject()
                .put("action", r.action.name)
                .put("passed", r.passed)
                .put("elapsedMs", r.elapsedMs)
                .put("bytes", r.bytes)
                .put("detail", r.detail)
        })).toString(2)

    fun export(uri: Uri, onDone: (Boolean) -> Unit) {
        val text = report()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray())
                    } ?: error("Unavailable")
                }.isSuccess
            }
            onDone(ok)
        }
    }
}

@Composable
fun MainContainer(sharedIntent: String, model: ProbeModel = viewModel()) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var processingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var processingTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val arabic = AppSettings.isArabic
    fun t(en: String, ar: String) = if (arabic) ar else en
    val destinations = listOf(
        Triple(0, Icons.Default.Home, t("Home", "الرئيسية")),
        Triple(1, Icons.Default.Download, t("Downloads", "التنزيلات")),
        Triple(2, Icons.Default.VideoLibrary, t("Pocket", "مكتبتي")),
        Triple(4, Icons.Default.Tune, t("Studio", "استوديو")),
        Triple(3, Icons.Default.Settings, t("Settings", "الإعدادات"))
    )
    CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 840.dp
            Scaffold(contentWindowInsets = WindowInsets.systemBars, bottomBar = {
                if (!expanded) NavigationBar {
                    destinations.forEach { (id, icon, label) ->
                        NavigationBarItem(selected = selectedTab == id, onClick = { selectedTab = id },
                            icon = { Icon(icon, contentDescription = null) }, label = { Text(label, maxLines = 1) })
                    }
                }
            }) { insets ->
                Row(Modifier.padding(insets).fillMaxSize()) {
                    if (expanded) NavigationRail {
                        destinations.forEach { (id, icon, label) ->
                            NavigationRailItem(selected = selectedTab == id, onClick = { selectedTab = id },
                                icon = { Icon(icon, contentDescription = null) }, label = { Text(label) })
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 1000.dp).fillMaxSize()) {
                            when (selectedTab) {
                                0 -> HomeScreen(sharedIntent, model, arabic,
                                    onToggleLanguage = { AppSettings.setLanguage(!arabic) },
                                    onNavigateDownloads = { selectedTab = 1 }, onNavigatePocket = { selectedTab = 2 })
                                1 -> DownloadsScreen(model, arabic)
                                2 -> LibraryScreen(model, arabic, onNavigateHome = { selectedTab = 0 }, onProcess = {
                                    processingUri = it.uriString; processingTitle = it.title; selectedTab = 4
                                })
                                4 -> ProcessingScreen(arabic, processingUri, processingTitle)
                                3 -> SettingsScreen(model, arabic, onToggleLanguage = { AppSettings.setLanguage(!arabic) })
                            }
                        }
                    }
                }
            }
        }
    }
}
