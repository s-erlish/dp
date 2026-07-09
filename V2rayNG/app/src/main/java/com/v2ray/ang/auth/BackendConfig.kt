package com.v2ray.ang.auth

import com.v2ray.ang.BuildConfig

/**
 * Central configuration for the Departament VPN backend + Telegram bot.
 *
 * Everything the auth/subscription layer needs to reach the backend lives here so that
 * wiring the real bot fork is a config change (BuildConfig fields), not a code rewrite.
 *
 * IMPORTANT: login is OPTIONAL. When [isConfigured] is false the app must remain fully
 * usable without any backend — callers should only offer/require login when configured.
 */
object BackendConfig {

    /** Backend base URL, e.g. https://api.departament.example (no trailing slash required). */
    val baseUrl: String get() = BuildConfig.BACKEND_BASE_URL.trimEnd('/')

    /** Telegram bot username without the leading '@', e.g. departament_vpn_bot. */
    val botUsername: String get() = BuildConfig.BOT_USERNAME

    /** User-Agent used when fetching the subscription (negotiates the response format). */
    val subscriptionUserAgent: String
        get() = BuildConfig.SUB_USER_AGENT.ifBlank { "DepartamentVPN/1.0" }

    /** True only when a backend base URL has been provided at build time. */
    fun isConfigured(): Boolean = BuildConfig.BACKEND_BASE_URL.isNotBlank()

    val endpoints: Endpoints = Endpoints()

    data class Endpoints(
        val authStart: String = "/auth/telegram/start",
        val authPoll: String = "/auth/telegram/poll",
        val authCode: String = "/auth/telegram/code",
        val profile: String = "/user/profile",
        val subscription: String = "/user/subscription",
        val refresh: String = "/auth/refresh",
        val logout: String = "/auth/logout",
    )
}
