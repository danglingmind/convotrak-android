package com.danglingmind.convotrack.data.net

import com.danglingmind.convotrack.data.auth.AuthRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException

/** Thrown for a non-2xx response. [message] carries the backend's `error` string when present. */
class ApiException(message: String) : Exception(message)

/** Thrown when no session token is available (the iOS `.notAuthenticated` case). */
class NotAuthenticatedException : Exception("Not signed in")

/**
 * Builds the Retrofit [ApiService]. Mirrors iOS APIClient: same base URL, Bearer auth,
 * camelCase JSON, and backend-`error`-aware failures. The Json config replicates the iOS
 * graceful decode (ignore unknown keys; missing values fall back to model defaults).
 */
object ApiClient {

    // Single source of truth for the backend — matches iOS AppURLs.backendBaseURL.
    const val BASE_URL = "https://convoy-backend-hx3c.onrender.com"

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun create(auth: AuthRepository): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(auth))
            .addInterceptor(logging)
            // The backend runs on Render's free tier, which cold-starts (~30-60s) after
            // idling. Generous timeouts keep the first request after a spin-down alive.
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .writeTimeout(java.time.Duration.ofSeconds(30))
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}

/**
 * Attaches `Authorization: Bearer <jwt>` to every request. OkHttp interceptors are
 * synchronous, so the suspend token fetch is bridged with runBlocking — acceptable on
 * OkHttp's background dispatcher thread.
 */
private class AuthInterceptor(private val auth: AuthRepository) : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val token = runBlocking { auth.getToken() } ?: throw NotAuthenticatedException()
        val authed: Request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }
}

/**
 * Wraps an [ApiService] call, converting Retrofit/HTTP failures into [ApiException] with
 * the backend `error` code when available — the Kotlin analogue of iOS APIClientError.
 */
suspend fun <T> apiCall(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string()
        val message = body
            ?.let { runCatching { ApiClient.json.decodeFromString(ApiErrorBody.serializer(), it).error }.getOrNull() }
            ?: "HTTP ${e.code()}"
        throw ApiException(message)
    } catch (e: NotAuthenticatedException) {
        throw e
    } catch (e: IOException) {
        throw ApiException(e.message ?: "Network error")
    }
}
