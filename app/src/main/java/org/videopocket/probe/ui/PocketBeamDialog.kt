package org.videopocket.probe.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videopocket.probe.beam.*
import org.videopocket.probe.core.PocketLibraryItem
import org.videopocket.probe.core.PocketLibraryManager

@Composable
fun PocketBeamDialog(
    initialItem: PocketLibraryItem? = null,
    libraryItems: List<PocketLibraryItem>,
    arabic: Boolean,
    onDismiss: () -> Unit,
    onPlayReceived: (Uri, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun t(en: String, ar: String) = if (arabic) ar else en

    var selectedTab by remember { mutableStateOf(if (initialItem != null) 0 else 0) } // 0 = Send, 1 = Receive
    var selectedItem by remember { mutableStateOf<PocketLibraryItem?>(initialItem ?: libraryItems.firstOrNull()) }

    // Server State
    val beamServer = remember { PocketBeamServer(context) }
    var activeSession by remember { mutableStateOf<BeamSession?>(null) }
    var serverProgress by remember { mutableStateOf<BeamProgress?>(null) }
    var isServing by remember { mutableStateOf(false) }

    // Client State
    val beamClient = remember { PocketBeamClient(context) }
    var receiveUrlInput by remember { mutableStateOf("") }
    var clientProgress by remember { mutableStateOf<BeamProgress?>(null) }
    var clientState by remember { mutableStateOf<BeamState>(BeamState.Idle) }

    DisposableEffect(Unit) {
        onDispose {
            beamServer.stopServing()
            beamClient.cancel()
        }
    }

    Dialog(
        onDismissRequest = {
            beamServer.stopServing()
            beamClient.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                text = t("Pocket Beam ⚡", "النقل الفضائي ⚡"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = t("Zero-Internet Local WiFi Transfer", "مشاركة فورية دون إنترنت بسرعة البرق"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Mode Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    indicator = {},
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(t("Send Video", "إرسال مقطع"), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(t("Receive", "استقبال"), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Body content based on tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedTab == 0) {
                        // SEND MODE
                        SendModeContent(
                            libraryItems = libraryItems,
                            selectedItem = selectedItem,
                            onSelectItem = { item ->
                                selectedItem = item
                                if (isServing) {
                                    beamServer.stopServing()
                                    isServing = false
                                    activeSession = null
                                }
                            },
                            isServing = isServing,
                            activeSession = activeSession,
                            serverProgress = serverProgress,
                            arabic = arabic,
                            onStartServing = {
                                val item = selectedItem ?: return@SendModeContent
                                scope.launch(Dispatchers.IO) {
                                    val session = beamServer.startServing(
                                        fileUri = item.contentUri,
                                        fileName = item.title.let { if (it.endsWith(".${item.format}")) it else "$it.${item.format}" },
                                        mimeType = if (org.videopocket.probe.core.MediaTypes.isAudio(item.format)) "audio/mpeg" else "video/mp4",
                                        fileSize = item.fileSizeBytes
                                    ) { prog ->
                                        serverProgress = prog
                                    }
                                    withContext(Dispatchers.Main) {
                                        activeSession = session
                                        isServing = true
                                    }
                                }
                            },
                            onStopServing = {
                                beamServer.stopServing()
                                isServing = false
                                activeSession = null
                                serverProgress = null
                            }
                        )
                    } else {
                        // RECEIVE MODE
                        ReceiveModeContent(
                            arabic = arabic,
                            inputUrl = receiveUrlInput,
                            onInputChange = { receiveUrlInput = it },
                            clientState = clientState,
                            progress = clientProgress,
                            onStartReceive = {
                                val session = BeamSession.parseBeamUri(receiveUrlInput)
                                if (session == null) {
                                    Toast.makeText(context, t("Invalid Beam URL or IP", "رابط أو عنوان غير صالح"), Toast.LENGTH_SHORT).show()
                                    return@ReceiveModeContent
                                }

                                scope.launch {
                                    clientState = BeamState.Transferring(BeamProgress(0, session.fileSizeBytes, 0, 0f))
                                    val result = beamClient.receiveFile(session) { prog ->
                                        clientProgress = prog
                                        clientState = BeamState.Transferring(prog)
                                    }

                                    result.onSuccess { uri ->
                                        clientState = BeamState.Success(session.fileName, session.fileSizeBytes, uri, true)
                                        Toast.makeText(context, t("Video received & saved successfully! 🎉", "تم استلام الفيديو وحفظه بنجاح! 🎉"), Toast.LENGTH_LONG).show()
                                    }.onFailure { err ->
                                        clientState = BeamState.Error(err.message ?: "Failed")
                                    }
                                }
                            },
                            onCancelReceive = {
                                beamClient.cancel()
                                clientState = BeamState.Idle
                                clientProgress = null
                            },
                            onPlay = { uri, name ->
                                onDismiss()
                                onPlayReceived(uri, name)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SendModeContent(
    libraryItems: List<PocketLibraryItem>,
    selectedItem: PocketLibraryItem?,
    onSelectItem: (PocketLibraryItem) -> Unit,
    isServing: Boolean,
    activeSession: BeamSession?,
    serverProgress: BeamProgress?,
    arabic: Boolean,
    onStartServing: () -> Unit,
    onStopServing: () -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String) = if (arabic) ar else en
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isServing) {
            // File Selector
            Text(
                text = t("Select media file to beam:", "اختر المقطع المراد إرساله:"),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth()
            )

            if (libraryItems.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(t("No videos or audio in library yet.", "لا توجد مقاطع محفوظة في المكتبة بعد."))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    libraryItems.take(5).forEach { item ->
                        val isSelected = selectedItem?.id == item.id
                        Card(
                            onClick = { onSelectItem(item) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RadioButton(selected = isSelected, onClick = { onSelectItem(item) })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val mb = "%.1f MB".format(item.fileSizeBytes / (1024.0 * 1024.0))
                                    Text("${item.format.uppercase()} • $mb", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onStartServing,
                enabled = selectedItem != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(t("Start Beam Server ⚡", "بدء الإرسال الفضائي ⚡"), fontWeight = FontWeight.Bold)
            }
        } else if (activeSession != null) {
            // Live Server Active
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = t("Ready for instant transfer!", "جاهز للإرسال والاستلام الفوري!"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // QR Code
                    val qrEncoder = remember(activeSession.webLandingUrl) {
                        runCatching { QrCodeEncoder.encodeText(activeSession.webLandingUrl) }.getOrNull()
                    }

                    if (qrEncoder != null) {
                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val moduleCount = qrEncoder.size
                                val cellSize = size.width / moduleCount
                                for (y in 0 until moduleCount) {
                                    for (x in 0 until moduleCount) {
                                        if (qrEncoder.getModule(x, y)) {
                                            drawRoundRect(
                                                color = Color.Black,
                                                topLeft = Offset(x * cellSize, y * cellSize),
                                                size = Size(cellSize, cellSize),
                                                cornerRadius = CornerRadius(cellSize * 0.2f, cellSize * 0.2f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = t("Scan QR Code with any phone camera or open in browser:", "امسح الرمز بكاميرا أي هاتف أو افتح الرابط في المتصفح:"),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )

                    // URL Copy Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Beam URL", activeSession.webLandingUrl))
                                Toast.makeText(context, t("Link copied!", "تم نسخ الرابط!"), Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = activeSession.webLandingUrl,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    }

                    // Transfer Progress (if client connected)
                    if (serverProgress != null && serverProgress.speedBytesPerSec > 0) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val speedMb = serverProgress.speedBytesPerSec / (1024.0 * 1024.0)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(t("Sending at full speed:", "جاري الإرسال بسرعة:"), style = MaterialTheme.typography.bodySmall)
                                Text("%.1f MB/s".format(speedMb), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = { serverProgress.percent },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onStopServing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(t("Stop Server", "إيقاف الإرسال"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiveModeContent(
    arabic: Boolean,
    inputUrl: String,
    onInputChange: (String) -> Unit,
    clientState: BeamState,
    progress: BeamProgress?,
    onStartReceive: () -> Unit,
    onCancelReceive: () -> Unit,
    onPlay: (Uri, String) -> Unit
) {
    fun t(en: String, ar: String) = if (arabic) ar else en

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (clientState) {
            is BeamState.Idle, is BeamState.Error -> {
                Text(
                    text = t("Connect to Sender Device:", "الاتصال بجهاز الراسل:"),
                    style = MaterialTheme.typography.labelLarge
                )

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = onInputChange,
                    label = { Text(t("Enter Beam Link or IP address", "أدخل رابط النقل أو عنوان IP للراسل")) },
                    placeholder = { Text("http://192.168.43.1:8990/beam") },
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                if (clientState is BeamState.Error) {
                    Text(
                        text = "${t("Error:", "خطأ:")} ${clientState.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = onStartReceive,
                    enabled = inputUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Connect & Receive ⚡", "اتصال واستلام المقطع ⚡"), fontWeight = FontWeight.Bold)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(t("💡 Instructions:", "💡 تعليمات الاستلام:"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text(t("1. Make sure both devices are on the same Wi-Fi or sender's Hotspot.", "1. تأكد من اتصال الجهازين بنفس شبكة الواي فاي أو نقطة اتصال الراسل."), style = MaterialTheme.typography.labelSmall)
                        Text(t("2. Copy or type the IP link shown on the sender's screen.", "2. اكتب أو الصق الرابط الظاهر على شاشة الراسل."), style = MaterialTheme.typography.labelSmall)
                        Text(t("3. The file will transfer directly at up to 70 MB/s.", "3. سيتم نقل الفيديو مباشرة وحفظه في التنزيلات بأقصى سرعة."), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            is BeamState.Transferring -> {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { progress?.percent ?: 0f },
                            modifier = Modifier.size(72.dp),
                            strokeWidth = 6.dp
                        )

                        val speedMb = (progress?.speedBytesPerSec ?: 0L) / (1024.0 * 1024.0)
                        val receivedMb = (progress?.bytesTransferred ?: 0L) / (1024.0 * 1024.0)
                        val totalMb = (progress?.totalBytes ?: 0L) / (1024.0 * 1024.0)

                        Text(
                            text = "%.1f MB/s".format(speedMb),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "${t("Transferring:", "جاري النقل:")} %.1f / %.1f MB (%.0f%%)".format(receivedMb, totalMb, (progress?.percent ?: 0f) * 100),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedButton(
                            onClick = onCancelReceive,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(t("Cancel Transfer", "إلغاء الاستلام"))
                        }
                    }
                }
            }

            is BeamState.Success -> {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(56.dp)
                        )

                        Text(
                            text = t("Transfer Completed! 🎉", "تم استلام الملف بنجاح! 🎉"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = clientState.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = t("Saved to: Download/VideoPocket (SHA-256 Verified ✅)", "تم الحفظ في: Download/VideoPocket (تم فحص السلامة ✅)"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        if (clientState.savedUri != null) {
                            Button(
                                onClick = { onPlay(clientState.savedUri, clientState.fileName) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(t("Play Video", "تشغيل المقطع الآن"))
                            }
                        }
                    }
                }
            }

            else -> {}
        }
    }
}
