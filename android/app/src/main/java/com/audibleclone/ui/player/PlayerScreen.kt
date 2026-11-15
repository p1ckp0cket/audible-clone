package com.audibleclone.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SeekBar
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import coil.size.Transformation
import kotlinx.coroutines.launch
import com.audibleclone.R
import com.audibleclone.data.api.ApiService
import com.audibleclone.data.repository.PlayerRepository
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.Chapter
import com.audibleclone.ui.theme.AudibleCloneTheme

@Composable
fun PlayerScreen(
    bookId: Int,
    modifier: Modifier = Modifier,
    repository: PlayerRepository = viewModel()
) {
    val uiState by repository.uiState.collectAsStateWithLifecycle()
    
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Top App Bar
            TopAppBar(
                title = { 
                    Text(
                        text = uiState.book?.title ?: "Player",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* TODO: Navigate back */ }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AudibleCloneTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = AudibleCloneTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = { /* TODO: Sleep timer */ }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.Bedtime,
                            contentDescription = "Sleep timer"
                        )
                    }
                    IconButton(onClick = { /* TODO: Speed */ }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.Speed,
                            contentDescription = "Playback speed"
                        )
                    }
                    IconButton(onClick = { /* TODO: Chapters */ }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.MenuBook,
                            contentDescription = "Chapters"
                        )
                    }
                }
            )
            
            // Content
            if (uiState.isLoading) {
                LoadingPlayer()
            } else {
                PlayerContent(
                    book = uiState.book,
                    currentChapter = uiState.currentChapter,
                    chapters = uiState.chapters,
                    playbackState = uiState.playbackState,
                    currentPosition = uiState.currentPosition,
                    duration = uiState.duration,
                    playbackSpeed = uiState.playbackSpeed,
                    onPlayPause = { repository.playPause() },
                    onSeek = { position -> repository.seekTo(position) },
                    onSpeedChange = { speed -> repository.setPlaybackSpeed(speed) },
                    onChapterClick = { chapter -> repository.playChapter(chapter) },
                    onSkipForward = { repository.skipForward() },
                    onSkipBackward = { repository.skipBackward() },
                )
            }
        }
    }
}

@Composable
fun PlayerContent(
    book: Book?,
    currentChapter: Chapter?,
    chapters: List<Chapter>,
    playbackState: PlaybackState,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onChapterClick: (Chapter) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cover Art
        book?.coverImageUrl?.let { url ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .placeholder(R.drawable.placeholder_cover)
                    .error(R.drawable.placeholder_cover)
                    .build(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(horizontal = 32.dp)
                    .clip(androidx.compose.material3.Shapes.medium)
            )
        }
        
        // Book Info
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = book?.title ?: "Unknown Title",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.TextAlign.Center,
                maxLines = 2,
                overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = book?.displayAuthor ?: "Unknown Author",
                fontSize = 16.sp,
                color = AudibleCloneTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.TextAlign.Center
            )
            currentChapter?.let { chapter ->
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                Text(
                    text = chapter.displayTitle,
                    fontSize = 14.sp,
                    color = AudibleCloneTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
                )
            }
        }
        
        // Progress Bar
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    fontSize = 12.sp,
                    color = AudibleCloneTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(duration),
                    fontSize = 12.sp,
                    color = AudibleCloneTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
            // SeekBar with chapter markers
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                SeekBar(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { fraction -> 
                        onSeek((duration * fraction).toLong()) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.SeekBarDefaults.colors(
                        thumbColor = AudibleCloneTheme.colorScheme.primary,
                        activeTrackColor = AudibleCloneTheme.colorScheme.primary,
                        inactiveTrackColor = AudibleCloneTheme.colorScheme.surfaceVariant
                    )
                )
                // Chapter markers on seekbar
                if (chapters.isNotEmpty() && duration > 0) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp) // Account for thumb radius
                    ) {
                        androidx.compose.foundation.lazy.items(chapters) { chapter ->
                            val startPos = chapter.startOffsetSeconds * 1000
                            val position = if (duration > 0) startPos.toFloat() / duration else 0f
                            if (position > 0f && position < 1f) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(8.dp)
                                        .graphicsLayer {
                                            translationX = (position * (LocalConfiguration.current.screenWidthDp.dp.toPx() - 24.dp.toPx() - 24.dp.toPx())) - 1.dp.toPx()
                                        }
                                        .background(AudibleCloneTheme.colorScheme.primary.copy(alpha = 0.6f))
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed
            IconButton(onClick = { /* TODO: Speed menu */ }) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Speed,
                    contentDescription = "Speed: ${playbackSpeed}x",
                    tint = AudibleCloneTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Skip Backward 30s
            IconButton(onClick = onSkipBackward) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Replay30,
                    contentDescription = "Rewind 30s",
                    tint = AudibleCloneTheme.colorScheme.onSurface
                )
            }
            
            // Previous Chapter
            IconButton(onClick = { /* TODO: Previous chapter */ }) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.SkipPrevious,
                    contentDescription = "Previous chapter",
                    tint = AudibleCloneTheme.colorScheme.onSurface
                )
            }
            
            // Play/Pause
            Button(
                onClick = onPlayPause,
                modifier = Modifier.size(64.dp),
                shape = androidx.compose.material3.Shapes.full,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = AudibleCloneTheme.colorScheme.primary,
                    contentColor = AudibleCloneTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (playbackState == PlaybackState.PLAYING) 
                        androidx.compose.material.icons.Icons.Filled.Pause 
                    else 
                        androidx.compose.material.icons.Icons.Filled.PlayArrow,
                    contentDescription = if (playbackState == PlaybackState.PLAYING) "Pause" else "Play",
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Next Chapter
            IconButton(onClick = { /* TODO: Next chapter */ }) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.SkipNext,
                    contentDescription = "Next chapter",
                    tint = AudibleCloneTheme.colorScheme.onSurface
                )
            }
            
            // Skip Forward 30s
            IconButton(onClick = onSkipForward) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Forward30,
                    contentDescription = "Forward 30s",
                    tint = AudibleCloneTheme.colorScheme.onSurface
                )
            }
            
            // Sleep Timer
            IconButton(onClick = { /* TODO: Sleep timer */ }) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Timer,
                    contentDescription = "Sleep timer",
                    tint = AudibleCloneTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Chapter List (expandable)
        if (chapters.isNotEmpty()) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
            Text(
                text = "Chapters (${chapters.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )
            
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.foundation.lazy.items(chapters) { chapter ->
                    ChapterItem(
                        chapter = chapter,
                        isCurrent = chapter.id == currentChapter?.id,
                        onClick = { onChapterClick(chapter) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChapterItem(
    chapter: Chapter,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isCurrent) AudibleCloneTheme.colorScheme.primaryContainer else AudibleCloneTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCurrent) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.PlayCircleFilled,
                    contentDescription = "Playing",
                    tint = AudibleCloneTheme.colorScheme.primary
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
            } else {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(24.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.displayTitle,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) AudibleCloneTheme.colorScheme.onPrimaryContainer else AudibleCloneTheme.colorScheme.onSurface
                )
                Text(
                    text = chapter.formattedDuration,
                    fontSize = 12.sp,
                    color = if (isCurrent) AudibleCloneTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else AudibleCloneTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LoadingPlayer() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = AudibleCloneTheme.colorScheme.primary)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
            Text(text = "Loading player...", color = AudibleCloneTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun formatTime(millis: Long): String {
    val seconds = millis / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

enum class PlaybackState {
    PLAYING, PAUSED, BUFFERING, COMPLETED, ERROR
}