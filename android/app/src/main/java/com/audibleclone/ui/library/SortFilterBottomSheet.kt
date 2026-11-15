package com.audibleclone.ui.library

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Chip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheetLayout
import androidx.compose.material3.ModalBottomSheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audibleclone.R
import com.audibleclone.model.BookModels.BookSource
import com.audibleclone.model.BookModels.BookStatus
import com.audibleclone.ui.theme.AudibleCloneTheme
import kotlinx.coroutines.CoroutineScope

@Composable
fun SortFilterBottomSheet(
    currentSort: SortOption,
    currentFilter: FilterOption,
    onSortChange: (SortOption) -> Unit,
    onFilterChange: (FilterOption) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        confirmStateChange = { it != ModalBottomSheetValue.HalfExpanded }
    )
    val scope = rememberCoroutineScope()
    val showSort = remember { mutableStateOf(true) }

    // Auto-expand on show
    androidx.compose.runtime.LaunchedEffect(key1 = true) {
        scope.launch { sheetState.show() }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(
                                color = AudibleCloneTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }

                // Tab selector
                TabRow(
                    selectedTab = if (showSort.value) 0 else 1,
                    onTabClick = { index ->
                        showSort.value = index == 0
                    },
                    tabs = listOf("Sort by", "Filter by")
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

                // Content
                if (showSort.value) {
                    SortOptionsContent(
                        currentSort = currentSort,
                        onSortSelect = { option ->
                            onSortChange(option)
                        }
                    )
                } else {
                    FilterOptionsContent(
                        currentFilter = currentFilter,
                        onFilterChange = { option ->
                            onFilterChange(option)
                        }
                    )
                }

                // Apply button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Button(
                        onClick = {
                            onApply()
                            scope.launch { sheetState.hide() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = AudibleCloneTheme.colorScheme.primary
                        )
                    ) {
                        Text(text = "Apply", fontWeight = FontWeight.Bold, style = AudibleCloneTheme.typography.labelLarge)
                    }
                }
            }
        },
        sheetDragHandle = {
            // Empty - we have our own handle
        }
    ) {
        // Empty content - this composable is the sheet itself
        Box(modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun TabRow(
    selectedTab: Int,
    onTabClick: (Int) -> Unit,
    tabs: List<String>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp)
                    .background(
                        color = if (isSelected) AudibleCloneTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tab,
                        style = AudibleCloneTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) AudibleCloneTheme.colorScheme.onPrimaryContainer else AudibleCloneTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            .clickable { onTabClick(index) }
        }
    }
}

@Composable
fun SortOptionsContent(
    currentSort: SortOption,
    onSortSelect: (SortOption) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(SortOption.values()) { option ->
            SortOptionItem(
                option = option,
                isSelected = currentSort == option,
                onClick = { onSortSelect(option) }
            )
        }
    }
}

@Composable
fun SortOptionItem(
    option: SortOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AudibleCloneTheme.colorScheme.primaryContainer else AudibleCloneTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(crossAxisAlignment = Alignment.Start) {
                Text(
                    text = option.displayName,
                    style = AudibleCloneTheme.typography.bodyLarge,
                    color = if (isSelected) AudibleCloneTheme.colorScheme.onPrimaryContainer else AudibleCloneTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = option.description,
                    style = AudibleCloneTheme.typography.bodySmall,
                    color = if (isSelected) AudibleCloneTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else AudibleCloneTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                    selectedColor = if (isSelected) AudibleCloneTheme.colorScheme.primary else AudibleCloneTheme.colorScheme.outline,
                    unselectedColor = AudibleCloneTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
fun FilterOptionsContent(
    currentFilter: FilterOption,
    onFilterChange: (FilterOption) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FilterSection(
                title = "Source",
                options = listOf("All") + BookSource.values().map { it.name },
                selectedValue = currentFilter.source,
                onSelect = { value ->
                    onFilterChange(currentFilter.copy(source = if (value == "All") null else value))
                }
            )
        }
        item {
            FilterSection(
                title = "Genre",
                options = listOf("All", "Fiction", "Non-Fiction", "Mystery", "Sci-Fi", "Fantasy", "Romance", "Biography", "History", "Self-Help", "Business", "Children", "Young Adult"),
                selectedValue = currentFilter.genre,
                onSelect = { value ->
                    onFilterChange(currentFilter.copy(genre = if (value == "All") null else value))
                }
            )
        }
        item {
            FilterSection(
                title = "Status",
                options = listOf("All") + BookStatus.values().map { it.name },
                selectedValue = currentFilter.status,
                onSelect = { value ->
                    onFilterChange(currentFilter.copy(status = if (value == "All") null else value))
                }
            )
        }
        item {
            FilterSection(
                title = "Progress",
                options = listOf("All", "Completed", "In Progress", "Not Started"),
                selectedValue = currentFilter.progress,
                onSelect = { value ->
                    onFilterChange(currentFilter.copy(progress = if (value == "All") null else value))
                }
            )
        }
        item {
            FilterSection(
                title = "Rating",
                options = listOf("All", "5 Stars", "4+ Stars", "3+ Stars", "2+ Stars", "1+ Stars", "Unrated"),
                selectedValue = currentFilter.rating,
                onSelect = { value ->
                    onFilterChange(currentFilter.copy(rating = if (value == "All") null else value))
                }
            )
        }
    }
}

@Composable
fun FilterSection(
    title: String,
    options: List<String>,
    selectedValue: String?,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = AudibleCloneTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = AudibleCloneTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyColumn(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(options) { option ->
                val isSelected = (selectedValue == option) || (selectedValue == null && option == "All")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelect(option) },
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 1.dp else 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) AudibleCloneTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else AudibleCloneTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = AudibleCloneTheme.typography.bodyMedium,
                            color = if (isSelected) AudibleCloneTheme.colorScheme.onPrimaryContainer else AudibleCloneTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = AudibleCloneTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class SortOption(val displayName: String, val description: String, val sortBy: String, val sortOrder: String) {
    RECENTLY_ADDED("Recently Added", "Newest books first", "added_at", "desc"),
    TITLE_A_Z("Title A-Z", "Alphabetical by title", "title", "asc"),
    AUTHOR_A_Z("Author A-Z", "Alphabetical by author", "author", "asc"),
    DURATION("Duration", "Shortest to longest", "duration_seconds", "asc"),
    DURATION_DESC("Duration", "Longest to shortest", "duration_seconds", "desc"),
    PROGRESS("Progress", "Most progress first", "progress_seconds", "desc"),
    RATING("Rating", "Highest rated first", "user_rating", "desc"),
    LAST_PLAYED("Last Played", "Most recently played", "last_played_at", "desc"),
    PLAY_COUNT("Play Count", "Most played first", "play_count", "desc"),
}

data class FilterOption(
    val source: String? = null,
    val genre: String? = null,
    val status: String? = null,
    val progress: String? = null,
    val rating: String? = null,
) {
    fun hasActiveFilters(): Boolean {
        return source != null || genre != null || status != null || progress != null || rating != null
    }
}