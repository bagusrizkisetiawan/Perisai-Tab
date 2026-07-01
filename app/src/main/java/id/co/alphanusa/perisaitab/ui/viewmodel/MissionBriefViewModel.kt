package id.co.alphanusa.perisaitab.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.co.alphanusa.perisaitab.data.remote.api.ApiService
import id.co.alphanusa.perisaitab.data.remote.response.MissionBriefItem
import id.co.alphanusa.perisaitab.data.remote.response.MissionNoteItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MissionBriefViewModel(private val apiService: ApiService) : ViewModel() {

    private val _items = MutableStateFlow<List<MissionBriefItem>>(emptyList())
    val items: StateFlow<List<MissionBriefItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Mission Notes ───────────────────────────────────────────────────────
    private val _notes = MutableStateFlow<List<MissionNoteItem>>(emptyList())
    val notes: StateFlow<List<MissionNoteItem>> = _notes.asStateFlow()

    private val _notesLoading = MutableStateFlow(false)
    val notesLoading: StateFlow<Boolean> = _notesLoading.asStateFlow()

    private val _notesError = MutableStateFlow<String?>(null)
    val notesError: StateFlow<String?> = _notesError.asStateFlow()

    /** Ambil ulang rundown + notes sekaligus (dipanggil saat dialog dibuka). */
    fun refresh() {
        fetchRundown()
        fetchNotes()
    }

    fun fetchRundown() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiService.getMissionBriefRundown()
                if (response.isSuccessful) {
                    _items.value = response.body()?.data ?: emptyList()
                    Log.d("MissionBriefVM", "Loaded ${_items.value.size} item")
                } else {
                    _error.value = "Gagal memuat data (HTTP ${response.code()})"
                    Log.w("MissionBriefVM", "Gagal: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                _error.value = "Error jaringan: ${e.localizedMessage}"
                Log.e("MissionBriefVM", "Error: ${e.localizedMessage}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchNotes() {
        viewModelScope.launch {
            _notesLoading.value = true
            _notesError.value = null
            try {
                val response = apiService.getMissionBriefNotes()
                Log.d("MissionBriefVM", "NOTES HTTP ${response.code()}")
                if (response.isSuccessful) {
                    val body = response.body()
                    // Log seluruh body ter-parse (data class → toString menampilkan semua field).
                    Log.d("MissionBriefVM", "NOTES BODY = $body")
                    body?.data?.forEachIndexed { i, note ->
                        Log.d(
                            "MissionBriefVM",
                            "note[$i] id=${note.ID} title=${note.Title} " +
                                "note=${note.Note} images=${note.Images.size}"
                        )
                        note.Images.forEachIndexed { j, img ->
                            Log.d(
                                "MissionBriefVM",
                                "  image[$i][$j] id=${img.ID} file=${img.FileName} url=${img.url}"
                            )
                        }
                    }
                    _notes.value = body?.data ?: emptyList()
                    Log.d("MissionBriefVM", "Loaded ${_notes.value.size} note")
                } else {
                    val err = response.errorBody()?.string()
                    Log.w("MissionBriefVM", "NOTES gagal HTTP ${response.code()} err=$err")
                    _notesError.value = "Gagal memuat notes (HTTP ${response.code()})"
                }
            } catch (e: Exception) {
                _notesError.value = "Error jaringan: ${e.localizedMessage}"
                Log.e("MissionBriefVM", "Notes error: ${e.localizedMessage}", e)
            } finally {
                _notesLoading.value = false
            }
        }
    }
}

class MissionBriefViewModelFactory(
    private val apiService: ApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MissionBriefViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MissionBriefViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
