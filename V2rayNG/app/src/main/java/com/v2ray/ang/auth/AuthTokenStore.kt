package com.v2ray.ang.auth

import com.tencent.mmkv.MMKV
import com.v2ray.ang.auth.dto.UserProfileDto
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils

/**
 * Dedicated MMKV-backed store for the app session token, refresh token, user profile,
 * a stable device id, and the guid of the subscription we manage.
 *
 * Isolated from [com.v2ray.ang.handler.MmkvManager]'s config stores in its own instance.
 *
 * TODO(security): derive a crypt key from an Android Keystore secret and pass it to
 *  MMKV.mmkvWithID(ID, mode, cryptKey) so tokens are encrypted at rest. Kept as plain
 *  mmkvWithID for this scaffold to avoid Keystore compile/runtime risk. Tokens are never logged.
 */
object AuthTokenStore {

    private const val ID = "departament_auth"

    private const val KEY_TOKEN = "token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_USER = "user_json"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_MANAGED_SUB_GUID = "departament_managed_sub_guid"

    private val store by lazy { MMKV.mmkvWithID(ID) }

    /** Stable per-install device id, generated once and reused. */
    fun deviceId(): String {
        val existing = store.decodeString(KEY_DEVICE_ID)
        if (!existing.isNullOrBlank()) return existing
        val generated = Utils.getUuid()
        store.encode(KEY_DEVICE_ID, generated)
        return generated
    }

    fun saveSession(token: String, refresh: String?, expiresAt: Long?, user: UserProfileDto?) {
        store.encode(KEY_TOKEN, token)
        if (refresh != null) store.encode(KEY_REFRESH, refresh) else store.remove(KEY_REFRESH)
        if (expiresAt != null) store.encode(KEY_EXPIRES_AT, expiresAt) else store.remove(KEY_EXPIRES_AT)
        if (user != null) store.encode(KEY_USER, JsonUtil.toJson(user)) else store.remove(KEY_USER)
    }

    fun getToken(): String? = store.decodeString(KEY_TOKEN)

    fun getRefreshToken(): String? = store.decodeString(KEY_REFRESH)

    fun getExpiresAt(): Long = store.decodeLong(KEY_EXPIRES_AT, 0L)

    fun getUser(): UserProfileDto? {
        val json = store.decodeString(KEY_USER) ?: return null
        return JsonUtil.fromJsonSafe(json, UserProfileDto::class.java)
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    /** Guid of the subscription owned by the auth flow (empty when none). */
    fun managedSubGuid(): String = store.decodeString(KEY_MANAGED_SUB_GUID).orEmpty()

    fun setManagedSubGuid(guid: String) {
        store.encode(KEY_MANAGED_SUB_GUID, guid)
    }

    /** Clears the session (logout / 401). Keeps deviceId; drops the managed-sub reference. */
    fun clear() {
        store.remove(KEY_TOKEN)
        store.remove(KEY_REFRESH)
        store.remove(KEY_EXPIRES_AT)
        store.remove(KEY_USER)
        store.remove(KEY_MANAGED_SUB_GUID)
    }
}
