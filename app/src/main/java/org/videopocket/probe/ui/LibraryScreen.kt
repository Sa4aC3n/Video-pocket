package org.videopocket.probe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.videopocket.probe.ProbeModel
import org.videopocket.probe.core.PocketLibraryItem
import org.videopocket.probe.core.PocketLibraryManager
import org.videopocket.probe.storage.StorageBreakdown
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.*

@Composable
fun LibraryScreen(
    model: ProbeModel,
    arabic: Boolean,
    onNavigateHome: () -> Unit,
    onNavigateVault: () -> Unit = {},
    onProcess: (PocketLibraryItem) -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String) = if (arabic) ar else en
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("newest") }
    var collection by rememberSaveable { mutableStateOf("") }
    var rename by remember { mutableStateOf<PocketLibraryItem?>(null) }
    var delete by remember { mutableStateOf<PocketLibraryItem?>(null) }
    var editCollections by remember { mutableStateOf<PocketLibraryItem?>(null) }
    var playingItem by remember { mutableStateOf<PocketLibraryItem?>(null) }
    var beamItem by remember { mutableStateOf<PocketLibraryItem?>(null) }
    var showPocketBeamDialog by remember { mutableStateOf(false) }

    val all = model.libraryItems
    val collections = remember(all) { all.flatMap { it.collections }.distinct().sorted() }
    val filtered = remember(all, query, filter, sort, collection) {
        val rows = all.filter { item ->
            val audio = org.videopocket.probe.core.MediaTypes.isAudio(item.format)
            (when (filter) {
                "video" -> !audio; "audio" -> audio; "clips" -> item.isClip; "favorites" -> item.isFavorite; else -> true
            }) && (collection.isBlank() || collection in item.collections) &&
                listOf(item.title, item.format, item.platform.orEmpty(), item.collections.joinToString(" ")).any { it.contains(query.trim(), true) }
        }
        when (sort) { "title" -> rows.sortedBy { it.title.lowercase(Locale.ROOT) }; "size" -> rows.sortedByDescending { it.fileSizeBytes }; "oldest" -> rows.sortedBy { it.createdAt }; else -> rows.sortedByDescending { it.createdAt } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(t("Your Pocket", "مكتبتي"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(t("${all.size} saved files", "${all.size} ملف محفوظ"))
        }
        if (model.libraryError || model.libraryActionError) item {
            Text(t("Library action failed. Existing records have been kept. Check storage and file access.", "تعذرت عملية المكتبة مع الاحتفاظ بالسجلات الموجودة. راجع المساحة وصلاحية الوصول للملفات."), color = MaterialTheme.colorScheme.error)
        }

        // Pocket Beam Local WiFi Transfer Card
        item {
            Card(
                onClick = {
                    beamItem = null
                    showPocketBeamDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("Pocket Beam ⚡ (WiFi P2P)", "النقل الفضائي ⚡ (بدون إنترنت)"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = t("Ultra-fast 70MB/s local transfer & QR sharing", "مشاركة فورية بسرعة 70 ميجابايت/ثانية بالواي فاي ورمز QR"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Encrypted Vault Card
        item {
            Card(
                onClick = onNavigateVault,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("Encrypted Vault", "الخزنة المشفرة بالبصمة"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = t("AES-256-GCM hardware protection", "تشفير وحماية بنكية فائقة للملفات الحساسة"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, label = { Text(t("Search title, format or collection", "البحث بالعنوان أو التنسيق أو المجموعة")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to t("All", "الكل"), "video" to t("Videos", "فيديو"), "audio" to t("Audio", "صوت"), "clips" to t("Clips", "مقاطع"), "favorites" to t("Favorites", "المفضلة")).forEach { (key, label) ->
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                }
            }
        }
        if (collections.isNotEmpty()) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = collection.isBlank(), onClick = { collection = "" }, label = { Text(t("All collections", "كل المجموعات")) })
                collections.forEach { name -> FilterChip(selected = collection == name, onClick = { collection = name }, label = { Text(name) }) }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("newest" to t("Newest", "الأحدث"), "oldest" to t("Oldest", "الأقدم"), "title" to t("Title", "العنوان"), "size" to t("Size", "الحجم")).forEach { (key, label) ->
                    FilterChip(selected = sort == key, onClick = { sort = key }, label = { Text(label) })
                }
            }
        }
        if (filtered.isEmpty()) item {
            Text(t("No matching saved media.", "لا توجد وسائط محفوظة مطابقة."))
            TextButton(onClick = onNavigateHome) { Text(t("Add a download", "إضافة تنزيل")) }
        }
        items(filtered, key = { it.id }) { item ->
            PocketItemCard(
                item = item,
                arabic = arabic,
                onPlay = { playingItem = item },
                onShare = { PocketLibraryManager.shareItem(context, item) },
                onBeam = {
                    beamItem = item
                    showPocketBeamDialog = true
                },
                onToggleFavorite = { model.toggleFavorite(item.id) },
                onRename = { rename = item },
                onDelete = { delete = item },
                onProcess = { onProcess(item) },
                onCollections = { editCollections = item }
            )
        }
    }

    if (showPocketBeamDialog) {
        PocketBeamDialog(
            initialItem = beamItem,
            libraryItems = all,
            arabic = arabic,
            onDismiss = { showPocketBeamDialog = false },
            onPlayReceived = { uri, name ->
                model.playMedia(uri, name)
            }
        )
    }

    playingItem?.let { item ->
        BuiltInPlayerDialog(
            item = item,
            onDismiss = { playingItem = null },
            arabic = arabic
        )
    }
    rename?.let { item ->
        var text by remember(item.id) { mutableStateOf(item.title) }
        AlertDialog(onDismissRequest = { rename = null }, title = { Text(t("Rename in library", "تعديل الاسم في المكتبة")) },
            text = { OutlinedTextField(text, { text = it.take(160) }, label = { Text(t("Title", "العنوان")) }) },
            confirmButton = { TextButton(onClick = { model.renameLibraryItem(item.id, text); rename = null }, enabled = text.isNotBlank()) { Text(t("Save", "حفظ")) } },
            dismissButton = { TextButton(onClick = { rename = null }) { Text(t("Cancel", "إلغاء")) } })
    }
    delete?.let { item ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text(t("Delete file?", "حذف الملف؟")) },
            text = { Text(t("Permanently delete ${item.title} from your device?", "حذف ${item.title} نهائيًا من الجهاز؟")) },
            confirmButton = { TextButton(onClick = { model.deleteLibraryItem(item.id); delete = null }) { Text(t("Delete", "حذف")) } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text(t("Cancel", "إلغاء")) } })
    }
    editCollections?.let { item ->
        var names by remember(item.id) { mutableStateOf(item.collections.joinToString(", ")) }
        AlertDialog(onDismissRequest = { editCollections = null }, title = { Text(t("Collections", "المجموعات")) },
            text = { OutlinedTextField(names, { names = it.take(500) }, label = { Text(t("Names separated by commas", "أسماء تفصلها فاصلة")) }) },
            confirmButton = { TextButton(onClick = {
                model.updateCollections(item.id, names.split(',', '،').map { it.trim().take(50) }.filter { it.isNotBlank() }.distinct()); editCollections = null
            }) { Text(t("Save", "حفظ")) } },
            dismissButton = { TextButton(onClick = { editCollections = null }) { Text(t("Cancel", "إلغاء")) } })
    }
}

@Composable
fun PocketItemCard(
    item: PocketLibraryItem,
    arabic: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onBeam: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProcess: () -> Unit,
    onCollections: () -> Unit
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    var expandedMenu by remember { mutableStateOf(false) }

    val formattedSize = remember(item.fileSizeBytes) {
        val mb = item.fileSizeBytes / (1024.0 * 1024.0)
        if (item.fileSizeBytes <= 0) "—" else if (mb >= 1000) String.format(Locale.US, "%.2f GB", mb / 1024.0)
        else String.format(Locale.US, "%.1f MB", mb)
    }

    val formattedDate = remember(item.createdAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(item.createdAt))
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thumbnail or media badge
                if (!item.thumbnailUri.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailUri,
                        contentDescription = item.title,
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(70.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (item.isClip) Icons.Default.ContentCut
                                else if (org.videopocket.probe.core.MediaTypes.isAudio(item.format)) Icons.Default.Audiotrack
                                else Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Title and Meta
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.isClip) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "CLIP",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = item.resolution,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = item.format.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = "$formattedSize • ${item.duration} • $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // Favorite button
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = t("Favorite", "المفضلة"),
                        tint = if (item.isFavorite) Color(0xFFE91E63) else MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Play", "تشغيل"))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Beam button
                    IconButton(onClick = onBeam) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = t("Pocket Beam", "النقل الفضائي"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = t("Share", "مشاركة"))
                    }

                    Box {
                        IconButton(onClick = { expandedMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = t("More", "المزيد"))
                        }
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(t("Pocket Beam ⚡", "إرسال فضائي ⚡")) },
                                leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { expandedMenu = false; onBeam() }
                            )
                            DropdownMenuItem(
                                text = { Text(t("Compress / edit", "ضغط / تعديل")) },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                onClick = { expandedMenu = false; onProcess() }
                            )
                            DropdownMenuItem(text = { Text(t("Collections", "المجموعات")) }, onClick = { expandedMenu = false; onCollections() })
                            DropdownMenuItem(
                                text = { Text(t("Rename", "إعادة تسمية")) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { expandedMenu = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = { Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { expandedMenu = false; onDelete() }
                            )
                        }
                    }
                }
            }
        }
    }
}
