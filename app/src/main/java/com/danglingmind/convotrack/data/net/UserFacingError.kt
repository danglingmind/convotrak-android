package com.danglingmind.convotrack.data.net

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns anything thrown in the app into something a rider can actually read.
 * Port of iOS `Networking/UserFacingError.swift`.
 *
 * The backend answers failures with machine codes — `QUOTA_EXCEEDED`, `NOT_LEADER`,
 * `RIDE_NOT_IN_LOBBY` — and [ApiException] carries them straight into `message`, so alerts showed
 * the rider a constant from a server file. Every alert in the app now reads [riderMessage]
 * instead, which means a new backend code can only ever degrade to the generic fallback below,
 * never leak as raw text.
 *
 * House style for these strings:
 * - **Say what happened first**, joke second. The rider is usually on a bike.
 * - **Say what to do** when there is something to do ("upgrade", "sign in again", "try again").
 * - **No jokes about safety.** Emergency and location failures are written straight; a rider
 *   reading one of those is having a bad day already.
 */
object UserFacingError {

    /**
     * Message for a backend error code. Returns null for codes with no rider-facing meaning
     * (API misuse, validation the client should have prevented) so they fall through to the
     * generic message rather than getting a jokey one they don't deserve.
     */
    fun messageForCode(code: String): String? = when (code) {

        // Plan limits
        "QUOTA_EXCEEDED" ->
            "You've hit your plan's ride limit. Your enthusiasm is outriding your subscription — upgrade to keep the convoys coming."

        // Permission
        "NOT_LEADER", "NOT_AUTHORIZED" ->
            "Only the ride leader can do that. Nice try, though."
        "UNAUTHORIZED", "INVALID_TOKEN" ->
            "Your session wandered off. Sign in again and we'll pick up where you left off."
        "NOT_IN_RIDE", "NOT_A_PARTICIPANT" ->
            "You're not in this ride, so there's nothing here to change."
        "CANNOT_REMOVE_LEADER" ->
            "You can't remove the ride leader from their own ride. Bold of you to try."
        "LEADER_CANNOT_LEAVE" ->
            "You're leading this one — walking out would strand everyone. Cancel the ride instead."

        // Joining
        "RIDE_FULL" ->
            "That convoy is packed mirror to mirror. No room for one more."
        "ALREADY_JOINED" ->
            "You're already in this one. Twice as committed, but once is enough."
        "INVITE_CODE_NOT_FOUND" ->
            "No convoy answers to that code. Check the letters and try again — O and 0 are the usual culprits."

        // Ride state
        "RIDE_NOT_FOUND" ->
            "That ride has vanished. Either it was deleted, or it was never here."
        "RIDE_ENDED", "RIDE_NOT_COMPLETED" ->
            "That ride is already in the history books."
        "RIDE_NOT_ACTIVE", "RIDE_NOT_ACTIVE_OR_PAUSED" ->
            "That ride isn't rolling right now, so there's nothing to update."
        "RIDE_NOT_LOBBY", "RIDE_NOT_IN_LOBBY" ->
            "This ride has already set off — too late to change the plan."
        "RIDE_NOT_PAUSED" ->
            "This ride isn't paused, so there's nothing to resume."

        // Ride setup
        "MISSING_REQUIRED_FIELDS" ->
            "Some details are still missing. Fill in the blanks and try again."
        "INVALID_WAYPOINTS" ->
            "This route needs somewhere to end up. Add a destination and try again."

        // Profile
        "USERNAME_TAKEN" ->
            "Another rider got there first. Pick a different name."
        "USER_NOT_FOUND" ->
            "We couldn't find that rider."

        // Purchases
        "INVALID_PRODUCT_ID", "INVALID_TRANSACTION", "INVALID_TRANSACTION_PAYLOAD" ->
            "The store sent something we couldn't make sense of. Nothing was charged — try again."
        "TRANSACTION_EXPIRED" ->
            "That purchase expired before we could confirm it. Nothing was charged."
        "SANDBOX_TRANSACTION_IN_PRODUCTION" ->
            "That was a test purchase, and this isn't a test. Nothing was charged."
        "PURCHASE_ALREADY_CLAIMED" ->
            "That subscription is already tied to another account. Sign in with the one that bought it, or contact us and we'll sort it out."
        "PLAY_VERIFICATION_UNAVAILABLE" ->
            "We couldn't reach the store to confirm your purchase. Nothing is lost — try again in a moment."
        "PURCHASE_NOT_FOUND", "PURCHASE_NOT_ACTIVE" ->
            "The store doesn't show an active subscription on this account. If you've just bought one, give it a minute and try again."

        // Written straight — no jokes on safety
        "EMERGENCY_NOT_FOUND" ->
            "That emergency has already been cleared."
        "USE_EMERGENCY_EVENT" ->
            "Emergencies are raised from the SOS control, not here."
        "REGROUP_NOT_FOUND" ->
            "That regroup has already finished."
        "PARTICIPANT_NOT_FOUND" ->
            "That rider isn't in the convoy any more."

        // Server's fault
        "INTERNAL_SERVER_ERROR" ->
            "Our server dropped its chain. Give it a moment and try again."

        else -> null
    }

