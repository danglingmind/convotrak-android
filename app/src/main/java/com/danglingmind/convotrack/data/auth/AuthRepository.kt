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

    /**
     * The rider backed out of a system sheet — the Google account chooser, most often.
     *
     * Distinct from [Error] because dismissing a sheet is not a failure: showing "sign-in failed"
     * for it puts a red message under a screen the rider closed on purpose.
     */
    data object Cancelled : AuthResult

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

    /**
     * Current session JWT for the Authorization header, or null when signed out.
     * Pass [forceRefresh] to bypass Clerk's cache and mint a brand-new token — used to
     * recover from a rejected (stale/expired) token, e.g. under device clock skew.
     */
    suspend fun getToken(forceRefresh: Boolean = false): String?

    /**
     * Starts email sign-in. The instance authenticates email with a one-time code rather than a
     * password, so this always emails a code and returns [AuthResult.NeedsEmailCode] — there is no
     * path where a bare identifier signs anyone in.
     */
    suspend fun signInWithEmail(email: String): AuthResult

    /** Starts email sign-up; on success returns [AuthResult.NeedsEmailCode]. */
    suspend fun signUpWithEmail(email: String): AuthResult

    /**
     * Completes whichever email flow is in progress — [signInWithEmail] or [signUpWithEmail] —
     * using the emailed code, and activates the session it creates.
     */
    suspend fun verifyEmailCode(code: String): AuthResult

    /**
     * Signs in with Google: the device's own account chooser first, the browser redirect flow as
     * a fallback.
     */
    suspend fun signInWithGoogle(): AuthResult

    suspend fun signOut()
}
