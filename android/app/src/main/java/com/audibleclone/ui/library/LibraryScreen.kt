package com.audibleclone.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animation
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.dragDown
import androidx.compose.foundation.gestures.dragUp
import androidx.compose.foundation.gestures.orientation
import androidx.compose.foundation.gestures.swipeable
import androidx.compose.foundation.gestures.anchors
import androidx.compose.foundation.gestures.direction
import androidx.compose.foundation.gestures.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberLazyListState
import androidx.compose.foundation.rememberLazyGridState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwipeDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.PullToRefreshState
import androidx.compose.material3.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposedModifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawImage
import androidx.compose.ui.graphics.drawscope.drawRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Transformation
import com.audibleclone.R
import com.audibleclone.data.api.ApiService
import com.audibleclone.data.local.datastore.SettingsDataStore
import com.audibleclone.data.repository.LibraryRepository
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.BookSource
import com.audibleclone.model.BookModels.BookStatus
import com.audibleclone.ui.theme.AudibleCloneTheme
import com.audibleclone.ui.library.SortFilterBottomSheet.SortOption
import com.audibleclone.ui.library.SortFilterBottomSheet.FilterOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.stiffness
import androidx.compose.animation.core.dampingRatio
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.easeInOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode

@Composable
fun LibraryScreen(
    onBookClick: (Book) -> Unit,
    modifier: Modifier = Modifier,
    repository: LibraryRepository = viewModel(),
) {
    val uiState by repository.uiState.collectAsStateWithLifecycle()
    val showSearchDialog = remember { mutableStateOf(false) }
    val showSortFilterSheet = remember { mutableStateOf(false) }
    val currentSort = remember { mutableStateOf(SortOption.RECENTLY_ADDED) }
    val currentFilter = remember { mutableStateOf(FilterOption()) }
    val scrollState = rememberScrollState()
    val lazyListState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState(refreshing = uiState.isLoading)

    // Staggered entrance animation
    val entranceAnimations = remember {
        mutableStateMapOf<Int, EntranceAnimationState>()
    }

    // Trigger pull to refresh
    LaunchedEffect(pullToRefreshState, uiState.isLoading) {
        if (pullToRefreshState.isRefreshing && !uiState.isLoading) {
            repository.refresh()
        }
    }

    // Update entrance animations when books change
    LaunchedEffect(uiState.books.size) {
        uiState.books.forEachIndexed { index, book ->
            val state = entranceAnimations.getOrPut(index) { EntranceAnimationState() }
            state.start()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            pullToRefreshState = pullToRefreshState,
            refreshing = uiState.isLoading,
            modifier = Modifier.fillMaxSize(),
            indicator = { state, _, _ ->
                PullToRefreshIndicator(state)
            }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                // Top App Bar
                TopAppBar(
                    title = { Text(text = "Library", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AudibleCloneTheme.colorScheme.surfaceContainerLow,
                        titleContentColor = AudibleCloneTheme.colorScheme.onSurface
                    ),
                    actions = {
                        IconButton(onClick = { showSearchDialog.value = true }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search"
                            )
                        }
                        IconButton(onClick = { showSortFilterSheet.value = true }) {
                            Icon(
                                imageVector = Icons.Filled.Sort,
                                contentDescription = "Sort & Filter"
                            )
                        }
                        IconButton(onClick = { repository.refresh() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                )

                // Active filter indicator
                if (currentFilter.value.hasActiveFilters() || uiState.searchQuery.isNotBlank()) {
                    ActiveFilterBanner(
                        searchQuery = uiState.searchQuery,
                        filter = currentFilter.value,
                        onClearSearch = { repository.clearSearch() },
                        onClearFilters = {
                            currentFilter.value = FilterOption()
                            repository.applySortAndFilter(
                                sortBy = currentSort.value.sortBy,
                                sortOrder = currentSort.value.sortOrder,
                            )
                        }
                    )
                }

                // Content
                when {
                    uiState.isLoading && uiState.books.isEmpty() -> {
                        LoadingIndicator()
                    }
                    uiState.error != null -> {
                        ErrorMessage(message = uiState.error ?: "Unknown error", onRetry = { repository.refresh() })
                    }
                    uiState.books.isEmpty() -> {
                        EmptyLibraryMessage()
                    }
                    else -> {
                        LibraryGrid(
                            books = uiState.books,
                            onBookClick = onBookClick,
                            onBookLongClick = { book, rect -> /* Show context menu */ },
                            entranceAnimations = entranceAnimations,
                            lazyListState = lazyListState,
                            currentSort = currentSort.value,
                            currentFilter = currentFilter.value,
                            repository = repository,
                        )
                    }
                }
            }
        }

        // Search Dialog
        if (showSearchDialog.value) {
            LibrarySearchDialog(
                onDismiss = { showSearchDialog.value = false },
                onSearch = { query, filters ->
                    repository.searchLibrary(query)
                    if (filters != null) {
                        currentFilter.value = filters
                    }
                }
            )
        }

        // Sort/Filter Bottom Sheet
        if (showSortFilterSheet.value) {
            SortFilterBottomSheet(
                currentSort = currentSort.value,
                currentFilter = currentFilter.value,
                onSortChange = { sort ->
                    currentSort.value = sort
                    repository.applySortAndFilter(
                        sortBy = sort.sortBy,
                        sortOrder = sort.sortOrder,
                        filterSource = currentFilter.value.source,
                        filterGenre = currentFilter.value.genre,
                        filterStatus = currentFilter.value.status,
                    )
                },
                onFilterChange = { filter ->
                    currentFilter.value = filter
                },
                onApply = {
                    repository.applySortAndFilter(
                        sortBy = currentSort.value.sortBy,
                        sortOrder = currentSort.value.sortOrder,
                        filterSource = currentFilter.value.source,
                        filterGenre = currentFilter.value.genre,
                        filterStatus = currentFilter.value.status,
                    )
                    showSortFilterSheet.value = false
                },
                onDismiss = { showSortFilterSheet.value = false }
            )
        }
    }
}

@Composable
fun LibraryGrid(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: ((Book, Rect) -> Unit)? = null,
    entranceAnimations: MutableStateMapOf<Int, EntranceAnimationState>,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    currentSort: SortOption,
    currentFilter: FilterOption,
    repository: LibraryRepository,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp), // Space for mini player
    ) {
        itemsIndexed(books) { index, book ->
            val animationState = entranceAnimations.getOrPut(index) { EntranceAnimationState() }
            
            AnimatedBookCard(
                book = book,
                index = index,
                animationState = animationState,
                onClick = { onBookClick(book) },
                onLongClick = { rect -> onBookLongClick?.invoke(book, rect) },
                onSwipeDelete = {
                    // TODO: Implement delete/archive
                },
                onSwipeArchive = {
                    // TODO: Implement archive
                }
            )
        }

        // Load more trigger
        if (repository.uiState.value.hasMore && !repository.uiState.value.isLoading) {
            item {
                LoadMoreTrigger(
                    onLoadMore = { repository.loadMore() },
                    isLoading = repository.uiState.value.isLoading
                )
            }
        }
    }
}

