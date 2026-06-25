package id.co.alphanusa.perisaitab.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.co.alphanusa.perisaitab.data.remote.api.ApiService
import id.co.alphanusa.perisaitab.data.remote.response.UserData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserViewModel(private val apiService: ApiService) : ViewModel() {

    private val _user = MutableStateFlow<UserData?>(null)
    val user: StateFlow<UserData?> = _user.asStateFlow()

    init {
        fetchUser()
    }

    fun fetchUser() {
        viewModelScope.launch {
            try {
                val response = apiService.getMe()
                if (response.isSuccessful) {
                    _user.value = response.body()?.data
                    Log.d("UserViewModel", "User loaded: ${_user.value?.Name}")
                } else {
                    Log.w("UserViewModel", "Gagal: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error Jaringan: ${e.localizedMessage}", e)
            }
        }
    }

    fun clearUser() {
        _user.value = null
    }
}

class UserViewModelFactory(
    private val apiService: ApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
