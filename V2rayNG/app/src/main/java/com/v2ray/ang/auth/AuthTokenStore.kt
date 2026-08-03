package com.v2ray.ang.auth

import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.auth.dto.UserProfileDto
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.security.MessageDigest

/**
 * Dedicated MMKV-backed store for the app session JWT (7-day, no refresh), the cached user
 * profile, a stable device id, and the uuid->guid map of subscriptions we manage.
 *
 * The store is encrypted at rest with a crypt key sealed by [KeystoreKeyProvider]. Tokens and
 * subscription URLs are never logged.
 *
 * Two properties of this file are load-bearing and easy to lose:
 *
 * **It is opened MULTI_PROCESS_MODE**, like every store in `MmkvManager`, because a second process
 * writes it. WorkManager is pointed at `:bg` (`AngApplication`), so `SubscriptionUpdater.UpdateTask`
 * runs there, and its subscription fetch reads [deviceId] whenever the HWID header is on (the
 * default). Two processes mmapping the same file with no inter-process lock can drop each other's
 * flushes, and the token, the cached profile and the managed-guid map all live in this one file —
 * that is a session lost at random, with the servers still on disk.
 *
 * **A store that cannot be opened is null, not a different store.** The handle is resolved on
 * demand and only a successful open is cached, so a Keystore that was briefly unavailable heals on
 * the next call instead of signing the user out for the rest of the process. It is never opened
 * unencrypted to "recover" from an unseal failure; see [KeystoreKeyProvider.CryptKeyState].
 */
object AuthTokenStore {

    private const val ID = "departament_auth"

    private const val KEY_TOKEN = "token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_USER = "user_json"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_MANAGED_GUIDS = "managed_guids_json"

    /**
     * How long to wait before trying to open the store again after a failure. Long enough that a
     * screen reading [isLoggedIn] in a loop cannot hammer the Keystore, short enough that the user
     * does not have to restart the app for a transient failure to clear.
     */
    private const val REOPEN_RETRY_MS = 2_000L

    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, String>>() {}.type

    @Volatile
    private var cachedStore: MMKV? = null

    @Volatile
    private var lastOpenAttemptAt = 0L

    @Volatile
    private var cachedDeviceId: String? = null

    /**
     * The store handle, or null when the session cannot be read right now.
     *
     * Callers treat null as "no session data available", which reads exactly like a signed-out
     * device — except that nothing is written either, so an unreadable file is never overwritten
     * with records it cannot hold.
     */
    private fun store(): MMKV? {
        cachedStore?.let { return it }
        synchronized(this) {
            cachedStore?.let { return it }
            val now = SystemClock.elapsedRealtime()
            if (lastOpenAttemptAt != 0L && now - lastOpenAttemptAt < REOPEN_RETRY_MS) return null
            lastOpenAttemptAt = now
            return openStore().also { cachedStore = it }
        }
    }

    private fun openStore(): MMKV? {
        val state = try {
            KeystoreKeyProvider.cryptKey()
        } catch (e: Throwable) {
            KeystoreKeyProvider.CryptKeyState.Unsealable(e)
        }
        return when (state) {
            is KeystoreKeyProvider.CryptKeyState.Available -> openWith(state.key)
            // Nothing was ever sealed here, so nothing in the file is ciphertext.
            KeystoreKeyProvider.CryptKeyState.Absent -> openWith(null)
            is KeystoreKeyProvider.CryptKeyState.Unsealable -> {
                LogUtil.w(
                    AppConfig.TAG,
                    "Auth store is sealed and cannot be unlocked right now: ${state.cause.message}"
                )
                null
            }
        }
    }

    private fun openWith(cryptKey: String?): MMKV? {
        return try {
            MMKV.mmkvWithID(ID, MMKV.MULTI_PROCESS_MODE, cryptKey)
        } catch (e: Throwable) {
            LogUtil.w(AppConfig.TAG, "Auth store could not be opened: ${e.message}")
            null
        }
    }

    // Emulator/QA sentinel: many devices report this exact ANDROID_ID, so it isn't unique.
    private const val BAD_ANDROID_ID = "9774d56d682e549c"

    /** Namespaces the two derivations below so they can never collide on one device. */
    private const val ID_SALT_ANDROID = "departament-hwid-v1|android_id|"
    private const val ID_SALT_BUILD = "departament-hwid-v1|build|"

