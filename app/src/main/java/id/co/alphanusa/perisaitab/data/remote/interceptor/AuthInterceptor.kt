package id.co.alphanusa.perisaitab.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Menambahkan header Authorization: Bearer <token> ke setiap request.
 * Token diambil lewat [tokenProvider] agar selalu mengikuti token terbaru.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val accessToken = tokenProvider()

        val newRequest = if (accessToken != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(newRequest)
    }
}
