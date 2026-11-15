package com.audibleclone.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.audibleclone.R
import com.audibleclone.data.api.ApiService
import com.audibleclone.data.repository.DiscoverRepository
import com.audibleclone.model.BookModels.ABBBook
import com.audibleclone.model.BookModels.LibriVoxBook
import com.audibleclone.model.BookModels.LibriVoxResponse
import com.audibleclone.ui.theme.AudibleCloneTheme

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    repository: DiscoverRepository = viewModel()
) {
    val uiState by repository.uiState.collectAsStateWithLifecycle()
    val searchText = remember { mutableStateOf("") }
    val activeTab = remember { mutableStateOf(0) } // 0 = LibriVox, 1 = AudioBookBay, 2 = TTS
    
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Top App Bar with Tab Selection
            TopAppBar(
                title = { Text(text = "Discover", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AudibleCloneTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = AudibleCloneTheme.colorScheme.onSurface
                ),
                actions = {
                    // Tab selector
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TabButton(text = "LibriVox", selected = activeTab.value == 0, onClick = { activeTab.value = 0 })
                        TabButton(text = "AudioBookBay", selected = activeTab.value == 1, onClick = { activeTab.value = 1 })
                        TabButton(text = "TTS Upload", selected = activeTab.value == 2, onClick = { activeTab.value = 2 })
                    }
                }
            )
            
            // Search Bar (for LibriVox and AudioBookBay)
            if (activeTab.value in 0..1) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
                androidx.compose.material3.TextField(
                    value = searchText.value,
                    onValueChange = { searchText.value = it },
                    label = { Text(text = "Search ${if (activeTab.value == 0) "LibriVox" else "AudioBookBay"}...") },
                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchText.value.isNotBlank()) {
                            IconButton(onClick = { searchText.value = "" }) {
                                Icon(androidx.compose.material.icons.Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    keyboardActions = androidx.compose.ui.text.input.KeyboardActions(
                        onDone = { repository.search(searchText.value, activeTab.value) }
                    ),
                    onKeyboardAction = { action ->
                        if (action == androidx.compose.ui.text.input.ImeAction.Done) {
                            repository.search(searchText.value, activeTab.value)
                        }
                    }
                )
            }
            
            // Content based on active tab
            when (activeTab.value) {
                0 -> LibriVoxTab(
                    uiState = uiState,
                    onSearch = { query -> repository.search(query, 0) },
                    onLoadMore = { repository.loadMore(0) },
                    onDownload = { book -> repository.downloadLibrivoxBook(book.id) }
                )
                1 -> AudioBookBayTab(
                    uiState = uiState,
                    onSearch = { query -> repository.search(query, 1) },
                    onLoadMore = { repository.loadMore(1) },
                    onDownload = { book -> repository.downloadAudiobookbayBook(book) }
                )
                2 -> TTSUploadTab(
                    uiState = uiState,
                    onUpload = { file, title, author -> repository.uploadEbookForTTS(file, title, author) }
                )
            }
        }
    }
}

@Composable
fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = if (selected) AudibleCloneTheme.colorScheme.primary else AudibleCloneTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            text = text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun LibriVoxTab(
    uiState: DiscoverUiState,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onDownload: (LibriVoxBook) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        if (uiState.librivoxIsLoading && uiState.librivoxBooks.isEmpty()) {
            item { LoadingIndicator() }
        } else if (uiState.librivoxError != null) {
            item { ErrorMessage(message = uiState.librivoxError ?: "Unknown error", onRetry = { onSearch("") }) }
        } else if (uiState.librivoxBooks.isEmpty()) {
            item { 
                EmptyMessage(
                    title = "Search LibriVox",
                    message = "Enter a title or author to find free public domain audiobooks"
                )
            }
        } else {
            items(uiState.librivoxBooks) { book ->
                LibriVoxBookItem(book = book, onDownload = { onDownload(book) })
            }
            
            if (uiState.librivoxHasMore) {
                item {
                    LoadMoreButton(onClick = onLoadMore, isLoading = uiState.librivoxIsLoading)
                }
            }
        }
    }
}

@Composable
fun LibriVoxBookItem(book: LibriVoxBook, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(book.urlCoverArt)
                    .crossfade(true)
                    .placeholder(R.drawable.placeholder_cover)
                    .error(R.drawable.placeholder_cover)
                    .build(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(60.dp, 90.dp)
                    .clip(androidx.compose.material3.Shapes.small)
            )
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(12.dp))
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = book.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = "by ${book.author}",
                    fontSize = 14.sp,
                    color = AudibleCloneTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = "${book.readers.joinToString(", ") { it.name }} • ${book.totalTimeSecs?.let { formatDuration(it) } ?: "Unknown duration"}",
                    fontSize = 12.sp,
                    color = AudibleCloneTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (book.genres.isNotEmpty()) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = book.genres.take(3).joinToString(", "),
                        fontSize = 11.sp,
                        color = AudibleCloneTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
                    )
                }
            }
            
            androidx.compose.material3.Button(onClick = onDownload) {
                Text(text = "Download")
            }
        }
    }
}

