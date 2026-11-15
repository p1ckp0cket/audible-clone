package com.audibleclone.ui.library

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Chip
import androidx.compose.material3.CompositionLocalProvider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentAlpha
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.audibleclone.R
import com.audibleclone.data.api.ApiService
import com.audibleclone.data.local.datastore.SettingsDataStore
import com.audibleclone.data.repository.LibraryRepository
import com.audibleclone.di.EntryPointAccessors
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.BookSource
import com.audibleclone.model.BookModels.BookStatus
import com.audibleclone.ui.theme.AudibleCloneTheme
import kotlinx.coroutines.flow.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsStateWithLifecycle

@Composable
fun LibrarySearchDialog(
    onDismiss: () -> Unit,
    onSearch: (String, SearchFilters?) -> Unit,
    repository: LibraryRepository = viewModel(),
) {
    val context = LocalContext.current
    val searchText = remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val filters = remember { mutableStateOf(SearchFilters()) }
    val showFilters = remember { mutableStateOf(false) }
    val showRecent = remember { mutableStateOf(false) }

    // Get recent searches from DataStore - use Hilt injection
    val settingsDataStore = remember { com.audibleclone.di.EntryPointAccessors.fromApplication(context).settingsDataStore() }
    val recentSearches by settingsDataStore.recentSearchesFlow.collectAsStateWithLifecycle(emptyList())

    // Focus search field on open
    androidx.compose.runtime.LaunchedEffect(key1 = true) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                // Search Bar / Top App Bar
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchText.value,
                            onValueChange = { searchText.value = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onKeyEvent { event ->
                                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                                        onDismiss()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            label = { Text("Search library...") },
                            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Search, contentDescription = "Search") },
                            trailingIcon = {
                                if (searchText.value.isNotBlank()) {
                                    IconButton(onClick = { searchText.value = "" }) {
                                        Icon(androidx.compose.material.icons.Icons.Filled.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                performSearch()
                            }),
                            visualTransformation = NoCursorTransformation,
                            singleLine = true,
                            colors = androidx.compose.material3.TextFieldDefaults.textFieldColors(
                                containerColor = AudibleCloneTheme.colorScheme.surfaceContainerHighest,
                                focusedContainerColor = AudibleCloneTheme.colorScheme.surfaceContainerHighest,
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFilters.value = !showFilters.value }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Tune,
                                contentDescription = "Filters",
                                tint = if (showFilters.value) AudibleCloneTheme.colorScheme.primary else AudibleCloneTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { onSearch("", filters.value) }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.ClearAll,
                                contentDescription = "Clear all filters",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AudibleCloneTheme.colorScheme.surfaceContainerLow,
                        titleContentColor = AudibleCloneTheme.colorScheme.onSurface,
                    )
                )

                // Filter Chips Bar
                if (showFilters.value) {
                    FilterChipsBar(
                        filters = filters,
                        onFilterChange = { newFilters ->
                            filters.value = newFilters
                        }
                    )
                }

                // Active filter chips summary
                if (filters.value.hasActiveFilters()) {
                    ActiveFiltersSummary(
                        filters = filters.value,
                        onRemoveFilter = { key ->
                            filters.value = filters.value.copy(
                                source = if (key == "source") null else filters.value.source,
                                genre = if (key == "genre") null else filters.value.genre,
                                status = if (key == "status") null else filters.value.status,
                                author = if (key == "author") null else filters.value.author,
                                completedOnly = if (key == "completed") false else filters.value.completedOnly,
                                inProgressOnly = if (key == "inProgress") false else filters.value.inProgressOnly,
                            )
                        },
                        onClearAll = {
                            filters.value = SearchFilters()
                        }
                    )
                }

                // Content: Recent searches or Results
                if (searchText.value.isBlank() && showRecent.value) {
                    RecentSearchesSection(
                        recentSearches = recentSearches,
                        onSearchClick = { query ->
                            searchText.value = query
                            performSearch()
                        },
                        onClearHistory = {
                            settingsDataStore.clearRecentSearches()
                        }
                    )
                } else if (searchText.value.isNotBlank()) {
                    SearchResultsSection(
                        query = searchText.value,
                        repository = repository,
                    )
                } else {
                    SearchHintSection(
                        onShowRecent = { showRecent.value = true },
                        recentSearches = recentSearches,
                    )
                }
            }
        }
    }

    fun performSearch() {
        val query = searchText.value.trim()
        if (query.isNotBlank()) {
            settingsDataStore.addRecentSearch(query)
            onSearch(query, filters.value)
            onDismiss()
        }
    }
}

