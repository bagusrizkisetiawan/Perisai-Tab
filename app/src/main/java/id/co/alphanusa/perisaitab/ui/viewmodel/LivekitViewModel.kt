package id.co.alphanusa.perisaitab.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.co.alphanusa.perisaitab.data.remote.api.ApiService
import id.co.alphanusa.perisaitab.data.remote.response.Participant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LivekitViewModel(private val apiService: ApiService) : ViewModel() {
    // State untuk menyimpan token (awalnya null)
    private val _livekitToken = MutableStateFlow<String?>(null)
    val livekitToken: StateFlow<String?> = _livekitToken.asStateFlow()

    private val _listParticipant = MutableStateFlow<List<Participant>>(emptyList())
    val listParticipant: StateFlow<List<Participant>> = _listParticipant.asStateFlow()

    fun fetchLivekitToken() {
        viewModelScope.launch {
            try {
                val response = apiService.generateLivekitToken()

                if (response.isSuccessful) {
                    val response = response.body()
                    // Misalnya token ada di dalam properti 'token' pada object response Anda
                    _livekitToken.value = response?.data?.token
                } else {
                    println("Gagal: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                println("Error Jaringan: ${e.localizedMessage}")
            }
        }
    }

    fun fetchListParticipant() {
        viewModelScope.launch {
            try {
                val response = apiService.generateListParticipant()
                if (response.isSuccessful) {
                    val response = response.body()
                    // Misalnya token ada di dalam properti 'token' pada object response Anda
                    _listParticipant.value = response?.data ?: emptyList()
                } else {
                    println("Gagal: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                println("Error Jaringan: ${e.localizedMessage}")
            }
        }
    }

    fun clearLivekitToken() {
        _livekitToken.value = null
    }
}

// Factory diperlukan karena ViewModel kita membutuhkan parameter (apiService)
class LivekitViewModelFactory(
    private val apiService: ApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LivekitViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LivekitViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
