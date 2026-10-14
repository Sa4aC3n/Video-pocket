package org.videopocket.probe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    model: ProbeModel,
    arabic: Boolean,
    onNavigateHome: () -> Unit,
    onProcess: (PocketLibraryItem) -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String) = if (arabic) ar else en

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("all") }
    var sortBy by rememberSaveable { mutableStateOf("newest") }

    var renameItemTarget by remember { mutableStateOf<PocketLibraryItem?>(null) }
    var deleteItemTarget by remember { mutableStateOf<PocketLibraryItem?>(null) }

    val rawItems = model.libraryItems

    val filteredItems = remember(rawItems, searchQuery, selectedFilter, sortBy) {
        var list = rawItems

        // Filter by category
        list = when (selectedFilter) {
            "videos" -> list.filter { !it.isClip && !it.format.equals("mp3", true) && !it.format.equals("m4a", true) }
            "audio" -> list.filter { it.format.equals("mp3", true) || it.format.equals("m4a", true) }
            "clips" -> list.filter { it.isClip }
            "favorites" -> list.filter { it.isFavorite }
            else -> list
        }

        // Search
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            list = list.filter {
                it.title.lowercase(Locale.ROOT).contains(q) ||
                it.format.lowercase(Locale.ROOT).contains(q) ||
                (it.platform?.lowercase(Locale.ROOT)?.contains(q) == true)
            }
        }

        // Sort
        when (sortBy) {
            "oldest" -> list.sortedBy { it.createdAt }
            "title" -> list.sortedBy { it.title.lowercase(Locale.ROOT) }
            "size" -> list.sortedByDescending { it.fileSizeBytes }
            else -> list.sortedByDescending { it.createdAt }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = t("Pocket", "مكتبتي"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${rawItems.size} " + t("saved items", "عنصراً محفوظاً"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        if (rawItems.isNotEmpty()) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(t("Search saved videos, audio, clips...", "ابحث في الملفات المحفوظة...")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = t("Clear", "مسح"))
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "all",
                    onClick = { selectedFilter = "all" },
                    label = { Text(t("All", "الكل")) }
                )
                FilterChip(
                    selected = selectedFilter == "videos",
                    onClick = { selectedFilter = "videos" },
                    label = { Text(t("Videos", "فيديوهات")) }
                )
                FilterChip(
                    selected = selectedFilter == "audio",
                    onClick = { selectedFilter = "audio" },
                    label = { Text(t("Audio", "صوتيات")) }
                )
                FilterChip(
                    selected = selectedFilter == "clips",
                    onClick = { selectedFilter = "clips" },
                    label = { Text(t("Clips", "المقاطع")) }
                )
                FilterChip(
                    selected = selectedFilter == "favorites",
                    onClick = { selectedFilter = "favorites" },
                    label = { Text(t("Favorites", "المفضلة")) },
                    leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            // Sort Options Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t("Sort by:", "ترتيب حسب:"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { sortBy = if (sortBy == "newest") "oldest" else "newest" }) {
                        Text(if (sortBy == "oldest") t("Oldest First", "الأقدم أولاً") else t("Newest First", "الأحدث أولاً"))
                    }
                    TextButton(onClick = { sortBy = if (sortBy == "size") "newest" else "size" }) {
                        Text(t("File Size", "الحجم"))
                    }
                }
            }

            // Items List
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t("No matches found for your search.", "لا توجد نتائج مطابقة لبحثك."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                filteredItems.forEach { item ->
                    PocketItemCard(
                        item = item,
                        arabic = arabic,
                        onPlay = { PocketLibraryManager.playItem(context, item) },
                        onShare = { PocketLibraryManager.shareItem(context, item) },
                        onToggleFavorite = {
                            PocketLibraryManager.toggleFavorite(context, item.id)
                            model.refreshLibrary()
                        },
                        onRename = { renameItemTarget = item },
                        onDelete = { deleteItemTarget = item },
                        onProcess = { onProcess(item) }
                    )
                }
            }
        } else {
            // Empty Library State
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Text(
                        text = t("Your Pocket is empty", "مكتبتك فارغة حاليًا"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t(
                            "Save a video and it will appear here offline in your personal Pocket.",
                            "حمّل مقطع الفيديو الأول وسيتم حفظه هنا بدون إنترنت في مكتبتك."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateHome,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.AddLink, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(t("Paste Video Link", "لصق رابط فيديو"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Rename Dialog
    renameItemTarget?.let { item ->
        var newTitle by remember { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { renameItemTarget = null },
            title = { Text(t("Rename Video", "إعادة تسمية الملف")) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text(t("Title", "العنوان")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            PocketLibraryManager.renameItem(context, item.id, newTitle.trim())
                            model.refreshLibrary()
                        }
                        renameItemTarget = null
                    }
                ) {
                    Text(t("Save", "حفظ"))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameItemTarget = null }) {
                    Text(t("Cancel", "إلغاء"))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deleteItemTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItemTarget = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(t("Delete from Pocket?", "حذف من المكتبة؟")) },
            text = {
                Text(
                    t("Are you sure you want to permanently delete \"${item.title}\"?",
                      "هل أنت متأكد من رغبتك في حذف \"${item.title}\" نهائيًا من جهازك؟")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        PocketLibraryManager.deleteItem(context, item.id)
                        model.refreshLibrary()
                        deleteItemTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(t("Delete", "حذف"))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteItemTarget = null }) {
                    Text(t("Cancel", "إلغاء"))
                }
            }
        )
    }
}

@Composable
fun PocketItemCard(
    item: PocketLibraryItem,
    arabic: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProcess: () -> Unit
) {
    fun t(en: String, ar: String) = if (arabic) ar else en
    var expandedMenu by remember { mutableStateOf(false) }

    val formattedSize = remember(item.fileSizeBytes) {
        val mb = item.fileSizeBytes / (1024.0 * 1024.0)
        if (mb >= 1000) String.format(Locale.US, "%.2f GB", mb / 1024.0)
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
                                else if (item.format.equals("mp3", true) || item.format.equals("m4a", true)) Icons.Default.Audiotrack
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
                                text = { Text(t("Compress / edit", "ضغط / تعديل")) },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                onClick = { expandedMenu = false; onProcess() }
                            )
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
