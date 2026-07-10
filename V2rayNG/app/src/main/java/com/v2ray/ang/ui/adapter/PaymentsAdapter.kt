package com.v2ray.ang.ui.adapter

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.auth.dto.PaymentDto
import com.v2ray.ang.databinding.ItemPaymentBinding
import java.util.Locale

/**
 * Renders the account payment/order history. Read-only list; each row shows the
 * description, date, amount and a coloured status chip.
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
        b.tvPaymentDate.text = formatIsoDate(item.createdAt)
        b.tvPaymentAmount.text = formatMoney(item.amount, item.currency)

        val (labelRes, colorRes) = statusStyle(item.status)
        if (labelRes != 0) {
            b.tvPaymentStatus.setText(labelRes)
        } else {
            b.tvPaymentStatus.text = item.status
        }
        // Subtle status chip: full-strength text over a faint tint of the same hue. Unmapped
        // statuses fall back to the neutral surface-variant chip.
        if (colorRes != 0) {
            val color = ContextCompat.getColor(ctx, colorRes)
            b.tvPaymentStatus.setTextColor(color)
            b.tvPaymentStatus.backgroundTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x24))
        } else {
            b.tvPaymentStatus.setTextColor(
                resolveThemeColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant),
            )
            b.tvPaymentStatus.backgroundTintList = ColorStateList.valueOf(
                resolveThemeColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant),
            )
        }
    }

    class VH(val binding: ItemPaymentBinding) : RecyclerView.ViewHolder(binding.root)
}

/** Maps a raw payment status to a (string res, colour res) pair. 0 means "no mapping". */
private fun statusStyle(status: String): Pair<Int, Int> = when (status.lowercase(Locale.US)) {
    "paid", "success", "succeeded", "completed", "confirmed" ->
        R.string.account_status_paid to R.color.icon_green

    "pending", "processing", "new", "created", "waiting", "in_progress" ->
        R.string.account_status_pending to R.color.icon_orange

    "failed", "error", "declined", "rejected" ->
        R.string.account_status_failed to R.color.icon_red

    "canceled", "cancelled", "expired" ->
        R.string.account_status_canceled to R.color.icon_yellow

    else -> 0 to 0
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
