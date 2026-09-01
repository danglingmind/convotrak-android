package com.danglingmind.convotrack.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.danglingmind.convotrack.data.net.ActivateMembershipRequest
import com.danglingmind.convotrack.data.net.ApiService
import com.danglingmind.convotrack.data.net.NotAuthenticatedException
import com.danglingmind.convotrack.data.net.UserFacingError
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Google Play Billing client for the ConvoTrack Pro subscription — the Android analogue of
 * iOS MembershipStore (StoreKit). Play is the local source of truth for entitlement, mirroring
 * iOS where "local StoreKit is authoritative; the server can only upgrade, not downgrade".
 *
 * PLACEHOLDER product IDs: create matching subscription products in Play Console.
 *
 * Server sync: a purchase is only half-real until the backend knows about it. Plan limits are
 * enforced server-side (`QUOTA_EXCEEDED`, and the rider cap on ride creation), so a rider whose
 * purchase never reached the server sees an unlocked paywall and is still refused at ride four.
 * [activateOnServer] posts the Play purchase token to `POST /memberships/activate`, which
 * exchanges it with the Play Developer API — the Play-side analogue of iOS posting a StoreKit JWS.
 *
 * Retry model differs from iOS deliberately. iOS withholds `finish()` until the server confirms,
 * because an unfinished transaction is what `Transaction.updates` replays. Play has no equivalent
 * lever worth pulling: `queryPurchasesAsync` returns every active purchase whether or not it has
 * been acknowledged, so [queryEntitlement] can re-post on each launch for free — while withholding
 * acknowledgement would hand Play grounds to auto-refund the subscription after three days.
 */
