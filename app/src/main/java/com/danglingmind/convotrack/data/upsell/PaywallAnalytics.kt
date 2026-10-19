package com.danglingmind.convotrack.data.upsell

import com.danglingmind.convotrack.BuildConfig
import com.danglingmind.convotrack.data.net.PaywallEventPayload
import com.danglingmind.convotrack.data.net.PaywallEventsRequest
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.Access
import com.danglingmind.convotrack.ui.common.PaywallSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Upsell funnel events (`POST /events`), iOS `PaywallAnalytics` parity. Batched for a couple of
 * seconds; a failed batch is kept (in memory) and goes out with the next one.
 */
object PaywallAnalytics {
    enum class Event(val wire: String) {
        PaywallShown("paywall_shown"),
        PurchaseStarted("paywall_purchase_started"),
        Converted("paywall_converted"),
        PaywallDismissed("paywall_dismissed"),
        NudgeShown("nudge_shown"),
        NudgeDismissed("nudge_dismissed"),
        HomeCardDismissed("home_card_dismissed"),
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pending = mutableListOf<PaywallEventPayload>()
    private var flushJob: Job? = null

    fun log(event: Event, source: PaywallSource? = null, access: Access? = null) {
        pending += PaywallEventPayload(
            name = event.wire,
            // Wire names match iOS (`homeCard`, `createBlocked`, …).
            source = source?.name?.replaceFirstChar { it.lowercase() },
            access = access?.let(::accessName),
            occurredAt = Instant.now().toString(),
        )
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(2_000)
            flush()
        }
    }

    private suspend fun flush() {
        while (pending.isNotEmpty()) {
            val batch = pending.take(50)
            pending.subList(0, batch.size).clear()
            val sent = runCatching {
                apiCall {
                    ServiceLocator.api.sendEvents(
                        PaywallEventsRequest(platform = "android", appVersion = BuildConfig.VERSION_NAME, events = batch),
                    )
                }
            }.isSuccess
            if (!sent) {
                // Keep it for the next flush (iOS parity), capped so an offline rider can't grow it.
                pending.addAll(0, batch)
                while (pending.size > 200) pending.removeAt(pending.lastIndex)
                return
            }
        }
    }

    private fun accessName(access: Access): String = when (access) {
        Access.Pro -> "pro"
        is Access.Trial -> "trial"
        Access.TrialExpired -> "trialExpired"
        Access.Free -> "free"
    }
}
