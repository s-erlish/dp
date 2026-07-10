package com.v2ray.ang.auth.dto

/**
 * Payment endpoints of the Departament backend.
 *
 *  POST /client/payments/platega -> [PaymentInitDto]
 *  POST /client/payments/balance -> [PaymentResultDto]
 *  GET  /client/payments         -> [PaymentsDto]
 */

/**
 * Body for POST /client/payments/platega and /client/payments/balance.
 * All fields optional — the caller fills only what a given purchase needs.
 */
data class PaymentRequestDto(
    val amount: Double? = null,
    val tariffId: String? = null,
    val tariffPriceOptionId: String? = null,
    val deviceCount: Int? = null,
    val paymentMethod: String? = null,
    val promoCode: String? = null,
    val subscriptionUuid: String? = null,
)

/** Returned when a payment provider checkout URL is issued (Platega, add-devices, upgrade). */
data class PaymentInitDto(
    val paymentUrl: String = "",
    val paymentId: String = "",
    val orderId: String = "",
)

/** Returned by a balance (wallet) payment that settles immediately. */
data class PaymentResultDto(
    val status: String = "",
    val orderId: String = "",
)

/** GET /client/payments */
data class PaymentsDto(
    val items: List<PaymentDto> = emptyList(),
)

/** A single payment/order history entry. */
data class PaymentDto(
    val id: String = "",
    val orderId: String = "",
    val amount: Double = 0.0,
    val currency: String = "",
    val status: String = "",
    val provider: String = "",
    val kind: String = "",
    val description: String = "",
    val createdAt: String = "",
)