    /**
     * A candidate identity and whether it is worth writing down.
     *
     * The distinction is the whole reason this type exists. A DERIVED id is a pure function of the
     * device, so persisting it is free: the same value comes back next launch anyway. A FALLBACK id
     * is a guess made because the device would not answer, and persisting THAT is what burns device
     * slots forever — one unlucky read at first launch and the install is pinned to a random value
     * no later launch can correct, which is exactly the failure this whole file exists to prevent.
     */
    private data class Candidate(val id: String, val derived: Boolean)

    /**
     * Stable per-device HWID that SURVIVES UNINSTALL/REINSTALL, computed once and reused.
     *
     * Read this before changing it — the app has already shipped the wrong answer here once.
     *
     * **What it is keyed on.** [Settings.Secure.ANDROID_ID], hashed to 32 lowercase hex chars so it
     * matches the UUID-without-dashes shape the panel expects. ANDROID_ID is stored by the OS, not
     * by the app, so wiping app data or uninstalling does not touch it: a clean reinstall on the
     * same phone derives the SAME HWID and the subscription keeps ONE device slot. From API 26 it
     * is scoped per (device, user, app signing key), which is precisely the property we want — and
     * it is also the one caveat: an app signed with a DIFFERENT key is a different app to the OS
     * and legitimately gets a different value. A debug-signed build and a release-signed build of
     * departament therefore report two devices on one phone. That is the OS, not this function, and
     * it stops mattering the moment every install the user receives is signed with the release key.
     *
     * **Why the previous "stable device id" was not.** `98f5397` kept a `Utils.getUuid()` — a fresh
     * random UUID minted on first run and persisted in this store. MMKV lives in the app's private
     * data directory, so uninstall deletes it, and the next install minted another one. Every update
     * the user side-loaded therefore registered as a brand-new device and consumed another slot
     * against the plan's `hwidDeviceLimit`; a 3-device subscription bricked after two updates.
     *
     * **Migration.** An id already on disk always wins, whatever it was keyed on. An install that
     * upgrades into this build keeps the identity the panel already knows and does NOT appear as yet
     * another new device — the derivation below only ever runs when there is nothing to carry
     * forward, i.e. on a genuinely fresh install.
     *
     * **Nothing new is requested from the user.** No permission, no Play-restricted identifier
     * (advertising id, IMEI, serial): only values every app may read.
     */
    fun deviceId(): String {
        val store = store()
        // MIGRATION FIRST, ALWAYS. Whatever this install has been telling the panel, it keeps
        // telling it. Re-deriving over a stored id would make every upgrade a new device — the same
        // defect as a reinstall, just triggered by us instead of by the user.
        val existing = store?.decodeString(KEY_DEVICE_ID)
        if (!existing.isNullOrBlank()) {
            cachedDeviceId = existing
            return existing
        }
        cachedDeviceId?.let { cached ->
            // Held from earlier in this process. Write it through if the store has since opened, so
            // a Keystore that was busy at launch does not cost the id its permanence.
            store?.encode(KEY_DEVICE_ID, cached)
            return cached
        }
        val candidate = computeDeviceId()
        cachedDeviceId = candidate.id
        // Only a DERIVED id is written down. A fallback stays in memory for this process only, so
        // the next launch gets another chance to derive the real one instead of inheriting a guess.
        if (candidate.derived) store?.encode(KEY_DEVICE_ID, candidate.id)
        return candidate.id
    }

    /**
     * ANDROID_ID first; a hardware-attribute digest when the OS will not answer; a random uuid only
     * when even that fails.
     *
     * The middle tier matters more than it looks. It is not unique between two identical handsets,
     * but it IS identical across reinstalls of the same handset — and since the panel scopes device
     * entries to one subscription, "the same phone keeps one slot" is worth far more here than
     * "two people with the same phone model would collide", which requires them to share an account.
     * A random uuid has neither property.
     */
    private fun computeDeviceId(): Candidate {
        val androidId = try {
            Settings.Secure.getString(
                AngApplication.application.contentResolver,
                Settings.Secure.ANDROID_ID,
            )
        } catch (e: Throwable) {
            LogUtil.w(AppConfig.TAG, "ANDROID_ID unavailable, falling back to build attributes")
            null
        }
        if (!androidId.isNullOrBlank() && androidId != BAD_ANDROID_ID) {
            hex(ID_SALT_ANDROID + androidId)?.let { return Candidate(it, derived = true) }
        }
        hex(ID_SALT_BUILD + buildFingerprint())?.let { return Candidate(it, derived = true) }
        return Candidate(Utils.getUuid(), derived = false)
    }

