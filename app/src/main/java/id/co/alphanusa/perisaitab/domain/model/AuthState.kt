package id.co.alphanusa.perisaitab.domain.model

// Auth State
data class AuthState(
    val isLoggedIn: Boolean = false,
    val accessToken: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

// API Error Response
data class ApiErrorResponse(
    val message: String?,
    val error: String?
)
