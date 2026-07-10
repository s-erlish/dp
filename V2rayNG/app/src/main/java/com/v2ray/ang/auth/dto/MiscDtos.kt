package com.v2ray.ang.auth.dto

/**
 * Remaining client endpoints: upgrade quote, devices, promo codes, referral stats.
 *
 *  GET  /client/subscriptions/upgrade-quote?targetTariffId=… -> [UpgradeQuoteDto]
 *  GET  /client/devices?uuid=…                               -> [DevicesDto]
 *  POST /client/devices/delete                                (body [DeleteDeviceRequestDto])
 *  POST /client/promo-code/check                              -> [PromoDto]
 *  POST /client/promo-code/activate                           (body [PromoRequestDto])
 *  GET  /client/referral-stats                                -> [ReferralStatsDto]
 */

/** GET /client/subscriptions/upgrade-quote */
data class UpgradeQuoteDto(
    val amount: Double = 0.0,
    val effectiveDays: Int = 0,
    val currency: String = "",
)

/** GET /client/devices */
data class DevicesDto(
    val items: List<DeviceDto> = emptyList(),
)

/** A device bound to a subscription (HWID). */
data class DeviceDto(
    val hwid: String = "",
    val platform: String? = null,
    val deviceModel: String? = null,
    val appVersion: String? = null,
    val lastActiveAt: String? = null,
)

data class DeleteDeviceRequestDto(
    val hwid: String,
    val uuid: String,
)

// region promo codes

data class PromoRequestDto(
    val code: String,
)

/** POST /client/promo-code/check */
data class PromoDto(
    val type: String = "",
    val discountPercent: Double? = null,
    val durationDays: Int? = null,
)

// endregion

/** GET /client/referral-stats */
data class ReferralStatsDto(
    val referralCode: String = "",
    val referralPercent: Double = 0.0,
    val totalReferrals: Int = 0,
    val totalEarned: Double = 0.0,
    val currency: String = "",
)
