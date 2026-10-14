package org.videopocket.probe.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.videopocket.probe.core.ClipRequest
import org.videopocket.probe.core.MediaInfo
import org.videopocket.probe.core.QualityTier

@Composable
fun DualHandleTimeline(
    totalDuration: Double,
    startSeconds: Double,
    endSeconds: Double,
    onRangeChanged: (Double, Double) -> Unit,
    arabic: Boolean
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    val totalSafe = totalDuration.coerceAtLeast(1.0).toFloat()
    var sliderValues by remember(startSeconds, endSeconds, totalDuration) {
        mutableStateOf(startSeconds.toFloat()..endSeconds.toFloat().coerceAtMost(totalSafe))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Visual indicator card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = t("Start", "البداية"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = ClipRequest.formatSeconds(startSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "✂️ " + ClipRequest.formatSeconds(endSeconds - startSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = t("Clip Duration", "المدة المحددة"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = t("End", "النهاية"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = ClipRequest.formatSeconds(endSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Custom Slider with Range
        RangeSlider(
            value = sliderValues,
            onValueChange = { range ->
                val newStart = range.start.toDouble().coerceAtLeast(0.0)
                val newEnd = range.endInclusive.toDouble().coerceAtMost(totalDuration)
                if (newEnd - newStart >= 0.5) {
                    sliderValues = range
                    onRangeChanged(newStart, newEnd)
                }
            },
            valueRange = 0f..totalSafe,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "00:00",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = ClipRequest.formatSeconds(totalDuration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun PreciseTimeSection(
    startSeconds: Double,
    endSeconds: Double,
    totalDuration: Double,
    onStartChanged: (Double) -> Unit,
    onEndChanged: (Double) -> Unit,
    arabic: Boolean
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    var showStartDialog by remember { mutableStateOf(false) }
    var showEndDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = t("Fine Precision Controls", "الضبط الدقيق للوقت"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        // Start Stepper Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = t("Start: ", "البداية: "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                FilledTonalButton(
                    onClick = { showStartDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(ClipRequest.formatSeconds(startSeconds), fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedIconButton(
                    onClick = { onStartChanged((startSeconds - 5.0).coerceAtLeast(0.0)) },
                    modifier = Modifier.size(36.dp)
                ) { Text("-5s", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                OutlinedIconButton(
                    onClick = { onStartChanged((startSeconds - 1.0).coerceAtLeast(0.0)) },
                    modifier = Modifier.size(36.dp)
                ) { Text("-1s", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                OutlinedIconButton(
                    onClick = { onStartChanged((startSeconds + 1.0).coerceAtMost(endSeconds - 0.5)) },
                    modifier = Modifier.size(36.dp)
                ) { Text("+1s", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                OutlinedIconButton(
                    onClick = { onStartChanged((startSeconds + 5.0).coerceAtMost(endSeconds - 0.5)) },
                    modifier = Modifier.size(36.dp)
                ) { Text("+5s", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }

        // End Stepper Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = t("End: ", "النهاية: "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                FilledTonalButton(
                    onClick = { showEndDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(ClipRequest.formatSeconds(endSeconds), fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedIconButton(
                    onClick = { onEndChanged((endSeconds - 5.0).coerceAtLeast(startSeconds + 0.5)) },
                    modifier = Modifier.size(36.dp)
                ) { Text("-5s", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                OutlinedIconButton(
                    onClick = { onEndChanged((endSeconds - 1.0).coerceAtLeast(startSeconds + 0.5)) },
                    modifier = Modifier.size(36.dp)
                ) { Text("-1s", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                OutlinedIconButton(
                    onClick = { onEndChanged((endSeconds + 1.0).coerceAtMost(totalDuration)) },
                    modifier = Modifier.size(36.dp)
                ) { Text("+1s", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                OutlinedIconButton(
                    onClick = { onEndChanged((endSeconds + 5.0).coerceAtMost(totalDuration)) },
                    modifier = Modifier.size(36.dp)
                ) { Text("+5s", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showStartDialog) {
        TimeInputDialog(
            title = t("Enter Start Time", "أدخل وقت البداية"),
            initialSeconds = startSeconds,
            maxSeconds = endSeconds - 0.5,
            onConfirm = { onStartChanged(it); showStartDialog = false },
            onDismiss = { showStartDialog = false },
            arabic = arabic
        )
    }

    if (showEndDialog) {
        TimeInputDialog(
            title = t("Enter End Time", "أدخل وقت النهاية"),
            initialSeconds = endSeconds,
            minSeconds = startSeconds + 0.5,
            maxSeconds = totalDuration,
            onConfirm = { onEndChanged(it); showEndDialog = false },
            onDismiss = { showEndDialog = false },
            arabic = arabic
        )
    }
}

@Composable
fun TimeInputDialog(
    title: String,
    initialSeconds: Double,
    minSeconds: Double = 0.0,
    maxSeconds: Double = Double.MAX_VALUE,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
    arabic: Boolean
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    var text by remember { mutableStateOf(ClipRequest.formatSeconds(initialSeconds)) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t("Format: MM:SS or HH:MM:SS", "الصيغة: ثواني:دقائق أو ثواني:دقائق:ساعات"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        errorMsg = null
                    },
                    isError = errorMsg != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = ClipRequest.parseTimestamp(text)
                when {
                    parsed == null -> errorMsg = t("Invalid time format", "صيغة الوقت غير صالحة")
                    parsed < minSeconds -> errorMsg = t("Must be >= ", "يجب أن يكون >= ") + ClipRequest.formatSeconds(minSeconds)
                    parsed > maxSeconds -> errorMsg = t("Must be <= ", "يجب أن يكون <= ") + ClipRequest.formatSeconds(maxSeconds)
                    else -> onConfirm(parsed)
                }
            }) {
                Text(t("Save", "حفظ"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel", "إلغاء")) }
        }
    )
}

@Composable
fun QuickSelectionChips(
    totalDuration: Double,
    onApplyRange: (Double, Double) -> Unit,
    arabic: Boolean
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = t("⚡ Quick Presets", "⚡ اختصارات سريعة"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (totalDuration >= 15.0) {
                FilterChip(
                    selected = false,
                    onClick = {
                        val s = (totalDuration - 15.0).coerceAtLeast(0.0)
                        onApplyRange(s, totalDuration)
                    },
                    label = { Text(t("Last 15s", "آخر ١٥ ثانية")) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        onApplyRange(0.0, 15.0.coerceAtMost(totalDuration))
                    },
                    label = { Text(t("First 15s", "أول ١٥ ثانية")) }
                )
            }
            if (totalDuration >= 30.0) {
                FilterChip(
                    selected = false,
                    onClick = {
                        val s = (totalDuration - 30.0).coerceAtLeast(0.0)
                        onApplyRange(s, totalDuration)
                    },
                    label = { Text(t("Last 30s", "آخر ٣٠ ثانية")) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        onApplyRange(0.0, 30.0.coerceAtMost(totalDuration))
                    },
                    label = { Text(t("First 30s", "أول ٣٠ ثانية")) }
                )
            }
            if (totalDuration >= 60.0) {
                FilterChip(
                    selected = false,
                    onClick = {
                        val s = (totalDuration - 60.0).coerceAtLeast(0.0)
                        onApplyRange(s, totalDuration)
                    },
                    label = { Text(t("Last 1 min", "آخر دقيقة")) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        onApplyRange(0.0, 60.0.coerceAtMost(totalDuration))
                    },
                    label = { Text(t("First 1 min", "أول دقيقة")) }
                )
            }
            if (totalDuration >= 300.0) {
                FilterChip(
                    selected = false,
                    onClick = {
                        onApplyRange(0.0, 300.0.coerceAtMost(totalDuration))
                    },
                    label = { Text(t("First 5 min", "أول ٥ دقائق")) }
                )
            }
        }
    }
}

@Composable
fun ClipPreviewPlayer(
    streamUrl: String?,
    startSeconds: Double,
    endSeconds: Double,
    arabic: Boolean
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf((startSeconds * 1000).toLong()) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var playerError by remember { mutableStateOf(false) }

    DisposableEffect(videoViewRef) {
        val view = videoViewRef
        onDispose { view?.stopPlayback() }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val vv = videoViewRef
            if (vv != null && vv.isPlaying) {
                val current = vv.currentPosition
                currentPositionMs = current.toLong()
                if (current >= (endSeconds * 1000).toInt()) {
                    vv.pause()
                    isPlaying = false
                }
            }
            delay(300)
        }
    }

    // Reset seek when startSeconds change
    LaunchedEffect(startSeconds) {
        val vv = videoViewRef
        if (vv != null && !isPlaying) {
            vv.seekTo((startSeconds * 1000).toInt())
            currentPositionMs = (startSeconds * 1000).toLong()
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = t("Preview Selected Clip", "معاينة الجزء المحدد"),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = "${ClipRequest.formatSeconds(currentPositionMs / 1000.0)} / ${ClipRequest.formatSeconds(endSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (!streamUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setOnPreparedListener { mp ->
                                    mp.isLooping = false
                                    seekTo((startSeconds * 1000).toInt())
                                }
                                setOnErrorListener { _, _, _ ->
                                    playerError = true
                                    isPlaying = false
                                    true
                                }
                                setOnCompletionListener { isPlaying = false }
                                videoViewRef = this
                            }
                        },
                        update = { view ->
                            if (view.tag != streamUrl) {
                                view.tag = streamUrl
                                playerError = false
                                isPlaying = false
                                view.setVideoURI(Uri.parse(streamUrl))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!isPlaying) {
                        IconButton(
                            onClick = {
                                val vv = videoViewRef
                                if (vv != null) {
                                    vv.seekTo((startSeconds * 1000).toInt())
                                    vv.start()
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), CircleShape)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = t("Play", "تشغيل"), tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    } else {
                        IconButton(
                            onClick = {
                                videoViewRef?.pause()
                                isPlaying = false
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = t("Pause", "إيقاف مؤقت"))
                        }
                    }
                }
                if (playerError) Text(t("Preview unavailable for this stream. You can try the saved file after downloading.", "تعذرت معاينة هذا البث. يمكنك تجربة الملف المحفوظ بعد التنزيل."), color = MaterialTheme.colorScheme.error)
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = t("No playable preview URL was provided by this source.", "لم يوفر المصدر رابطًا صالحًا للمعاينة."),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QualitySelectionSection(
    availableHeights: List<Int>,
    selectedHeight: Int,
    isBestQuality: Boolean,
    onSelectQuality: (Int, Boolean) -> Unit,
    arabic: Boolean
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = t("Select Video Quality", "جودة الفيديو"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = isBestQuality,
                onClick = { onSelectQuality(0, true) },
                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(t("✨ Best Available", "✨ أفضل جودة متاحة")) }
            )

            val heightsToShow = availableHeights.filter { it > 0 }.distinct().sortedDescending()
            heightsToShow.forEach { h ->
                val tier = QualityTier.fromHeight(h)
                FilterChip(
                    selected = !isBestQuality && selectedHeight == h,
                    onClick = { onSelectQuality(h, false) },
                    label = {
                        Text("${h}p • ${tier.category}")
                    }
                )
            }
        }
    }
}

@Composable
fun FormatSelectionSection(
    selectedFormat: String,
    isAudioOnly: Boolean,
    onSelectFormat: (String, Boolean) -> Unit,
    arabic: Boolean
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = t("Output Format", "صيغة الإخراج"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isAudioOnly && selectedFormat.equals("mp4", true),
                onClick = { onSelectFormat("mp4", false) },
                leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("MP4") }
            )
            FilterChip(
                selected = !isAudioOnly && selectedFormat.equals("webm", true),
                onClick = { onSelectFormat("webm", false) },
                leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("WebM") }
            )
            FilterChip(
                selected = isAudioOnly,
                onClick = { onSelectFormat("mp3", true) },
                leadingIcon = { Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(t("MP3 (Audio Clip)", "صوت فقط (MP3)")) }
            )
        }
    }
}

@Composable
fun ClipSummaryCard(
    clip: ClipRequest,
    totalDuration: Double,
    availableFormats: List<org.videopocket.probe.core.MediaFormat>,
    onDownloadClick: () -> Unit,
    busy: Boolean,
    arabic: Boolean
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    val tier = QualityTier.fromHeight(clip.height)

    // Re-encoding and variable bitrate make proportional guesses misleading.
    val estSizeMb = t("Known after processing", "يُعرف بعد المعالجة")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t("✂️ Clip Summary", "✂️ ملخص المقطع"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = if (clip.isAudioOnly) "MP3" else if (clip.height > 0) "${clip.height}p • ${tier.category}" else t("Source quality", "جودة المصدر"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryRow(
                    label = t("Selected Range", "الجزء المحدد"),
                    value = "${clip.startFormatted()} → ${clip.endFormatted()}"
                )
                SummaryRow(
                    label = t("Duration", "المدة"),
                    value = clip.durationFormatted()
                )
                SummaryRow(
                    label = t("Format", "الصيغة"),
                    value = if (clip.isAudioOnly) "MP3 Audio" else clip.format.uppercase()
                )
                SummaryRow(
                    label = t("Estimated Size", "الحجم المتوقع"),
                    value = estSizeMb
                )
            }

            Button(
                onClick = onDownloadClick,
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = t("Download Clip Now", "تحميل الجزء الآن"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
