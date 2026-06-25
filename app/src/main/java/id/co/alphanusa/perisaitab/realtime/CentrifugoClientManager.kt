package id.co.alphanusa.perisaitab.realtime

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import id.co.alphanusa.perisaitab.data.local.AppSettingsManager
import id.co.alphanusa.perisaitab.data.remote.api.ApiConfig
import id.co.alphanusa.perisaitab.data.remote.request.CentrifugoTokenRequest
import id.co.alphanusa.perisaitab.domain.model.PocData
import io.github.centrifugal.centrifuge.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Enhanced Centrifugo client using centrifuge-java library
 * Provides better reliability, automatic reconnection, and Centrifugo-specific features
 */
class CentrifugoClientManager private constructor(context: Context) {

    companion object {
        private const val TAG = "CentrifugoClientManager"
        private var INSTANCE: CentrifugoClientManager? = null
        private const val DATA_TRANSMISSION_INTERVAL_MS = 100L

        fun getInstance(context: Context): CentrifugoClientManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CentrifugoClientManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * Clear the cached instance to force re-initialization
         * This is useful when URL settings change and we need to reconnect with new URLs
         */
        @Synchronized
        fun clearInstance() {
            INSTANCE?.cleanup()
            INSTANCE = null
        }
    }

    private val gson = Gson()
    private val apiConfig = ApiConfig.getInstance(context)
    private val settingsManager = AppSettingsManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var client: Client? = null
    private var subscription: Subscription? = null
    private var dataTransmissionJob: Job? = null

    // State flows for UI observation
    private val _connectionState = MutableStateFlow(CentrifugoConnectionState.DISCONNECTED)
    val connectionState: StateFlow<CentrifugoConnectionState> = _connectionState

    private val _lastDataSent = MutableStateFlow<PocData?>(null)
    val lastDataSent: StateFlow<PocData?> = _lastDataSent

    private val _receivedMessages = MutableStateFlow<List<String>>(emptyList())
    val receivedMessages: StateFlow<List<String>> = _receivedMessages

    private var currentPocData: PocData? = null

    // Single consolidated API service for token generation
    private val apiService get() = apiConfig.apiService