class BillingManager(
    context: Context,
    private val api: ApiService,
) {

    companion object {
        const val MONTHLY_ID = "convotrack_pro_monthly" // TODO: create in Play Console
        const val YEARLY_ID = "convotrack_pro_yearly"   // TODO: create in Play Console
        private val PRODUCT_IDS = listOf(MONTHLY_ID, YEARLY_ID)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    /** Subscription details keyed by product id (for prices + purchase). */
    val products: StateFlow<Map<String, ProductDetails>> = _products

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    /**
     * Purchase tokens this process has already had confirmed, so that a fresh purchase — which
     * reaches [handlePurchase] and then [queryEntitlement] — is not posted twice in a row.
     * Deliberately not persisted: forgetting it across launches is what makes the retry work.
     * Only ever touched from [scope], which is single-threaded on Main.
     */
    private val serverConfirmed = mutableSetOf<String>()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { scope.launch { handlePurchase(it) } }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _lastError.value = null
        } else {
            // Reassurance about not being charged goes first — that is the user's actual worry —
            // and this is only reached on a real failure, never on a cancelled purchase.
            _lastError.value = UserFacingError.PURCHASE_FAILED
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Connects (idempotent-ish) and loads products + current entitlement. */
    fun start() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }

            override fun onBillingServiceDisconnected() { /* Play will call start() again on demand. */ }
        })
    }

    private fun refresh() {
        scope.launch { queryProducts() }
        scope.launch { queryEntitlement() }
    }

    private suspend fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PRODUCT_IDS.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()
        val result = client.queryProductDetails(params)
        val list = result.productDetailsList.orEmpty()
        _products.value = list.associateBy { it.productId }
    }

    /** What a pass over Play's purchase list found. Callers that run it in the background ignore
     *  this; [restorePurchases] is the one that has to tell a waiting rider what happened. */
    enum class EntitlementResult { ACTIVE, NONE, UNAVAILABLE }

    /**
     * Local entitlement: any active, acknowledged subscription = premium.
     *
     * Doubles as the retry for server activation. Every active purchase is re-posted on each
     * launch, so a purchase made while the backend was unreachable repairs itself the next time
     * the app opens, with no persisted queue to keep correct.
     */
    suspend fun queryEntitlement(): EntitlementResult {
        if (!client.isReady) return EntitlementResult.UNAVAILABLE
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        // An empty list means "no subscription" only if Play actually answered. A disconnected
        // client returns the same empty list, and treating that as an answer would silently
        // revoke premium from a paying rider whose Play connection dropped for a moment.
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return EntitlementResult.UNAVAILABLE
        }
        var premium = false
        result.purchasesList.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                premium = true
                if (!purchase.isAcknowledged) acknowledge(purchase)
                activateOnServer(purchase, surfaceErrors = false)
            }
        }
        _isPremium.value = premium
        return if (premium) EntitlementResult.ACTIVE else EntitlementResult.NONE
    }

    /**
     * Rider-initiated restore, behind the paywall's "Restore purchases".
     *
     * [queryEntitlement] already runs on every launch, so this recovers nothing that time would
     * not — which is exactly why it has to report an outcome. A rider who reinstalled and taps
     * this needs an answer either way; a button that runs the same silent query the launch already
     * ran is indistinguishable from one that is not wired up at all.
     */
    suspend fun restorePurchases(): EntitlementResult {
        _lastError.value = null
        // A cold client is the common case here — the rider may have opened the paywall before
        // Play finished connecting. Kick the connection so the retry a moment later can work.
        if (!client.isReady) {
            start()
            return EntitlementResult.UNAVAILABLE
        }
        return runCatching { queryEntitlement() }.getOrDefault(EntitlementResult.UNAVAILABLE)
    }

    /** Launches the Play purchase sheet for the given product's base-plan offer. */
    fun purchase(activity: Activity, productId: String) {
        val details = _products.value[productId] ?: run {
            _lastError.value = UserFacingError.PLAN_UNAVAILABLE
            return
        }
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
            _lastError.value = UserFacingError.PLAN_UNAVAILABLE
            return
        }
        _lastError.value = null
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.isAcknowledged) acknowledge(purchase)
        // Play has taken the money, so the paywall unlocks now rather than waiting on a round
        // trip. The server call below is what makes the server-side plan limits agree.
        _isPremium.value = true
        activateOnServer(purchase, surfaceErrors = true)
    }

    /**
     * Tell the backend about a verified Play purchase.
     *
     * [surfaceErrors] is on only for the interactive path. A rider who has just paid needs to hear
     * that the store rejected the purchase — silently leaving them locally premium would strand
     * them on a plan the server will not honour. A background retry has no such audience, and
     * would only be reporting a transient network failure to someone who is already premium.
     */
    private suspend fun activateOnServer(purchase: Purchase, surfaceErrors: Boolean) {
        if (purchase.purchaseToken in serverConfirmed) return
        try {
            apiCall {
                api.activateMembership(
                    ActivateMembershipRequest(
                        provider = "google",
                        purchaseToken = purchase.purchaseToken,
                    ),
                )
            }
            serverConfirmed += purchase.purchaseToken
        } catch (e: NotAuthenticatedException) {
            // Signed out mid-purchase. Nothing to say and nothing to fix here — the next launch
            // re-posts once there is a token to send.
        } catch (e: Exception) {
            if (surfaceErrors) _lastError.value = e.riderMessage
        }
    }

    private suspend fun acknowledge(purchase: Purchase) {
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        )
    }

    /** Formatted base-plan price for a product, e.g. "$4.99", or null if not loaded. */
    fun formattedPrice(productId: String): String? =
        PlanPeriod.recurringPhase(_products.value[productId] ?: return null)?.formattedPrice

    /**
     * The loaded plans, ordered LONGEST BILLING CYCLE FIRST, with price only as a tiebreak.
     *
     * Ordering by price would put the cheapest cycle at the top, which inverts the intent of a
     * paywall and is wrong the moment a plan is discounted. Ordering by period is stable against
     * price changes and against a new plan being added in the console.
     */
    fun displayPlans(): List<DisplayPlan> =
        _products.value.values.mapNotNull { details ->
            val phase = PlanPeriod.recurringPhase(details) ?: return@mapNotNull null
            DisplayPlan(
                productId = details.productId,
                details = details,
                period = PlanPeriod.of(details),
                formattedPrice = phase.formattedPrice,
                priceMicros = phase.priceAmountMicros,
                currencyCode = phase.priceCurrencyCode,
            )
        }.sortedWith(
            compareByDescending<DisplayPlan> { it.period?.months ?: 0.0 }
                .thenByDescending { it.priceMicros },
        )

    /** Shortest cycle on offer — the yardstick every other plan's saving is measured against. */
    fun baselinePlan(): DisplayPlan? = displayPlans().minByOrNull { it.period?.months ?: Double.MAX_VALUE }

    /** Plan with the largest per-month saving versus the baseline; it carries the SAVE badge. */
    fun bestValuePlan(): DisplayPlan? {
        val baseline = baselinePlan() ?: return null
        return displayPlans()
            .filter { it.productId != baseline.productId }
            .mapNotNull { plan -> savingsPercent(plan.details, baseline.details)?.let { plan to it } }
            .maxByOrNull { it.second }
            ?.first
    }

    /** Whole-percent saving of [plan] against the shortest cycle, or null when there isn't one. */
    fun savingsFor(plan: DisplayPlan): Int? {
        val baseline = baselinePlan() ?: return null
        if (plan.productId == baseline.productId) return null
        return savingsPercent(plan.details, baseline.details)
    }
}
