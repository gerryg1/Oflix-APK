package com.oflix.app.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class RankingUiState {
    object Loading : RankingUiState()
    data class Success(
        val items: List<MediaItem>,
        val isFetchingMore: Boolean = false,
        val hasReachedEnd: Boolean = false
    ) : RankingUiState()
    data class Error(val message: String) : RankingUiState()
}

class RankingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<RankingUiState>(RankingUiState.Loading)
    val uiState: StateFlow<RankingUiState> = _uiState

    private var currentCategoryId: String = ""
    private var currentPage = 1
    private val perPage = 15
    private val items = mutableListOf<MediaItem>()

    fun loadRanking(id: String) {
        if (id == currentCategoryId && items.isNotEmpty()) return // Already loaded
        
        currentCategoryId = id
        currentPage = 1
        items.clear()
        _uiState.value = RankingUiState.Loading

        fetchPage()
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState is RankingUiState.Success) {
            if (currentState.isFetchingMore || currentState.hasReachedEnd) return
            
            _uiState.value = currentState.copy(isFetchingMore = true)
            currentPage++
            fetchPage()
        }
    }

    private fun fetchPage() {
        viewModelScope.launch {
            try {
                val response = ApiClient.getClient().getRanking(currentCategoryId, currentPage, perPage)
                
                if (response.isSuccessful && response.body() != null) {
                    val newItems = DataMapper.parseRanking(response.body()!!)
                    
                    // If we got fewer items than requested, we've likely hit the end
                    val reachedEnd = newItems.isEmpty() || newItems.size < perPage
                    
                    items.addAll(newItems)
                    
                    _uiState.value = RankingUiState.Success(
                        items = items.toList(), // return copy
                        isFetchingMore = false,
                        hasReachedEnd = reachedEnd
                    )
                } else {
                    if (currentPage == 1) {
                        _uiState.value = RankingUiState.Error("Gagal memuat data")
                    } else {
                        // If it fails on pagination, just stop fetching more
                        val currentState = _uiState.value as? RankingUiState.Success
                        if (currentState != null) {
                            _uiState.value = currentState.copy(isFetchingMore = false, hasReachedEnd = true)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (currentPage == 1) {
                    _uiState.value = RankingUiState.Error(e.message ?: "Terjadi kesalahan jaringan")
                } else {
                    // Retry logic or just stop fetching more state
                    val currentState = _uiState.value as? RankingUiState.Success
                    if (currentState != null) {
                        _uiState.value = currentState.copy(isFetchingMore = false)
                    }
                }
            }
        }
    }
}
