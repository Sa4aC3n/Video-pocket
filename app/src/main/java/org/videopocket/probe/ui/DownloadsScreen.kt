package org.videopocket.probe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var filter by rememberSaveable { mutableStateOf("all") }
    val all = model.queueManager.tasks
    val rows = when (filter) {
        "active" -> model.queueManager.activeTasks
        "completed" -> model.queueManager.completedTasks
        "failed" -> model.queueManager.failedTasks
        "waiting" -> model.queueManager.queuedTasks
        else -> all
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(t("Downloads & queue", "التنزيلات وقائمة الانتظار"), style = MaterialTheme.typography.headlineMedium)
            Text(t("Interrupted downloads can be restarted. Keep the app open for reliable processing.", "يمكن إعادة بدء التنزيلات المتوقفة. أبقِ التطبيق مفتوحًا لضمان استمرار المعالجة."))
            if (all.any { it.state == DownloadState.COMPLETED }) TextButton(onClick = model.queueManager::clearCompleted) { Text(t("Clear completed", "مسح المكتمل")) }
            if (model.queueManager.persistenceError) Text(t("Queue history could not be saved or read. Existing history is retained.", "تعذر حفظ سجل الانتظار أو قراءته. تم الاحتفاظ بالسجل الموجود."), color = MaterialTheme.colorScheme.error)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to t("All", "الكل"), "active" to t("Active", "النشطة"), "waiting" to t("Waiting", "الانتظار"), "completed" to t("Done", "المكتملة"), "failed" to t("Retry", "إعادة المحاولة")).forEach { (key, label) ->
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                }
            }
        }
        if (rows.isEmpty()) item { Text(t("No downloads in this view.", "لا توجد تنزيلات في هذا العرض.")) }
        items(rows, key = { it.id }) { task ->
            DownloadTaskCard(task, arabic,
                onPause = { model.queueManager.pause(task.id) }, onResume = { model.queueManager.resume(task.id) },
                onCancel = { model.queueManager.cancel(task.id) }, onRetry = { model.queueManager.retry(task.id) },
                onRemove = { model.queueManager.removeTask(task.id) },
                onPlay = { model.libraryItems.firstOrNull { it.uriString == task.outputUri }?.let { PocketLibraryManager.playItem(context, it) } },
                onShare = { model.libraryItems.firstOrNull { it.uriString == task.outputUri }?.let { PocketLibraryManager.shareItem(context, it) } })
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
                if (task.progress < 0f) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { task.progress / 100f }, modifier = Modifier.fillMaxWidth())
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
                    DownloadState.DOWNLOADING, DownloadState.RESOLVING, DownloadState.MERGING, DownloadState.TRANSCODING -> {
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
                        if (task.state == DownloadState.PAUSED) {
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
