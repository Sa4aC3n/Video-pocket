package org.videopocket.probe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.videopocket.probe.idm.*
import org.videopocket.probe.torrent.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdmScreen(arabic: Boolean) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val idmEngine = remember { IdmEngine(context) }
    val torrentEngine = remember { TorrentEngine(context) }

    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) } // 0: IDM, 1: BitTorrent

    // ── IDM State ──────────────────────────────────────────────────────────
    var idmUrlInput by rememberSaveable { mutableStateOf("") }
    var idmInspecting by remember { mutableStateOf(false) }
    var idmInspectedTask by remember { mutableStateOf<IdmTask?>(null) }
    var idmSegmentsChoice by rememberSaveable { mutableIntStateOf(4) }
    val idmTasks = remember { mutableStateListOf<IdmTask>() }

    // ── Torrent State ──────────────────────────────────────────────────────
    var torrentInput by rememberSaveable { mutableStateOf("") }
    val torrentTasks = remember { mutableStateListOf<TorrentTask>() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Tab Selector: IDM vs Torrent
        TabRow(selectedTabIndex = selectedSubTab, modifier = Modifier.fillMaxWidth()) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text(t("IDM Downloader", "تحميل الملفات (IDM)")) },
                icon = { Icon(Icons.Default.Download, contentDescription = null) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text(t("BitTorrent", "التورنت (uTorrent)")) },
                icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (selectedSubTab == 0) {
            // ── IDM SECTION ────────────────────────────────────────────────
            OutlinedTextField(
                value = idmUrlInput,
                onValueChange = { idmUrlInput = it },
                label = { Text(t("Direct file link (HTTP/HTTPS)", "رابط مباشر للتحميل (HTTP/HTTPS)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { idmUrlInput = it.trim() }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = t("Paste", "لصق"))
                        }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (idmUrlInput.isNotBlank()) {
                            idmInspecting = true
                            scope.launch {
                                try {
                                    val task = idmEngine.inspect(idmUrlInput.trim())
                                    idmInspectedTask = task
                                } catch (_: Exception) {}
                                finally { idmInspecting = false }
                            }
                        }
                    },
                    enabled = idmUrlInput.isNotBlank() && !idmInspecting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (idmInspecting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(t("Examine Link", "فحص الرابط"))
                    }
                }
            }

            idmInspectedTask?.let { inspected ->
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = inspected.filename,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {},
                                label = { Text(if (arabic) inspected.category.arabicLabel else inspected.category.label) },
                                leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, Modifier.size(16.dp)) }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text(inspected.formattedTotalSize()) },
                                leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null, Modifier.size(16.dp)) }
                            )
                            if (inspected.supportsRange) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(t("Multi-thread OK", "يدعم التجزئة المتوازية")) }
                                )
                            }
                        }

                        // Thread / Segments Count Selector
                        Text(t("Parallel Segments (IDM Threads):", "عدد مسارات التحميل المتوازية (مسارات IDM):"), style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 4, 8).forEach { count ->
                                FilterChip(
                                    selected = idmSegmentsChoice == count,
                                    onClick = { idmSegmentsChoice = count },
                                    label = { Text("$count ${t("threads", "مسارات")}") }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val newTask = inspected.copy(segmentsCount = idmSegmentsChoice)
                                idmTasks.add(0, newTask)
                                idmInspectedTask = null
                                idmUrlInput = ""
                                idmEngine.start(
                                    task = newTask,
                                    onUpdate = { updated ->
                                        val idx = idmTasks.indexOfFirst { it.id == updated.id }
                                        if (idx >= 0) idmTasks[idx] = updated.copy()
                                    },
                                    onComplete = { _, _ -> },
                                    onError = { _, _ -> }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(t("Download with Acceleration", "بدء التنزيل المسرَّع"))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Task List
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(idmTasks, key = { it.id }) { task ->
                    IdmTaskCard(task = task, arabic = arabic,
                        onPause = { idmEngine.pause(task.id) },
                        onResume = {
                            idmEngine.start(task,
                                onUpdate = { updated ->
                                    val idx = idmTasks.indexOfFirst { it.id == updated.id }
                                    if (idx >= 0) idmTasks[idx] = updated.copy()
                                },
                                onComplete = { _, _ -> },
                                onError = { _, _ -> }
                            )
                        },
                        onCancel = {
                            idmEngine.cancel(task.id)
                            idmTasks.remove(task)
                        }
                    )
                }
            }

        } else {
            // ── BITTORRENT SECTION ─────────────────────────────────────────
            OutlinedTextField(
                value = torrentInput,
                onValueChange = { torrentInput = it },
                label = { Text(t("Magnet link (magnet:?xt=...)", "رابط ماجنت (magnet:?xt=...)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let { torrentInput = it.trim() }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = t("Paste", "لصق"))
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (torrentInput.isNotBlank()) {
                        try {
                            val task = torrentEngine.parseMagnet(torrentInput.trim())
                            torrentTasks.add(0, task)
                            torrentInput = ""
                            torrentEngine.start(task,
                                onUpdate = { updated ->
                                    val idx = torrentTasks.indexOfFirst { it.id == updated.id }
                                    if (idx >= 0) torrentTasks[idx] = updated.copy()
                                },
                                onComplete = { _, _ -> },
                                onError = { _, _ -> }
                            )
                        } catch (_: Exception) {}
                    }
                },
                enabled = torrentInput.startsWith("magnet:?", ignoreCase = true),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(t("Start Torrent Download", "بدء تحميل التورنت"))
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(torrentTasks, key = { it.id }) { task ->
                    TorrentTaskCard(task = task, arabic = arabic,
                        onPause = { torrentEngine.pause(task.id) },
                        onResume = {
                            torrentEngine.start(task,
                                onUpdate = { updated ->
                                    val idx = torrentTasks.indexOfFirst { it.id == updated.id }
                                    if (idx >= 0) torrentTasks[idx] = updated.copy()
                                },
                                onComplete = { _, _ -> },
                                onError = { _, _ -> }
                            )
                        },
                        onCancel = {
                            torrentEngine.cancel(task.id)
                            torrentTasks.remove(task)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun IdmTaskCard(
    task: IdmTask,
    arabic: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    fun t(en: String, ar: String) = if (arabic) ar else en

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.filename,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (arabic) task.state.arabicLabel else task.state.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            LinearProgressIndicator(
                progress = { task.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )

            // Segment Progress Bars (Visual Multi-chunk IDM style)
            if (task.segments.size > 1 && task.state == IdmState.DOWNLOADING) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    task.segments.forEach { seg ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (seg.isCompleted) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${task.formattedDownloadedSize()} / ${task.formattedTotalSize()} (${"%.1f".format(task.progressPercent)}%)",
                    style = MaterialTheme.typography.bodySmall
                )
                if (task.formattedSpeed().isNotEmpty()) {
                    Text(
                        text = "⚡ ${task.formattedSpeed()}  ⏳ ETA ${task.formattedEta()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.state == IdmState.DOWNLOADING) {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = t("Pause", "إيقاف مؤقت"))
                    }
                } else if (task.state == IdmState.PAUSED) {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = t("Resume", "استئناف"))
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = t("Cancel", "إلغاء"), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun TorrentTaskCard(
    task: TorrentTask,
    arabic: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    fun t(en: String, ar: String) = if (arabic) ar else en

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (arabic) task.state.arabicLabel else task.state.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            LinearProgressIndicator(
                progress = { task.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${task.formattedDownloadedSize()} / ${task.formattedTotalSize()}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Seeds: ${task.seedsCount} · Peers: ${task.peersCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⬇ ${task.formattedDownSpeed()} · ⬆ ${task.formattedUpSpeed()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Row {
                    if (task.state == TorrentState.DOWNLOADING) {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = t("Pause", "إيقاف مؤقت"))
                        }
                    } else if (task.state == TorrentState.PAUSED) {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = t("Resume", "استئناف"))
                        }
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = t("Cancel", "إلغاء"), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
