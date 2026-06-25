package id.co.alphanusa.perisaitab.data.remote.response

import com.google.gson.annotations.SerializedName

data class CentrifugoTokenResponse(
    @SerializedName("data") val data: CentrifugoTokenData,
    @SerializedName("message") val message: String
)

data class CentrifugoTokenData(
    @SerializedName("token") val token: String
)