    /**
     * The device's own unchanging description. Every field is a build-time constant of the ROM, so
     * the string is the same on every launch and every reinstall of the same handset, and it changes
     * only when the user flashes a different build — at which point they are, for our purposes,
     * on a different device anyway.
     */
    private fun buildFingerprint(): String = listOf(
        Build.MANUFACTURER,
        Build.BRAND,
        Build.DEVICE,
        Build.BOARD,
        Build.MODEL,
        Build.PRODUCT,
        Build.HARDWARE,
    ).joinToString("|") { it.orEmpty() }

    /** MD5 as 32 lowercase hex chars — the UUID-without-dashes shape the panel stores. */
    private fun hex(input: String): String? = try {
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    } catch (e: Throwable) {
        null
    }

    /** Persists a new session. No refresh token in this backend. */
    fun saveSession(token: String, expiresAt: Long? = null, user: UserProfileDto? = null) {
        val store = store() ?: return
        store.encode(KEY_TOKEN, token)
        if (expiresAt != null) store.encode(KEY_EXPIRES_AT, expiresAt) else store.remove(KEY_EXPIRES_AT)
        if (user != null) store.encode(KEY_USER, JsonUtil.toJson(user)) else store.remove(KEY_USER)
    }

    /** Updates just the cached user profile (keeps the current token). */
    fun saveUser(user: UserProfileDto) {
        store()?.encode(KEY_USER, JsonUtil.toJson(user))
    }

    fun getToken(): String? = store()?.decodeString(KEY_TOKEN)

    fun getExpiresAt(): Long = store()?.decodeLong(KEY_EXPIRES_AT, 0L) ?: 0L

    fun getUser(): UserProfileDto? {
        val json = store()?.decodeString(KEY_USER) ?: return null
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
        val json = store()?.decodeString(KEY_MANAGED_GUIDS) ?: return mutableMapOf()
        return try {
            gson.fromJson(json, mapType) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun setManagedGuids(map: Map<String, String>) {
        store()?.encode(KEY_MANAGED_GUIDS, gson.toJson(map))
    }

    /**
     * Ends the session but keeps the account's local footprint: the token, its expiry and the
     * cached profile go, the uuid->guid map stays.
     *
     * This is what an expired JWT gets. The 7-day token dying says nothing about the user's
     * subscriptions — they are still theirs, their servers still work, and their провайдеры must
     * still be on the device when they sign back in. Keeping the map is also what makes that
     * sign-in an *update*: the import reuses the stored guid per uuid, so the same провайдер is
     * refreshed in place instead of being re-added beside the old one.
     */
    fun clearSession() {
        val store = store() ?: run {
            // Best effort, and safe to be: while the store cannot be opened it reads as signed-out
            // anyway ([isLoggedIn] asks the same store), and the next getMe 401 runs this again
            // once it can. Log it rather than let a token that is still on disk look erased.
            LogUtil.w(AppConfig.TAG, "Auth store unavailable: session not cleared, will retry")
            return
        }
        store.remove(KEY_TOKEN)
        store.remove(KEY_EXPIRES_AT)
        store.remove(KEY_USER)
    }

    /**
     * Clears the session AND the managed-subscription references (explicit logout only).
     *
     * Called after [SubscriptionSyncManager.removeAllManaged] has actually removed those
     * subscriptions, so the map is dropped once it describes nothing. Keeps deviceId.
     *
     * @return true when the records were actually removed; false when the store could not be
     *         opened and the token is therefore STILL ON DISK. The caller must not report a
     *         sign-out it did not perform: a store that reopens later brings the old session back,
     *         and on a shared device that is someone else's account returning under a screen that
     *         said "signed out". [AccountSession.wipe] turns false into a failure the user can retry.
     */
    fun clear(): Boolean {
        val store = store() ?: return false
        store.remove(KEY_TOKEN)
        store.remove(KEY_EXPIRES_AT)
        store.remove(KEY_USER)
        store.remove(KEY_MANAGED_GUIDS)
        return true
    }
}
