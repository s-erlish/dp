package com.v2ray.ang.auth

import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.auth.dto.UserProfileDto
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import java.security.MessageDigest

/**
 * Dedicated MMKV-backed store for the app session JWT (7-day, no refresh), the cached user
 * profile, a stable device id, and the uuid->guid map of subscriptions we manage.
 *
 * The store is encrypted at rest with a crypt key sealed by [KeystoreKeyProvider]. If the
 * Android Keystore is unavailable the store transparently falls back to a plain MMKV so it
 * never crashes. Tokens and subscription URLs are never logged.
 */
object AuthTokenStore {

    private const val ID = "departament_auth"

    private const val KEY_TOKEN = "token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_USER = "user_json"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_MANAGED_GUIDS = "managed_guids_json"

    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, String>>() {}.type

    private val store: MMKV by lazy { openStore() }

    private fun openStore(): MMKV {
        return try {
            val cryptKey = KeystoreKeyProvider.getOrCreateCryptKey()
            if (!cryptKey.isNullOrBlank()) {
                MMKV.mmkvWithID(ID, MMKV.SINGLE_PROCESS_MODE, cryptKey)
            } else {
                MMKV.mmkvWithID(ID)
            }
        } catch (e: Throwable) {
            try {
                MMKV.mmkvWithID(ID)
            } catch (e2: Throwable) {
                MMKV.defaultMMKV()
            }
        }
    }

    // Emulator/QA sentinel: many devices report this exact ANDROID_ID, so it isn't unique.
    private const val BAD_ANDROID_ID = "9774d56d682e549c"

    /**
     * Stable per-device HWID that survives reinstall, computed once and reused.
     *
     * Existing installs keep whatever id is already cached (no churn). First run derives the id
     * from [Settings.Secure.ANDROID_ID] — stable for the signing key + device + user across
     * reinstalls — hashed to a 32-hex-char id (MD5) to match the UUID-without-dashes format the
     * backend expects, so a clean reinstall on the same device yields the SAME HWID and the panel
     * keeps a single device slot. Falls back to a random uuid only when ANDROID_ID is unusable.
     */
    fun deviceId(): String {
        val existing = store.decodeString(KEY_DEVICE_ID)
        if (!existing.isNullOrBlank()) return existing
        val generated = computeStableDeviceId()
        store.encode(KEY_DEVICE_ID, generated)
        return generated
    }

    /** MD5(ANDROID_ID) as 32 lowercase hex chars, or a random uuid when ANDROID_ID is unusable. */
    private fun computeStableDeviceId(): String {
        val androidId = try {
            Settings.Secure.getString(
                AngApplication.application.contentResolver,
                Settings.Secure.ANDROID_ID,
            )
        } catch (e: Throwable) {
            null
        }
        if (androidId.isNullOrBlank() || androidId == BAD_ANDROID_ID) {
            return Utils.getUuid()
        }
        return try {
            MessageDigest.getInstance("MD5")
                .digest(androidId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } catch (e: Throwable) {
            Utils.getUuid()
        }
    }

    /** Persists a new session. No refresh token in this backend. */
    fun saveSession(token: String, expiresAt: Long? = null, user: UserProfileDto? = null) {
        store.encode(KEY_TOKEN, token)
        if (expiresAt != null) store.encode(KEY_EXPIRES_AT, expiresAt) else store.remove(KEY_EXPIRES_AT)
        if (user != null) store.encode(KEY_USER, JsonUtil.toJson(user)) else store.remove(KEY_USER)
    }

    /** Updates just the cached user profile (keeps the current token). */
    fun saveUser(user: UserProfileDto) {
        store.encode(KEY_USER, JsonUtil.toJson(user))
    }

    fun getToken(): String? = store.decodeString(KEY_TOKEN)

    fun getExpiresAt(): Long = store.decodeLong(KEY_EXPIRES_AT, 0L)

    fun getUser(): UserProfileDto? {
        val json = store.decodeString(KEY_USER) ?: return null
        // Use the null-tolerant backend Gson so a cached profile with null string fields
        // (e.g. Telegram-only users without an email) decodes without NPE-prone null fields.
        return try {
            ApiGson.instance.fromJson(json, UserProfileDto::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    /** uuid -> local subscription guid map of subscriptions owned by the auth flow. */
    fun getManagedGuids(): MutableMap<String, String> {
        val json = store.decodeString(KEY_MANAGED_GUIDS) ?: return mutableMapOf()
        return try {
            gson.fromJson(json, mapType) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun setManagedGuids(map: Map<String, String>) {
        store.encode(KEY_MANAGED_GUIDS, gson.toJson(map))
    }

    /** Clears the session (logout / 401). Keeps deviceId; drops managed-sub references. */
    fun clear() {
        store.remove(KEY_TOKEN)
        store.remove(KEY_EXPIRES_AT)
        store.remove(KEY_USER)
        store.remove(KEY_MANAGED_GUIDS)
    }
}