class EntranceAnimationState {
    var progress by mutableStateOf(0f)
        private set

    fun start() {
        progress = 0f
        // Animation will be driven by animateFloatAsState in composable
    }
}

@Composable
fun AnimatedBookCard(
    book: Book,
    index: Int,
    animationState: EntranceAnimationState,
    onClick: () -> Unit,
    onLongClick: ((Rect) -> Unit)? = null,
    onSwipeDelete: () -> Unit,
    onSwipeArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Staggered entrance animation
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            duration = 400,
            delayMillis = (index * 50).coerceAtMost(300),
            easing = easeInOut
        ),
        label = "entrance_${book.id}"
    )

    // Swipe state
    val swipeProgress = remember { mutableStateOf(0f) }
    val isSwiping = remember { mutableStateOf(false) }
    val swipeDirection = remember { mutableStateOf(0) } // -1 = left, 1 = right

    // Cover art fade-in
    val coverAlpha = animateFloatAsState(
        targetValue = if (animatedProgress > 0.5f) 1f else 0f,
        animationSpec = tween(duration = 300, delayMillis = 100),
        label = "cover_${book.id}"
    )

    // Progress bar animation
    val progressAnim = animateFloatAsState(
        targetValue = book.progressPercent,
        animationSpec = tween(duration = 500, easing = easeInOut),
        label = "progress_${book.id}"
    )

    // Press ripple state
    val pressScale = remember { mutableStateOf(1f) }

    val cardModifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
            scaleX = animatedProgress * pressScale.value
            scaleY = animatedProgress * pressScale.value
            alpha = animatedProgress
            translationY = (1f - animatedProgress) * 30.dp.toPx()
        }
        .combinedClickable(
            onClick = onClick,
            onLongClick = { rect -> onLongClick?.invoke(rect) },
            onDoubleClick = null
        )
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { },
                onDrag = { change, dragAmount ->
                    if (!isSwiping.value && Math.abs(dragAmount.x) > Math.abs(dragAmount.y)) {
                        isSwiping.value = true
                        swipeDirection.value = if (dragAmount.x > 0) 1 else -1
                    }
                    if (isSwiping.value) {
                        swipeProgress.value = (swipeProgress.value + dragAmount.x).coerceIn(-120f, 120f)
                    }
                },
                onDragEnd = {
                    if (isSwiping.value) {
                        if (Math.abs(swipeProgress.value) > 80f) {
                            if (swipeDirection.value > 0) {
                                onSwipeArchive()
                            } else {
                                onSwipeDelete()
                            }
                        }
                        // Animate back
                        // Note: In production, use Animatable for smooth return
                        isSwiping.value = false
                        swipeProgress.value = 0f
                        swipeDirection.value = 0
                    }
                },
                onDragCancel = {
                    isSwiping.value = false
                    swipeProgress.value = 0f
                    swipeDirection.value = 0
                }
            )
        }

    Box(modifier = cardModifier) {
        // Swipe background actions
        if (swipeProgress.value != 0f) {
            SwipeActionBackground(
                progress = swipeProgress.value,
                direction = swipeDirection.value,
                onArchive = onSwipeArchive,
                onDelete = onSwipeDelete,
            )
        }

        // Main card content
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeProgress.value.roundToInt(), 0) }
                .graphicsLayer {
                    // Shadow elevation during swipe
                    shadowElevation = if (isSwiping.value) 8.dp.toPx() else 4.dp.toPx()
                },
            onClick = {}, // Handled by combinedClickable
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = androidx.compose.material3.Shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover image with fade-in
                Box(
                    modifier = Modifier.size(80.dp, 120.dp)
                        .clip(androidx.compose.material3.Shapes.small)
                        .graphicsLayer { alpha = coverAlpha.value }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(book.coverImageUrl)
                            .crossfade(true)
                            .placeholder(R.drawable.placeholder_cover)
                            .error(R.drawable.placeholder_cover)
                            .build(),
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Progress overlay on cover
                    if (book.progressSeconds > 0 && !book.completed) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            LinearProgressIndicator(
                                progress = progressAnim.value,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = AudibleCloneTheme.colorScheme.primary,
                                trackColor = Color.Black.copy(alpha = 0.3f)
                            )
                        }
                    } else if (book.completed) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(8.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Completed",
                                    tint = AudibleCloneTheme.colorScheme.primary
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                                Text(
                                    text = "Completed",
                                    fontSize = 10.sp,
                                    color = AudibleCloneTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))

                // Book info
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
                        text = book.displayAuthor,
                        fontSize = 14.sp,
                        color = AudibleCloneTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))

                    // Source badge + duration
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceBadge(source = book.source)
                        Text(
                            text = book.formattedDuration,
                            fontSize = 12.sp,
                            color = AudibleCloneTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // Progress indicator
                    if (book.progressSeconds > 0 && !book.completed) {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                        LinearProgressIndicator(
                            progress = progressAnim.value,
                            modifier = Modifier.fillMaxWidth(),
                            color = AudibleCloneTheme.colorScheme.primary,
                            trackColor = AudibleCloneTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            text = "${(book.progressPercent * 100).toInt()}% • ${book.formattedProgress} / ${book.formattedDuration}",
                            fontSize = 11.sp,
                            color = AudibleCloneTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else if (book.completed) {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Completed",
                                tint = AudibleCloneTheme.colorScheme.primary
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                            Text(
                                text = "Completed",
                                fontSize = 12.sp,
                                color = AudibleCloneTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Play/Continue button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = pressScale.value
                            scaleY = pressScale.value
                        }
                ) {
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (book.progressSeconds > 0 && !book.completed)
                                Icons.Filled.PlayArrow
                            else
                                Icons.Filled.PlayCircleFilled,
                            contentDescription = if (book.completed) "Replay" else "Play",
                            tint = AudibleCloneTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SwipeActionBackground(
    progress: Float,
    direction: Int,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val absProgress = Math.abs(progress)
    val maxSwipe = 120f
    val alpha = (absProgress / maxSwipe).coerceIn(0f, 1f)

    if (direction > 0) {
        // Right swipe - Archive
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = AudibleCloneTheme.colorScheme.primaryContainer.copy(alpha = alpha),
                    shape = androidx.compose.material3.Shapes.medium
                )
                .padding(12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Archive",
                    color = AudibleCloneTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Filled.Archive,
                    contentDescription = "Archive",
                    tint = AudibleCloneTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    } else {
        // Left swipe - Delete
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = AudibleCloneTheme.colorScheme.errorContainer.copy(alpha = alpha),
                    shape = androidx.compose.material3.Shapes.medium
                )
                .padding(12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = AudibleCloneTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Remove",
                    color = AudibleCloneTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SourceBadge(source: BookSource) {
    val (label, color) = when (source) {
        BookSource.LOCAL -> "Local" to AudibleCloneTheme.colorScheme.primaryContainer
        BookSource.LIBRIVOX -> "LibriVox" to AudibleCloneTheme.colorScheme.secondaryContainer
        BookSource.AUDIOBOOKBAY -> "ABB" to AudibleCloneTheme.colorScheme.tertiaryContainer
        BookSource.TTS_GENERATED -> "TTS" to AudibleCloneTheme.colorScheme.primaryContainer
        BookSource.UPLOADED -> "Uploaded" to AudibleCloneTheme.colorScheme.secondaryContainer
    }

    Box(
        modifier = Modifier
            .padding(4.dp)
            .background(
                color = color,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (source == BookSource.LOCAL) AudibleCloneTheme.colorScheme.onPrimaryContainer
            else if (source == BookSource.LIBRIVOX) AudibleCloneTheme.colorScheme.onSecondaryContainer
            else AudibleCloneTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
fun ActiveFilterBanner(
    searchQuery: String,
    filter: FilterOption,
    onClearSearch: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val hasSearch = searchQuery.isNotBlank()
    val hasFilters = filter.hasActiveFilters()

    if (!hasSearch && !hasFilters) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = AudibleCloneTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            if (hasSearch) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = AudibleCloneTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Search: \"$searchQuery\"",
                            style = AudibleCloneTheme.typography.bodySmall,
                            color = AudibleCloneTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onClearSearch) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = AudibleCloneTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (hasFilters) {
                if (hasSearch) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Filters",
                            tint = AudibleCloneTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Filters active",
                            style = AudibleCloneTheme.typography.bodySmall,
                            color = AudibleCloneTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = onClearFilters) {
                        Text(text = "Clear", style = AudibleCloneTheme.typography.labelSmall, color = AudibleCloneTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun LoadMoreTrigger(
    onLoadMore: () -> Unit,
    isLoading: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = AudibleCloneTheme.colorScheme.primary)
        } else {
            Text(
                text = "Pull to load more",
                style = AudibleCloneTheme.typography.bodySmall,
                color = AudibleCloneTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PullToRefreshIndicator(state: androidx.compose.material3.PullToRefreshState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val progress = state.indicatorProgress
        val isRefreshing = state.isRefreshing

        CircularProgressIndicator(
            progress = if (isRefreshing) null else progress,
            color = AudibleCloneTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = AudibleCloneTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Loading your library...",
                style = AudibleCloneTheme.typography.bodyMedium,
                color = AudibleCloneTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "Error",
                tint = AudibleCloneTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Error loading library",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AudibleCloneTheme.colorScheme.error
            )
            Text(text = message, color = AudibleCloneTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            Button(onClick = onRetry) {
                Text(text = "Retry")
            }
        }
    }
}

@Composable
fun EmptyLibraryMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Animated empty state illustration
            EmptyStateIllustration()

            Text(
                text = "Your library is empty",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = AudibleCloneTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Add books from Discover or upload your own",
                fontSize = 14.sp,
                color = AudibleCloneTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
fun EmptyStateIllustration() {
    val infiniteRotation = rememberInfiniteTransition(label = "empty_illustration")
    val rotation by infiniteRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration = 20000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = AudibleCloneTheme.colorScheme.surfaceVariant,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )

        // Rotating book icon
        Box(
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer {
                    rotationZ = rotation
                }
        ) {
            Icon(
                imageVector = Icons.Filled.LibraryBooks,
                contentDescription = "Empty library",
                tint = AudibleCloneTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(60.dp)
            )
        }

        // Pulse rings
        PulseRing(delay = 0)
        PulseRing(delay = 600)
        PulseRing(delay = 1200)
    }
}

@Composable
fun PulseRing(delay: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_$delay")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration = 2000, delayMillis = delay, easing = easeInOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration = 2000, delayMillis = delay, easing = easeInOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(120.dp * scale)
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha
            }
            .background(
                color = AudibleCloneTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = androidx.compose.foundation.shape.CircleShape
            )
    )
}

@Composable
fun rememberInfiniteTransition(label: String = ""): androidx.compose.animation.core.InfiniteTransition {
    return androidx.compose.animation.core.rememberInfiniteTransition(label = label)
}