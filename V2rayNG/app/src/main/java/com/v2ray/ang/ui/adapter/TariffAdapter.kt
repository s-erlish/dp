package com.v2ray.ang.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.auth.dto.PriceOptionDto
import com.v2ray.ang.auth.dto.TariffDto
import com.v2ray.ang.auth.dto.TariffGroupDto
import com.v2ray.ang.databinding.ItemPriceOptionBinding
import com.v2ray.ang.databinding.ItemTariffBinding
import java.util.Locale

/**
 * Flattens the grouped tariff catalog into one collapsible card per tariff. Each card shows a
 * compact header (emoji + name + devices/traffic summary + chevron); tapping it expands the
 * duration/price options. Options are collapsed by default and the expanded set is tracked here.
 */
class TariffAdapter(
    private val onBuy: (tariff: TariffDto, priceOption: PriceOptionDto?) -> Unit,
) : RecyclerView.Adapter<TariffAdapter.VH>() {

    /** One flattened catalog row: a tariff together with its group label. */
    data class Row(val groupName: String, val groupEmoji: String, val tariff: TariffDto)

    private val rows = mutableListOf<Row>()

    /** Stable keys of the tariffs whose price options are currently expanded. */
    private val expandedKeys = mutableSetOf<String>()

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
        val key = keyOf(row)

        // Emoji glyph (hidden when the group has none).
        if (row.groupEmoji.isBlank()) {
            b.tvGroupEmoji.visibility = View.GONE
        } else {
            b.tvGroupEmoji.visibility = View.VISIBLE
            b.tvGroupEmoji.text = row.groupEmoji
        }

        b.tvTariffName.text = tariff.name

        val limitBytes = tariff.trafficLimitBytes ?: 0L
        val trafficStr = if (tariff.isUnlimitedTraffic() || limitBytes <= 0L) {
            ctx.getString(R.string.account_unlimited)
        } else {
            formatBytes(limitBytes)
        }
        b.tvTariffInfo.text = ctx.getString(R.string.account_tariff_devices, tariff.includedDevices) +
            " · " + ctx.getString(R.string.account_tariff_traffic, trafficStr)

        val isExpanded = expandedKeys.contains(key)
        b.llPriceOptions.visibility = if (isExpanded) View.VISIBLE else View.GONE
        b.ivChevron.rotation = if (isExpanded) 90f else 0f

        // Rebuild the price-option rows (views are recycled).
        b.llPriceOptions.removeAllViews()
        val options = tariff.priceOptions.sortedBy { it.sortOrder }
        if (options.isEmpty()) {
            addOptionRow(b.llPriceOptions, tariff.durationDays, tariff.price, tariff.currency) {
                onBuy(tariff, null)
            }
        } else {
            options.forEach { option ->
                addOptionRow(b.llPriceOptions, option.durationDays, option.price, tariff.currency) {
                    onBuy(tariff, option)
                }
            }
        }

        b.headerTariff.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (expandedKeys.contains(key)) expandedKeys.remove(key) else expandedKeys.add(key)
            notifyItemChanged(pos)
        }
    }

    private fun addOptionRow(
        container: ViewGroup,
        durationDays: Int,
        price: Double,
        currency: String,
        onClick: () -> Unit,
    ) {
        val ctx = container.context
        val ob = ItemPriceOptionBinding.inflate(LayoutInflater.from(ctx), container, false)
        ob.tvOptionDuration.text = ctx.getString(R.string.account_option_duration, durationDays)
        ob.tvOptionPrice.text = formatMoney(price, currency)
        ob.root.setOnClickListener { onClick() }
        container.addView(ob.root)
    }

    private fun keyOf(row: Row): String =
        row.tariff.id.ifBlank { row.groupName + "/" + row.tariff.name }

    class VH(val binding: ItemTariffBinding) : RecyclerView.ViewHolder(binding.root)
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
