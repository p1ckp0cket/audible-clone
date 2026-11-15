package com.audibleclone.data.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audibleclone.data.api.ApiService
import com.audibleclone.data.local.AppDatabase
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.PaginatedResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class LibraryRepository(
    private val apiService: ApiService,
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    fun loadLibrary(
        page: Int = 1,
        forceRefresh: Boolean = false,
        searchQuery: String? = null,
        sortBy: String = "added_at",
        sortOrder: String = "desc",
        filterSource: String? = null,
        filterGenre: String? = null,
        filterStatus: String? = null,
    ) {
        viewModelScope.launch {
            if (page == 1) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            try {
                // Try local cache first for instant UI
                if (!forceRefresh && page == 1 && (searchQuery == null || searchQuery.isBlank())) {
                    val cachedBooks = database.bookDao().getAllBooks()
                    if (cachedBooks.isNotEmpty()) {
                        _uiState.update { it.copy(books = cachedBooks, isLoading = false) }
                    }
                }

                // Fetch from API
                val response: PaginatedResponse<Book> = apiService.getBooks(
                    page = page,
                    search = searchQuery,
                    sort = sortBy,
                    order = sortOrder,
                    source = filterSource,
                    genre = filterGenre,
                    status = filterStatus,
                )

                // Update local cache
                if (page == 1) {
                    database.bookDao().insertAll(response.items)
                }

                _uiState.update {
                    val newBooks = if (page == 1) response.items else it.books + response.items
                    it.copy(
                        books = newBooks,
                        isLoading = false,
                        currentPage = page,
                        totalPages = response.pagination.total_pages,
                        hasMore = page < response.pagination.total_pages,
                        searchQuery = searchQuery ?: "",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = when (e) {
                            is HttpException -> "Server error: ${e.code()}"
                            else -> e.message ?: "Unknown error"
                        }
                    )
                }
            }
        }
    }

    fun refresh() {
        loadLibrary(page = 1, forceRefresh = true)
    }

    fun loadMore() {
        if (!uiState.value.isLoading && uiState.value.hasMore) {
            loadLibrary(
                page = uiState.value.currentPage + 1,
                searchQuery = uiState.value.searchQuery.takeIf { it.isNotBlank() },
                sortBy = uiState.value.sortBy,
                sortOrder = uiState.value.sortOrder,
                filterSource = uiState.value.filterSource,
                filterGenre = uiState.value.filterGenre,
                filterStatus = uiState.value.filterStatus,
            )
        }
    }

    fun searchLibrary(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, searchQuery = query) }

            try {
                val response: PaginatedResponse<Book> = apiService.getBooks(search = query)

                _uiState.update {
                    it.copy(
                        books = response.items,
                        isLoading = false,
                        currentPage = 1,
                        totalPages = response.pagination.total_pages,
                        hasMore = 1 < response.pagination.total_pages,
                        searchQuery = query,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Search failed"
                    )
                }
            }
        }
    }

    fun applySortAndFilter(
        sortBy: String,
        sortOrder: String,
        filterSource: String? = null,
        filterGenre: String? = null,
        filterStatus: String? = null,
    ) {
        loadLibrary(
            page = 1,
            forceRefresh = true,
            searchQuery = uiState.value.searchQuery.takeIf { it.isNotBlank() },
            sortBy = sortBy,
            sortOrder = sortOrder,
            filterSource = filterSource,
            filterGenre = filterGenre,
            filterStatus = filterStatus,
        )
    }

    fun clearSearch() {
        loadLibrary(page = 1, forceRefresh = true)
    }
}

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val hasMore: Boolean = false,
    val searchQuery: String = "",
    val sortBy: String = "added_at",
    val sortOrder: String = "desc",
    val filterSource: String? = null,
    val filterGenre: String? = null,
    val filterStatus: String? = null,
)