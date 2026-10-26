package org.videopocket.probe.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videopocket.probe.core.ClipRequest
import org.videopocket.probe.core.PocketLibraryItem
import org.videopocket.probe.core.AppSettings
import org.videopocket.probe.core.PocketLibraryManager
import org.videopocket.probe.sponsorblock.*
import org.videopocket.probe.download.DownloadQueueManager
import org.videopocket.probe.download.DownloadState
import org.videopocket.probe.download.DownloadTask
import org.videopocket.probe.resolver.*
import org.videopocket.probe.storage.FileNamingEngine
import org.videopocket.probe.storage.StorageBreakdown
import org.videopocket.probe.storage.StorageManager
import org.videopocket.probe.storage.TempFileManager
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportedSourcesDialog(
    onDismiss: () -> Unit,
    arabic: Boolean
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    var searchQuery by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    val providers = remember(searchQuery, refreshKey) {
        ProviderRegistry.getSupportedProviders().filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.supportedDomains.any { d -> d.contains(searchQuery, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = t("Supported Sources", "المنصات والمصادر المدعومة"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = t("Close", "إغلاق"))
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(t("Search platforms...", "ابحث عن منصة...")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(providers, key = { it.id }) { provider ->
                        val health = ProviderHealthManager.getHealth(provider.id)
                        val metrics = ProviderHealthManager.getMetrics(provider.id)
                        val isEnabled = FeatureFlagsManager.isProviderEnabled(provider.id)

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = provider.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = when (health) {
                                                ProviderHealth.AVAILABLE, ProviderHealth.WORKING -> Color(0xFF2E7D32).copy(alpha = 0.2f)
                                                ProviderHealth.DEGRADED -> Color(0xFFF57F17).copy(alpha = 0.2f)
                                                ProviderHealth.TEMPORARILY_BROKEN, ProviderHealth.TEMPORARILY_UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
                                                ProviderHealth.DISABLED -> MaterialTheme.colorScheme.surfaceVariant
                                                ProviderHealth.UNKNOWN -> Color(0xFF1565C0).copy(alpha = 0.2f)
                                            }
                                        ) {
                                            Text(
                                                text = if (arabic) health.arabicLabel else health.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when (health) {
                                                    ProviderHealth.AVAILABLE, ProviderHealth.WORKING -> Color(0xFF2E7D32)
                                                    ProviderHealth.DEGRADED -> Color(0xFFF57F17)
                                                    ProviderHealth.TEMPORARILY_BROKEN, ProviderHealth.TEMPORARILY_UNAVAILABLE -> MaterialTheme.colorScheme.error
                                                    ProviderHealth.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    ProviderHealth.UNKNOWN -> Color(0xFF1565C0)
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        provider.supportedMediaTypes.forEach { type ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            ) {
                                                Text(
                                                    text = if (arabic) type.arabicLabel else type.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (metrics.preventedLoginRequiredCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = if (arabic) "عام: ${metrics.preventedLoginRequiredCount}"
                                                    else "Public: ${metrics.preventedLoginRequiredCount}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF2E7D32),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            metrics.lastStrategy?.let { strat ->
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = if (arabic) strat.arabicLabel else strat.label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { enabled ->
                                        FeatureFlagsManager.setProviderEnabled(provider.id, enabled)
                                        refreshKey++
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchDownloadDialog(
    onDismiss: () -> Unit,
    queueManager: DownloadQueueManager,
    onNavigateDownloads: () -> Unit = {},
    arabic: Boolean
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    var rawUrls by remember { mutableStateOf("") }
    var analyzedUrls by remember { mutableStateOf(listOf<String>()) }
    var isQueued by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("Batch Download", "تنزيل دفعات متعددة"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                Text(
                    text = t("Paste multiple links (one link per line):", "ألصق روابط متعددة (رابط في كل سطر):"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                OutlinedTextField(
                    value = rawUrls,
                    onValueChange = {
                        rawUrls = it
                        analyzedUrls = it.lines().map { l -> l.trim() }.filter { l -> l.startsWith("http://", true) || l.startsWith("https://", true) }
                    },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("https://...\nhttps://...") }
                )

                Text(
                    text = t("Detected Links: ", "الروابط المكتشفة: ") + "${analyzedUrls.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (isQueued) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = t("✓ Added to Download Queue!", "✓ تمت الإضافة إلى قائمة التنزيلات!"),
                            color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                onDismiss()
                                onNavigateDownloads()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("View in Downloads", "عرض في قائمة التنزيلات"))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(t("Close", "إغلاق"))
                    }
                    Button(
                        onClick = {
                            analyzedUrls.forEach { url ->
                                val provider = ProviderRegistry.findProvider(url)
                                val task = DownloadTask(
                                    url = url,
                                    title = "Batch Media (${provider.name})",
                                    platform = provider.name,
                                    quality = "Best available",
                                    height = -1,
                                    format = AppSettings.defaultVideoFormat
                                )
                                queueManager.enqueue(task)
                            }
                            isQueued = true
                        },
                        enabled = analyzedUrls.isNotEmpty() && !isQueued,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Download All", "تنزيل الكل"))
                    }
                }
            }
        }
    }
}

@Composable
fun StorageManagerDialog(
    onDismiss: () -> Unit,
    arabic: Boolean
) {
    val context = LocalContext.current
    fun t(en: String, ar: String) = if (arabic) ar else en
    var breakdown by remember { mutableStateOf(org.videopocket.probe.storage.StorageBreakdown(0, 0, 0, 0, 0, 0)) }
    var freedStatus by remember { mutableStateOf("") }
    var storageBusy by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { breakdown = withContext(Dispatchers.IO) { StorageManager.getBreakdown(context) } }
        catch (_: Exception) { freedStatus = t("Unable to read storage usage.", "تعذر قراءة مساحة التخزين.") }
        finally { storageBusy = false }
    }
    fun cleanStorage(cache: Boolean) {
        if (storageBusy) return
        storageBusy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val freed = if (cache) StorageManager.clearCache(context) else TempFileManager.cleanupTemporaryFiles(context)
                    freed to StorageManager.getBreakdown(context)
                }
                breakdown = result.second
                freedStatus = t("Freed ${breakdown.formatBytes(result.first)}", "تم تحرير ${breakdown.formatBytes(result.first)}")
            } catch (_: Exception) { freedStatus = t("Storage operation failed; try again.", "تعذرت عملية التخزين؛ حاول مجددًا.") }
            finally { storageBusy = false }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("Storage Manager", "إدارة التخزين"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                if (storageBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageItemRow(t("Downloaded Videos", "مقاطع الفيديو"), breakdown.formatBytes(breakdown.videoBytes))
                    StorageItemRow(t("Audio Files", "الملفات الصوتية"), breakdown.formatBytes(breakdown.audioBytes))
                    StorageItemRow(t("Saved Clips", "المقاطع المقصوصة"), breakdown.formatBytes(breakdown.clipBytes))
                    StorageItemRow(t("App Cache", "الذاكرة المؤقتة"), breakdown.formatBytes(breakdown.cacheBytes))
                    StorageItemRow(t("Temporary Files", "الملفات المؤقتة"), breakdown.formatBytes(breakdown.tempBytes))
                    HorizontalDivider()
                    StorageItemRow(t("Indexed media and temporary files", "الوسائط المفهرسة والملفات المؤقتة"), breakdown.formatBytes(breakdown.totalBytes), isBold = true)
                }

                if (freedStatus.isNotBlank()) {
                    Text(
                        text = freedStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { cleanStorage(false) },
                        enabled = !storageBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(t("Clean Temp", "مسح المؤقت"))
                    }
                    Button(
                        onClick = { cleanStorage(true) },
                        enabled = !storageBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(t("Clear Cache", "مسح الكاش"))
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageItemRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SubtitleCenterDialog(
    title: String,
    subtitles: List<MediaSubtitle>,
    onDismiss: () -> Unit,
    arabic: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun t(en: String, ar: String) = if (arabic) ar else en
    var exportStatus by remember { mutableStateOf("") }
    var exportingLang by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("Subtitle Center", "مركز الترجمة"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                if (subtitles.isEmpty()) {
                    Text(
                        text = t("No subtitle tracks detected for this media.", "لم يتم العثور على مسارات ترجمة لهذه الوسائط."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(subtitles) { sub ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${sub.languageName} (${sub.languageCode.uppercase()})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (sub.isAutoGenerated) {
                                            Text(
                                                text = t("Auto-generated", "توليد تلقائي"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilledTonalButton(
                                            enabled = exportingLang == null && sub.format.lowercase() in setOf("srt", "vtt"),
                                            onClick = {
                                                exportingLang = sub.languageCode
                                                scope.launch {
                                                    val res = SubtitleCenter.exportSubtitle(context, title, sub, "srt")
                                                    exportingLang = null
                                                    exportStatus = if (res.isSuccess) t("Saved SRT to Downloads ✓", "تم حفظ ملف SRT في التنزيلات ✓")
                                                    else t("Failed to save SRT", "فشل حفظ ملف SRT")
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("SRT", style = MaterialTheme.typography.labelSmall)
                                        }
                                        FilledTonalButton(
                                            enabled = exportingLang == null && sub.format.lowercase() in setOf("srt", "vtt"),
                                            onClick = {
                                                exportingLang = sub.languageCode
                                                scope.launch {
                                                    val res = SubtitleCenter.exportSubtitle(context, title, sub, "vtt")
                                                    exportingLang = null
                                                    exportStatus = if (res.isSuccess) t("Saved VTT to Downloads ✓", "تم حفظ ملف VTT في التنزيلات ✓")
                                                    else t("Failed to save VTT", "فشل حفظ ملف VTT")
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("VTT", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (exportStatus.isNotBlank()) {
                    Text(
                        text = exportStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun BuiltInPlayerDialog(
    item: PocketLibraryItem,
    onDismiss: () -> Unit,
    arabic: Boolean
) {
    val context = LocalContext.current
    fun t(en: String, ar: String) = if (arabic) ar else en
    var isPlaying by remember { mutableStateOf(true) }
    var currentSpeed by remember { mutableFloatStateOf(1f) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    val sponsorSegments = remember { mutableStateListOf<SponsorSegment>() }
    var autoSkipSponsors by remember { mutableStateOf(AppSettings.sponsorBlockEnabled) }
    var skippedBannerText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item) {
        val candidateId = item.sourceMediaId ?: SponsorBlockClient.extractVideoId(item.sourceUrl ?: item.title)
        if (candidateId != null) {
            val list = SponsorBlockClient.getSegments(candidateId)
            sponsorSegments.clear()
            sponsorSegments.addAll(list)
        }
    }

    LaunchedEffect(isPlaying, autoSkipSponsors, sponsorSegments.size) {
        if (!autoSkipSponsors || sponsorSegments.isEmpty()) return@LaunchedEffect
        while (isPlaying) {
            delay(400)
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    val currentSec = vv.currentPosition / 1000.0
                    val seg = SponsorSegment.findSegmentToSkip(currentSec, sponsorSegments)
                    if (seg != null) {
                        val skipToMs = (seg.end * 1000).toInt() + 150
                        vv.seekTo(skipToMs)
                        val catLabel = SponsorSegment.categoryLabel(seg.category, arabic)
                        val range = "${ClipRequest.formatSeconds(seg.start)} → ${ClipRequest.formatSeconds(seg.end)}"
                        skippedBannerText = if (arabic) "⏭️ تم تخطي $catLabel ($range)" else "⏭️ Skipped $catLabel ($range)"
                    }
                }
            }
        }
    }

    LaunchedEffect(skippedBannerText) {
        if (skippedBannerText != null) {
            delay(3500)
            skippedBannerText = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = {
                        val activity = context as? Activity
                        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val params = PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                            activity.enterPictureInPictureMode(params)
                        }
                    }) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                    }
                    IconButton(onClick = { PocketLibraryManager.shareItem(context, item) }) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.parse(item.uriString))
                                setOnPreparedListener { mp ->
                                    mediaPlayerRef = mp
                                    mp.isLooping = true
                                    start()
                                    isPlaying = true
                                }
                                videoViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (skippedBannerText != null) {
                        Card(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.FastForward, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                Text(skippedBannerText!!, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Controls
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (sponsorSegments.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FilterChip(
                                selected = autoSkipSponsors,
                                onClick = { autoSkipSponsors = !autoSkipSponsors },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (autoSkipSponsors) Icons.Default.FastForward else Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (autoSkipSponsors) Color(0xFF38BDF8) else Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        if (arabic) "تخطي الرعايات تلقائياً (${sponsorSegments.size})" else "SponsorBlock (${sponsorSegments.size} segments)",
                                        color = if (autoSkipSponsors) Color.White else Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = if (autoSkipSponsors) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1E293B),
                                    containerColor = Color(0xFF1E1E1E)
                                )
                            )
                        }
                    }
                    // Playback speed chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            FilterChip(
                                selected = currentSpeed == speed,
                                onClick = {
                                    currentSpeed = speed
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        mediaPlayerRef?.let { mp ->
                                            try {
                                                mp.playbackParams = mp.playbackParams.setSpeed(speed)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                label = { Text("${speed}x", color = if (currentSpeed == speed) Color.White else Color.LightGray) },
                                modifier = Modifier.padding(horizontal = 4.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    containerColor = Color(0xFF2C2C2C)
                                )
                            )
                        }
                    }

                    // Seek & Play/Pause buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            videoViewRef?.let { vv ->
                                val target = (vv.currentPosition - 10000).coerceAtLeast(0)
                                vv.seekTo(target)
                            }
                        }) {
                            Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        FilledIconButton(
                            onClick = {
                                videoViewRef?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        IconButton(onClick = {
                            videoViewRef?.let { vv ->
                                val target = (vv.currentPosition + 10000).coerceAtMost(vv.duration)
                                vv.seekTo(target)
                            }
                        }) {
                            Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionsDialog(
    item: PocketLibraryItem,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit,
    arabic: Boolean
) {
    val context = LocalContext.current
    fun t(en: String, ar: String) = if (arabic) ar else en
    val predefinedCollections = listOf("Music", "Tutorials", "Travel", "Funny", "Study", "Watch Later")
    var selectedCollections by remember { mutableStateOf(item.collections.toSet()) }
    var newCollectionName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("Add to Collection", "إضافة إلى مجموعة"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val allCollections = (predefinedCollections + selectedCollections).distinct()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allCollections.forEach { col ->
                        val isSelected = selectedCollections.contains(col)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedCollections = if (isSelected) selectedCollections - col else selectedCollections + col
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(col, style = MaterialTheme.typography.bodyMedium)
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { chk ->
                                    selectedCollections = if (chk) selectedCollections + col else selectedCollections - col
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCollectionName,
                        onValueChange = { newCollectionName = it },
                        placeholder = { Text(t("New collection...", "مجموعة جديدة...")) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            if (newCollectionName.isNotBlank()) {
                                selectedCollections = selectedCollections + newCollectionName.trim()
                                newCollectionName = ""
                            }
                        },
                        enabled = newCollectionName.isNotBlank()
                    ) {
                        Text(t("Add", "إضافة"))
                    }
                }

                Button(
                    onClick = {
                        PocketLibraryManager.updateCollections(context, item.id, selectedCollections.toList())
                        onUpdated()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(t("Save Collections", "حفظ المجموعات"))
                }
            }
        }
    }
}