@Composable
fun FilterChipsBar(
    filters: MutableState<SearchFilters>,
    onFilterChange: (SearchFilters) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = AudibleCloneTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Source filter
            FilterChipRow(
                label = "Source",
                selectedValue = filters.value.source,
                options = listOf("All") + BookSource.values().map { it.name },
                onSelect = { value ->
                    val newFilters = filters.value.copy(source = if (value == "All") null else value)
                    filters.value = newFilters
                    onFilterChange(newFilters)
                }
            )

            // Genre filter
            FilterChipRow(
                label = "Genre",
                selectedValue = filters.value.genre,
                options = listOf("All", "Fiction", "Non-Fiction", "Mystery", "Sci-Fi", "Fantasy", "Romance", "Biography", "History", "Self-Help", "Business", "Children"),
                onSelect = { value ->
                    val newFilters = filters.value.copy(genre = if (value == "All") null else value)
                    filters.value = newFilters
                    onFilterChange(newFilters)
                }
            )

            // Status filter
            FilterChipRow(
                label = "Status",
                selectedValue = filters.value.status,
                options = listOf("All") + BookStatus.values().map { it.name },
                onSelect = { value ->
                    val newFilters = filters.value.copy(status = if (value == "All") null else value)
                    filters.value = newFilters
                    onFilterChange(newFilters)
                }
            )

            // Author filter
            FilterChipRow(
                label = "Author",
                selectedValue = filters.value.author,
                options = listOf("All", "Filter by author..."),
                onSelect = { value ->
                    if (value == "Filter by author...") {
                        // TODO: Show author picker
                    } else {
                        val newFilters = filters.value.copy(author = if (value == "All") null else value)
                        filters.value = newFilters
                        onFilterChange(newFilters)
                    }
                }
            )

            // Progress filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Progress:", style = AudibleCloneTheme.typography.labelMedium, color = AudibleCloneTheme.colorScheme.onSurfaceVariant)

                FilterChip(
                    label = "Completed",
                    selected = filters.value.completedOnly,
                    onClick = {
                        val newFilters = filters.value.copy(
                            completedOnly = !filters.value.completedOnly,
                            inProgressOnly = if (!filters.value.completedOnly) false else filters.value.inProgressOnly
                        )
                        filters.value = newFilters
                        onFilterChange(newFilters)
                    }
                )

                FilterChip(
                    label = "In Progress",
                    selected = filters.value.inProgressOnly,
                    onClick = {
                        val newFilters = filters.value.copy(
                            inProgressOnly = !filters.value.inProgressOnly,
                            completedOnly = if (!filters.value.inProgressOnly) false else filters.value.completedOnly
                        )
                        filters.value = newFilters
                        onFilterChange(newFilters)
                    }
                )
            }
        }
    }
}