@Composable
fun AudioBookBayTab(
    uiState: DiscoverUiState,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onDownload: (ABBBook) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        if (uiState.abbIsLoading && uiState.abbBooks.isEmpty()) {
            item { LoadingIndicator() }
        } else if (uiState.abbError != null) {
            item { ErrorMessage(message = uiState.abbError ?: "Unknown error", onRetry = { onSearch("") }) }
        } else if (uiState.abbBooks.isEmpty()) {
            item { 
                EmptyMessage(
                    title = "Search AudioBookBay",
                    message = "Find audiobooks from AudioBookBay and download via torrent"
                )
            }
        } else {
            items(uiState.abbBooks) { book ->
                ABBBookItem(book = book, onDownload = { onDownload(book) })
            }
            
            if (uiState.abbHasMore) {
                item {
                    LoadMoreButton(onClick = onLoadMore, isLoading = uiState.abbIsLoading)
                }
            }
        }
    }
}

@Composable
fun ABBBookItem(book: ABBBook, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(book.cover)
                    .crossfade(true)
                    .placeholder(R.drawable.placeholder_cover)
                    .error(R.drawable.placeholder_cover)
                    .build(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(60.dp, 90.dp)
                    .clip(androidx.compose.material3.Shapes.small)
            )
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(12.dp))
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = book.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = "${book.format} • ${book.bitrate} • ${book.file_size}",
                    fontSize = 12.sp,
                    color = AudibleCloneTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
                )
                if (book.language != "N/A") {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = "Language: ${book.language}",
                        fontSize = 11.sp,
                        color = AudibleCloneTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            androidx.compose.material3.Button(onClick = onDownload) {
                Text(text = "Get Magnet")
            }
        }
    }
}

@Composable
fun TTSUploadTab(
    uiState: DiscoverUiState,
    onUpload: (File, String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Upload Area
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = AudibleCloneTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.CloudUpload,
                        contentDescription = "Upload",
                        tint = AudibleCloneTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
                    Text(
                        text = "Upload eBook for TTS Conversion",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.TextAlign.Center
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                    Text(
                        text = "Supports EPUB, PDF, TXT • Full cast voices with Piper & Kokoro",
                        fontSize = 14.sp,
                        color = AudibleCloneTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.TextAlign.Center
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
                    Button(onClick = { /* TODO: File picker */ }) {
                        Text(text = "Choose File")
                    }
                }
            }
        }
        
        // Voice Selection Preview
        Text(
            text = "Available Voices",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.availableVoices) { voice ->
                VoicePreviewItem(voice = voice)
            }
        }
    }
}

@Composable
fun VoicePreviewItem(voice: VoicePreview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { /* TODO: Preview voice */ }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (voice.engine == "kokoro") 
                    androidx.compose.material.icons.Icons.Filled.Star 
                else 
                    androidx.compose.material.icons.Icons.Filled.Mic,
                contentDescription = voice.engine,
                tint = if (voice.engine == "kokoro") AudibleCloneTheme.colorScheme.secondary else AudibleCloneTheme.colorScheme.primary
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(12.dp))
            Column {
                Text(text = voice.name, fontWeight = FontWeight.Medium)
                Text(text = "${voice.engine.uppercase()} • ${voice.language} • ${voice.quality}", fontSize = 12.sp, color = AudibleCloneTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { /* TODO: Play preview */ }) {
                Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, contentDescription = "Preview")
            }
        }
    }
}

@Composable
fun LoadMoreButton(onClick: () -> Unit, isLoading: Boolean) {
    androidx.compose.material3.OutlinedButton(
        onClick = if (isLoading) null else onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Text(text = "Load More")
        }
    }
}

@Composable
fun EmptyMessage(title: String, message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Search,
                contentDescription = "Search",
                tint = AudibleCloneTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = AudibleCloneTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
            Text(text = message, fontSize = 14.sp, color = AudibleCloneTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = androidx.compose.ui.text.TextAlign.Center)
        }
    )
}

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = AudibleCloneTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Error", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AudibleCloneTheme.colorScheme.error)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
            Text(text = message, color = AudibleCloneTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
            Button(onClick = onRetry) { Text(text = "Retry") }
        }
    }
}

data class VoicePreview(
    val id: String,
    val name: String,
    val engine: String,
    val language: String,
    val quality: String,
)

fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}