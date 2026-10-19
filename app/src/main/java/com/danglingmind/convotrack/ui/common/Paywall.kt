package com.danglingmind.convotrack.ui.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Where a paywall was opened from (iOS `PaywallSource`). Picks the paywall's headline. */
enum class PaywallSource {
    AppOpen, HomeCard, HomeBadge, CreateBlocked, JoinBlocked, RiderCap, QuotaExceeded, RideSummary, Profile;

    companion object {
        fun fromRoute(value: String?): PaywallSource = entries.firstOrNull { it.name == value } ?: Profile
    }
}

/**
 * The app's single paywall entry point (iOS `PaywallPresenter`). Any screen calls [present];
 * `AppRoot` collects [requests] and navigates to the membership route, so no screen has to thread
 * an `onOpenMembership` callback down just to sell Pro.
 */
object Paywall {
    private val _requests = MutableSharedFlow<PaywallSource>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_LATEST,
    )
    val requests: SharedFlow<PaywallSource> = _requests

    fun present(source: PaywallSource) {
        _requests.tryEmit(source)
    }
}
