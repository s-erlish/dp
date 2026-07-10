package com.v2ray.ang.ui.adapter

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.auth.dto.SubInfoDto
import com.v2ray.ang.databinding.ItemSubscriptionBinding
import java.util.Locale

/**
 * Lists the user's subscriptions. Tapping a card selects it as the "active" subscription
 * (its [SubInfoDto.remnawaveUuid] is used for upgrade / add-devices). The auto-renew switch
 * reports user-initiated toggles only.
 */
class AccountSubscriptionsAdapter(
    private val onClick: (SubInfoDto) -> Unit,
    private val onAutoRenew: (SubInfoDto, Boolean) -> Unit,
) : RecyclerView.Adapter<AccountSubscriptionsAdapter.VH>() {

    private val items = mutableListOf<SubInfoDto>()

    /** remnawaveUuid of the currently selected subscription (highlighted). */
    var activeUuid: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submit(list: List<SubInfoDto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemSubscriptionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sub = items[position]
        val ctx = holder.itemView.context
        val b = holder.binding

        val name = sub.displayName?.takeIf { it.isNotBlank() }
            ?: sub.tariffDisplayName?.takeIf { it.isNotBlank() }
            ?: ctx.getString(R.string.account_subs_header)
        b.tvSubName.text = name

        val tariff = sub.tariffDisplayName?.takeIf { it.isNotBlank() && it != name }
        if (tariff != null) {
            b.tvSubTariff.visibility = View.VISIBLE
            b.tvSubTariff.text = tariff
        } else {
            b.tvSubTariff.visibility = View.GONE
        }

        b.tvTrialBadge.visibility = if (sub.isTrial) View.VISIBLE else View.GONE

        // Expiry — never fabricate one when the backend didn't send it.
        b.tvExpiry.text = if (sub.expireAtIso.isNullOrBlank()) {
            ctx.getString(R.string.account_no_subscription)
        } else {
            ctx.getString(R.string.account_expires, formatIsoDate(sub.expireAtIso))
        }

        val raw = sub.subscription?.response

        // Devices
        val unlimitedDevices = raw?.isUnlimitedDevices() == true
        val totalDevicesStr = if (unlimitedDevices) ctx.getString(R.string.account_unlimited)
        else sub.totalDevices.toString()
        b.tvDevices.text = ctx.getString(
            R.string.account_devices,
            sub.connectedDevices.toString(),
            totalDevicesStr,
        )

        // Traffic — only meaningful when the raw subscription payload is present.
        if (raw != null) {
            b.tvTraffic.visibility = View.VISIBLE
            val used = formatBytes(raw.userTraffic.usedTrafficBytes)
            val limit = if (raw.isUnlimitedTraffic()) {
                ctx.getString(R.string.account_unlimited)
            } else {
                formatBytes(raw.trafficLimitBytes ?: 0L)
            }
            b.tvTraffic.text = ctx.getString(R.string.account_traffic, used, limit)
        } else {
            b.tvTraffic.visibility = View.GONE
        }

        // Auto-renew — set state without firing the listener for programmatic changes.
        b.switchAutoRenew.setOnCheckedChangeListener(null)
        b.switchAutoRenew.isChecked = sub.autoRenewEnabled
        b.switchAutoRenew.setOnCheckedChangeListener { btn, checked ->
            if (!btn.isPressed) return@setOnCheckedChangeListener
            onAutoRenew(sub, checked)
        }

        // Active highlight
        val isActive = sub.remnawaveUuid.isNotBlank() && sub.remnawaveUuid == activeUuid
        val strokeColor = if (isActive) {
            ContextCompat.getColor(ctx, R.color.icon_blue)
        } else {
            resolveThemeColor(ctx, com.google.android.material.R.attr.colorOutlineVariant)
        }
        b.cardSub.strokeColor = strokeColor
        b.cardSub.strokeWidth = if (isActive) dp(ctx, 2) else dp(ctx, 1)

        b.cardSub.setOnClickListener { onClick(sub) }
    }

    class VH(val binding: ItemSubscriptionBinding) : RecyclerView.ViewHolder(binding.root)
}

private fun dp(context: android.content.Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()

private fun formatIsoDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val datePart = iso.substringBefore('T')
    val parts = datePart.split('-')
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else datePart
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

private fun resolveThemeColor(context: android.content.Context, attr: Int): Int {
    val tv = TypedValue()
    context.theme.resolveAttribute(attr, tv, true)
    return tv.data
}
