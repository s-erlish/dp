package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.v2ray.ang.R
import com.v2ray.ang.auth.dto.SubInfoDto
import com.v2ray.ang.databinding.ItemSubscriptionCardBinding
import java.util.Locale

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
    private val onRenew: (SubInfoDto) -> Unit,
    private val onOpenDevices: (SubInfoDto) -> Unit,
    private val onAutoRenew: (SubInfoDto, Boolean) -> Unit,
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
            binding.rowSubDevices.setOnClickListener { onOpenDevices(sub) }

            bindTrafficMeter(sub)

            // «Продлить» is ALWAYS offered, including on a perpetual подписка — «она должна быть и
            // при бессрочной подписке». There is nothing about an open-ended plan that makes
            // extending it meaningless, and a card with no action reads as a dead end.
            binding.btnSubRenew.setOnClickListener { onRenew(sub) }

            bindAutoRenew(sub)
        }

        /**
         * THE TRAFFIC PILL, FINISHED. `view_meter.xml` has always described four states; this bound
         * two of them, and the owner read the result as unfinished — «пилюля не сделана, надо
         * дорабатывать чтобы было нормальное отображение и понятное для пользователя». All four are
         * bound now, and every one of them leaves a figure on the card:
         *
         *  - under quota  bar at the real fraction, «12,4 из 50 ГБ», the accent fill;
         *  - OVER quota   bar full, «Лимит исчерпан», AND THE FILL AND THE FIGURE GO RED. This is
         *                 what view_meter.xml documents («over quota (>= 100%) fill ->
         *                 ?attr/colorError and the value says «Лимит исчерпан»») and what nothing
         *                 implemented: the value changed, the bar stayed accent-blue and read as a
         *                 healthy full tank. An exhausted allowance now looks exhausted;
         *  - unlimited    no bar — a bar with no ceiling is a lie — and «1,9 ТБ · безлимит»;
         *  - NO DATA      the label and «Нет данных», with no bar. It used to hide the whole meter,
         *                 which on a SECONDARY подписка is every single card: /client/subscription/all
         *                 carries no connect payload at all, so `raw` is null for anything that is not
         *                 the root, and the card simply had a hole where the pill belongs. A stated
         *                 absence is finished; a silent gap is the thing he was looking at.
         *
         * Both colours are re-applied on EVERY bind, never only in the branch that needs them: this
         * is a recycled holder, and a card that inherited the previous подписка's red fill would
         * report a limit that is not its own.
         */
        private fun bindTrafficMeter(sub: SubInfoDto) {
            val ctx = binding.root.context
            val meter = binding.meterTraffic
            val raw = sub.subscription?.raw()
            val used: Long = raw?.trafficUsed ?: raw?.userTraffic?.usedTrafficBytes ?: 0L
            val limit: Long? = raw?.trafficLimitBytes

            meter.meter.visibility = View.VISIBLE
            meter.meterLabel.setText(R.string.account_meter_traffic)

            val accent = MaterialColors.getColor(meter.meterBar, androidx.appcompat.R.attr.colorPrimary)
            val onSurface = MaterialColors.getColor(
                meter.meterValue, com.google.android.material.R.attr.colorOnSurface
            )
            val danger = ContextCompat.getColor(ctx, R.color.color_destructive_text)

            if (raw == null || (used <= 0L && limit == null)) {
                meter.meterBar.visibility = View.GONE
                meter.meterValue.setText(R.string.account_meter_traffic_none)
                meter.meterValue.setTextColor(onSurface)
                return
            }

            if (limit == null || limit <= 0L) {
                meter.meterBar.visibility = View.GONE
                meter.meterValue.text =
                    ctx.getString(R.string.account_meter_traffic_unlimited, formatBytes(used))
                meter.meterValue.setTextColor(onSurface)
                return
            }

            meter.meterBar.visibility = View.VISIBLE
            val pct = ((used.toDouble() / limit.toDouble()) * 100).toInt().coerceIn(0, 100)
            meter.meterBar.setProgressCompat(pct, false)
            val exhausted = used >= limit
            meter.meterBar.setIndicatorColor(if (exhausted) danger else accent)
            meter.meterValue.setTextColor(if (exhausted) danger else onSurface)
            meter.meterValue.text = if (exhausted) {
                ctx.getString(R.string.account_meter_traffic_over)
            } else {
                ctx.getString(
                    R.string.account_meter_traffic_of,
                    formatBytes(used),
                    formatBytes(limit),
                )
            }
        }

        /**
         * The auto-renew switch and the line that says what it will do next.
         *
         * The listener is detached before the state is written and re-attached after, because a
         * recycled holder is re-bound with `setChecked` and an attached listener would report that
         * as a user decision — firing a PATCH nobody asked for, on whichever подписка the holder
         * has just been pointed at.
         */
        private fun bindAutoRenew(sub: SubInfoDto) {
            val ctx = binding.root.context
            binding.switchSubAutorenew.setOnCheckedChangeListener(null)
            binding.switchSubAutorenew.isChecked = sub.autoRenewEnabled
            binding.tvSubAutorenew.text = when {
                !sub.autoRenewEnabled -> ctx.getString(R.string.account_sub_autorenew_off)
                sub.renewalPrice != null && !sub.expireAtIso.isNullOrBlank() -> ctx.getString(
                    R.string.account_sub_autorenew_next,
                    formatIsoDate(sub.expireAtIso),
                    formatMoney(sub.renewalPrice, sub.tariffCurrency),
                )

                else -> ctx.getString(R.string.account_sub_autorenew_on)
            }
            binding.switchSubAutorenew.setOnCheckedChangeListener { _, checked ->
                onAutoRenew(sub, checked)
            }
        }
    }
}

/**
 * Bytes as the product writes them: Russian units, one decimal past kilobytes. A file-private copy
 * for the same reason [formatIsoDate] is one — this adapter depends on no screen.
 */
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
    else String.format(Locale.US, "%.1f", value).replace('.', ',')
    return "$formatted ${units[idx]}"
}

/** ₽ by default — the product's currency (owner ruling); anything else prints its own code. */
private fun formatMoney(amount: Double, currency: String?): String {
    val symbol = when (currency?.uppercase()) {
        null, "", "RUB", "RUR" -> "₽"
        "USD" -> "$"
        "EUR" -> "€"
        else -> currency
    }
    val whole = amount.toLong()
    val text = if (amount == whole.toDouble()) whole.toString()
    else String.format(Locale.US, "%.2f", amount).replace('.', ',')
    return "$text $symbol"
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
