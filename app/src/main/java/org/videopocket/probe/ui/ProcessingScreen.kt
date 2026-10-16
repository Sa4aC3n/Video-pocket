package org.videopocket.probe.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videopocket.probe.core.ClipRequest
import org.videopocket.probe.core.SavedMedia
import org.videopocket.probe.processing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ProcessingScreen(arabic: Boolean, initialUri: String? = null, initialTitle: String? = null, model: ProcessingModel = viewModel()) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by rememberSaveable { mutableStateOf(initialUri) }
    var title by rememberSaveable { mutableStateOf(initialTitle ?: "Media") }
    var sourceBytes by rememberSaveable { mutableLongStateOf(0L) }
    var mode by rememberSaveable { mutableStateOf(ProcessingMode.COMPRESS) }
    var preset by rememberSaveable { mutableStateOf(CompressionPreset.BALANCED) }
    var format by rememberSaveable { mutableStateOf("mp4") }
    var start by rememberSaveable { mutableStateOf("0:00") }
    var end by rememberSaveable { mutableStateOf("") }
    var sizeMb by rememberSaveable { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selecting = true
            scope.launch {
                try {
                    val media = withContext(Dispatchers.IO) { SavedMedia.read(context, selected.toString()) }
                    uri = selected.toString(); title = media.name.substringBeforeLast('.')
                    sourceBytes = media.bytes
                    end = media.durationMs?.let { String.format(Locale.US, "%.3f", it / 1000.0) } ?: ""
                    inputError = false
                } catch (_: Exception) { inputError = true }
                finally { selecting = false }
            }
        }
    }
    LaunchedEffect(Unit) { model.checkCapabilities() }
    LaunchedEffect(initialUri) {
        if (initialUri != null && !model.busy) {
            uri = initialUri
            title = initialTitle ?: "Media"
            // Try to read source size
            try {
                val media = withContext(Dispatchers.IO) { SavedMedia.read(context, initialUri) }
                sourceBytes = media.bytes
            } catch (_: Exception) {}
        }
    }
    val audioOnly = mode == ProcessingMode.EXTRACT_AUDIO
    val formats = model.capabilities?.formats(audioOnly).orEmpty()
    LaunchedEffect(audioOnly, formats) { if (format !in formats) format = formats.firstOrNull().orEmpty() }
    val validStart = ClipRequest.parseTimestamp(start)
    val validEnd = ClipRequest.parseTimestamp(end)
    val target = sizeMb.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 4096 }?.let { (it * 1024 * 1024).toLong() }
    val rangeValid = mode != ProcessingMode.TRIM || validStart != null && validEnd != null && validEnd > validStart
    val targetValid = mode != ProcessingMode.COMPRESS || sizeMb.isBlank() || target != null
    val available = if (mode == ProcessingMode.COMPRESS) "mp4" in formats else format in formats

    // Estimated output size for the selected preset (rough heuristic shown as "Estimated")
    val estimatedSizeMb: Double? = remember(sourceBytes, preset, mode) {
        if (mode != ProcessingMode.COMPRESS || sourceBytes <= 0) null
        else {
            val ratio = when (preset) {
                CompressionPreset.HIGH_QUALITY -> 0.75
                CompressionPreset.BALANCED    -> 0.50
                CompressionPreset.SMALL_FILE  -> 0.30
                CompressionPreset.SMART       -> 0.45
            }
            sourceBytes * ratio / (1024 * 1024)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(t("Media Studio", "استوديو الوسائط"), style = MaterialTheme.typography.headlineMedium)
        Text(t("Process files on your device. Your original is kept.", "معالجة محلية على جهازك مع الاحتفاظ بالملف الأصلي."))
        OutlinedButton(onClick = { launcher.launch(arrayOf("video/*", "audio/*")) }, enabled = !model.busy && !selecting) {
            Text(t("Choose a local file", "اختيار ملف من الجهاز"))
        }
        if (uri != null) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sourceBytes > 0) {
                Text(
                    t("Source: ${formatBytes(sourceBytes)}", "المصدر: ${formatBytes(sourceBytes)}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProcessingMode.entries.forEach { choice ->
                FilterChip(selected = mode == choice, enabled = !model.busy, onClick = { mode = choice }, label = {
                    Text(when (choice) {
                        ProcessingMode.COMPRESS     -> t("Compress", "ضغط")
                        ProcessingMode.TRIM         -> t("Trim", "قص")
                        ProcessingMode.EXTRACT_AUDIO -> t("Extract audio", "استخراج الصوت")
                        ProcessingMode.CONVERT      -> t("Convert", "تحويل")
                    })
                })
            }
        }
        if (!audioOnly) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompressionPreset.entries.forEach { choice ->
                    FilterChip(selected = preset == choice, enabled = !model.busy, onClick = { preset = choice }, label = {
                        Text(when (choice) {
                            CompressionPreset.HIGH_QUALITY -> t("High quality", "جودة عالية")
                            CompressionPreset.BALANCED    -> t("Balanced", "متوازن")
                            CompressionPreset.SMALL_FILE  -> t("Small file", "حجم صغير")
                            CompressionPreset.SMART       -> t("Smart ✨", "ذكي ✨")
                        })
                    })
                }
            }
            // Description for the selected preset
            val presetDesc = when (preset) {
                CompressionPreset.HIGH_QUALITY -> t("Up to 1080p · Best visual quality · Larger file", "حتى 1080p · أفضل جودة بصرية · حجم أكبر")
                CompressionPreset.BALANCED    -> t("Up to 720p · Good quality / size balance (recommended)", "حتى 720p · توازن مثالي بين الجودة والحجم (موصى به)")
                CompressionPreset.SMALL_FILE  -> t("Up to 540p · Smallest possible file · Lower quality", "حتى 540p · أصغر حجم ممكن · جودة أقل")
                CompressionPreset.SMART       -> t("Analyses the video and automatically picks the best settings", "يحلل الفيديو ويختار الإعدادات الأمثل تلقائياً")
            }
            Text(presetDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (mode == ProcessingMode.COMPRESS) {
            OutlinedTextField(value = sizeMb, onValueChange = { sizeMb = it }, enabled = !model.busy,
                label = { Text(t("Optional maximum size (MiB)", "الحد الأقصى الاختياري للحجم (ميبيبايت)")) },
                isError = !targetValid, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(t("The result must be smaller than the source. A size limit is checked after encoding.", "يجب أن يكون الناتج أصغر من الأصل. يتم التحقق من حد الحجم بعد المعالجة."))
            // Show estimated output size
            if (estimatedSizeMb != null) {
                Text(
                    t("Estimated output: ~${String.format(Locale.US, "%.1f", estimatedSizeMb)} MiB",
                      "الحجم التقديري للناتج: ~${String.format(Locale.US, "%.1f", estimatedSizeMb)} ميبيبايت"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                formats.forEach { extension ->
                    FilterChip(selected = format == extension, enabled = !model.busy, onClick = { format = extension }, label = { Text(extension.uppercase(Locale.ROOT)) })
                }
            }
        }
        if (mode == ProcessingMode.TRIM) {
            OutlinedTextField(start, { start = it }, label = { Text(t("Start (seconds or mm:ss)", "البداية (ثوانٍ أو دقائق:ثوانٍ)")) }, enabled = !model.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(end, { end = it }, label = { Text(t("End (seconds or mm:ss)", "النهاية (ثوانٍ أو دقائق:ثوانٍ)")) }, enabled = !model.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (model.checking || selecting) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(t("Checking file and available encoders…", "فحص الملف وبرامج الترميز المتاحة…"))
        } else if (!available) {
            Text(t("This format has no verified encoder on this device.", "لا يوجد برنامج ترميز تم التحقق منه لهذا التنسيق على الجهاز."))
        }
        if (inputError) Text(t("Unable to read this file.", "تعذر قراءة هذا الملف."), color = MaterialTheme.colorScheme.error)
        if (model.busy) {
            val progress = model.update.percent
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text(processingMessage(model.update.stage, arabic) + (progress?.let { " · ${it.toInt()}%" } ?: ""),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            OutlinedButton(onClick = model::cancel, enabled = model.update.stage !in setOf("SAVING", "CANCELLING")) { Text(t("Cancel", "إلغاء")) }
        } else {
            Button(onClick = {
                model.start(ProcessingRequest(uri!!, title, mode, preset,
                    if (mode == ProcessingMode.COMPRESS) "mp4" else format,
                    if (mode == ProcessingMode.TRIM) validStart!! else 0.0,
                    if (mode == ProcessingMode.TRIM) validEnd else null,
                    if (mode == ProcessingMode.COMPRESS) target else null))
            }, enabled = uri != null && available && rangeValid && targetValid && !model.checking && !selecting,
                modifier = Modifier.fillMaxWidth()) { Text(t("Start processing", "بدء المعالجة")) }
        }
        model.error?.let { Text(processingMessage(it, arabic), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        model.result?.let { result ->
            val saved = result.media.bytes
            val reduction = if (result.inputBytes > 0 && saved > 0) {
                ((1.0 - saved.toDouble() / result.inputBytes) * 100).toInt()
            } else 0
            Text(t("Saved to your library", "تم الحفظ في المكتبة"), style = MaterialTheme.typography.titleMedium)
            Text(t(
                "Before: ${formatBytes(result.inputBytes)} → After: ${formatBytes(saved)} (−$reduction%)\nEncoder: ${result.encoder}",
                "قبل: ${formatBytes(result.inputBytes)} ← بعد: ${formatBytes(saved)} (−$reduction%)\nالمرمِّز: ${result.encoder}"
            ))
        }
    }
}

/** Formats byte count to a human-readable string (KB / MiB). */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MiB", bytes / 1_048_576.0)
    bytes >= 1_024    -> String.format(Locale.US, "%.0f KB", bytes / 1_024.0)
    else              -> "$bytes B"
}

private fun processingMessage(code: String, arabic: Boolean): String {
    val pair = when (code) {
        "COPYING"   -> "Reading source…"           to "قراءة الملف…"
        "ENCODING"  -> "Processing…"               to "جاري المعالجة…"
        "OPTIMIZING" -> "Optimizing to meet size limit…" to "تحسين الناتج لحد الحجم…"
        "VALIDATING" -> "Verifying output…"         to "التحقق من الناتج…"
        "SAVING"    -> "Saving…"                   to "جاري الحفظ…"
        "COMPLETE"  -> "Complete"                  to "اكتملت المعالجة"
        "CANCELLING" -> "Cancelling…"               to "جاري الإلغاء…"
        "CANCELLED" -> "Cancelled; original kept." to "تم الإلغاء والاحتفاظ بالأصل."
        "NO_SIZE_REDUCTION" -> "The result was not smaller. Original kept; no extra copy saved." to "الناتج لم يكن أصغر. تم الاحتفاظ بالأصل دون حفظ نسخة إضافية."
        "TARGET_TOO_SMALL", "TARGET_NOT_REACHED" -> "Cannot meet this size limit. Try a larger limit or another preset." to "تعذر الوصول لهذا الحجم. جرّب حدًا أكبر أو إعداد جودة آخر."
        "INVALID_RANGE", "DURATION_UNAVAILABLE" -> "Check the start and end times and source duration." to "راجع البداية والنهاية ومدة الملف."
        "SPACE"     -> "Insufficient free storage; original kept." to "المساحة غير كافية؛ تم الاحتفاظ بالأصل."
        "HDR_UNSUPPORTED" -> "HDR conversion is not supported yet; original kept." to "تحويل HDR غير مدعوم حاليًا؛ تم الاحتفاظ بالأصل."
        "DRM_UNSUPPORTED" -> "Protected media cannot be processed." to "لا يمكن معالجة الوسائط المحمية."
        "MISSING_TRACK" -> "This file does not have the required video or audio track." to "الملف لا يحتوي على مسار الفيديو أو الصوت المطلوب."
        "FORMAT_UNAVAILABLE", "ENGINE_UNAVAILABLE" -> "No verified encoder is available for this operation." to "لا يوجد برنامج ترميز تم التحقق منه لهذه العملية."
        "PAGE_SIZE_UNSUPPORTED" -> "This media engine requires 4 KB memory pages. This device needs an updated native engine." to "يتطلب محرك الوسائط صفحات ذاكرة بحجم ٤ كيلوبايت. يحتاج هذا الجهاز إلى إصدار متوافق من المحرك."
        "TIMEOUT"   -> "Processing timed out. Try a shorter clip or smaller preset." to "انتهت مهلة المعالجة. جرّب مقطعًا أقصر أو إعدادًا أصغر."
        else        -> "Processing failed; original kept. Try another file or format." to "تعذرت المعالجة وتم الاحتفاظ بالأصل. جرّب ملفًا أو تنسيقًا آخر."
    }
    return if (arabic) pair.second else pair.first
}
