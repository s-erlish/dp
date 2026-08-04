package com.v2ray.ang.ui.adapter

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.auth.dto.PaymentDto
import com.v2ray.ang.databinding.ItemPaymentBinding
import java.util.Locale

/**
 * Renders the account payment/order history as rows inside ONE card (handoff README §7).
 *
 * THE STATUS IS PART OF THE SENTENCE. The prototype writes «03.08.2026 · Оплачено» on the row's
 * second line and dims the AMOUNT of anything that has not settled; this used to be a coloured chip
 * in a second right-hand column, which made every row three lines tall and gave a read-only ledger
 * four accent colours. Nothing about the status is lost:
 *
 *  - all four states are still NAMED, which is what the user reads;
 *  - a FAILED one is still coloured, because a payment that did not go through is the only thing on
 *    this screen worth interrupting for, and destructive red is the app's word for it;
 *  - anything not yet settled still dims its sum, which is the design's own signal that the figure
 *    is provisional.
 */
class PaymentsAdapter : RecyclerView.Adapter<PaymentsAdapter.VH>() {

    private val items = mutableListOf<PaymentDto>()

    fun submit(list: List<PaymentDto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        val b = holder.binding

        b.tvPaymentDesc.text = item.description.ifBlank { item.kind.ifBlank { item.orderId } }

        // «03.08.2026 · Оплачено» — one muted line, and either half may be missing without
        // leaving a stray separator behind.
        val state = statusStyle(item.status)
        val statusText = if (state.labelRes != 0) ctx.getString(state.labelRes) else item.status
        b.tvPaymentDate.text = listOf(formatIsoDate(item.createdAt), statusText)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        b.tvPaymentDate.setTextColor(
            if (state.failed) {
                ContextCompat.getColor(ctx, R.color.color_destructive_text)
            } else {
                resolveThemeColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant)
            },
        )

        b.tvPaymentAmount.text = formatMoney(item.amount, item.currency)
        // An unsettled sum is provisional, and the design says so by dimming it rather than by
        // adding a fifth colour to a ledger.
        b.tvPaymentAmount.setTextColor(
            resolveThemeColor(
                ctx,
                if (state.settled) {
                    com.google.android.material.R.attr.colorOnSurface
                } else {
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                },
            ),
        )

        // A card of rows needs a rule between them and the card cannot draw one; never above
        // the first row, which would cut a line across the card's own top edge.
        b.paymentDivider.visibility = if (position == 0) View.GONE else View.VISIBLE
    }

    class VH(val binding: ItemPaymentBinding) : RecyclerView.ViewHolder(binding.root)
}

/**
 * What a raw backend status means to this row: the word to print, whether the money is actually
 * gone (a settled sum is stated at full strength) and whether it went wrong (the one case that
 * still earns a colour).
 *
 * [labelRes] 0 means the backend sent something this build does not know; the raw value is printed
 * rather than swallowed, and it is treated as unsettled, which is the safe direction — a sum shown
 * at full strength is a claim that it was charged.
 */
private data class PaymentState(val labelRes: Int, val settled: Boolean, val failed: Boolean)

private fun statusStyle(status: String): PaymentState = when (status.lowercase(Locale.US)) {
    "paid", "success", "succeeded", "completed", "confirmed" ->
        PaymentState(R.string.account_status_paid, settled = true, failed = false)

    "pending", "processing", "new", "created", "waiting", "in_progress" ->
        PaymentState(R.string.account_status_pending, settled = false, failed = false)

    "failed", "error", "declined", "rejected" ->
        PaymentState(R.string.account_status_failed, settled = false, failed = true)

    "canceled", "cancelled", "expired" ->
        PaymentState(R.string.account_status_canceled, settled = false, failed = false)

    else -> PaymentState(0, settled = false, failed = false)
}

private fun formatMoney(amount: Double, currency: String): String {
    val n = if (amount % 1.0 == 0.0) amount.toLong().toString()
    else String.format(Locale.US, "%.2f", amount)
    if (currency.isBlank()) return n
    val symbol = when (currency.uppercase(Locale.US)) {
        "RUB" -> "₽"
        "USD" -> "$"
        "EUR" -> "€"
        "KZT" -> "₸"
        "UAH" -> "₴"
        else -> currency
    }
    return "$n $symbol"
}

private fun formatIsoDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val datePart = iso.substringBefore('T')
    val parts = datePart.split('-')
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else datePart
}

private fun resolveThemeColor(context: android.content.Context, attr: Int): Int {
    val tv = TypedValue()
    context.theme.resolveAttribute(attr, tv, true)
    return tv.data
}
