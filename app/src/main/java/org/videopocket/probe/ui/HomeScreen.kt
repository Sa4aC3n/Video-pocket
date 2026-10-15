package org.videopocket.probe.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.videopocket.probe.ProbeModel
import org.videopocket.probe.core.*
import org.videopocket.probe.download.DownloadState
import org.videopocket.probe.download.DownloadTask
import org.videopocket.probe.resolver.MediaType
import org.videopocket.probe.resolver.ResolverErrorType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sharedIntent: String,
    model: ProbeModel,
    arabic: Boolean,
    onToggleLanguage: () -> Unit,
    onNavigateDownloads: () -> Unit,
    onNavigatePocket: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    fun t(en: String, ar: String) = if (arabic) ar else en

    var url by rememberSaveable { mutableStateOf(sharedIntent) }
    var playlistMode by rememberSaveable { mutableStateOf(false) }
    var selectedHeight by remember { mutableIntStateOf(1080) }
    var selectedBitrate by remember { mutableIntStateOf(192) }
    var videoFormat by rememberSaveable { mutableStateOf(AppSettings.defaultVideoFormat) }
    var audioFormat by rememberSaveable { mutableStateOf(AppSettings.defaultAudioFormat) }

    var showSupportedSources by remember { mutableStateOf(false) }
    var showBatchDownload by remember { mutableStateOf(false) }
    var showStorageManager by remember { mutableStateOf(false) }
    var showDownloadOptionsSheet by remember { mutableStateOf(false) }

    var duplicateItemToConfirm by remember { mutableStateOf<PocketLibraryItem?>(null) }
    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var detectedClipboardUrl by remember { mutableStateOf<String?>(null) }
    var dismissedClipboardUrl by remember { mutableStateOf<String?>(null) }
    var urlValidationError by remember { mutableStateOf<String?>(null) }

    // Privacy-conscious smart clipboard detection on view
    LaunchedEffect(Unit) {
        if (AppSettings.smartClipboardEnabled) {
            try {
                val clipText = clipboardManager.getText()?.text?.toString()?.trim()
                if (!clipText.isNullOrBlank() &&
                    (clipText.startsWith("http://") || clipText.startsWith("https://")) &&
                    clipText != url &&
                    clipText != dismissedClipboardUrl
                ) {
                    detectedClipboardUrl = clipText
                }
            } catch (_: Throwable) {}
        }
    }

    // Auto-detect playlist URLs
    LaunchedEffect(url) {
        if (url.contains("list=") || url.contains("/playlist") || url.contains("/sets/")) {
            playlistMode = true
        }
    }

    // Handle shared URL from Android share sheet
    LaunchedEffect(sharedIntent) {
        if (sharedIntent.isNotBlank()) {
            url = sharedIntent.trim()
        }
    }

    // Update height when media is inspected
    LaunchedEffect(model.info) {
        model.info?.let {
            selectedHeight = when (AppSettings.defaultQuality) {
                DefaultQualityPreset.BEST_AVAILABLE -> it.heights.maxOrNull() ?: it.defaultHeight
                DefaultQualityPreset.BALANCED -> it.heights.firstOrNull { h -> h in 720..1080 } ?: it.defaultHeight
                DefaultQualityPreset.SMALLEST_FILE -> it.heights.minOrNull() ?: it.defaultHeight
                DefaultQualityPreset.ASK_EVERY_TIME -> it.defaultHeight
            }
        }
    }

    val triggerDownloadWithDuplicateCheck: (() -> Unit) -> Unit = { action ->
        val media = model.info
        val platform = model.normalizedMetadata?.platform ?: media?.extractor
        val existing = model.libraryItems.firstOrNull {
            MediaIdentity.sameSource(it, model.inspectedUrl, media?.id, platform)
        }
        if (existing != null) {
            duplicateItemToConfirm = existing
            pendingDownloadAction = action
        } else {
            action()
            onNavigateDownloads()
        }
    }

    val performAnalyze = {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            urlValidationError = t("Please enter a link to analyze", "يرجى إدخال رابط للتحليل")
        } else if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            urlValidationError = t("Link must start with http:// or https://", "يجب أن يبدأ الرابط بـ http:// أو https://")
        } else {
            urlValidationError = null
            model.inspect(trimmed, playlistMode)
        }
    }

    val pasteAction = {
        try {
            val clip = clipboardManager.getText()?.text?.toString()?.trim()
            if (!clip.isNullOrBlank()) {
                url = clip
                urlValidationError = null
                model.inspect(url, playlistMode)
            }
        } catch (_: Throwable) {}
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Video Pocket",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = t("Save. Watch. Keep.", "احفظ. شاهد. احتفظ."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
            FilledTonalIconButton(onClick = onToggleLanguage) {
                Icon(Icons.Default.Language, contentDescription = t("Switch Language", "تغيير اللغة"))
            }
        }

        // Quick Shortcuts Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { showSupportedSources = true },
                label = { Text(t("15 Sources", "١٥ منصة مدعومة")) },
                icon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            SuggestionChip(
                onClick = { showBatchDownload = true },
                label = { Text(t("Batch", "تنزيل دفعات")) },
                icon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            SuggestionChip(
                onClick = { showStorageManager = true },
                label = { Text(t("Storage", "التخزين")) },
                icon = { Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        // Smart Clipboard Banner
        detectedClipboardUrl?.let { clipUrl ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("📋 Link detected on clipboard", "📋 تم اكتشاف رابط في الحافظة"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = clipUrl.take(45) + if (clipUrl.length > 45) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            dismissedClipboardUrl = clipUrl
                            detectedClipboardUrl = null
                        }) {
                            Text(t("Dismiss", "تجاهل"))
                        }
                        Button(
                            onClick = {
                                url = clipUrl
                                dismissedClipboardUrl = clipUrl
                                detectedClipboardUrl = null
                                performAnalyze()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(t("Paste & Analyze", "لصق وفحص"))
                        }
                    }
                }
            }
        }

        // Hero URL Input Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = t("Paste video link", "الصق رابط الفيديو"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlValidationError = null
                    },
                    enabled = !model.busy,
                    placeholder = {
                        Text(t("Paste YouTube, TikTok, Instagram, X link...", "ألصق رابط يوتيوب، تيك توك، إنستغرام، إكس..."))
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (url.isNotBlank()) {
                                IconButton(onClick = { url = ""; urlValidationError = null }, enabled = !model.busy) {
                                    Icon(Icons.Default.Clear, contentDescription = t("Clear", "مسح"))
                                }
                            }
                            IconButton(onClick = pasteAction, enabled = !model.busy) {
                                Icon(Icons.Default.ContentPaste, contentDescription = t("Paste", "لصق"))
                            }
                        }
                    },
                    isError = urlValidationError != null,
                    supportingText = urlValidationError?.let { msg -> { Text(msg, color = MaterialTheme.colorScheme.error) } },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = pasteAction,
                        enabled = !model.busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Smart Paste", "لصق ذكي"))
                    }

                    FilterChip(
                        selected = playlistMode,
                        onClick = { playlistMode = !playlistMode },
                        enabled = !model.busy,
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        label = { Text(t("Playlist", "قائمة تشغيل")) }
                    )
                }

                Button(
                    onClick = performAnalyze,
                    enabled = !model.busy && url.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (playlistMode) t("Analyze Playlist", "فحص قائمة التشغيل") else t("Analyze", "تحليل الرابط"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Loading State during Analysis
        if (model.busy && model.info == null) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (model.currentStageText.isNotBlank()) model.currentStageText
                            else t("Analyzing link...", "جارٍ فحص الرابط..."),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { model.cancel() }) {
                            Text(t("Cancel", "إلغاء"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Friendly Error State
        model.problem?.let { prob ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        val titleText = model.resolverError?.let { err ->
                            if (arabic) err.type.defaultArabic else err.type.defaultMessage
                        } ?: problemMessage(prob.name, arabic)
                        Text(
                            text = titleText,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val userMessage = model.resolverError?.userMessage(arabic)
                    if (model.initErrorDetail == NativeRuntimePolicy.PAGE_SIZE_MESSAGE) {
                        Text(t("This device uses a memory page size that the bundled media engine does not support yet.", "يستخدم الجهاز حجم صفحات ذاكرة لا يدعمه محرك الوسائط المرفق حاليًا."))
                    }
                    if (!userMessage.isNullOrBlank()) {
                        Text(
                            text = userMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { model.retryInit(); performAnalyze() }) {
                            Text(t("Retry", "إعادة المحاولة"), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        // Resolved Media Preview Card
        model.info?.let { media ->
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Top: Thumbnail + Metadata
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!media.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = media.thumbnailUrl,
                                contentDescription = media.title,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (media.isPlaylist) Icons.AutoMirrored.Filled.PlaylistPlay else Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val platformName = model.normalizedMetadata?.platform ?: "Web"
                                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(
                                        text = platformName,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (model.normalizedMetadata?.resolvedViaSmartPublicResolution == true) {
                                    Badge(containerColor = Color(0xFF2E7D32).copy(alpha = 0.15f)) {
                                        Text(
                                            text = if (arabic) "عام" else "Public",
                                            color = Color(0xFF2E7D32),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (media.subtitles.isNotEmpty()) {
                                    Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text(
                                            text = "CC (${media.subtitles.size})",
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = media.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = if (media.isPlaylist) "${media.playlistCount} " + t("items in playlist", "عنصراً في القائمة")
                                else (media.duration?.let { ClipRequest.formatSeconds(it) } ?: t("Ready to download", "جاهز للتنزيل")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Quick Download and Custom Options row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                triggerDownloadWithDuplicateCheck {
                                    val action = if (media.isPlaylist) ProbeAction.PLAYLIST_VIDEO else ProbeAction.VIDEO
                                    model.run(action, selectedHeight, selectedBitrate, videoFormat)
                                }
                            },
                            enabled = !model.busy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("Quick Download", "تحميل سريع"), fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showDownloadOptionsSheet = true },
                            enabled = !model.busy,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("Options", "خيارات"))
                        }
                    }

                    HorizontalDivider()

                    // Media Studio Tabs: Video, Audio, Clip
                    var mediaTab by rememberSaveable(media.title) { mutableIntStateOf(0) }
                    val totalDur = media.duration?.takeIf { it.isFinite() && it > 0 } ?: 0.0

                    var clipStart by remember(media.title) { mutableDoubleStateOf(0.0) }
                    var clipEnd by remember(media.title) {
                        mutableDoubleStateOf(if (totalDur > 30.0) 30.0.coerceAtMost(totalDur) else totalDur)
                    }

                    TabRow(
                        selectedTabIndex = mediaTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        indicator = { tabPositions ->
                            if (mediaTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[mediaTab]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    ) {
                        Tab(
                            selected = mediaTab == 0,
                            onClick = { mediaTab = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(t("Video", "فيديو"), fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        Tab(
                            selected = mediaTab == 1,
                            onClick = { mediaTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(t("Audio", "صوت"), fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        Tab(
                            selected = mediaTab == 2,
                            onClick = { mediaTab = 2 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(t("Clip", "قص"), fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }

                    when (mediaTab) {
                        0 -> {
                            // VIDEO TAB
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                PresetQualitySelector(
                                    normalizedMetadata = model.normalizedMetadata,
                                    mediaInfo = media,
                                    selectedHeight = selectedHeight,
                                    onHeightSelected = { selectedHeight = it },
                                    arabic = arabic
                                )

                                Text(t("Format:", "الصيغة:"), fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("mp4", "mkv", "webm").forEach { fmt ->
                                        FilterChip(
                                            selected = videoFormat == fmt,
                                            onClick = { videoFormat = fmt },
                                            label = { Text(fmt.uppercase()) }
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        triggerDownloadWithDuplicateCheck {
                                            model.run(ProbeAction.VIDEO, selectedHeight, selectedBitrate, videoFormat)
                                        }
                                    },
                                    enabled = !model.busy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (selectedHeight > 0) t("Download Video (${selectedHeight}p)", "تنزيل الفيديو (${selectedHeight}p)") else t("Download available video", "تنزيل الفيديو المتاح"))
                                }
                            }
                        }
                        1 -> {
                            // AUDIO TAB
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(t("Output MP3 encoding bitrate (not source quality):", "معدل ترميز ملف MP3 الناتج (وليس جودة المصدر):"), fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(320 to "Convert to 320 kbps", 192 to "Convert to 192 kbps", 128 to "Convert to 128 kbps").forEach { (br, label) ->
                                        FilterChip(
                                            selected = selectedBitrate == br,
                                            onClick = { selectedBitrate = br },
                                            label = { Text(label) }
                                        )
                                    }
                                }

                                Text(t("Audio Output Format:", "صيغة الملف الصوتي الناتج:"), fontWeight = FontWeight.SemiBold)
                                Text(
                                    t("MP3 is currently supported. M4A conversion is not yet implemented.", "صيغة MP3 مدعومة حاليًا. تحويل M4A لم يُنفذ بعد."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        triggerDownloadWithDuplicateCheck {
                                            model.run(ProbeAction.MP3, selectedHeight, selectedBitrate)
                                        }
                                    },
                                    enabled = !model.busy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(t("Convert to MP3 ($selectedBitrate kbps)", "تحويل إلى MP3 ($selectedBitrate كيلوبت/ث)"))
                                }
                            }
                        }
                        2 -> {
                            // Clip controls require an actual duration.
                            if (totalDur <= 0) Text(t("Duration unavailable; use Media Studio after downloading.", "المدة غير متاحة؛ استخدم الاستوديو بعد التنزيل."))
                            else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                DualHandleTimeline(
                                    totalDuration = totalDur,
                                    startSeconds = clipStart,
                                    endSeconds = clipEnd,
                                    onRangeChanged = { s, e ->
                                        clipStart = s
                                        clipEnd = e
                                    },
                                    arabic = arabic
                                )

                                QuickSelectionChips(
                                    totalDuration = totalDur,
                                    onApplyRange = { s, e ->
                                        clipStart = s
                                        clipEnd = e
                                    },
                                    arabic = arabic
                                )

                                Button(
                                    onClick = {
                                        val req = ClipRequest(
                                            startSeconds = clipStart,
                                            endSeconds = clipEnd,
                                            height = selectedHeight,
                                            format = videoFormat,
                                            isAudioOnly = false
                                        )
                                        triggerDownloadWithDuplicateCheck {
                                            model.runClip(req)
                                        }
                                    },
                                    enabled = !model.busy && (clipEnd > clipStart),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ContentCut, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        t("Download Clip (${ClipRequest.formatSeconds(clipEnd - clipStart)})",
                                          "تنزيل المقطع (${ClipRequest.formatSeconds(clipEnd - clipStart)})")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Downloads Section (3-5 items)
        if (model.libraryItems.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("Recent Downloads", "التنزيلات الأخيرة"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onNavigatePocket) {
                            Text(t("View All (${model.libraryItems.size})", "عرض الكل (${model.libraryItems.size})"))
                        }
                    }

                    model.libraryItems.take(4).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { PocketLibraryManager.playItem(context, item) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!item.thumbnailUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.thumbnailUri,
                                    contentDescription = item.title,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (item.isClip) Icons.Default.ContentCut else Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.resolution} • ${item.duration}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            IconButton(onClick = { PocketLibraryManager.playItem(context, item) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = t("Play", "تشغيل"))
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Download Options
    if (showDownloadOptionsSheet && model.info != null) {
        DownloadOptionsBottomSheet(
            media = model.info!!,
            normalized = model.normalizedMetadata,
            initialHeight = selectedHeight,
            initialFormat = videoFormat,
            arabic = arabic,
            onDismiss = { showDownloadOptionsSheet = false },
            onConfirmDownload = { height, fmt ->
                selectedHeight = height
                videoFormat = fmt
                triggerDownloadWithDuplicateCheck {
                    model.run(ProbeAction.VIDEO, height, selectedBitrate, fmt)
                }
            }
        )
    }

    // Duplicate Item Confirmation Dialog
    duplicateItemToConfirm?.let { dupItem ->
        DuplicateConfirmDialog(
            item = dupItem,
            arabic = arabic,
            onOpenExisting = {
                PocketLibraryManager.playItem(context, dupItem)
                duplicateItemToConfirm = null
                pendingDownloadAction = null
            },
            onDownloadAgain = {
                val action = pendingDownloadAction
                duplicateItemToConfirm = null
                pendingDownloadAction = null
                action?.invoke()
                onNavigateDownloads()
            },
            onDismiss = {
                duplicateItemToConfirm = null
                pendingDownloadAction = null
            }
        )
    }

    // Dialogs
    if (showSupportedSources) {
        SupportedSourcesDialog(onDismiss = { showSupportedSources = false }, arabic = arabic)
    }
    if (showBatchDownload) {
        BatchDownloadDialog(
            onDismiss = { showBatchDownload = false },
            queueManager = model.queueManager,
            arabic = arabic
        )
    }
    if (showStorageManager) {
        StorageManagerDialog(onDismiss = { showStorageManager = false }, arabic = arabic)
    }
}
