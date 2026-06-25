package id.co.alphanusa.perisaitab.data.remote.response

// Login Response
data class LoginResponse(
    val message: String,
    val data: LoginData
)

data class LoginData(
    val accessToken: String,
    val refreshToken: String
)
