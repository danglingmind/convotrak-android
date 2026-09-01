package com.danglingmind.convotrack.data.net

import com.danglingmind.convotrack.BuildConfig
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
    // Custom subdomain on Render, part of the unified convotrack.in domain.
    const val BASE_URL = "https://api.convotrack.in"

    // Host used for the ride-invite HTTPS App Link. Must match the manifest's
    // https intent-filter host and the assetlinks.json served by the website
    // (convotrack-website on Vercel) at
    // https://convotrack.in/.well-known/assetlinks.json. Shared invite links use
    // this instead of the convotrack:// scheme so they're tappable in messaging
    // apps and fall back to a web page when the app isn't installed.
    const val JOIN_LINK_HOST = "convotrack.in"

    /** Canonical shareable invite link, e.g. https://convotrack.in/join/ABC123 */
    fun joinLink(code: String): String = "https://$JOIN_LINK_HOST/join/$code"

    /**
     * Legal pages, served by the marketing site off the same host as the invite links.
     *
     * They live here rather than in the screens that open them because the host is already stated
     * once above, and because Play treats the privacy policy as a requirement rather than a
     * footer link: it has to be reachable from inside the app, not only from the store listing.
     */
    const val PRIVACY_URL = "https://$JOIN_LINK_HOST/privacy"
    const val TERMS_URL = "https://$JOIN_LINK_HOST/terms"

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun create(auth: AuthRepository): ApiService {
        // BASIC prints no headers, so the bearer token was never at risk — but it does print the
        // URL and timing of every call, ride IDs included, and on a release build that is
        // production traffic narrated into logcat for any app with READ_LOGS to read back. The
        // interceptor is therefore not merely quietened in release, it is never installed.
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(auth))
            .apply { if (BuildConfig.DEBUG) addInterceptor(logging) }
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
 *
 * If the server rejects the token (401/403 — e.g. a stale token under device clock skew),
 * force a fresh token and retry the request once. This keeps the app resilient to token
 * expiry races on any device.
 */
private class AuthInterceptor(private val auth: AuthRepository) : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val token = runBlocking { auth.getToken() } ?: throw NotAuthenticatedException()
        val response = chain.proceed(chain.request().withBearer(token))

        if (response.code == 401 || response.code == 403) {
            val fresh = runBlocking { auth.getToken(forceRefresh = true) }
            if (fresh != null && fresh != token) {
                response.close()
                return chain.proceed(chain.request().withBearer(fresh))
            }
        }
        return response
    }

    private fun Request.withBearer(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()
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

/**
 * Ride statuses to ask the server for. Mirrors iOS `APIClient.RideStatusFilter`.
 *
 * `/rides/me` caps at 50 rows, so filtering server-side is what keeps a busy rider's in-progress
 * rides from being pushed out of the response by their finished ones.
 */
object RideStatusFilter {
    /** Finished rides, for the history list. */
    val COMPLETED = listOf("COMPLETED")

    /** Rides still joinable or under way, for the home screen's resume list. */
    val IN_PROGRESS = listOf("LOBBY", "ACTIVE", "PAUSED")

    fun query(statuses: List<String>): String? =
        statuses.takeIf { it.isNotEmpty() }?.joinToString(",")
}

/** True for the two shapes a missing ride arrives in: the backend's `RIDE_NOT_FOUND` error body,
 *  and the bare `HTTP 404` fallback [apiCall] synthesises when there is no body. */
fun isNotFoundError(message: String?): Boolean =
    message == "RIDE_NOT_FOUND" || message == "HTTP 404"

/**
 * [ApiService.getRide] with short backoff retries, returning null instead of throwing.
 *
 * For the screens that fetch a ride exactly once on appear (the lobby, the navigation screen) and
 * have no path back to a populated state if that one call fails: a blip, a token refresh landing
 * mid-request or a cancelled coroutine left them permanently inert — no hero photo, no route, no
 * leader controls — with no error and nothing to retry it. A genuine 404 is not retried, since no
 * amount of waiting will conjure the ride.
 */
suspend fun ApiService.getRideRetrying(rideId: String, attempts: Int = 3): Ride? {
    repeat(attempts) { attempt ->
        try {
            return apiCall { getRide(rideId) }
        } catch (e: ApiException) {
            if (isNotFoundError(e.message)) return null
            if (attempt == attempts - 1) return null
            kotlinx.coroutines.delay(400L shl attempt)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (attempt == attempts - 1) return null
            kotlinx.coroutines.delay(400L shl attempt)
        }
    }
    return null
}
