package org.videopocket.probe.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.videopocket.probe.core.MediaTypes
import org.videopocket.probe.core.PocketLibraryItem
import org.videopocket.probe.vault.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VaultScreen(
    arabic: Boolean,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    fun t(en: String, ar: String) = if (arabic) ar else en

    val lockState by VaultManager.lockState.collectAsState()
    val items by VaultManager.items.collectAsState()

    var isImporting by remember { mutableStateOf(false) }
    var exportingId by remember { mutableStateOf<String?>(null) }
    var deletingItem by remember { mutableStateOf<VaultItem?>(null) }
    var playingItem by remember { mutableStateOf<PocketLibraryItem?>(null) }
    var authError by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        VaultManager.initialize(context)
    }

    // Auto-lock when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            VaultManager.lock()
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selectedUri ->
        if (selectedUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            isImporting = true
            scope.launch {
                try {
                    val imported = VaultManager.importMedia(context, selectedUri)
                    statusMessage = t("Encrypted successfully: ${imported.title}", "تم التشفير والحفظ بنجاح: ${imported.title}")
                } catch (e: Exception) {
                    authError = t("Failed to import file: ${e.message}", "فشل استيراد الملف: ${e.message}")
                } finally {
                    isImporting = false
                }
            }
        }
    }

    fun triggerAuth() {
        if (activity == null) {
            VaultManager.unlock()
            return
        }
        authError = null
        BiometricAuthHelper.prompt(
            activity = activity,
            title = t("Unlock Media Vault", "إلغاء قفل الخزنة المشفرة"),
            subtitle = t("Authenticate using fingerprint or device lock", "المصادقة باستخدام بصمة الإصبع أو قفل الجهاز"),
            onSuccess = {
                VaultManager.unlock()
            },
            onError = { err ->
                if (err != "CANCELLED") {
                    authError = if (err == "FAILED") {
                        t("Fingerprint not recognized. Try again.", "لم يتم التعرف على البصمة. أعد المحاولة.")
                    } else {
                        err
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Navigation & Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "رجوع"))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = t("Encrypted Vault", "الخزنة المشفرة"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (lockState == VaultLockState.UNLOCKED) {
                IconButton(onClick = { VaultManager.lock() }) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = t("Lock Vault", "قفل الخزنة"),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        if (lockState == VaultLockState.LOCKED) {
            // Locked UI View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(
                            text = t("Personal AES-256 Vault", "الخزنة الشخصية المشفرة"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = t(
                                "Bank-grade encrypted storage. Files in this vault are hidden from galleries, file managers, and media crawlers.",
                                "حماية بنكية بمعيار AES-256-GCM. ملفاتك داخل الخزنة مشفرة ومعزولة تماماً عن المعرض وتطبيقات تصفح الملفات."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { triggerAuth() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(t("Unlock with Biometrics", "إلغاء القفل بالبصمة"))
                        }

                        authError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            // Unlocked UI View
            val totalBytes = remember(items) { items.sumOf { it.originalBytes } }

            // Stats & Import Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = t("${items.size} Protected Files", "${items.size} ملفات محمية"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = t("Encrypted Size: ${formatBytes(totalBytes)}", "الحجم المشفر: ${formatBytes(totalBytes)}"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { docPickerLauncher.launch(arrayOf("video/*", "audio/*")) },
                        enabled = !isImporting
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(t("Import File", "تشفير ملف"))
                    }
                }
            }

            if (isImporting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    t("Encrypting & isolating file with AES-256-GCM…", "جاري التشفير والعزل في الخزنة بتقنية AES-256…"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            statusMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            authError?.let { err ->
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EnhancedEncryption,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = t("Your Vault is Empty", "خزنتك المشفرة خالية حالياً"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = t(
                                "Tap 'Import File' to encrypt sensitive videos or audio clips.",
                                "اضغط على 'تشفير ملف' لاختيار وحماية أي فيديو أو تسجيل صوتي خاص."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        VaultItemCard(
                            item = item,
                            arabic = arabic,
                            isExporting = exportingId == item.id,
                            onPlay = {
                                playingItem = PocketLibraryItem(
                                    id = item.id,
                                    title = item.title + " (🔒 Vault)",
                                    uriString = VaultContentProvider.buildPlayUri(context, item.id).toString(),
                                    duration = item.durationMs?.let { org.videopocket.probe.core.ClipRequest.formatSeconds(it / 1000.0) } ?: "—",
                                    resolution = if (MediaTypes.isAudio(item.extension)) "Audio" else "—",
                                    format = item.extension,
                                    fileSizeBytes = item.originalBytes,
                                    platform = "Vault",
                                    mimeType = item.mimeType
                                )
                            },
                            onExport = {
                                exportingId = item.id
                                scope.launch {
                                    try {
                                        VaultManager.exportMedia(context, item)
                                        statusMessage = t("Restored to Downloads/VideoPocket", "تمت الاستعادة بنجاح إلى مجلد التنزيلات")
                                    } catch (e: Exception) {
                                        authError = t("Failed to export: ${e.message}", "فشلت الاستعادة: ${e.message}")
                                    } finally {
                                        exportingId = null
                                    }
                                }
                            },
                            onDelete = {
                                deletingItem = item
                            }
                        )
                    }
                }
            }
        }
    }

    // Secure Playback Dialog
    playingItem?.let { playItem ->
        BuiltInPlayerDialog(
            item = playItem,
            onDismiss = { playingItem = null },
            arabic = arabic
        )
    }

    // Deletion confirmation dialog
    deletingItem?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(t("Permanently Delete from Vault?", "حذف نهائي من الخزنة؟")) },
            text = {
                Text(
                    t(
                        "Are you sure you want to delete '${toDelete.title}'? The encrypted file will be permanently removed.",
                        "هل أنت متأكد من حذف '${toDelete.title}'؟ سيتم حذف الملف المشفر نهائياً ولا يمكن استرجاعه."
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            VaultManager.deleteItem(context, toDelete)
                            deletingItem = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(t("Delete", "حذف نهائي"))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text(t("Cancel", "إلغاء"))
                }
            }
        )
    }
}

@Composable
private fun VaultItemCard(
    item: VaultItem,
    arabic: Boolean,
    isExporting: Boolean,
    onPlay: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    val isAudio = MediaTypes.isAudio(item.extension)
    val dateStr = remember(item.encryptedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.encryptedAt))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isAudio) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAudio) Icons.Default.AudioFile else Icons.Default.Movie,
                    contentDescription = null,
                    tint = if (isAudio) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${item.extension.uppercase()} · ${formatBytes(item.originalBytes)} · $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = t("Play", "تشغيل"), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onExport, enabled = !isExporting) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.FileDownload, contentDescription = t("Restore", "استعادة"))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = t("Delete", "حذف"), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MiB", bytes / 1_048_576.0)
    bytes >= 1_024     -> String.format(Locale.US, "%.0f KB", bytes / 1_024.0)
    else               -> "$bytes B"
}
