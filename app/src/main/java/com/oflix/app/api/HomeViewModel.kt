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

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                val api = ApiClient.getClient()
                
                // Fetch all categories in parallel
                val trendingDef = async { api.getRanking("872031290915189720") }
                val indoMoviesDef = async { api.getRanking("6528093688173053896") }
                val indoDramaDef = async { api.getRanking("5283462032510044280") }
                val kdramaDef = async { api.getRanking("4380734070238626200") }
                val animeDef = async { api.getRanking("8617025562613270856") }
                val westernTvDef = async { api.getRanking("1469286917119311888") }
                val shortTvDef = async { api.getRanking("8624142774394406504") }
                val horrorDef = async { api.getRanking("5848753831881965888") }
                val thailandDramaDef = async { api.getRanking("1164329479448281992") }

                val trendingRes = trendingDef.await()
                val indoMoviesRes = indoMoviesDef.await()
                val indoDramaRes = indoDramaDef.await()
                val kdramaRes = kdramaDef.await()
                val animeRes = animeDef.await()
                val westernTvRes = westernTvDef.await()
                val shortTvRes = shortTvDef.await()
                val horrorRes = horrorDef.await()
                val thailandDramaRes = thailandDramaDef.await()

                // If at least trending is successful, we consider it a success
                if (trendingRes.isSuccessful && trendingRes.body() != null) {
                    val trendingList = DataMapper.parseRanking(trendingRes.body()!!)
                    val indoMoviesList = DataMapper.parseRanking(indoMoviesRes.body())
                    val indoDramaList = DataMapper.parseRanking(indoDramaRes.body())
                    val kdramaList = DataMapper.parseRanking(kdramaRes.body())
                    val animeList = DataMapper.parseRanking(animeRes.body())
                    val westernTvList = DataMapper.parseRanking(westernTvRes.body())
                    val shortTvList = DataMapper.parseRanking(shortTvRes.body())
                    val horrorList = DataMapper.parseRanking(horrorRes.body())
                    val thailandDramaList = DataMapper.parseRanking(thailandDramaRes.body())

                    _uiState.value = HomeUiState.Success(
                        trending = trendingList,
                        indonesianMovies = indoMoviesList,
                        indonesianDrama = indoDramaList,
                        kdrama = kdramaList,
                        anime = animeList,
                        westernTv = westernTvList,
                        shortTv = shortTvList,
                        horror = horrorList,
                        thailandDrama = thailandDramaList
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
