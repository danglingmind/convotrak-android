package com.danglingmind.convotrack.data.analytics

import android.app.Application
import android.content.Context
import android.os.Bundle
import com.danglingmind.convotrack.BuildConfig
import com.facebook.FacebookSdk
import com.facebook.LoggingBehavior
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import java.math.BigDecimal
import java.util.Currency

/**
 * Meta app events, iOS `MetaEvents` parity. These exist for ad attribution and campaign
 * optimisation only; product analytics stays in [Analytics] (PostHog).
 *
 * The SDK logs installs and app opens itself once started. Everything else is the handful of
 * conversions a campaign can optimise for:
 *  - `CompleteRegistration` — first sign-in on this install
 *  - `ride_created` / `ride_joined` — the activation events
 *  - `StartTrial` / `Subscribe` — a paid plan, with value and currency
 *
 * Purchases are logged here by hand, so "Log in-app events automatically" must stay off in the
 * Meta app dashboard or every subscription is counted twice.
 *
 * No user data is ever passed to Meta (no ids, emails, names, coordinates or invite codes).
 *
 * Off when `META_APP_ID` (local.properties) is empty, in debug builds (unless
 * [Analytics.debugEnabled]), and when the rider has opted out of analytics.
 */
object MetaEvents {
    const val RIDE_CREATED = "ride_created"
    const val RIDE_JOINED = "ride_joined"

    private const val PREFS = "meta_events"
    private const val KEY_REGISTERED = "registered"

    private lateinit var app: Application
    private var logger: AppEventsLogger? = null

    private val configured: Boolean
        get() = BuildConfig.META_APP_ID.isNotEmpty() && BuildConfig.META_CLIENT_TOKEN.isNotEmpty() &&
            (!BuildConfig.DEBUG || Analytics.debugEnabled)

    /** Call once from `Application.onCreate`, after [Analytics.start]. */
    @Suppress("DEPRECATION") // sdkInitialize is the only entry point once the init provider is removed.
    fun start(application: Application) {
        app = application
        if (!configured || Analytics.isOptedOut) return
        FacebookSdk.setApplicationId(BuildConfig.META_APP_ID)
        FacebookSdk.setClientToken(BuildConfig.META_CLIENT_TOKEN)
        if (BuildConfig.DEBUG) {
            // Debug + analyticsDebug only: logcat tag "FacebookSDK" shows each event and request.
            FacebookSdk.setIsDebugEnabled(true)
            FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS)
            FacebookSdk.addLoggingBehavior(LoggingBehavior.REQUESTS)
        }
        FacebookSdk.sdkInitialize(application)
        FacebookSdk.setAutoInitEnabled(true)
        FacebookSdk.setAutoLogAppEventsEnabled(true)
        FacebookSdk.setAdvertiserIDCollectionEnabled(true)
        FacebookSdk.fullyInitialize()
        AppEventsLogger.activateApp(application)
        logger = AppEventsLogger.newLogger(application)
    }

    /** First successful sign-in on this install. Later sign-ins (re-login, account switch) are not new sign-ups. */
    fun signedIn(method: String) {
        val log = logger ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REGISTERED, false)) return
        prefs.edit().putBoolean(KEY_REGISTERED, true).apply()
        log.logEvent(
            AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION,
            Bundle().apply { putString(AppEventsConstants.EVENT_PARAM_REGISTRATION_METHOD, method) },
        )
    }

    fun rideCreated() {
        logger?.logEvent(RIDE_CREATED)
    }

    fun rideJoined() {
        logger?.logEvent(RIDE_JOINED)
    }

    /**
     * A completed subscription purchase. [trial] = the offer started with a free phase, which Meta
     * counts as `StartTrial` rather than revenue; [price] is the first paid phase either way, so
     * value-based optimisation sees what the trial is expected to be worth.
     */
    fun subscribed(productId: String, price: BigDecimal, currency: String, trial: Boolean) {
        val log = logger ?: return
        val params = Bundle().apply {
            putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, productId)
            putString(AppEventsConstants.EVENT_PARAM_CURRENCY, Currency.getInstance(currency).currencyCode)
        }
        val name = if (trial) AppEventsConstants.EVENT_NAME_START_TRIAL else AppEventsConstants.EVENT_NAME_SUBSCRIBE
        log.logEvent(name, price.toDouble(), params)
    }
}
