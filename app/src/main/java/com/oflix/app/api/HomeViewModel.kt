package com.oflix.app.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oflix.app.data.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val trending: List<MediaItem>,
        val indonesianMovies: List<MediaItem>,
        val indonesianDrama: List<MediaItem>,
        val kdrama: List<MediaItem>,
        val anime: List<MediaItem>,
        val westernTv: List<MediaItem>,
        val shortTv: List<MediaItem>,
        val horror: List<MediaItem>,
        val thailandDrama: List<MediaItem>
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    // Category IDs matching api/cache/route.js RANKING_LISTS
    private val CATEGORY_IDS = mapOf(
        "trending" to "872031290915189720",
        "indonesian-movies" to "6528093688173053896",
        "indonesian-drama" to "5283462032510044280",
        "kdrama" to "4380734070238626200",
        "anime" to "8617025562613270856",
        "western-tv" to "1469286917119311888",
        "short-tv" to "8624142774394406504",
        "horror" to "5848753831881965888",
        "thailand-drama" to "1164329479448281992"
    )

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                val api = ApiClient.getClient()

                // Fetch with cache: if cache hit, skip network call
                suspend fun fetchCategory(key: String): List<MediaItem> {
                    val id = CATEGORY_IDS[key] ?: return emptyList()
                    val cached = ResponseCache.getRanking(id)
                    if (cached != null) return cached

                    val response = api.getRanking(id)
                    val items = if (response.isSuccessful && response.body() != null) {
                        DataMapper.parseRanking(response.body()!!)
                    } else emptyList()

                    if (items.isNotEmpty()) ResponseCache.putRanking(id, items)
                    return items
                }

                // Fetch all categories in parallel
                val trendingDef = async { fetchCategory("trending") }
                val indoMoviesDef = async { fetchCategory("indonesian-movies") }
                val indoDramaDef = async { fetchCategory("indonesian-drama") }
                val kdramaDef = async { fetchCategory("kdrama") }
                val animeDef = async { fetchCategory("anime") }
                val westernTvDef = async { fetchCategory("western-tv") }
                val shortTvDef = async { fetchCategory("short-tv") }
                val horrorDef = async { fetchCategory("horror") }
                val thailandDramaDef = async { fetchCategory("thailand-drama") }

                val trending = trendingDef.await()
                val indoMovies = indoMoviesDef.await()
                val indoDrama = indoDramaDef.await()
                val kdrama = kdramaDef.await()
                val anime = animeDef.await()
                val westernTv = westernTvDef.await()
                val shortTv = shortTvDef.await()
                val horror = horrorDef.await()
                val thailandDrama = thailandDramaDef.await()

                if (trending.isNotEmpty()) {
                    _uiState.value = HomeUiState.Success(
                        trending = trending,
                        indonesianMovies = indoMovies,
                        indonesianDrama = indoDrama,
                        kdrama = kdrama,
                        anime = anime,
                        westernTv = westernTv,
                        shortTv = shortTv,
                        horror = horror,
                        thailandDrama = thailandDrama
                    )
                } else {
                    _uiState.value = HomeUiState.Error("Gagal memuat data utama")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = HomeUiState.Error(e.message ?: "Terjadi kesalahan jaringan")
            }
        }
    }
}
