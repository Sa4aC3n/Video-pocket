package org.videopocket.probe.ui

import android.net.Uri
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.videopocket.probe.torrent.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentScreen(arabic: Boolean) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val torrentEngine = remember { TorrentEngine(context) }
    var magnetInput by rememberSaveable { mutableStateOf("") }
    val torrentTasks = remember { mutableStateListOf<TorrentTask>() }

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
                        torrentEngine.start(
                            task = task,
                            onUpdate = { updated ->
                                val idx = torrentTasks.indexOfFirst { it.id == updated.id }
                                if (idx >= 0) torrentTasks[idx] = updated.copy()
                            },
                            onComplete = { _, _ -> },
                            onError = { _, _ -> }
                        )
                    }
                } catch (_: Exception) {}
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
        // ── Hero Section / Swarm Dashboard ─────────────────────────────────
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
                                text = t("BitTorrent Client", "محرك التورنت (uTorrent)"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Badge(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary) {
                            Text(t("P2P Swarm", "شبكة P2P"), modifier = Modifier.padding(4.dp), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Swarm Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t("Download Speed", "سرعة التنزيل"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("⬇ ${TorrentTask.formatSpeed(totalDownSpeed)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t("Upload Speed", "سرعة الرفع"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("⬆ ${TorrentTask.formatSpeed(totalUpSpeed)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t("Torrents", "التورنتات"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${torrentTasks.size}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Input & File Opener Card ───────────────────────────────────────
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
                                        torrentEngine.start(
                                            task = task,
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

        // ── Torrents List ──────────────────────────────────────────────────
        if (torrentTasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
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
                    onPause = { torrentEngine.pause(task.id) },
                    onResume = {
                        torrentEngine.start(
                            task = task,
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
