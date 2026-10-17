package org.videopocket.probe.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdmScreen(arabic: Boolean) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val idmEngine = remember { IdmEngine(context) }

    var idmUrlInput by rememberSaveable { mutableStateOf("") }
    var idmInspecting by remember { mutableStateOf(false) }
    var idmInspectedTask by remember { mutableStateOf<IdmTask?>(null) }
    var idmSegmentsChoice by rememberSaveable { mutableIntStateOf(8) }
    val idmTasks = remember { mutableStateListOf<IdmTask>() }

    var filterCategory by rememberSaveable { mutableStateOf<IdmCategory?>(null) }

    val filteredTasks = remember(idmTasks.size, filterCategory, idmTasks.map { it.state }) {
        if (filterCategory == null) idmTasks
        else idmTasks.filter { it.category == filterCategory }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // ── Hero Section / Header ──────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = t("IDM Downloader", "تحميل الملفات (IDM)"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = t("Multi-threaded parallel download accelerator", "مسرِّع التحميل متعدد المسارات المتوازية"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("8x Speed", fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }

        // ── Input & Inspection Card ────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = idmUrlInput,
                        onValueChange = { idmUrlInput = it },
                        label = { Text(t("Direct Download URL (HTTP / HTTPS)", "رابط مباشر للتحميل (HTTP / HTTPS)")) },
                        placeholder = { Text("https://example.com/file.zip") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboard.getText()?.text?.let { idmUrlInput = it.trim() }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = t("Paste", "لصق"))
                            }
                        }
                    )

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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (idmInspecting) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(t("Analyzing...", "جاري الفحص..."))
                            } else {
                                Icon(Icons.Default.TravelExplore, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(t("Examine Link", "فحص الرابط"))
                            }
                        }

                        if (idmUrlInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = { idmUrlInput = ""; idmInspectedTask = null },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = t("Clear", "مسح"))
                            }
                        }
                    }
                }
            }
        }

        // ── Inspected Task Details Card ────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = idmInspectedTask != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                idmInspectedTask?.let { inspected ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = when (inspected.category) {
                                        IdmCategory.VIDEO -> Icons.Default.Movie
                                        IdmCategory.AUDIO -> Icons.Default.MusicNote
                                        IdmCategory.COMPRESSED -> Icons.Default.FolderZip
                                        IdmCategory.DOCUMENT -> Icons.Default.Description
                                        IdmCategory.PROGRAM -> Icons.Default.Android
                                        else -> Icons.Default.InsertDriveFile
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = inspected.filename,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(if (arabic) inspected.category.arabicLabel else inspected.category.label) }
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(inspected.formattedTotalSize(), fontWeight = FontWeight.Bold) }
                                )
                                if (inspected.supportsRange) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(t("Parallel OK ⚡", "تجزئة متوازية ⚡"), color = MaterialTheme.colorScheme.tertiary) }
                                    )
                                }
                            }

                            // Parallel Connections Selector
                            Text(
                                text = t("Parallel Connections (Speed Boost):", "عدد المسارات المتوازية (مضاعفة السرعة):"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 4, 8, 16).forEach { threads ->
                                    FilterChip(
                                        selected = idmSegmentsChoice == threads,
                                        onClick = { idmSegmentsChoice = threads },
                                        label = { Text("$threads ${t("threads", "مسارات")}") },
                                        leadingIcon = if (idmSegmentsChoice == threads) {
                                            { Icon(Icons.Default.Bolt, contentDescription = null, Modifier.size(16.dp)) }
                                        } else null
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
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.RocketLaunch, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(t("Start Accelerated Download", "بدء التحميل المسرَّع"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ── Filter Chips ───────────────────────────────────────────────────
        if (idmTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterCategory == null,
                        onClick = { filterCategory = null },
                        label = { Text(t("All (${idmTasks.size})", "الكل (${idmTasks.size})")) }
                    )
                    IdmCategory.entries.forEach { cat ->
                        val count = idmTasks.count { it.category == cat }
                        if (count > 0) {
                            FilterChip(
                                selected = filterCategory == cat,
                                onClick = { filterCategory = cat },
                                label = { Text("${if (arabic) cat.arabicLabel else cat.label} ($count)") }
                            )
                        }
                    }
                }
            }
        }

        // ── Active Downloads List ──────────────────────────────────────────
        if (filteredTasks.isEmpty() && idmTasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                        Text(
                            text = t("No active downloads", "لا توجد تنزيلات حالياً"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = t("Paste a direct file URL above to download at maximum speed", "الصق رابط ملف مباشر أعلاه للتحميل بأقصى سرعة ممكنة"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(filteredTasks, key = { it.id }) { task ->
                IdmTaskCard(
                    task = task,
                    arabic = arabic,
                    onPause = { idmEngine.pause(task.id) },
                    onResume = {
                        idmEngine.start(
                            task = task,
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
    }
}
