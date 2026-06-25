package id.co.alphanusa.perisaitab.data.remote.request

import com.google.gson.annotations.SerializedName

data class CentrifugoTokenRequest(
    @SerializedName("client_id") val clientId: String = ""
)
