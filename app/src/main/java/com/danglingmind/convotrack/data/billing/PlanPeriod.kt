package com.danglingmind.convotrack.data.billing

import com.android.billingclient.api.ProductDetails
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Billing period derived from Play's own subscription descriptor. Android port of iOS
 * `SubscriptionPeriod+Display.swift`.
 *
 * Replaces `productId.contains("yearly")` string matching and a hardcoded `/ 12` divisor. Deriving
 * from `billingPeriod` means a weekly or six-month plan added in the Play Console renders correctly
 * with no code change — and, more importantly, means the period shown is one we can actually prove.
 * Stating a billing period you cannot prove is a store-listing misrepresentation, not a cosmetic
 * slip.
 *
 * [parse] returns null for anything that isn't a recurring subscription phase; callers must degrade
 * to a bare price with no period suffix rather than guessing.
 */
data class PlanPeriod(val unit: Unit, val value: Int) {

    enum class Unit { DAY, WEEK, MONTH, YEAR }

    companion object {
        /** Mean Gregorian month: 365.25 / 12. Used so week/day plans normalise sensibly. */
        private const val DAYS_PER_MONTH = 30.4375

        /**
         * Parses an ISO-8601 duration as Play reports it — "P1M", "P1Y", "P4W", "P7D".
         *
         * Only the subset Play actually emits for `billingPeriod` is handled; anything else returns
         * null, which callers treat the same as "not a subscription".
         */
        fun parse(iso: String?): PlanPeriod? {
            if (iso.isNullOrBlank() || !iso.startsWith("P")) return null
            val body = iso.drop(1)
            val value = body.dropLast(1).toIntOrNull() ?: return null
            if (value <= 0) return null
            return when (body.last()) {
                'D' -> PlanPeriod(Unit.DAY, value)
                'W' -> PlanPeriod(Unit.WEEK, value)
                'M' -> PlanPeriod(Unit.MONTH, value)
                'Y' -> PlanPeriod(Unit.YEAR, value)
                else -> null
            }
        }

        /** The recurring phase of a product's first offer — the one whose price actually repeats.
         *  A free trial or intro phase sits ahead of it and must not be mistaken for the plan. */
        fun recurringPhase(details: ProductDetails): ProductDetails.PricingPhase? =
            details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                // recurrenceMode 1 == INFINITE_RECURRING; a trial/intro phase is FINITE or NON.
                ?.lastOrNull { it.priceAmountMicros > 0 }

        fun of(details: ProductDetails): PlanPeriod? = parse(recurringPhase(details)?.billingPeriod)
    }

    /** Length of one billing cycle expressed in months. */
    val months: Double
        get() = when (unit) {
            Unit.YEAR -> 12.0 * value
            Unit.MONTH -> value.toDouble()
            Unit.WEEK -> value * 7 / DAYS_PER_MONTH
            Unit.DAY -> value / DAYS_PER_MONTH
        }

    /**
     * True when a per-month breakdown is meaningful. A monthly plan's per-month figure is
     * redundant; a weekly plan's is upsell noise.
     */
    val isLongerThanAMonth: Boolean get() = months > 1.0

    /** "month", "year", "3 months" — the suffix after a price. */
    val label: String
        get() {
            val noun = when (unit) {
                Unit.DAY -> "day"
                Unit.WEEK -> "week"
                Unit.MONTH -> "month"
                Unit.YEAR -> "year"
            }
            return if (value == 1) noun else "$value ${noun}s"
        }

    /** Short suffix for a tight card: "mo", "yr", "3 mo". */
    val shortLabel: String
        get() {
            val noun = when (unit) {
                Unit.DAY -> "d"
                Unit.WEEK -> "wk"
                Unit.MONTH -> "mo"
                Unit.YEAR -> "yr"
            }
            return if (value == 1) noun else "$value $noun"
        }

    /** Price of one month at this plan's rate, in micros. */
    fun monthlyEquivalentMicros(priceMicros: Long): Double? =
        if (months <= 0) null else priceMicros / months
}

/**
 * Whole-percent saving of [candidate] against [baseline], compared PER MONTH so plans of different
 * lengths are comparable at all. Returns null when there is no genuine saving — a "SAVE 0%" badge
 * is worse than no badge.
 */
fun savingsPercent(candidate: ProductDetails, baseline: ProductDetails): Int? {
    val cPhase = PlanPeriod.recurringPhase(candidate) ?: return null
    val bPhase = PlanPeriod.recurringPhase(baseline) ?: return null
    val c = PlanPeriod.of(candidate)?.monthlyEquivalentMicros(cPhase.priceAmountMicros) ?: return null
    val b = PlanPeriod.of(baseline)?.monthlyEquivalentMicros(bPhase.priceAmountMicros) ?: return null
    if (b <= 0) return null
    val pct = ((b - c) / b * 100).roundToInt()
    return if (pct in 1..99) pct else null
}

/** A plan as the paywall shows it: price, period, and its saving against the cheapest cycle. */
data class DisplayPlan(
    val productId: String,
    val details: ProductDetails,
    val period: PlanPeriod?,
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
) {
    /**
     * "≈ $3.33 / mo", or null when a per-month breakdown says nothing useful.
     *
     * Formatted by [NumberFormat], not by hand: a hand-built "symbol then number" is wrong in every
     * locale that puts the symbol last ("3,33 €"), and falling back to a bare symbol-less number
     * would print an unqualified figure on a paywall — the exact class of misstatement this file's
     * header argues against. If the currency can't be resolved we return null and show nothing.
     */
    fun monthlyEquivalentText(): String? {
        val p = period ?: return null
        if (!p.isLongerThanAMonth) return null
        val micros = p.monthlyEquivalentMicros(priceMicros) ?: return null
        val formatter = runCatching {
            NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                currency = java.util.Currency.getInstance(currencyCode)
            }
        }.getOrNull() ?: return null
        return "≈ ${formatter.format(micros / 1_000_000.0)} / mo"
    }
}
