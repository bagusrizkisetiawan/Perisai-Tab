package id.co.alphanusa.perisaitab.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

class SecureTokenStorage(private val context: Context) {

    companion object {
        private const val TAG = "SecureTokenStorage"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val PREFS_NAME = "auth_prefs"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        createEncryptedPrefs()
    }

    private fun createEncryptedPrefs() = try {
        buildEncryptedPrefs()
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences gagal dibuka, menghapus data lama...", e)
        clearCorruptedData()
        buildEncryptedPrefs()
    }

    private fun buildEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun clearCorruptedData() {
        context.deleteSharedPreferences(PREFS_NAME)

        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghapus entry keystore", e)
        }
    }

    fun saveRefreshToken(token: String) {
        sharedPreferences.edit().putString(REFRESH_TOKEN_KEY, token).apply()
    }

    fun getRefreshToken(): String? {
        return sharedPreferences.getString(REFRESH_TOKEN_KEY, null)
    }

    fun clearRefreshToken() {
        sharedPreferences.edit().remove(REFRESH_TOKEN_KEY).apply()
    }

    fun hasRefreshToken(): Boolean {
        return getRefreshToken() != null
    }

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}
