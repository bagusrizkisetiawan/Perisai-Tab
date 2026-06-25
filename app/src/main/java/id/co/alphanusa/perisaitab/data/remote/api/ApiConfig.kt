package id.co.alphanusa.perisaitab.data.remote.api

import android.content.Context
import android.util.Log
import id.co.alphanusa.perisaitab.BuildConfig
import id.co.alphanusa.perisaitab.data.local.SecureTokenStorage
import id.co.alphanusa.perisaitab.data.remote.interceptor.AuthInterceptor
import id.co.alphanusa.perisaitab.data.local.AppSettingsManager
import id.co.alphanusa.perisaitab.data.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Pusat konfigurasi networking: OkHttp + Retrofit + token/session.
 * (Sebelumnya bernama AuthManager.)
 */
class ApiConfig private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ApiConfig"
        private var INSTANCE: ApiConfig? = null

        fun getInstance(context: Context): ApiConfig {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiConfig(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val secureStorage = SecureTokenStorage(context)
    private lateinit var _authRepository: AuthRepository
    private val settingsManager = AppSettingsManager.getInstance(context)

    // Get base URL from settings (which falls back to secrets.properties if not set)
    private val baseUrl = settingsManager.getBaseUrl().let { url ->
        if (!url.endsWith("/")) "$url/" else url
    }

    private var currentAccessToken: String? = null

    private val authInterceptor = AuthInterceptor { getCurrentAccessToken() }

    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            // If we get 401, try to refresh the token
            if (response.code == 401) {
                Log.d(TAG, "Got 401, attempting token refresh")

                val newToken = runBlocking {
                    try {
                        _authRepository.refreshToken().getOrNull()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during token refresh", e)
                        null
                    }
                }

                return if (newToken != null) {
                    Log.d(TAG, "Token refresh successful, retrying request")
                    setCurrentAccessToken(newToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                } else {
                    Log.d(TAG, "Token refresh failed, forcing logout")
                    // Refresh failed, logout user
                    _authRepository.logout()
                    null
                }
            }
            return null
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .apply {
            if (BuildConfig.DEBUG) {
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                addInterceptor(loggingInterceptor)
            }
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /** Satu-satunya instance ApiService yang dipakai seluruh aplikasi. */
    val apiService: ApiService = retrofit.create(ApiService::class.java)

    init {
        _authRepository = AuthRepository(apiService, secureStorage, context)
    }

    val authRepository: AuthRepository
        get() = _authRepository

    fun setCurrentAccessToken(token: String?) {
        currentAccessToken = token
    }

    fun getCurrentAccessToken(): String? = currentAccessToken

    fun getHttpClient(): OkHttpClient = httpClient

    fun clearSession() {
        currentAccessToken = null
        secureStorage.clearAll()
    }

    fun recreate(context: Context) {
        INSTANCE = ApiConfig(context.applicationContext)
    }
}
