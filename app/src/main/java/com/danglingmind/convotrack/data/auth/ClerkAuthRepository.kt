package com.danglingmind.convotrack.data.auth

import android.app.Application
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.clerk.api.Clerk
import com.clerk.api.credentials.resolvedCredentialFlowMessage
import com.clerk.api.credentials.shouldSuppressCredentialFlowError
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.network.serialization.shortErrorMessageOrNull
import com.clerk.api.session.GetTokenOptions
import com.clerk.api.signin.SignIn
import com.clerk.api.signin.verifyCode
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
 * Auth-strategy note: this Clerk instance has the password attribute DISABLED — email is a
 * first factor via `email_code` only (plus Google/Apple OAuth). Sending a `password` to
 * `sign_ups`/`sign_ins` is rejected outright ("password is not a valid parameter for this
 * request"), which is why the email flows here are one-time-code flows on both sides: sign-in
 * emails a code, sign-up emails a code, and [verifyEmailCode] finishes whichever is open. Adding a
 * password field back means enabling the password attribute in the Clerk dashboard first.
 *
 * Runtime note: the Clerk AAR already declares its own OAuth redirect activity
 * (SSOReceiverActivity, via the ${applicationId} manifest placeholder), so no manifest
 * changes are needed here. What IS required is allowlisting these redirect URLs under
 * Clerk dashboard → Native applications → "Allowlist for mobile SSO redirect":
 *     clerk://com.danglingmind.convotrack.callback
 *     clerk://com.danglingmind.convotrack.oauth
 * Until then, Google OAuth will fail at runtime even though it compiles.
 */
class ClerkAuthRepository(private val app: Application) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Which email flow the code now in the user's inbox belongs to.
     *
     * Both flows land on the same code screen, and the same six digits are attempted against
     * whichever is open. Reading Clerk's `currentSignUp`/`currentSignIn` to decide would guess:
     * both can be non-null on one client (a sign-in attempt that transferred to a sign-up, or a
     * stale attempt from earlier in the session), and picking the wrong one attempts the code
     * against a flow that never asked for it. The screen that started the flow is the only thing
     * that knows for certain, so it is recorded here.
     */
    private var pendingCodeFlow: PendingCodeFlow? = null

    private enum class PendingCodeFlow { SIGN_IN, SIGN_UP }

    override val userState: StateFlow<AuthUser?> =
        Clerk.userFlow
            .map { it?.toAuthUser() }
            .stateIn(scope, SharingStarted.Eagerly, Clerk.user?.toAuthUser())

    override val isReady: StateFlow<Boolean> = Clerk.isInitialized

    override suspend fun getToken(forceRefresh: Boolean): String? {
        // skipCache mints a fresh token from Clerk, sidestepping a locally-cached token that
        // a skewed device clock still believes is valid but the server has already expired.
        val options = GetTokenOptions().copy(skipCache = forceRefresh)
        return when (val r = Clerk.auth.getToken(options)) {
            is ClerkResult.Success -> r.value
            is ClerkResult.Failure -> null
        }
    }

    override suspend fun signInWithEmail(email: String): AuthResult {
        // Aliased to a local so the builder's own `email` member doesn't shadow the parameter.
        val emailAddr = email
        return when (val started = Clerk.auth.signInWithOtp { this.email = emailAddr }) {
            is ClerkResult.Success -> {
                pendingCodeFlow = PendingCodeFlow.SIGN_IN
                AuthResult.NeedsEmailCode
            }
            is ClerkResult.Failure -> AuthResult.Error(started.msg())
        }
    }

    override suspend fun signUpWithEmail(email: String): AuthResult {
        val emailAddr = email
        return when (val created = Clerk.auth.signUp { this.email = emailAddr }) {
            is ClerkResult.Success ->
                // Trigger the emailed verification code; the session isn't active yet.
                when (val prep = created.value.prepareVerification(
                    SignUp.PrepareVerificationParams.Strategy.EmailCode(),
                )) {
                    is ClerkResult.Success -> {
                        pendingCodeFlow = PendingCodeFlow.SIGN_UP
                        AuthResult.NeedsEmailCode
                    }
                    is ClerkResult.Failure -> AuthResult.Error(prep.msg())
                }
            is ClerkResult.Failure -> AuthResult.Error(created.msg())
        }
    }

    override suspend fun verifyEmailCode(code: String): AuthResult = when (pendingCodeFlow) {
        PendingCodeFlow.SIGN_UP -> {
            val signUp = Clerk.auth.currentSignUp
            if (signUp == null) {
                AuthResult.Error("No sign-up in progress.")
            } else {
                when (val r = signUp.attemptVerification(
                    SignUp.AttemptVerificationParams.EmailCode(code),
                )) {
                    is ClerkResult.Success ->
                        activate(r.value.status == SignUp.Status.COMPLETE, r.value.createdSessionId)
                    is ClerkResult.Failure -> AuthResult.Error(r.msg())
                }
            }
        }

        PendingCodeFlow.SIGN_IN -> {
            val signIn = Clerk.auth.currentSignIn
            if (signIn == null) {
                AuthResult.Error("No sign-in in progress.")
            } else {
                when (val r = signIn.verifyCode(code)) {
                    is ClerkResult.Success ->
                        activate(r.value.status == SignIn.Status.COMPLETE, r.value.createdSessionId)
                    is ClerkResult.Failure -> AuthResult.Error(r.msg())
                }
            }
        }

        null -> AuthResult.Error("No verification in progress.")
    }

    /**
     * Makes the session a completed flow just created the active one.
     *
     * A verified sign-in or sign-up hands back a session id, but nothing is signed in until that
     * session is the client's active one — [Clerk.userFlow], and with it every screen that watches
     * it, reads the ACTIVE session. Skipped when the session is already active, which is how the
     * OAuth flows arrive.
     */
    private suspend fun activate(complete: Boolean, sessionId: String?): AuthResult {
        if (!complete) return AuthResult.Error("Verification incomplete. Please try again.")
        pendingCodeFlow = null
        if (sessionId == null || Clerk.session?.id == sessionId) return AuthResult.Success
        return when (val r = Clerk.auth.setActive(sessionId)) {
            is ClerkResult.Success -> AuthResult.Success
            is ClerkResult.Failure -> AuthResult.Error(r.msg())
        }
    }

    /**
     * Google sign-in through the device's own account chooser, falling back to the browser.
     *
     * The browser redirect flow alone is what made it impossible to change accounts: a Custom Tab
     * shares Chrome's cookie jar, so Google still held a session for the rider who just signed out,
     * saw one signed-in identity, skipped the chooser and handed the same account straight back.
     * Nothing in the app could ask for the chooser — Clerk's API takes an
     * `oidc_prompt=select_account` parameter but the SDK does not expose it on this flow.
     *
     * Credential Manager sidesteps the cookie jar entirely: it lists every Google account ON THE
     * DEVICE, plus "Use another account", and the sheet is native rather than a web page. Despite
     * the name, [Clerk.auth.signUpWithGoogleOneTap] is the two-way entry point — it signs in when
     * the account already exists and transfers to a sign-up when it doesn't.
     *
     * The fallback matters because Credential Manager can fail for reasons that have nothing to do
     * with the rider: no Google account on the device, Play Services missing or stale, or a build
     * whose signing key isn't registered in the Google Cloud project. None of that stops the
     * browser flow, so we still offer it — but never after a deliberate dismissal, which would pop
     * a browser the rider didn't ask for the instant they closed the sheet.
     */
    override suspend fun signInWithGoogle(): AuthResult {
        val oneTap = Clerk.auth.signUpWithGoogleOneTap()
        if (oneTap is ClerkResult.Success) return AuthResult.Success

        val failure = oneTap as ClerkResult.Failure<ClerkErrorResponse>
        if (failure.shouldSuppressCredentialFlowError) return AuthResult.Cancelled
        // A null `error` means Clerk's servers never rejected anything — the credential flow itself
        // couldn't produce a token, which is exactly the class of failure the browser can recover.
        if (failure.error == null) return signInWithGoogleViaBrowser()
        return AuthResult.Error(failure.resolvedCredentialFlowMessage)
    }

    private suspend fun signInWithGoogleViaBrowser(): AuthResult =
        when (val r = Clerk.auth.signInWithOAuth(OAuthProvider.GOOGLE)) {
            is ClerkResult.Success -> AuthResult.Success
            is ClerkResult.Failure -> AuthResult.Error(r.msg())
        }

    override suspend fun signOut() {
        pendingCodeFlow = null
        Clerk.auth.signOut()
        // Credential Manager remembers the account it last issued a token for and, because the SDK
        // asks with auto-select enabled, will hand back the same one WITHOUT showing the sheet on a
        // device with a single Google account — the very "it picks the previous account" behaviour
        // this is meant to end. Clearing the state is what forces the next request to ask.
        // Wrapped because signing out must not fail on a device whose credential provider is
        // missing or misbehaving: the Clerk session is already gone by this point.
        runCatching {
            CredentialManager.create(app).clearCredentialState(ClearCredentialStateRequest())
        }.onFailure { Log.w("ClerkAuthRepository", "clearCredentialState failed", it) }
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
