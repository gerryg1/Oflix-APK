package com.oflix.app.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val data: JsonObject) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

class DetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState

    fun loadDetail(detailPath: String) {
        _uiState.value = DetailUiState.Loading
        viewModelScope.launch {
            try {
                val api = ApiClient.getClient()
                val response = api.getDetail(detailPath)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = DetailUiState.Success(response.body()!!)
                } else {
                    _uiState.value = DetailUiState.Error("Gagal memuat detail film.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = DetailUiState.Error(e.message ?: "Terjadi kesalahan jaringan.")
            }
        }
    }
}
