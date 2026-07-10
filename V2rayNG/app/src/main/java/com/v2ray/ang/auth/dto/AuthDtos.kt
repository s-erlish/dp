package com.v2ray.ang.auth.dto

/**
 * Auth endpoints of the Departament backend (JWT, 7-day, NO refresh).
 *
 *  POST /client/auth/telegram-login-token  -> [TelegramTokenDto]
 *  GET  /client/auth/telegram-login-check  -> 404 NotYet / 200 Confirmed / 410 Expired ([TelegramCheckResult])
 *  POST /client/auth/login                 -> [LoginResult] (Success | Requires2FA)
 *  POST /client/auth/2fa-login             -> [AuthResult]
 *  POST /client/auth/google                -> [AuthResult]
 *  GET  /client/auth/me                    -> [UserProfileDto]
 */

// region request bodies

data class LoginRequestDto(
    val email: String,
    val password: String,
)

data class TwoFaLoginRequestDto(
    val tempToken: String,
    val code: String,
)

data class GoogleLoginRequestDto(
    val idToken: String,
    val referralCode: String? = null,
)

// endregion

// region raw responses (parsed then mapped to the sealed result types)

/** POST /client/auth/telegram-login-token */
data class TelegramTokenDto(
    val token: String = "",
)

/** Raw 200 body of GET /client/auth/telegram-login-check. */
data class TelegramCheckResponseDto(
    val confirmed: Boolean = false,
    val token: String? = null,
    val client: UserProfileDto? = null,
    val justCreated: Boolean = false,
)

/** Raw body of POST /client/auth/login (either shape). */
data class LoginResponseDto(
    val token: String? = null,
    val client: UserProfileDto? = null,
    val requires2FA: Boolean = false,
    val tempToken: String? = null,
)

// endregion

// region result types consumed by the UI/session layer

/** Outcome of polling GET /client/auth/telegram-login-check. */
sealed interface TelegramCheckResult {
    /** 404 — not confirmed yet, keep polling. */
    object NotYet : TelegramCheckResult

    /** 410 — the login token expired. */
    object Expired : TelegramCheckResult

    /** 200 — the user confirmed in Telegram; session is ready. */
    data class Confirmed(
        val token: String,
        val client: UserProfileDto,
        val justCreated: Boolean,
    ) : TelegramCheckResult
}

/** Outcome of POST /client/auth/login. */
sealed interface LoginResult {
    /** Password accepted, session issued. */
    data class Success(val token: String, val client: UserProfileDto) : LoginResult

    /** Password accepted but a TOTP code is required; call 2fa-login with [tempToken]. */
    data class Requires2FA(val tempToken: String) : LoginResult
}

/** A successful authentication carrying the JWT and profile. */
data class AuthResult(
    val token: String = "",
    val client: UserProfileDto = UserProfileDto(),
)

/** The authenticated user's profile (GET /client/auth/me and embedded in auth responses). */
data class UserProfileDto(
    val id: String = "",
    val email: String = "",
    val balance: Double = 0.0,
    val currency: String = "",
    val telegramLinked: Boolean = false,
    val telegramId: Long? = null,
    val telegramUsername: String? = null,
    val referralCode: String = "",
    val remnawaveUuid: String = "",
    val trialUsed: Boolean = false,
    val autoRenewEnabled: Boolean = false,
    val totpEnabled: Boolean = false,
)
