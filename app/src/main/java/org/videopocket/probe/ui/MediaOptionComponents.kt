package org.videopocket.probe.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.videopocket.probe.core.*
import org.videopocket.probe.resolver.NormalizedMetadata

object QualityBadgeHelper {
    fun getBadgeText(height: Int): String = when {
        height >= 2160 -> "4K"
        height >= 1440 -> "2K"
        height >= 1080 -> "FHD"
        height >= 720 -> "HD"
        height in 1..480 -> "SD"
        else -> ""
    }

    fun getBadgeColor(height: Int): Color = when {
        height >= 2160 -> Color(0xFF673AB7) // Deep Purple
        height >= 1440 -> Color(0xFF3F51B5) // Indigo
        height >= 1080 -> Color(0xFF00897B) // Teal
        height >= 720 -> Color(0xFF0288D1)  // Light Blue
        else -> Color(0xFF546E7A)           // Blue Grey
    }
}

@Composable
fun QualityChipWithBadge(
    height: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    fileSizeStr: String? = null,
    enabled: Boolean = true
) {
    val badge = QualityBadgeHelper.getBadgeText(height)
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${height}p",
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                if (badge.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                if (!fileSizeStr.isNullOrBlank()) {
                    Text(
                        text = "($fileSizeStr)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    )
}

@Composable
fun PresetQualitySelector(
    normalizedMetadata: NormalizedMetadata?,
    mediaInfo: MediaInfo,
    selectedHeight: Int,
    onHeightSelected: (Int) -> Unit,
    arabic: Boolean
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    val realHeights = mediaInfo.heights

    // Real dynamic presets calculated from actual streams
    val bestHeight = realHeights.maxOrNull() ?: mediaInfo.defaultHeight
    val balancedHeight = realHeights.firstOrNull { it in 720..1080 } ?: bestHeight
    val smallestHeight = realHeights.minOrNull() ?: bestHeight

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = t("Quick Presets:", "إعدادات الجودة السريعة:"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedHeight == bestHeight,
                onClick = { onHeightSelected(bestHeight) },
                label = {
                    Column {
                        Text(t("Best Available", "أفضل جودة"), fontWeight = FontWeight.Bold)
                        Text("${bestHeight}p", style = MaterialTheme.typography.labelSmall)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            if (balancedHeight != bestHeight) {
                FilterChip(
                    selected = selectedHeight == balancedHeight,
                    onClick = { onHeightSelected(balancedHeight) },
                    label = {
                        Column {
                            Text(t("Balanced", "متوازنة"), fontWeight = FontWeight.Bold)
                            Text("${balancedHeight}p", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (smallestHeight != balancedHeight && smallestHeight != bestHeight) {
                FilterChip(
                    selected = selectedHeight == smallestHeight,
                    onClick = { onHeightSelected(smallestHeight) },
                    label = {
                        Column {
                            Text(t("Smallest File", "أصغر حجم"), fontWeight = FontWeight.Bold)
                            Text("${smallestHeight}p", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text(
            text = t("All Available Qualities:", "جميع الجودات المتوفرة:"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold
        )

        if (realHeights.isEmpty()) {
            Text(
                text = t("Single standard quality stream available", "يتوفر تدفق بجودة قياسية واحدة"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            realHeights.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { h ->
                        QualityChipWithBadge(
                            height = h,
                            isSelected = selectedHeight == h,
                            onClick = { onHeightSelected(h) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DuplicateConfirmDialog(
    item: PocketLibraryItem,
    arabic: Boolean,
    onOpenExisting: () -> Unit,
    onDownloadAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    fun t(en: String, ar: String) = if (arabic) ar else en

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text(
                text = t("Already in your Pocket", "موجود بالفعل في مكتبتك"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = t(
                        "This video has already been saved to your Pocket library:",
                        "تم حفظ هذا الفيديو مسبقًا في مكتبتك:"
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${item.resolution} • ${item.format} • ${item.duration}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenExisting) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Open Existing", "فتح الملف الحالي"))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(t("Cancel", "إلغاء"))
                }
                OutlinedButton(onClick = onDownloadAgain) {
                    Text(t("Download Again", "تنزيل مجددًا"))
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsBottomSheet(
    media: MediaInfo,
    normalized: NormalizedMetadata?,
    initialHeight: Int,
    initialFormat: String,
    initialAudioTrackId: String? = null,
    arabic: Boolean,
    onDismiss: () -> Unit,
    onConfirmDownload: (height: Int, format: String, audioTrackId: String?) -> Unit
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    var selectedHeight by remember { mutableIntStateOf(initialHeight) }
    var selectedFormat by remember { mutableStateOf(initialFormat) }
    var selectedAudioTrackId by remember {
        mutableStateOf(initialAudioTrackId ?: media.audioTracks.firstOrNull { it.isOriginal }?.id ?: media.audioTracks.firstOrNull()?.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t("Download Options", "خيارات التنزيل المتقدمة"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t("Close", "إغلاق"))
                }
            }

            // Media Title Preview
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!media.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = media.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = media.duration?.let { ClipRequest.formatSeconds(it) } ?: t("Video", "فيديو"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            HorizontalDivider()

            // Quality Selection
            PresetQualitySelector(
                normalizedMetadata = normalized,
                mediaInfo = media,
                selectedHeight = selectedHeight,
                onHeightSelected = { selectedHeight = it },
                arabic = arabic
            )

            HorizontalDivider()

            // Format Selection
            Text(
                text = t("Container Format:", "صيغة الملف:"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("mp4", "mkv", "webm").forEach { fmt ->
                    FilterChip(
                        selected = selectedFormat == fmt,
                        onClick = { selectedFormat = fmt },
                        label = { Text(fmt.uppercase()) }
                    )
                }
            }

            if (media.audioTracks.size > 1) {
                HorizontalDivider()
                Text(
                    text = t("Audio Language / Dub:", "لغة الصوت / الدبلجة:"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    media.audioTracks.forEach { track ->
                        val isSel = (selectedAudioTrackId == track.id) || (selectedAudioTrackId == null && track.isOriginal)
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedAudioTrackId = track.id },
                            label = {
                                Text(if (track.isOriginal) "${track.displayName} (${t("Original", "الأصلية")})" else track.displayName)
                            },
                            leadingIcon = if (isSel) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    onConfirmDownload(selectedHeight, selectedFormat, selectedAudioTrackId)
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = t("Start Download (${selectedHeight}p)", "بدء التنزيل (${selectedHeight}p)"),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
