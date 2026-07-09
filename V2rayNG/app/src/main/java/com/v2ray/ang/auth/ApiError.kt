package com.v2ray.ang.auth

/**
 * Sealed hierarchy of failures the API layer can surface. Callers pattern-match on these
 * and never see raw exceptions. Messages must never contain tokens or subscription URLs.
 */
sealed class ApiError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** Backend not configured (blank base URL) — login should not be offered. */
    object NotConfigured : ApiError("Backend not configured")

    /** Network/IO failure (no connectivity, DNS, TLS, timeout). */
    class Network(cause: Throwable? = null) : ApiError("Network error", cause)

    /** 401/403 — session invalid or expired. */
    object Unauthorized : ApiError("Unauthorized")

    /** 429 — too many requests. */
    object RateLimited : ApiError("Rate limited")

    /** 5xx or unexpected non-2xx. */
    class Server(val code: Int) : ApiError("Server error ($code)")

    /** Response body could not be parsed into the expected shape. */
    class Parse(cause: Throwable? = null) : ApiError("Failed to parse response", cause)

    /** The login flow ran out of time before the user confirmed in Telegram. */
    object Timeout : ApiError("Login timed out")
}
