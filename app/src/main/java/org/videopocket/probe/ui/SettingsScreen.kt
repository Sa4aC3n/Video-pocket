package org.videopocket.probe.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.videopocket.probe.ProbeModel
import org.videopocket.probe.core.AppSettings
import org.videopocket.probe.core.DefaultQualityPreset
import org.videopocket.probe.core.ThemeMode
import org.videopocket.probe.storage.StorageBreakdown
import org.videopocket.probe.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videopocket.probe.storage.TempFileManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun SettingsScreen(
    model: ProbeModel,
    arabic: Boolean,
    onToggleLanguage: () -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String) = if (arabic) ar else en

    val scope = rememberCoroutineScope()
    var storageBreakdown by remember { mutableStateOf(StorageBreakdown(0, 0, 0, 0, 0, 0)) }
    LaunchedEffect(Unit) { storageBreakdown = withContext(Dispatchers.IO) { runCatching { StorageManager.getBreakdown(context) }.getOrDefault(storageBreakdown) } }
    var showClearTempConfirm by remember { mutableStateOf(false) }
    fun notificationsAllowed() = android.os.Build.VERSION.SDK_INT < 33 ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var notificationPermission by remember { mutableStateOf(notificationsAllowed()) }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationPermission = it
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationPermission = notificationsAllowed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Column {
            Text(
                text = t("Settings", "الإعدادات"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = t("Preferences & App Management", "التفضيلات وإدارة التطبيق"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // Appearance Section
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = t("Appearance & Theme", "المظهر والسمة"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.values().forEach { mode ->
                        FilterChip(
                            selected = AppSettings.themeMode == mode,
                            onClick = { AppSettings.setTheme(mode) },
                            label = { Text(if (arabic) mode.arabicLabel else mode.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Language Section
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = t("Language", "لغة التطبيق"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (arabic) "العربية (RTL)" else "English (LTR)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Button(
                    onClick = {
                        val newAr = !arabic
                        AppSettings.setLanguage(newAr)
                        onToggleLanguage()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (arabic) "English" else "العربية")
                }
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Download notifications", "إشعارات التنزيل"), style = MaterialTheme.typography.titleMedium)
                    Text(if (notificationPermission) t("Allowed", "مسموح بها") else
                        t("Allow notifications to see download progress outside the app.", "اسمح بالإشعارات لعرض تقدم التنزيل خارج التطبيق."))
                    if (!notificationPermission) OutlinedButton(onClick = {
                        notificationRequest.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }) { Text(t("Enable notifications", "تفعيل الإشعارات")) }
                    TextButton(onClick = {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }) { Text(t("System notification settings", "إعدادات إشعارات النظام")) }
                }
            }
        }

        // Download Preferences
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = t("Download Defaults", "خيارات التنزيل الافتراضية"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = t("Default Quality Preset:", "الجودة الافتراضية المفضلة:"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefaultQualityPreset.values().forEach { preset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = AppSettings.defaultQuality == preset,
                                onClick = { AppSettings.setDefaultQualityPreset(preset) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (arabic) preset.arabicLabel else preset.label)
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = t("Default Video Container:", "صيغة الفيديو الافتراضية:"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("mp4", "mkv", "webm").forEach { fmt ->
                        FilterChip(
                            selected = AppSettings.defaultVideoFormat == fmt,
                            onClick = { AppSettings.setDefaultVideoFormatSetting(fmt) },
                            label = { Text(fmt.uppercase()) }
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = t("Max Concurrent Downloads", "أقصى عدد تنزيلات متزامنة"),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${AppSettings.maxConcurrentDownloads} " + t("simultaneous downloads", "تنزيلات في نفس الوقت"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3).forEach { count ->
                            FilterChip(
                                selected = AppSettings.maxConcurrentDownloads == count,
                                onClick = {
                                    AppSettings.setMaxConcurrent(count)
                                    model.queueManager.maxConcurrent = count
                                },
                                label = { Text("$count") }
                            )
                        }
                    }
                }
            }
        }

        // Privacy & Smart Clipboard
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                text = t("Smart Clipboard Detection", "اكتشاف الروابط الذكي من الحافظة"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = t(
                                    "Shows a prompt when a copied link is detected. Does NOT send data without tapping.",
                                    "يعرض تنبيهًا عند نسخ رابط. لا يرسل أي بيانات عبر الشبكة دون موافقتك."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Switch(
                        checked = AppSettings.smartClipboardEnabled,
                        onCheckedChange = { AppSettings.setSmartClipboard(it) }
                    )
                }
            }
        }

        // Storage & Cache Management
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = t("Storage Breakdown", "مساحة التخزين المستهلكة"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(t("Saved Videos:", "مقاطع الفيديو:"))
                    Text(storageBreakdown.formatBytes(storageBreakdown.videoBytes), fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(t("Saved Audio:", "المقاطع الصوتية:"))
                    Text(storageBreakdown.formatBytes(storageBreakdown.audioBytes), fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(t("Clips:", "المقاطع المجتزأة:"))
                    Text(storageBreakdown.formatBytes(storageBreakdown.clipBytes), fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(t("Cache & Temp Files:", "ملفات التخزين المؤقت:"))
                    Text(storageBreakdown.formatBytes(storageBreakdown.tempBytes + storageBreakdown.cacheBytes), fontWeight = FontWeight.Bold)
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("Indexed media and temporary files", "الوسائط المفهرسة والملفات المؤقتة"),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = storageBreakdown.formatBytes(storageBreakdown.totalBytes),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = { showClearTempConfirm = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Clear Temporary Files", "تنظيف الملفات المؤقتة"))
                }
            }
        }

        // About & Engine Section
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Video Pocket ${org.videopocket.probe.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = t("Save. Watch. Keep. — Modern media pocket with offline freedom.",
                             "احفظ. شاهد. احتفظ. — تطبيق حفظ الوسائط الحديث للمشاهدة بدون إنترنت."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "${t("Engine Status:", "حالة المحرك:")} ${if (model.ready) t("Ready (Loaded)", "جاهز") else t("Deferred (Cold-Start Safe)", "مؤجل للأمان")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    // Clear Temp Confirmation Dialog
    if (showClearTempConfirm) {
        AlertDialog(
            onDismissRequest = { showClearTempConfirm = false },
            icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(t("Clear Temporary Files?", "تنظيف الملفات المؤقتة؟")) },
            text = {
                Text(
                    t(
                        "This will safely remove temporary cache and partial download fragments. Your saved library videos and audio will NOT be deleted.",
                        "سيتم مسح بقايا التحميل والملفات المؤقتة بأمان. لن تتأثر أي مقاطع فيديو أو صوتيات محفوظة في مكتبتك."
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearTempConfirm = false
                        scope.launch {
                        val freed = withContext(Dispatchers.IO) { TempFileManager.cleanupTemporaryFiles(context) }
                        storageBreakdown = withContext(Dispatchers.IO) { runCatching { StorageManager.getBreakdown(context) }.getOrDefault(storageBreakdown) }
                        val mbFreed = freed / (1024.0 * 1024.0)
                        val msg = if (arabic) "تم تنظيف %.1f ميجابايت بنجاح".format(mbFreed) else "Cleaned %.1f MB of temp files".format(mbFreed)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(t("Clean", "تنظيف"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTempConfirm = false }) {
                    Text(t("Cancel", "إلغاء"))
                }
            }
        )
    }
}
