package com.v2ray.ang.auth.dto

/**
 * Gson data classes for the Departament backend auth + subscription contract.
 * Field names mirror the design doc; only these need to change if the real backend differs.
 */

data class AuthStartRequest(
    val nonce: String,
    val deviceId: String,
    val platform: String = "android",
)

data class AuthStartResponse(
    val deepLink: String? = null,
    val botUsername: String? = null,
    val expiresInSec: Int = 120,
)

data class AuthPollRequest(
    val nonce: String,
    val deviceId: String,
)

data class AuthCodeRequest(
    val code: String,
    val deviceId: String,
)

/** status = "pending" | "ready" | "expired" */
data class AuthPollResponse(
    val status: String,
    val token: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val user: UserProfileDto? = null,
    val subscription: SubscriptionInfoDto? = null,
)

data class RefreshRequest(
    val refreshToken: String,
    val deviceId: String,
)

data class UserProfileDto(
    val id: String,
    val telegramId: Long? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

data class SubscriptionInfoDto(
    val subscriptionUrl: String,      // Remnawave-style /api/sub/<shortUuid>
    val remarks: String? = null,      // group name shown in the app
    val status: String? = null,       // active | expired | limited
    val expiresAt: Long? = null,      // epoch millis
    val trafficUsedBytes: Long? = null,
    val trafficLimitBytes: Long? = null,
    val userAgent: String? = null,    // UA the backend wants us to send when fetching
)