@Composable
fun FilterChipRow(
    label: String,
    selectedValue: String?,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = AudibleCloneTheme.typography.labelMedium, color = AudibleCloneTheme.colorScheme.onSurfaceVariant)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options) { option ->
                val isSelected = (selectedValue == option) || (selectedValue == null && option == "All")
                FilterChip(
                    label = option,
                    selected = isSelected,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        selected = selected,
        colors = androidx.compose.material3.ChipDefaults.chipColors(
            containerColor = if (selected) AudibleCloneTheme.colorScheme.primaryContainer else AudibleCloneTheme.colorScheme.surfaceContainerHighest,
            selectedContainerColor = AudibleCloneTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier
            .wrapContentSize()
            .height(32.dp)
    ) {
        Text(
            text = label,
            style = AudibleCloneTheme.typography.labelSmall,
            color = if (selected) AudibleCloneTheme.colorScheme.onPrimaryContainer else AudibleCloneTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ActiveFiltersSummary(
    filters: SearchFilters,
    onRemoveFilter: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = AudibleCloneTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Active filters:", style = AudibleCloneTheme.typography.labelMedium, color = AudibleCloneTheme.colorScheme.primary)
                TextButton(onClick = onClearAll) {
                    Text(text = "Clear all", style = AudibleCloneTheme.typography.labelSmall, color = AudibleCloneTheme.colorScheme.primary)
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.source?.let { items(listOf(it)) { value ->
                    ActiveFilterChip(label = "Source: $value", onRemove = { onRemoveFilter("source") })
                } }
                filters.genre?.let { items(listOf(it)) { value ->
                    ActiveFilterChip(label = "Genre: $value", onRemove = { onRemoveFilter("genre") })
                } }
                filters.status?.let { items(listOf(it)) { value ->
                    ActiveFilterChip(label = "Status: $value", onRemove = { onRemoveFilter("status") })
                } }
                filters.author?.let { items(listOf(it)) { value ->
                    ActiveFilterChip(label = "Author: $value", onRemove = { onRemoveFilter("author") })
                } }
                if (filters.completedOnly) items(listOf("Completed")) { _ ->
                    ActiveFilterChip(label = "Completed", onRemove = { onRemoveFilter("completed") })
                }
                if (filters.inProgressOnly) items(listOf("In Progress")) { _ ->
                    ActiveFilterChip(label = "In Progress", onRemove = { onRemoveFilter("inProgress") })
                }
            }
        }
    }
}

@Composable
fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    Chip(
        onClick = onRemove,
        colors = androidx.compose.material3.ChipDefaults.chipColors(
            containerColor = AudibleCloneTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = AudibleCloneTheme.typography.labelSmall, color = AudibleCloneTheme.colorScheme.onPrimaryContainer)
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                contentDescription = "Remove filter",
                tint = AudibleCloneTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun RecentSearchesSection(
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (recentSearches.isEmpty()) {
        SearchHintSection(onShowRecent = {}, recentSearches = emptyList())
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Recent searches", style = AudibleCloneTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClearHistory) {
                    Text(text = "Clear history", style = AudibleCloneTheme.typography.labelMedium, color = AudibleCloneTheme.colorScheme.primary)
                }
            }
        }
        items(recentSearches) { query ->
            RecentSearchItem(
                query = query,
                onClick = { onSearchClick(query) }
            )
        }
    }
}

@Composable
fun RecentSearchItem(
    query: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = AudibleCloneTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.History,
                contentDescription = "Recent search",
                tint = AudibleCloneTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = query,
                style = AudibleCloneTheme.typography.bodyMedium,
                color = AudibleCloneTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.NorthEast,
                contentDescription = "Search",
                tint = AudibleCloneTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SearchHintSection(
    onShowRecent: () -> Unit,
    recentSearches: List<String>,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Search,
                contentDescription = "Search",
                tint = AudibleCloneTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Search your library",
                style = AudibleCloneTheme.typography.headlineSmall,
                color = AudibleCloneTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Find books by title, author, or narrator",
                style = AudibleCloneTheme.typography.bodyMedium,
                color = AudibleCloneTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.TextAlign.Center
            )
            if (recentSearches.isNotEmpty()) {
                TextButton(onClick = onShowRecent) {
                    Text(text = "Show recent searches", style = AudibleCloneTheme.typography.labelLarge, color = AudibleCloneTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SearchResultsSection(
    query: String,
    repository: LibraryRepository,
) {
    val uiState by repository.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.books.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = AudibleCloneTheme.colorScheme.primary)
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Search failed", style = AudibleCloneTheme.typography.titleMedium, color = AudibleCloneTheme.colorScheme.error)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                        Text(text = uiState.error!!, style = AudibleCloneTheme.typography.bodyMedium, color = AudibleCloneTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            uiState.books.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.SearchOff,
                            contentDescription = "No results",
                            tint = AudibleCloneTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
                        Text(text = "No results for \"$query\"", style = AudibleCloneTheme.typography.titleMedium, color = AudibleCloneTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                        Text(text = "Try different keywords or clear filters", style = AudibleCloneTheme.typography.bodyMedium, color = AudibleCloneTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
            else -> {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.books) { book ->
                        BookCard(
                            book = book,
                            onClick = { /* handled by parent */ }
                        )
                    }
                }
            }
        }
    }
}

data class SearchFilters(
    var source: String? = null,
    var genre: String? = null,
    var status: String? = null,
    var author: String? = null,
    var completedOnly: Boolean = false,
    var inProgressOnly: Boolean = false,
) {
    fun hasActiveFilters(): Boolean {
        return source != null || genre != null || status != null || author != null || completedOnly || inProgressOnly
    }
}

// Visual transformation to hide cursor
class NoCursorTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.TransformedText {
        return androidx.compose.ui.text.TransformedText(text, object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset
            override fun transformedToOriginal(offset: Int): Int = offset
        })
    }
}