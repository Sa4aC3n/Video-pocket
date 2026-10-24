package org.videopocket.probe.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.FileProvider
import org.videopocket.probe.torrent.TorrentTaskManager
import kotlinx.coroutines.launch
import org.videopocket.probe.torrent.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentScreen(arabic: Boolean) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val torrentEngine = remember { TorrentEngine(context) }
    var magnetInput by rememberSaveable { mutableStateOf("") }
    val torrentTasks = TorrentTaskManager.tasks

    fun startTask(task: TorrentTask) {
        torrentEngine.start(
            task = task,
            onUpdate = { updated ->
                val idx = torrentTasks.indexOfFirst { it.id == updated.id }
                if (idx >= 0) torrentTasks[idx] = updated.copy()
            },
            onComplete = { completedTask, file ->
                Toast.makeText(
                    context,
                    if (arabic) "اكتمل تنزيل التورنت: ${file.name}" else "Torrent completed: ${file.name}",
                    Toast.LENGTH_LONG
                ).show()
            },
            onError = { failedTask, error ->
                Toast.makeText(
                    context,
                    (if (arabic) "خطأ في التورنت: " else "Torrent error: ") + (error.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Launcher for selecting a .torrent file from local storage
    val torrentFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val task = torrentEngine.parseTorrentFile(stream)
                        torrentTasks.add(0, task)
                        startTask(task)
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        (if (arabic) "فشل فتح ملف التورنت: " else "Failed to open torrent: ") + (e.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val totalDownSpeed = torrentTasks.sumOf { it.downloadSpeedBps }
    val totalUpSpeed = torrentTasks.sumOf { it.uploadSpeedBps }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // ── Hero Section / Swarm Dashboard ──────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = t("BitTorrent Engine", "محرك التورنت (P2P)"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Badge(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary) {
                            Text(t("Swarm Active", "الشبكة نشطة"), modifier = Modifier.padding(4.dp), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Swarm Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t("Download Speed", "سرعة التنزيل"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("↓ ${TorrentTask.formatSpeed(totalDownSpeed)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t("Upload Speed", "سرعة الرفع"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("↑ ${TorrentTask.formatSpeed(totalUpSpeed)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t("Torrents", "التورنتات"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${torrentTasks.size}", fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = t(
                                    "Real P2P block exchange, UDP/HTTP trackers & WebSeeds are enabled.",
                                    "محرك P2P وتنزيل الكتل الفعلي وتتبع النظراء عبر UDP/HTTP يعملان بنجاح."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ── Input & File Opener Card ──────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = magnetInput,
                        onValueChange = { magnetInput = it },
                        label = { Text(t("Magnet Link (magnet:?xt=...)", "رابط ماجنت (magnet:?xt=...)")) },
                        placeholder = { Text("magnet:?xt=urn:btih:...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboard.getText()?.text?.let { magnetInput = it.trim() }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = t("Paste", "لصق"))
                            }
                        }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (magnetInput.isNotBlank()) {
                                    try {
                                        val task = torrentEngine.parseMagnet(magnetInput.trim())
                                        torrentTasks.add(0, task)
                                        magnetInput = ""
                                        startTask(task)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            (if (arabic) "رابط ماجنت غير صالح: " else "Invalid magnet: ") + (e.message ?: ""),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            enabled = magnetInput.startsWith("magnet:?", ignoreCase = true),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(t("Download Magnet", "تحميل الماجنت"))
                        }

                        OutlinedButton(
                            onClick = {
                                torrentFileLauncher.launch(arrayOf("application/x-bittorrent", "*/*"))
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t(".torrent File", "ملف تورنت"))
                        }
                    }
                }
            }
        }

        // ── Torrents List ────────────────────────────────────────────────────
        if (torrentTasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                        Text(
                            text = t("No torrents added yet", "لم يتم إضافة أي تورنت بعد"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = t("Paste a magnet URI or open a .torrent file to download", "الصق رابط ماجنت أو اختر ملف .torrent لبدء التنزيل والمشاركة"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(torrentTasks, key = { it.id }) { task ->
                TorrentTaskCard(
                    task = task,
                    arabic = arabic,
                    context = context,
                    onPause = { torrentEngine.pause(task.id) },
                    onResume = { startTask(task) },
                    onCancel = {
                        torrentEngine.cancel(task.id)
                        torrentTasks.remove(task)
                    }
                )
            }
        }
    }
}

@Composable
fun TorrentTaskCard(
    task: TorrentTask,
    arabic: Boolean,
    context: Context,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    fun t(en: String, ar: String) = if (arabic) ar else en

    fun openFile(filePath: String) {
        val uri = if (filePath.startsWith("content://")) {
            Uri.parse(filePath)
        } else {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, if (arabic) "الملف غير موجود" else "File not found", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (_: Exception) {
                Uri.fromFile(file)
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, task.name)) }
            .onFailure { Toast.makeText(context, if (arabic) "لا يوجد تطبيق لفتح هذا الملف" else "No app found to open file", Toast.LENGTH_SHORT).show() }
    }

    fun shareFile(filePath: String) {
        val uri = if (filePath.startsWith("content://")) {
            Uri.parse(filePath)
        } else {
            val file = File(filePath)
            if (!file.exists()) return
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (_: Exception) {
                Uri.fromFile(file)
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, task.name)) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (task.state) {
                            TorrentState.COMPLETED -> Icons.Default.CheckCircle
                            TorrentState.FAILED -> Icons.Default.Error
                            else -> Icons.Default.CloudDownload
                        },
                        contentDescription = null,
                        tint = when (task.state) {
                            TorrentState.COMPLETED -> Color(0xFF2E7D32)
                            TorrentState.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = task.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (arabic) task.state.arabicLabel else task.state.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (task.state) {
                                TorrentState.DOWNLOADING, TorrentState.SEEDING -> MaterialTheme.colorScheme.primary
                                TorrentState.COMPLETED -> Color(0xFF2E7D32)
                                TorrentState.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.state == TorrentState.DOWNLOADING || task.state == TorrentState.SEEDING) {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                        }
                    } else if (task.state == TorrentState.PAUSED || task.state == TorrentState.FAILED) {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                        }
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (task.errorMessage != null && task.state == TorrentState.FAILED) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = task.errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (task.progressPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${task.formattedDownloadedSize()} / ${task.formattedTotalSize()} (%.1f%%)".format(task.progressPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "↓ ${task.formattedDownSpeed()} • ↑ ${task.formattedUpSpeed()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = t("Peers: ${task.peersCount} • Seeds: ${task.seedsCount}", "النظراء: ${task.peersCount} • المتصلون: ${task.seedsCount}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (task.etaSeconds > 0) {
                    Text(
                        text = "ETA: ${task.formattedEta()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // If task is completed, show Open and Share buttons!
            if (task.state == TorrentState.COMPLETED && !task.outputPath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { task.outputPath?.let { openFile(it) } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Open File", "تشغيل الملف"))
                    }

                    OutlinedButton(
                        onClick = { task.outputPath?.let { shareFile(it) } },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Share", "مشاركة"))
                    }
                }
            }
        }
    }
}
