package com.v2ray.ang.auth.dto

/**
 * Client subscription endpoints of the Departament backend.
 *
 *  GET   /client/subscription/all               -> [SubscriptionAllDto]
 *  PATCH /client/subscription/{scope}/{id}/name  (body [RenameRequestDto])
 *  GET   /client/subscription/qr?uuid=…          (PNG bytes)
 *  POST  /client/subscription/{scope}/{id}/add-devices (body [AddDevicesRequestDto])
 *  GET   /client/subscriptions/upgrade-quote?targetTariffId=…  -> [UpgradeQuoteDto]
 *  POST  /client/subscriptions/upgrade            (body [UpgradeRequestDto])
 */

/** GET /client/subscription/all */
data class SubscriptionAllDto(
    val items: List<SubInfoDto> = emptyList(),
)

/** A single subscription (root or secondary). */
data class SubInfoDto(
    /** "root" | "secondary" — used as the {scope} path segment. */
    val type: String = "root",
    val id: String = "",
    val remnawaveUuid: String = "",
    val subscription: SubResponseWrapper? = null,
    val tariffDisplayName: String? = null,
    val displayName: String? = null,
    val deviceCount: Int = 0,
    val totalDevices: Int = 0,
    val connectedDevices: Int = 0,
    val autoRenewEnabled: Boolean = false,
    val expireAtIso: String? = null,
    val isTrial: Boolean = false,
    val tariffPrice: Double? = null,
    val tariffCurrency: String? = null,
    val renewalPrice: Double? = null,
)

/** Wrapper around the Remnawave subscription payload. */
data class SubResponseWrapper(
    val response: RawSubDto? = null,
)

/** The raw Remnawave subscription record. */
data class RawSubDto(
    val subscriptionUrl: String = "",
    val hwidDeviceLimit: Int = 0,
    val trafficLimitBytes: Long? = null,
    val userTraffic: UserTrafficDto = UserTrafficDto(),
    val expireAt: String? = null,
    val status: String? = null,
) {
    /** trafficLimitBytes == null means an unlimited traffic plan. */
    fun isUnlimitedTraffic(): Boolean = trafficLimitBytes == null

    /** hwidDeviceLimit <= 0 means an unlimited device plan. */
    fun isUnlimitedDevices(): Boolean = hwidDeviceLimit <= 0
}

data class UserTrafficDto(
    val usedTrafficBytes: Long = 0L,
)

// region request bodies

data class RenameRequestDto(
    val name: String,
)

data class AddDevicesRequestDto(
    val extraDevices: Int,
    val method: String,
    val paymentMethod: String? = null,
)

data class UpgradeRequestDto(
    val targetTariffId: String,
    val method: String,
    val paymentMethod: String? = null,
    val subscriptionUuid: String,
)

data class AutoRenewRequestDto(
    val autoRenew: Boolean,
)

// endregion
