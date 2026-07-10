package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.auth.dto.SubInfoDto
import com.v2ray.ang.databinding.ItemSubscriptionCardBinding

/**
 * One subscription per ViewPager2 page. Ports the active-sub rendering that used to live inline in
 * [AccountFragment]'s updateActiveSubUi: the subscription name, the expiry line (via the shared date
 * formatter), the "n / m" device figure (∞ when the plan is unlimited), and the Base/Plus tariff
 * badge.
 *
 * The badge text is resolved by the fragment (which owns the tariff catalog) and passed in via
 * [resolveBadge]; a null/blank result HIDES the badge so a wrong tariff is never shown. The live
 * "used" device count (GET /client/devices) likewise lives on the fragment's ViewModel and is
 * passed in via [resolveUsedDevices], keeping this adapter free of any ViewModel dependency.
 */
class SubscriptionPagerAdapter(
    private val resolveBadge: (SubInfoDto) -> String?,
    private val resolveUsedDevices: (SubInfoDto) -> Int,
) : RecyclerView.Adapter<SubscriptionPagerAdapter.VH>() {

    private val items = mutableListOf<SubInfoDto>()

    /** Replaces the pages with [list] and repaints. */
    fun submit(list: List<SubInfoDto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSubscriptionCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val binding: ItemSubscriptionCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(sub: SubInfoDto) {
            val ctx = binding.root.context

            // Name: user label, then the friendly tariff name, then the backend default, then a
            // neutral header so the card is never blank.
            binding.tvSubName.text = sub.displayName?.takeIf { it.isNotBlank() }
                ?: sub.tariffDisplayName?.takeIf { it.isNotBlank() }
                ?: sub.defaultLabel?.takeIf { it.isNotBlank() }
                ?: ctx.getString(R.string.account_subs_header)

            // Tariff badge (Base/Plus): the fragment resolves it from the catalog; hide on null/blank
            // so a stale/unknown tariff never shows a wrong badge.
            val badge = resolveBadge(sub)
            if (badge.isNullOrBlank()) {
                binding.tvTariffBadge.visibility = View.GONE
            } else {
                binding.tvTariffBadge.text = badge
                binding.tvTariffBadge.visibility = View.VISIBLE
            }

            if (sub.expireAtIso.isNullOrBlank()) {
                binding.tvSubExpiry.visibility = View.GONE
            } else {
                binding.tvSubExpiry.visibility = View.VISIBLE
                binding.tvSubExpiry.text =
                    ctx.getString(R.string.account_expires, formatIsoDate(sub.expireAtIso))
            }

            // Used = the live connected-device count resolved by the fragment; limit = total slots,
            // or ∞ when the plan is unlimited.
            val unlimitedDevices = sub.subscription?.raw()?.isUnlimitedDevices() == true
            val totalDevicesStr = if (unlimitedDevices) {
                ctx.getString(R.string.account_unlimited)
            } else {
                sub.totalDevices.toString()
            }
            val usedDevices = resolveUsedDevices(sub)
            binding.tvSubDevices.text =
                ctx.getString(R.string.account_devices, usedDevices.toString(), totalDevicesStr)
        }
    }
}

/**
 * Formats an ISO-8601 timestamp as dd.MM.yyyy (a file-private copy of the fragment's helper so this
 * adapter carries no dependency on it). Returns "" for blank/unparseable input.
 */
private fun formatIsoDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val datePart = iso.substringBefore('T')
    val parts = datePart.split('-')
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else datePart
}
