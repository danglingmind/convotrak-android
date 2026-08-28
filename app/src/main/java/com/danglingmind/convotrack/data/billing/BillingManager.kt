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
import com.danglingmind.convotrack.data.net.UserFacingError
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
 * Verification note: iOS activates on the server via a StoreKit JWS (POST /memberships/activate
 * with `signedTransaction`). Play uses a purchase token verified server-side against the Play
 * Developer API — a DIFFERENT contract the backend does not expose yet. Until that endpoint
 * exists, entitlement is granted locally from Play's own acknowledged purchase (see the TODO in
 * [handlePurchase]).
 */
class BillingManager(context: Context) {

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

    /** Local entitlement: any active, acknowledged subscription = premium. */
    suspend fun queryEntitlement() {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        var premium = false
        result.purchasesList.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                premium = true
                if (!purchase.isAcknowledged) acknowledge(purchase)
            }
        }
        _isPremium.value = premium
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
        // TODO [backend]: when the server exposes a Google Play verification endpoint, POST
        //  purchase.purchaseToken + productId there (analogue of iOS activateMembership(JWS)).
        //  Until then, Play's acknowledged purchase is the source of truth.
        _isPremium.value = true
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
