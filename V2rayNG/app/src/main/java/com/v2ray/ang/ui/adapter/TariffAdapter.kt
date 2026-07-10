package com.v2ray.ang.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.v2ray.ang.R
import com.v2ray.ang.auth.dto.PriceOptionDto
import com.v2ray.ang.auth.dto.TariffDto
import com.v2ray.ang.auth.dto.TariffGroupDto
import com.v2ray.ang.databinding.ItemTariffBinding
import java.util.Locale

/**
 * Flattens the grouped tariff catalog into one card per tariff. Each duration/price option
 * becomes a tappable button; tapping it starts a purchase for that (tariff, priceOption).
 */
class TariffAdapter(
    private val onBuy: (tariff: TariffDto, priceOption: PriceOptionDto?) -> Unit,
) : RecyclerView.Adapter<TariffAdapter.VH>() {

    /** One flattened catalog row: a tariff together with its group label. */
    data class Row(val groupName: String, val groupEmoji: String, val tariff: TariffDto)

    private val rows = mutableListOf<Row>()

    fun submit(groups: List<TariffGroupDto>) {
        rows.clear()
        groups.forEach { group ->
            group.tariffs.forEach { tariff ->
                rows.add(Row(group.name, group.emoji, tariff))
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemTariffBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        val tariff = row.tariff
        val ctx = holder.itemView.context
        val b = holder.binding

        b.tvGroup.text = listOf(row.groupEmoji, row.groupName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        b.tvGroup.visibility = if (b.tvGroup.text.isNullOrBlank()) android.view.View.GONE
        else android.view.View.VISIBLE

        b.tvTariffName.text = tariff.name

        val trafficStr = if (tariff.isUnlimitedTraffic()) {
            ctx.getString(R.string.account_unlimited)
        } else {
            formatBytes(tariff.trafficLimitBytes ?: 0L)
        }
        b.tvTariffInfo.text = ctx.getString(R.string.account_tariff_devices, tariff.includedDevices) +
            " · " + ctx.getString(R.string.account_tariff_traffic, trafficStr)

        // Rebuild the price-option buttons (views are recycled).
        b.llPriceOptions.removeAllViews()
        if (tariff.priceOptions.isEmpty()) {
            addOptionButton(
                b.llPriceOptions,
                ctx,
                tariff.durationDays,
                tariff.price,
                tariff.currency,
            ) { onBuy(tariff, null) }
        } else {
            tariff.priceOptions.sortedBy { it.sortOrder }.forEach { option ->
                addOptionButton(
                    b.llPriceOptions,
                    ctx,
                    option.durationDays,
                    option.price,
                    tariff.currency,
                ) { onBuy(tariff, option) }
            }
        }
    }

    private fun addOptionButton(
        container: LinearLayout,
        ctx: Context,
        durationDays: Int,
        price: Double,
        currency: String,
        onClick: () -> Unit,
    ) {
        val button = MaterialButton(ctx).apply {
            text = ctx.getString(
                R.string.account_price_option,
                durationDays,
                formatMoney(price, currency),
            )
            setAllCaps(false)
            cornerRadius = dp(ctx, 22)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp(ctx, 6)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
        container.addView(button)
    }

    class VH(val binding: ItemTariffBinding) : RecyclerView.ViewHolder(binding.root)
}

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()

private fun formatMoney(amount: Double, currency: String): String {
    val n = if (amount % 1.0 == 0.0) amount.toLong().toString()
    else String.format(Locale.US, "%.2f", amount)
    return if (currency.isBlank()) n else "$n $currency"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024.0 && idx < units.size - 1) {
        value /= 1024.0
        idx++
    }
    val formatted = if (idx == 0) value.toLong().toString()
    else String.format(Locale.US, "%.1f", value)
    return "$formatted ${units[idx]}"
}
