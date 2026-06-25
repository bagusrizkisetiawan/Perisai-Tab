package id.co.alphanusa.perisaitab.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import id.co.alphanusa.perisaitab.data.local.SecureTokenStorage
import id.co.alphanusa.perisaitab.data.remote.api.ApiService
import id.co.alphanusa.perisaitab.data.remote.request.LoginRequest
import id.co.alphanusa.perisaitab.data.remote.request.RefreshTokenRequest
import id.co.alphanusa.perisaitab.data.remote.response.LoginData
import id.co.alphanusa.perisaitab.domain.model.ApiErrorResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class AuthRepository(
    private val apiService: ApiService,
    private val secureStorage: SecureTokenStorage,
    private val context: Context
) {

    companion object {
        private const val TAG = "AuthRepository"
    }

    private val gson = Gson()

    suspend fun login(otp: String): Result<LoginData> = withContext(Dispatchers.IO) {
        try {
            val request = LoginRequest(otp)
            val response = apiService.login(request)

            if (response.isSuccessful) {
                val loginData = response.body()?.data
                if (loginData != null) {
                    // Save refresh token securely
                    secureStorage.saveRefreshToken(loginData.refreshToken)
                    Log.d(TAG, "Login successful, tokens saved")
                    Result.success(loginData)
                } else {
                    Log.e(TAG, "Login response body is null")
                    Result.failure(Exception("Invalid response from server"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, ApiErrorResponse::class.java).message
                        ?: "Login failed"
                } catch (e: Exception) {
                    "Login failed: HTTP ${response.code()}"
                }
                Log.e(TAG, "Login failed: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error during login", e)
            Result.failure(Exception("Network error. Please check your connection."))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during login", e)
            Result.failure(Exception("An unexpected error occurred"))
        }
    }

    suspend fun refreshToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val refreshToken = secureStorage.getRefreshToken()
            if (refreshToken == null) {
                Log.e(TAG, "No refresh token found")
                return@withContext Result.failure(Exception("No refresh token available"))
            }

            val request = RefreshTokenRequest(refreshToken)
            val response = apiService.refreshToken(request)

            if (response.isSuccessful) {
                val newAccessToken = response.body()?.data?.accessToken
                if (newAccessToken != null) {
                    Log.d(TAG, "Token refresh successful")
                    Result.success(newAccessToken)
                } else {
                    Log.e(TAG, "Refresh token response body is null")
                    Result.failure(Exception("Invalid response from server"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, ApiErrorResponse::class.java).message
                        ?: "Token refresh failed"
                } catch (e: Exception) {
                    "Token refresh failed: HTTP ${response.code()}"
                }
                Log.e(TAG, "Token refresh failed: $errorMessage")

                // If refresh fails, clear stored refresh token (force re-login)
                if (response.code() == 401 || response.code() == 403) {
                    logout()
                }

                Result.failure(Exception(errorMessage))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error during token refresh", e)
            Result.failure(Exception("Network error. Please check your connection."))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during token refresh", e)
            Result.failure(Exception("An unexpected error occurred"))
        }
    }

    fun logout() {
        Log.d(TAG, "Logging out, clearing tokens")
        secureStorage.clearRefreshToken()
    }

    fun hasValidSession(): Boolean {
        return secureStorage.hasRefreshToken()
    }
}
