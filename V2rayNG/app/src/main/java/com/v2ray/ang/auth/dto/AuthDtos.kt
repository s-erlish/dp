package com.v2ray.ang.auth.dto

import com.google.gson.annotations.SerializedName

/**
 * Auth endpoints of the Departament backend (JWT, 7-day, NO refresh).
 *
 *  POST /client/auth/register              -> [RegisterResult] (Success | RequiresVerification)
 *  POST /client/auth/telegram-login-token  -> [TelegramTokenDto]
 *  GET  /client/auth/telegram-login-check  -> 404 NotYet / 200 Confirmed / 410 Expired ([TelegramCheckResult])
 *  POST /client/auth/login                 -> [LoginResult] (Success | Requires2FA)
 *  POST /client/auth/2fa-login             -> [AuthResult]
 *  POST /client/auth/google                -> [AuthResult]
 *  GET  /client/auth/me                    -> [UserProfileDto]
 *
 * And one errand of an account that already exists, which is why it sits on the client root
 * rather than under `/client/auth`:
 *
 *  POST /client/link-email-request         -> 200 (a letter is out) / 400 / 500 / 503
 */

// region request bodies

data class LoginRequestDto(
    val email: String,
    val password: String,
)

/**
 * POST /client/auth/register. The password must be at least 8 characters — the panel refuses a
 * shorter one with 400, so the form gates on the same number rather than letting the server say it.
 * [referralCode] is optional and is sent only when the app has one to pass on.
 */
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val referralCode: String? = null,
)

data class TwoFaLoginRequestDto(
    val tempToken: String,
    val code: String,
)

data class GoogleLoginRequestDto(
    val idToken: String,
    val referralCode: String? = null,
)

/**
 * POST /client/link-email-request — attach an address to the session the request already carries.
 *
 * THE ADDRESS IS THE WHOLE BODY. There is no password here and there must not be one: the account
 * exists, the caller is already authenticated by the Bearer token, and what the panel is being
 * asked for is a letter, not a credential. The password the account will sign in with afterwards
 * is set on the site, behind the link.
 */
data class LinkEmailRequestDto(
    val email: String,
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

/**
 * Raw 201 body of POST /client/auth/register, in EITHER of the two shapes the panel answers with:
 * `{token, client}` when e-mail verification is switched off (a session, immediately), or
 * `{message, requiresVerification}` when it is on and a confirmation letter has been sent.
 */
data class RegisterResponseDto(
    val token: String? = null,
    val client: UserProfileDto? = null,
    val requiresVerification: Boolean = false,
    val message: String? = null,
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

/**
 * Outcome of POST /client/auth/register.
 *
 * The two cases are a property of the PANEL, not of the request: the same body registers a session
 * outright on an installation with verification disabled and only sends a letter on one with it
 * enabled, so the app has to be able to finish either way.
 */
sealed interface RegisterResult {
    /** Verification is off on this panel — the account exists and the session is already issued. */
    data class Success(val token: String, val client: UserProfileDto) : RegisterResult

    /**
     * Verification is on: a letter is on its way and there is no token yet. [message] is the
     * panel's own sentence about it, kept for the log — the waiting screen writes its own copy,
     * which names the address the letter went to.
     */
    data class RequiresVerification(val message: String?) : RegisterResult
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
    // The backend exposes the profile currency as `preferredCurrency`; accept `currency` too so
    // either spelling maps. Stays blank when absent (currencySymbol then defaults to ₽ for RUB).
    @SerializedName(value = "currency", alternate = ["preferredCurrency"])
    val currency: String = "",
    val telegramLinked: Boolean = false,
    val telegramId: Long? = null,
    val telegramUsername: String? = null,
    // Telegram display / first name, when the backend exposes it. Preferred for the primary
    // account line; the key name varies across backends so accept the common spellings. Stays
    // null (so the @username/email fallback is used unchanged) when absent.
    @SerializedName(
        value = "telegramName",
        alternate = ["telegramFirstName", "firstName", "first_name", "name", "displayName", "tgName"],
    )
    val telegramName: String? = null,
    val referralCode: String = "",
    val remnawaveUuid: String = "",
    val trialUsed: Boolean = false,
    val autoRenewEnabled: Boolean = false,
    val totpEnabled: Boolean = false,
    // Telegram profile photo, if the backend exposes one. Key name varies across backends,
    // so accept the common spellings; stays null (monogram fallback) when absent.
    @SerializedName(
        value = "avatarUrl",
        alternate = ["photoUrl", "photo_url", "telegramPhotoUrl", "tgPhotoUrl", "telegramAvatarUrl", "avatar", "photo"],
    )
    val avatarUrl: String? = null,
)