    /** Shown when nothing more specific is known — an unmapped code, or a failure that never
     *  reached the server at all. */
    const val GENERIC = "Something went sideways on our end. Give it another go."
    const val OFFLINE = "No signal out here. Find some bars and try again."
    const val TIMED_OUT = "The server's taking the scenic route. Try again in a moment."
    const val NOT_SIGNED_IN = "You're not signed in — do that first and we'll get you rolling."
    const val UNREADABLE_RESPONSE = "The server answered in a language we don't speak. Try again."

    /** Store failures. Reassurance about not being charged goes first — that's the rider's actual
     *  worry — and only reached on a real failure, never on a cancelled purchase. */
    const val PURCHASE_FAILED = "The store wasn't having it. Nothing was charged — try again."
    const val RESTORE_FAILED = "Couldn't dig up your previous purchases. Try again in a moment."

    /** The plan itself couldn't be loaded — distinct from a purchase that failed, and from a
     *  restore, because the user tapped neither. */
    const val PLAN_UNAVAILABLE = "That plan isn't available right now. Try again shortly."

    /** Every code we have wording for, used for substring matching when a code arrives wrapped in
     *  other text. Kept next to [messageForCode] so the two can't drift. */
    val KNOWN_CODES = listOf(
        "QUOTA_EXCEEDED", "NOT_LEADER", "NOT_AUTHORIZED", "UNAUTHORIZED", "INVALID_TOKEN",
        "NOT_IN_RIDE", "NOT_A_PARTICIPANT", "CANNOT_REMOVE_LEADER", "LEADER_CANNOT_LEAVE",
        "RIDE_FULL", "ALREADY_JOINED", "INVITE_CODE_NOT_FOUND", "RIDE_NOT_FOUND", "RIDE_ENDED",
        "RIDE_NOT_COMPLETED", "RIDE_NOT_ACTIVE_OR_PAUSED", "RIDE_NOT_ACTIVE", "RIDE_NOT_IN_LOBBY",
        "RIDE_NOT_LOBBY", "RIDE_NOT_PAUSED", "MISSING_REQUIRED_FIELDS", "INVALID_WAYPOINTS",
        "USERNAME_TAKEN", "USER_NOT_FOUND", "INVALID_PRODUCT_ID", "INVALID_TRANSACTION_PAYLOAD",
        "INVALID_TRANSACTION", "TRANSACTION_EXPIRED", "SANDBOX_TRANSACTION_IN_PRODUCTION",
        "PURCHASE_ALREADY_CLAIMED", "PLAY_VERIFICATION_UNAVAILABLE", "PURCHASE_NOT_FOUND",
        "PURCHASE_NOT_ACTIVE",
        "EMERGENCY_NOT_FOUND", "USE_EMERGENCY_EVENT", "REGROUP_NOT_FOUND", "PARTICIPANT_NOT_FOUND",
        "INTERNAL_SERVER_ERROR",
    )
}

/**
 * The string to put in front of a rider. Never a raw error code.
 *
 * Connectivity gets its own wording — "find some bars" is actionable in a way the generic message
 * isn't — so IO failures are separated from server-side ones.
 */
val Throwable.riderMessage: String
    get() = when (this) {
        is NotAuthenticatedException -> UserFacingError.NOT_SIGNED_IN
        is ApiException -> {
            val code = message.orEmpty()
            UserFacingError.messageForCode(code)
                // Codes arrive bare, but tolerate a wrapped/prefixed form too.
                ?: UserFacingError.KNOWN_CODES.firstOrNull { code.contains(it) }
                    ?.let { UserFacingError.messageForCode(it) }
                ?: if (looksLikeNetworkFailure(code)) UserFacingError.OFFLINE else UserFacingError.GENERIC
        }
        is SocketTimeoutException -> UserFacingError.TIMED_OUT
        is UnknownHostException -> UserFacingError.OFFLINE
        is IOException -> UserFacingError.OFFLINE
        else -> UserFacingError.GENERIC
    }

/**
 * `apiCall` collapses IOExceptions into `ApiException(e.message)`, so by the time a connectivity
 * failure reaches a screen it no longer carries its original type. These are the shapes OkHttp
 * produces for one.
 */
private fun looksLikeNetworkFailure(message: String): Boolean {
    val m = message.lowercase()
    return m.contains("unable to resolve host") || m.contains("failed to connect") ||
        m.contains("network") || m.contains("timeout") || m.contains("timed out") ||
        m.contains("unexpected end of stream") || m.contains("connection")
}
