package id.co.alphanusa.perisaitab.data.remote.request

import com.google.gson.annotations.SerializedName

// Login Request
data class LoginRequest(
    @SerializedName("Otp")
    val otp: String
)
