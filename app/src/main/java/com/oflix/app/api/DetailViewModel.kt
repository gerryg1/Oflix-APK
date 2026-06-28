package com.oflix.app.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Parsed episode data matching the JS transformDetail() output
 */
data class EpisodeData(
    val episode: Int,
    val title: String,
    val thumbnail: String = ""
)

data class SeasonData(
    val season: Int,
    val episodes: List<EpisodeData>
)

data class DetailData(
    val subjectId: String,
    val title: String,
    val description: String,
    val coverUrl: String,
    val year: String,
    val rating: String,
    val genre: String,
    val country: String,
    val duration: String,
    val isMovie: Boolean,
    val seasons: List<SeasonData>,
    val trailerUrl: String,
    val detailPath: String
)

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val data: JsonObject, val detail: DetailData) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

sealed class StreamUiState {
    object Idle : StreamUiState()
    object Loading : StreamUiState()
    data class Ready(val videoUrl: String, val title: String) : StreamUiState()
    data class Error(val message: String) : StreamUiState()
}

class DetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState

    private val _streamState = MutableStateFlow<StreamUiState>(StreamUiState.Idle)
    val streamState: StateFlow<StreamUiState> = _streamState

    fun loadDetail(detailPath: String) {
        _uiState.value = DetailUiState.Loading
        _streamState.value = StreamUiState.Idle
        viewModelScope.launch {
            try {
                // Check cache first
                val cached = ResponseCache.getDetail(detailPath)
                val responseBody: JsonObject

                if (cached != null) {
                    responseBody = cached
                } else {
                    val api = ApiClient.getClient()
                    val response = api.getDetail(detailPath)
                    if (response.isSuccessful && response.body() != null) {
                        responseBody = response.body()!!
                        ResponseCache.putDetail(detailPath, responseBody)
                    } else {
                        _uiState.value = DetailUiState.Error("Gagal memuat detail film.")
                        return@launch
                    }
                }

                val detail = parseDetailData(responseBody, detailPath)
                if (detail != null) {
                    _uiState.value = DetailUiState.Success(responseBody, detail)
                } else {
                    _uiState.value = DetailUiState.Error("Format data tidak valid.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = DetailUiState.Error(e.message ?: "Terjadi kesalahan jaringan.")
            }
        }
    }

    /**
     * Load stream for playback. Mirrors Detail.jsx loadStream() logic.
     */
    fun loadStream(subjectId: String, seasonIdx: Int, episodeIdx: Int, title: String, detailPath: String) {
        _streamState.value = StreamUiState.Loading
        viewModelScope.launch {
            try {
                val se = if (seasonIdx >= 0) (seasonIdx + 1).toString() else "0"
                val ep = if (episodeIdx >= 0) (episodeIdx + 1).toString() else "0"

                // Retry up to 3 times (matching JS fetchStream retry logic)
                var lastResult: StreamRepository.StreamResult? = null
                for (attempt in 1..3) {
                    val result = StreamRepository.fetchStream(subjectId, se, ep, detailPath)
                    lastResult = result
                    if (result.success && result.videoUrl.isNotEmpty()) {
                        _streamState.value = StreamUiState.Ready(result.videoUrl, title)
                        return@launch
                    }
                    if (attempt < 3) kotlinx.coroutines.delay(500)
                }

                _streamState.value = StreamUiState.Error(
                    lastResult?.error ?: "Gagal mendapatkan link streaming."
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _streamState.value = StreamUiState.Error(e.message ?: "Error loading stream.")
            }
        }
    }

    fun resetStream() {
        _streamState.value = StreamUiState.Idle
    }

    /**
     * Parse the raw API JSON into our clean DetailData model.
     * Mirrors transformDetail() from lib/moviebox.js
     */
    private fun parseDetailData(responseBody: JsonObject, detailPath: String): DetailData? {
        try {
            val data = responseBody.getAsJsonObject("data") ?: return null
            val subject = if (data.has("subject") && !data.get("subject").isJsonNull)
                data.getAsJsonObject("subject") else null
            val resource = if (data.has("resource") && !data.get("resource").isJsonNull)
                data.getAsJsonObject("resource") else null
            val metadata = if (data.has("metadata") && !data.get("metadata").isJsonNull)
                data.getAsJsonObject("metadata") else null

            val subjectId = subject?.get("subjectId")?.asString ?: ""
            val title = subject?.get("title")?.asString ?: metadata?.get("title")?.asString ?: "Unknown"
            val description = subject?.get("description")?.asString
                ?: subject?.get("brief")?.asString
                ?: metadata?.get("description")?.asString ?: ""
            val year = (subject?.get("releaseDate")?.asString ?: "").take(4)
            val rating = subject?.get("imdbRatingValue")?.asString ?: ""
            val genre = subject?.get("genre")?.asString ?: ""
            val country = subject?.get("countryName")?.asString ?: ""
            val durationMinutes = try { subject?.get("duration")?.asInt ?: 0 } catch (_: Exception) { 0 }
            val durationStr = if (durationMinutes > 0) "${durationMinutes}m" else ""

            // Cover URL
            var coverUrl = ""
            if (subject?.has("cover") == true && subject.get("cover").isJsonObject) {
                coverUrl = subject.getAsJsonObject("cover")?.get("url")?.asString ?: ""
            }
            if (coverUrl.isEmpty() && metadata?.has("image") == true) {
                coverUrl = metadata.get("image")?.asString ?: ""
            }

            // Trailer URL - matching JS: can be string, object with videoAddress, or null
            var trailerUrl = ""
            if (subject?.has("trailer") == true && !subject.get("trailer").isJsonNull) {
                val trailer = subject.get("trailer")
                if (trailer.isJsonPrimitive) {
                    trailerUrl = trailer.asString
                } else if (trailer.isJsonObject) {
                    val trailerObj = trailer.asJsonObject
                    if (trailerObj.has("videoAddress") && trailerObj.get("videoAddress").isJsonObject) {
                        trailerUrl = trailerObj.getAsJsonObject("videoAddress")?.get("url")?.asString ?: ""
                    }
                    if (trailerUrl.isEmpty()) {
                        trailerUrl = trailerObj.get("url")?.asString ?: ""
                    }
                }
            }

            // Seasons / Episodes - matching JS transformDetail()
            val seasons = mutableListOf<SeasonData>()
            if (resource?.has("seasons") == true && resource.get("seasons").isJsonArray) {
                for (seElement in resource.getAsJsonArray("seasons")) {
                    if (!seElement.isJsonObject) continue
                    val seObj = seElement.asJsonObject
                    val seNum = try { seObj.get("se")?.asInt ?: 1 } catch (_: Exception) { 1 }
                    val maxEp = try { seObj.get("maxEp")?.asInt ?: 0 } catch (_: Exception) { 0 }

                    val episodes = mutableListOf<EpisodeData>()
                    for (e in 1..maxEp) {
                        episodes.add(EpisodeData(episode = e, title = "Episode $e"))
                    }

                    if (episodes.isNotEmpty()) {
                        seasons.add(SeasonData(season = seNum, episodes = episodes))
                    }
                }
            }

            val subjectType = try { subject?.get("subjectType")?.asInt ?: 1 } catch (_: Exception) { 1 }
            val totalEps = seasons.sumOf { it.episodes.size }
            val isMovie = totalEps == 0 || subjectType == 1

            return DetailData(
                subjectId = subjectId,
                title = title,
                description = description,
                coverUrl = coverUrl,
                year = year,
                rating = rating,
                genre = genre,
                country = country,
                duration = durationStr,
                isMovie = isMovie,
                seasons = if (isMovie) emptyList() else seasons,
                trailerUrl = trailerUrl,
                detailPath = detailPath
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
