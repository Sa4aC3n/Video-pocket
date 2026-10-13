package org.videopocket.probe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.videopocket.probe.ProbeModel
import org.videopocket.probe.core.PocketLibraryManager
import org.videopocket.probe.download.DownloadState
import org.videopocket.probe.download.DownloadTask

@Composable
fun DownloadsScreen(model: ProbeModel, arabic: Boolean) {
    val context = LocalContext.current
    fun t(en: String, ar: String) = if (arabic) ar else en

    var queueFilter by rememberSaveable { mutableStateOf("all") }
    val allTasks = model.queueManager.tasks
    val filteredTasks = remember(allTasks, queueFilter) {
        when (queueFilter) {
            "active" -> allTasks.filter {
                it.state == DownloadState.DOWNLOADING ||
                it.state == DownloadState.MERGING ||
                it.state == DownloadState.TRANSCODING ||
                it.state == DownloadState.SAVING ||
                it.state == DownloadState.RESOLVING
            }
            "completed" -> allTasks.filter { it.state == DownloadState.COMPLETED }
            "failed" -> allTasks.filter { it.state == DownloadState.FAILED || it.state == DownloadState.CANCELLED }
            "paused" -> allTasks.filter { it.state == DownloadState.PAUSED }
            else -> allTasks
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = t("Downloads & Queue", "التنزيلات وقائمة الانتظار"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = t("Real-time download manager", "إدارة التنزيلات الحية والمهام"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (allTasks.any { it.state == DownloadState.COMPLETED }) {
                TextButton(onClick = { model.queueManager.clearCompleted() }) {
                    Text(t("Clear Completed", "مسح المكتمل"))
                }
            }
        }

        // Filter Chips
        if (allTasks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = queueFilter == "all",
                    onClick = { queueFilter = "all" },
                    label = { Text(t("All (${allTasks.size})", "الكل (${allTasks.size})")) }
                )
                FilterChip(
                    selected = queueFilter == "active",
                    onClick = { queueFilter = "active" },
                    label = { Text(t("Active", "النشطة")) }
                )
                FilterChip(
                    selected = queueFilter == "completed",
                    onClick = { queueFilter = "completed" },
                    label = { Text(t("Done", "المكتملة")) }
                )
                FilterChip(
                    selected = queueFilter == "failed",
                    onClick = { queueFilter = "failed" },
                    label = { Text(t("Failed", "فشل")) }
                )
            }

            // Task List
            filteredTasks.forEach { task ->
                DownloadTaskCard(
                    task = task,
                    arabic = arabic,
                    onPause = { model.queueManager.pause(task.id) },
                    onResume = { model.queueManager.resume(task.id) },
                    onCancel = { model.queueManager.cancel(task.id) },
                    onRetry = { model.queueManager.retry(task.id) },
                    onRemove = { model.queueManager.removeTask(task.id) },
                    onPlay = {
                        val libItem = model.libraryItems.firstOrNull { it.title == task.title }
                        if (libItem != null) {
                            PocketLibraryManager.playItem(context, libItem)
                        }
                    },
                    onShare = {
                        val libItem = model.libraryItems.firstOrNull { it.title == task.title }
                        if (libItem != null) {
                            PocketLibraryManager.shareItem(context, libItem)
                        }
                    }
                )
            }
        } else {
            // Empty Queue State
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Text(
                        text = t("No active downloads", "لا توجد تنزيلات نشطة حاليًا"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("Items you download will appear here with live progress.",
                                 "الملفات التي تقوم بتنزيلها ستظهر هنا مع التقدم اللحظي."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    arabic: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit
) {
    fun t(en: String, ar: String) = if (arabic) ar else en

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top row: Title + State badge
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Badge(
                    containerColor = when (task.state) {
                        DownloadState.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                        DownloadState.FAILED, DownloadState.CANCELLED -> MaterialTheme.colorScheme.errorContainer
                        DownloadState.DOWNLOADING, DownloadState.MERGING, DownloadState.TRANSCODING, DownloadState.SAVING -> MaterialTheme.colorScheme.tertiaryContainer
                        DownloadState.PAUSED -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = if (arabic) task.state.arabicLabel else task.state.label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Specs row: Platform, Quality, Format, Clip badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = task.platform,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (task.isAudioOnly) "MP3 Audio" else task.quality,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = task.format.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (task.isClip) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "CLIP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Progress bar and stage text
            if (task.state == DownloadState.DOWNLOADING ||
                task.state == DownloadState.MERGING ||
                task.state == DownloadState.TRANSCODING ||
                task.state == DownloadState.SAVING ||
                task.state == DownloadState.RESOLVING
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (arabic) task.state.arabicLabel else task.state.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (task.progress > 0f) {
                        Text(
                            text = "${task.progress.toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { if (task.progress > 0f) task.progress / 100f else 0f },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                )
            } else if (task.state == DownloadState.COMPLETED) {
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Error info if failed
            if (task.state == DownloadState.FAILED && !task.error.isNullOrBlank()) {
                Text(
                    text = "${t("Error:", "خطأ:")} ${task.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Action buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (task.state) {
                    DownloadState.DOWNLOADING -> {
                        if (task.canPause) {
                            OutlinedButton(
                                onClick = onPause,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(t("Pause", "إيقاف مؤقت"))
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        TextButton(onClick = onCancel) {
                            Text(t("Cancel", "إلغاء"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    DownloadState.PAUSED -> {
                        if (task.canResume) {
                            Button(
                                onClick = onResume,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(t("Resume", "استئناف"))
                            }
                        } else {
                            OutlinedButton(
                                onClick = onRetry,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(t("Restart", "إعادة البدء"))
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = onCancel) {
                            Text(t("Cancel", "إلغاء"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    DownloadState.FAILED, DownloadState.CANCELLED -> {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("Retry", "إعادة المحاولة"))
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Default.Delete, contentDescription = t("Remove", "حذف"))
                        }
                    }
                    DownloadState.COMPLETED -> {
                        Button(
                            onClick = onPlay,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("Play", "تشغيل"))
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = t("Share", "مشاركة"))
                        }
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Default.Close, contentDescription = t("Dismiss", "إزالة"))
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
