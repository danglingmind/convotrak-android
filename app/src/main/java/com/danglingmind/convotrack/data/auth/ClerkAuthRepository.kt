package com.danglingmind.convotrack.data.auth

import com.clerk.api.Clerk
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.network.serialization.shortErrorMessageOrNull
import com.clerk.api.signup.SignUp
import com.clerk.api.signup.attemptVerification
import com.clerk.api.signup.prepareVerification
import com.clerk.api.sso.OAuthProvider
import com.clerk.api.user.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [AuthRepository] backed by the Clerk Android SDK (com.clerk:clerk-android-api).
 *
 * Clerk is a process-wide singleton initialised in [com.danglingmind.convotrack.ConvoTrackApp];
 * this class is the only place that touches Clerk types, so the rest of the app stays
 * decoupled from the SDK.
 *
 * Runtime note: Google OAuth uses Clerk's redirect flow and requires the redirect URL to be
 * allowlisted in the Clerk dashboard for the Android app — a dashboard/config step, not code.
 */
class ClerkAuthRepository : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val userState: StateFlow<AuthUser?> =
        Clerk.userFlow
            .map { it?.toAuthUser() }
            .stateIn(scope, SharingStarted.Eagerly, Clerk.user?.toAuthUser())

    override val isReady: StateFlow<Boolean> = Clerk.isInitialized

    override suspend fun getToken(): String? =
        when (val r = Clerk.auth.getToken()) {
            is ClerkResult.Success -> r.value
            is ClerkResult.Failure -> null
        }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        // Aliased to locals so the builder's own `identifier`/`password` members don't shadow them.
        val emailAddr = email
        val pwd = password
        return Clerk.auth.signInWithPassword {
            identifier = emailAddr
            this.password = pwd
        }.toAuthResult()
    }

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult {
        val emailAddr = email
        val pwd = password
        return when (val created = Clerk.auth.signUp {
            this.email = emailAddr
            this.password = pwd
        }) {
            is ClerkResult.Success ->
                // Trigger the emailed verification code; the session isn't active yet.
                when (val prep = created.value.prepareVerification(
                    SignUp.PrepareVerificationParams.Strategy.EmailCode(),
                )) {
                    is ClerkResult.Success -> AuthResult.NeedsEmailCode
                    is ClerkResult.Failure -> AuthResult.Error(prep.msg())
                }
            is ClerkResult.Failure -> AuthResult.Error(created.msg())
        }
    }

    override suspend fun verifyEmailCode(code: String): AuthResult {
        val signUp = Clerk.auth.currentSignUp ?: return AuthResult.Error("No sign-up in progress.")
        return when (val r = signUp.attemptVerification(
            SignUp.AttemptVerificationParams.EmailCode(code),
        )) {
            is ClerkResult.Success -> AuthResult.Success
            is ClerkResult.Failure -> AuthResult.Error(r.msg())
        }
    }

    override suspend fun signInWithGoogle(): AuthResult =
        when (val r = Clerk.auth.signInWithOAuth(OAuthProvider.GOOGLE)) {
            is ClerkResult.Success -> AuthResult.Success
            is ClerkResult.Failure -> AuthResult.Error(r.msg())
        }

    override suspend fun signOut() {
        Clerk.auth.signOut()
    }

    // MARK: - Helpers

    private fun ClerkResult<*, ClerkErrorResponse>.toAuthResult(): AuthResult = when (this) {
        is ClerkResult.Success -> AuthResult.Success
        is ClerkResult.Failure -> AuthResult.Error(msg())
    }

    private fun ClerkResult.Failure<ClerkErrorResponse>.msg(): String =
        shortErrorMessageOrNull() ?: "Something went wrong. Please try again."

    private fun User.toAuthUser(): AuthUser {
        val fullName = listOfNotNull(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { username ?: "Rider" }
        return AuthUser(id = id, name = fullName, avatarUrl = imageUrl.ifBlank { null })
    }
}
