package id.co.alphanusa.perisaitab.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.co.alphanusa.perisaitab.data.remote.api.ApiConfig
import id.co.alphanusa.perisaitab.domain.model.AuthState
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val apiConfig get() = ApiConfig.getInstance(getApplication())
    private val authRepository get() = apiConfig.authRepository
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    companion object {
        private const val TAG = "AuthViewModel"
    }

    init {
        // Check if user has valid session on app start
        checkExistingSession()
    }

    private fun checkExistingSession() {
        if (authRepository.hasValidSession()) {
            refreshToken()
        } else {
            // Tidak ada session, langsung set state final
            _authState.value = AuthState(isLoading = false)
        }
    }

    fun loginWithQR(qrCode: String) {
        viewModelScope.launch {
            // Nyalakan loading
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            try {
                // Berikan batas waktu agar tidak stuck selamanya jika server no-response (misal: 10 detik)
                withTimeout(5_000L) {
                    authRepository.login(qrCode).fold(
                        onSuccess = { loginData ->
                            apiConfig.setCurrentAccessToken(loginData.accessToken)
                            _authState.value = AuthState(
                                isLoggedIn = true,
                                accessToken = loginData.accessToken,
                                isLoading = false,
                                error = null
                            )
                            Log.d(TAG, "Login successful")
                        },
                        onFailure = { exception ->
                            _authState.value = AuthState(
                                isLoggedIn = false,
                                accessToken = null,
                                isLoading = false,
                                error = exception.message
                            )
                            Log.e(TAG, "Login failed: ${exception.message}")
                        }
                    )
                }
            } catch (e: TimeoutCancellationException) {
                // Menangkap jika proses login memakan waktu lebih dari 10 detik
                Log.e(TAG, "Login timed out")
                _authState.value = AuthState(
                    isLoggedIn = false,
                    accessToken = null,
                    isLoading = false,
                    error = "Waktu login habis (Timeout). Periksa koneksi server/internet."
                )
            } catch (e: Exception) {
                // Menangkap error jaringan mendadak (seperti SocketTimeoutException) yang lolos dari .fold()
                Log.e(TAG, "Login unexpected error: ${e.message}")
                _authState.value = AuthState(
                    isLoggedIn = false,
                    accessToken = null,
                    isLoading = false,
                    error = e.message ?: "Terjadi kesalahan sistem/jaringan."
                )
            }
        }
    }

    fun refreshToken() {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            try {
                withTimeout(5_000L) { // timeout 10 detik
                    authRepository.refreshToken().fold(
                        onSuccess = { newAccessToken ->
                            _authState.value = AuthState(
                                isLoggedIn = true,
                                accessToken = newAccessToken,
                                isLoading = false
                            )
                            apiConfig.setCurrentAccessToken(newAccessToken)
                        },
                        onFailure = { exception ->
                            Log.e(TAG, "Token refresh failed: ${exception.message}")
                            authRepository.logout()
                            apiConfig.setCurrentAccessToken(null)
                            _authState.value = AuthState(isLoading = false)
                        }
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "refreshToken timed out")
                authRepository.logout()
                _authState.value = AuthState(isLoading = false)
            } catch (e: Exception) {
                Log.e(TAG, "refreshToken unexpected error: ${e.message}")
                _authState.value = AuthState(isLoading = false)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authRepository.logout()
            } catch (e: Exception) {
                Log.e(TAG, "Logout error: ${e.message}")
            }

            // 🔥 clear semua state
            apiConfig.clearSession() // ← tambahin ini
            _authState.value = AuthState() // reset total

            Log.d(TAG, "User logged out & state cleared")
        }
    }

    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }

    fun getCurrentAccessToken(): String? {
        return _authState.value.accessToken
    }

    fun isLoggedIn(): Boolean {
        return _authState.value.isLoggedIn && _authState.value.accessToken != null
    }


}
