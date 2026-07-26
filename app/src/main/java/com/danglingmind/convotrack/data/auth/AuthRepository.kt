package com.danglingmind.convotrack.data.auth

import kotlinx.coroutines.flow.StateFlow

/** Minimal user identity the app cares about, decoupled from the Clerk SDK types. */
data class AuthUser(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

/** Outcome of an auth attempt. */
sealed interface AuthResult {
    /** Session is active — the user is signed in. */
    data object Success : AuthResult

    /** Sign-up created but an emailed code must be verified before the session activates. */
    data object NeedsEmailCode : AuthResult

    /** Something went wrong; [message] is user-presentable. */
    data class Error(val message: String) : AuthResult
}

/**
 * The app's auth port. The UI and networking layer depend on this interface only —
 * the Clerk SDK is confined to [ClerkAuthRepository]. This keeps the rest of the app
 * testable and lets us swap/stub auth without touching screens.
 */
interface AuthRepository {

    /** Emits the current user, or null when signed out. Backed by Clerk's session state. */
    val userState: StateFlow<AuthUser?>

    /** True once Clerk has finished bootstrapping its session on launch. */
    val isReady: StateFlow<Boolean>

    /** Current session JWT for the Authorization header, or null when signed out. */
    suspend fun getToken(): String?

    suspend fun signInWithEmail(email: String, password: String): AuthResult

    /** Starts email sign-up; on success returns [AuthResult.NeedsEmailCode]. */
    suspend fun signUpWithEmail(email: String, password: String): AuthResult

    /** Completes the sign-up started by [signUpWithEmail] using the emailed code. */
    suspend fun verifyEmailCode(code: String): AuthResult

    /** Launches the Google OAuth redirect flow. */
    suspend fun signInWithGoogle(): AuthResult

    suspend fun signOut()
}
