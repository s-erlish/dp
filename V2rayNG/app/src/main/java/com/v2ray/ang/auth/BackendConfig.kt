package com.v2ray.ang.auth

import com.v2ray.ang.BuildConfig

/**
 * Central configuration for the Departament VPN backend + Telegram bot.
 *
 * Base URL is `https://web.departament.site/api`; every path in [Endpoints] is relative to it.
 * JWT auth uses `Authorization: Bearer <token>` (7-day token, NO refresh endpoint).
 *
 * IMPORTANT: login is OPTIONAL. When [isConfigured] is false the app must remain fully
 * usable without any backend — callers only offer/require login when configured.
 */
object BackendConfig {

    /** Backend base URL, e.g. https://web.departament.site/api (no trailing slash). */
    val baseUrl: String get() = BuildConfig.BACKEND_BASE_URL.trimEnd('/')

    /** Telegram bot username without the leading '@', e.g. departament_vpn_bot. */
    val botUsername: String get() = BuildConfig.BOT_USERNAME

    /** User-Agent used for API + subscription requests (negotiates the response format). */
    val subscriptionUserAgent: String
        get() = BuildConfig.SUB_USER_AGENT.ifBlank { "DepartamentVPN/1.0" }

    /** True only when a backend base URL has been provided at build time. */
    fun isConfigured(): Boolean = BuildConfig.BACKEND_BASE_URL.isNotBlank()

    /** Relative paths (appended to [baseUrl]). Parameterized paths are built via helper funcs. */
    object Endpoints {
        // Public
        const val publicConfig = "/public/config"
        const val publicTariffs = "/public/tariffs"
        const val serverStatus = "/public/server-status"

        // Auth
        const val telegramLoginToken = "/client/auth/telegram-login-token"
        const val telegramLoginCheck = "/client/auth/telegram-login-check"
        const val login = "/client/auth/login"
        const val twoFaLogin = "/client/auth/2fa-login"
        const val googleLogin = "/client/auth/google"
        const val me = "/client/auth/me"

        // Subscription
        /** The authoritative ACTIVE (root) subscription summary — richer than the /all root item. */
        const val subscription = "/client/subscription"
        const val subscriptionAll = "/client/subscription/all"
        const val subscriptionQr = "/client/subscription/qr"
        const val upgradeQuote = "/client/subscriptions/upgrade-quote"
        const val upgrade = "/client/subscriptions/upgrade"
        fun renameSubscription(scope: String, id: String) = "/client/subscription/$scope/$id/name"
        fun addDevices(scope: String, id: String) = "/client/subscription/$scope/$id/add-devices"

        // Devices
        const val devices = "/client/devices"
        const val deleteDevice = "/client/devices/delete"

        // Payments
        const val payPlatega = "/client/payments/platega"
        const val payBalance = "/client/payments/balance"
        const val payments = "/client/payments"

        // Promo / trial / referral
        const val promoCheck = "/client/promo-code/check"
        const val promoActivate = "/client/promo-code/activate"
        const val trial = "/client/trial"
        const val referralStats = "/client/referral-stats"
        fun secondaryAutoRenew(id: String) = "/client/secondary-subscriptions/$id/auto-renew"

        /** Auto-renew of the ACTIVE (root/primary) subscription — no id in the path. */
        const val primaryAutoRenew = "/client/subscription/auto-renew"
    }
}
