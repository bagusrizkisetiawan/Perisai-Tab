package id.co.alphanusa.perisaitab.data.local

import android.content.Context
import android.content.SharedPreferences
import id.co.alphanusa.perisaitab.BuildConfig

/**
 * Manager for application settings that can be configured in-app
 */
class AppSettingsManager private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_CENTRIFUGO_WEBSOCKET_URL = "centrifugo_websocket_url"
        private const val KEY_RTMP_URL = "rtmp_url"
        private const val KEY_LIVEKIT_URL = "livekit_url"

        @Volatile
        private var INSTANCE: AppSettingsManager? = null

        fun getInstance(context: Context): AppSettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get the base URL for API calls
     * Returns saved value or default from BuildConfig
     */
    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, BuildConfig.BASE_URL) ?: BuildConfig.BASE_URL

    }

    /**
     * Set the base URL for API calls
     */
    fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    /**
     * Get the Centrifugo WebSocket URL
     * Returns saved value or default from BuildConfig
     */
    fun getCentrifugoWebSocketUrl(): String {
        return prefs.getString(KEY_CENTRIFUGO_WEBSOCKET_URL, BuildConfig.CENTRIFUGO_WEBSOCKET_URL)
            ?: BuildConfig.CENTRIFUGO_WEBSOCKET_URL
    }

    /**
     * Set the Centrifugo WebSocket URL
     */
    fun setCentrifugoWebSocketUrl(url: String) {
        prefs.edit().putString(KEY_CENTRIFUGO_WEBSOCKET_URL, url).apply()
    }

    /**
     * Get the RTMP streaming URL
     * Returns saved value or default from BuildConfig
     */
    fun getRtmpUrl(): String {
        return prefs.getString(KEY_RTMP_URL, BuildConfig.RTMP_URL) ?: BuildConfig.RTMP_URL

    }

    /**
     * Set the RTMP streaming URL
     */
    fun setRtmpUrl(url: String) {
        prefs.edit().putString(KEY_RTMP_URL, url).apply()
    }


    /**
     * Get the LIVEKIT streaming URL
     * Returns saved value or default from BuildConfig
     */
    fun getLivekitUrl(): String {
        return prefs.getString(KEY_LIVEKIT_URL, BuildConfig.LIVEKIT_URL) ?: BuildConfig.LIVEKIT_URL

    }

    /**
     * Set the LIVEKIT streaming URL
     */
    fun seLivekitUrl(url: String) {
        prefs.edit().putString(KEY_LIVEKIT_URL, url).apply()
    }

    /**
     * Reset all settings to default values from BuildConfig
     */
    fun resetToDefaults() {
        prefs.edit().apply {
//            putString(KEY_BASE_URL, BuildConfig.BASE_URL)
//            putString(KEY_CENTRIFUGO_WEBSOCKET_URL, BuildConfig.CENTRIFUGO_WEBSOCKET_URL)
//            putString(KEY_RTMP_URL, BuildConfig.RTMP_URL)
            apply()
        }
    }
}