    /**
     * Start connection to Centrifugo server
     */
    fun startConnection() {
        Log.d(TAG, "Starting Centrifugo connection with centrifuge-java")
        scope.launch {
            try {
                _connectionState.value = CentrifugoConnectionState.CONNECTING

                // Generate fresh token before connecting
                val centrifugoToken = generateToken()
                if (centrifugoToken != null) {
                    connectToCentrifugo(centrifugoToken)
                } else {
                    Log.e(TAG, "Failed to generate Centrifugo token")
                    _connectionState.value = CentrifugoConnectionState.ERROR
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting connection", e)
                _connectionState.value = CentrifugoConnectionState.ERROR
            }
        }
    }

    /**
     * Stop connection to Centrifugo server
     */
    fun stopConnection() {
        Log.d(TAG, "Stopping Centrifugo connection")
        dataTransmissionJob?.cancel()
        subscription?.unsubscribe()
        client?.disconnect()
        _connectionState.value = CentrifugoConnectionState.DISCONNECTED
    }

    /**
     * Update poc data to be sent to Centrifugo
     */
    fun updatePocData(pocData: PocData) {
        currentPocData = pocData
    }

    /**
     * Publish data to a specific channel
     */
    fun publishToChannel(channel: String, data: Any) {
        client?.let { client ->
            scope.launch {
                try {
                    val jsonData = gson.toJson(data).toByteArray()
                    client.publish(channel, jsonData) { error, result ->
                        if (error != null) {
                            Log.e(TAG, "Failed to publish to channel $channel", error)
                        } else {
//                            Log.d(TAG, "Successfully published to channel $channel")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error publishing to channel $channel", e)
                }
            }
        }
    }

    private suspend fun generateToken(): String? {
        return try {
            val request = CentrifugoTokenRequest()
            val response = apiService.generateCentrifugoToken(request)

            if (response.isSuccessful) {
                val tokenData = response.body()?.data
                if (tokenData != null) {
                    Log.d(TAG, "Centrifugo token generated successfully")
                    tokenData.token
                } else {
                    Log.e(TAG, "Centrifugo token generation failed: ${response.body()?.message}")
                    null
                }
            } else {
                Log.e(TAG, "Centrifugo token generation HTTP error: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating Centrifugo token", e)
            null
        }
    }

    private fun connectToCentrifugo(token: String) {
        try {
            val websocketUrl = settingsManager.getCentrifugoWebSocketUrl()
            Log.d(TAG, "Connecting to Centrifugo: $websocketUrl")

            // Create a minimal event listener to satisfy the API requirement
            val eventListener = object : EventListener() {
                override fun onConnected(
                    client: Client?,
                    event: ConnectedEvent?
                ) {
                    Log.d(TAG, "CONNECTED KE CENTRIFUGO 🔥")
                    _connectionState.value = CentrifugoConnectionState.CONNECTED

                    val pocId = token.let {
                        val parts = it.split(".")
                        if (parts.size == 3) {
                            val payload = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
                            val payloadJson = String(payload, Charsets.UTF_8)
                            gson.fromJson(payloadJson, Map::class.java)["sub"] as? String
                        } else null
                    }

                    if (pocId == null) {
                        Log.e(TAG, "Invalid token: unable to extract poc ID")
                        _connectionState.value = CentrifugoConnectionState.ERROR
                        return
                    }

                    val channel = "mobile-data:$pocId"

                    subscribeToChannels(channel)
                    startDataTransmission(channel)
                }

                override fun onDisconnected(
                    client: Client?,
                    event: DisconnectedEvent?
                ) {
                    Log.d(TAG, "disconnect KE CENTRIFUGO 🔥")

                    _connectionState.value = CentrifugoConnectionState.DISCONNECTED
                    dataTransmissionJob?.cancel()
                }

                override fun onError(
                    client: Client?,
                    event: ErrorEvent?
                ) {
                    Log.e(TAG, "Centrifugo error: ${event?.error?.message ?: "Unknown error"}")
                    _connectionState.value = CentrifugoConnectionState.ERROR
                }

                override fun onConnecting(
                    client: Client?,
                    event: ConnectingEvent?
                ) {
                    _connectionState.value = CentrifugoConnectionState.CONNECTING
                }
            }

            // Create client with required listener parameter
            client = Client(websocketUrl, Options().apply {
                this.token = token
            }, eventListener)

            // Connect to server
            client?.connect()

            // Set connection state manually

        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Centrifugo", e)
            _connectionState.value = CentrifugoConnectionState.ERROR
        }
    }

    private fun subscribeToChannels(channel: String) {
        client?.let { client ->
            try {
                // Create a minimal subscription listener to satisfy the API requirement
                val subscriptionListener = object : SubscriptionEventListener() {}

                // Create subscription with required listener parameter
                subscription = client.newSubscription(channel, subscriptionListener)
                subscription?.subscribe()
                Log.d(TAG, "Subscribed to $channel")

            } catch (e: Exception) {
                Log.e(TAG, "Error subscribing to channels", e)
            }
        }
    }

    private fun startDataTransmission(channel: String) {
        dataTransmissionJob = scope.launch {
            try {
                while (isActive) {
                    currentPocData?.let { data ->
                        // Publish the latest poc data at regular intervals
                        publishToChannel(channel, data)
                        _lastDataSent.value = data
//                        Log.d("woilah kirim", "${data}")

                    }
                    delay(DATA_TRANSMISSION_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in data transmission", e)
            }
        }
    }

    private fun handleServerMessage(message: String) {
        val currentMessages = _receivedMessages.value.toMutableList()
        currentMessages.add("Server: $message")
        if (currentMessages.size > 50) {
            currentMessages.removeAt(0)
        }
        _receivedMessages.value = currentMessages
    }

    private fun handleChannelMessage(channel: String, message: String) {
        scope.launch(Dispatchers.Main) {
            val currentMessages = _receivedMessages.value.toMutableList()
            currentMessages.add("$channel: $message")
            if (currentMessages.size > 50) {
                currentMessages.removeAt(0)
            }
            _receivedMessages.value = currentMessages
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopConnection()
        scope.cancel()
    }
}

enum class CentrifugoConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
