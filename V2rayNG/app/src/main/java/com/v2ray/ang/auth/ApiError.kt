package com.v2ray.ang.auth

/**
 * Sealed hierarchy of failures the API layer can surface. Callers pattern-match on these
 * and never see raw exceptions. Messages must never contain tokens or subscription URLs.
 */
sealed class ApiError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** Backend not configured (blank base URL) — login should not be offered. */
    object NotConfigured : ApiError("Backend not configured")

    /** Network/IO failure (no connectivity, DNS, TLS). */
    class Network(cause: Throwable? = null) : ApiError("Network error", cause)

    /** A request (or the login flow) exceeded its time budget. */
    object Timeout : ApiError("Request timed out")

    /**
     * 401/403 — session invalid or expired. [detail] carries a sanitized snippet of the response
     * body (payment diagnostics), never a token/URL. Compare with `is ApiError.Unauthorized`.
     */
    data class Unauthorized(val detail: String? = null) : ApiError("Unauthorized")

    /** 404 — resource not found (also the "keep polling" signal for telegram-login-check). */
    object NotFound : ApiError("Not found")

    /** 410 — resource gone / login token expired. */
    object Gone : ApiError("Gone")

    /** 429 — too many requests. */
    object RateLimited : ApiError("Rate limited")

    /** 502/503 — backend temporarily unavailable. */
    object ServiceUnavailable : ApiError("Service unavailable")

    /**
     * Any other unexpected non-2xx status. [detail] carries a sanitized snippet of the response
     * body (payment diagnostics), never a token/URL.
     */
    class Server(val code: Int, val detail: String? = null) : ApiError("Server error ($code)")

    /** Response body could not be parsed into the expected shape. */
    class Parse(cause: Throwable? = null) : ApiError("Failed to parse response", cause)
}
