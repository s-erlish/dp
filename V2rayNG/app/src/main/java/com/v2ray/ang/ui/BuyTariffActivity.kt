package com.v2ray.ang.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.auth.ApiError
import com.v2ray.ang.auth.dto.PaymentInitDto
import com.v2ray.ang.auth.dto.PaymentRequestDto
import com.v2ray.ang.auth.dto.PriceOptionDto
import com.v2ray.ang.auth.dto.TariffDto
import com.v2ray.ang.auth.dto.TariffGroupDto
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.viewmodel.AccountViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * "Купить подписку" — the buy-subscription flow. The user picks a tariff, a duration/price
 * option and (optionally) extra devices, then taps «Оплатить» to choose a payment method.
 *
 * Data comes from [AccountViewModel]: [AccountViewModel.tariffs] + [AccountViewModel.loadTariffs]
 * for the catalog and [AccountViewModel.publicConfig] + [AccountViewModel.loadPublicConfig] for
 * the payment methods; [AccountViewModel.profile] provides the wallet balance shown in the sheet.
 */
class BuyTariffActivity : BaseActivity() {

    private val viewModel: AccountViewModel by viewModels()

    // Selection state.
    private var selectedTariff: TariffDto? = null
    private var selectedOption: PriceOptionDto? = null
    private var extraDevices: Int = 0

    // Per-tariff check marks so the whole list needn't be rebuilt on re-select.
    private val checkMarks = mutableMapOf<String, ImageView>()
    // Per-tariff option rows, so the selected row can be highlighted without a rebuild.
    private val optionRows = mutableMapOf<String, MutableList<Pair<PriceOptionDto, View>>>()

    // A purchase/balance payment was just fired: the NEXT error must surface as the
    // "Ошибка оплаты" diagnostic dialog (with the real backend detail), not be swallowed.
    private var awaitingPaymentError = false

    // True once the catalog request has completed at least once, so an empty list can be
    // distinguished from "still loading" (otherwise an empty catalog spins forever).
    private var loaded = false

    // A browser checkout was just launched: on return we re-poll for the webhook-confirmed result.
    private var pendingPayment = false
    private var pollJob: Job? = null

    private lateinit var progressBuy: ProgressBar
    private lateinit var skeleton: LinearLayout
    private lateinit var stateIcon: ImageView
    private lateinit var btnRetry: MaterialButton
    private lateinit var tvPending: TextView
    private lateinit var stateView: TextView
    private lateinit var tariffsHeader: TextView
    private lateinit var tariffsContainer: LinearLayout
    private lateinit var checkoutCard: MaterialCardView
    private lateinit var extraDevicesRow: LinearLayout
    private lateinit var tvExtraCount: TextView
    private lateinit var tvExtraCost: TextView
    private lateinit var btnDevMinus: MaterialButton
    private lateinit var btnDevPlus: MaterialButton
    private lateinit var tvTotal: TextView
    private lateinit var btnPay: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(R.layout.activity_buy_tariff, title = getString(R.string.buy_title))

        progressBuy = findViewById(R.id.progress_buy)
        skeleton = findViewById(R.id.ll_skeleton)
        stateIcon = findViewById(R.id.iv_state_icon)
        btnRetry = findViewById(R.id.btn_retry)
        tvPending = findViewById(R.id.tv_pending)
        stateView = findViewById(R.id.tv_state)
        tariffsHeader = findViewById(R.id.tv_tariffs_header)
        tariffsContainer = findViewById(R.id.ll_tariffs)
        checkoutCard = findViewById(R.id.card_checkout)
        extraDevicesRow = findViewById(R.id.ll_extra_devices)
        tvExtraCount = findViewById(R.id.tv_extra_devices_count)
        tvExtraCost = findViewById(R.id.tv_extra_devices_cost)
        btnDevMinus = findViewById(R.id.btn_dev_minus)
        btnDevPlus = findViewById(R.id.btn_dev_plus)
        tvTotal = findViewById(R.id.tv_total)
        btnPay = findViewById(R.id.btn_pay)

        btnDevMinus.setOnClickListener { changeExtraDevices(-1) }
        btnDevPlus.setOnClickListener { changeExtraDevices(+1) }
        btnPay.setOnClickListener { onPayClicked() }
        btnRetry.setOnClickListener { reload() }

        observe()
        reload()
    }

    /** (Re)fetch the catalog + payment config, showing the loading state until the first result. */
    private fun reload() {
        loaded = false
        viewModel.clearError()
        showBuyLoading()
        lifecycleScope.launch {
            viewModel.loadTariffs().join()
            loaded = true
            renderState()
        }
        viewModel.loadPublicConfig()
        viewModel.refreshProfile()
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.tariffs.combine(viewModel.error) { tariffs, error -> tariffs to error }
                .collect { renderState() }
        }
        // Surface payment failures. Without this the СБП/balance flow "does nothing" on error —
        // the request fails silently in the ViewModel and the user never learns why.
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                if (error != null && awaitingPaymentError) {
                    awaitingPaymentError = false
                    showPaymentErrorDialog(error)
                    viewModel.clearError()
                }
            }
        }
    }

    // region rendering

    /**
     * Single source of truth for the catalog state. An empty list is only "unavailable" once
     * [loaded] is set — before the first result completes it still reads as "loading", so an
     * empty catalog no longer spins forever.
     */
    private fun renderState() {
        val tariffs = viewModel.tariffs.value
        val error = viewModel.error.value
        val hasAny = tariffs.any { it.tariffs.isNotEmpty() }
        when {
            hasAny -> renderTariffs(tariffs)
            error != null -> showError()
            loaded -> showEmpty()
            else -> showBuyLoading()
        }
    }

    private fun showBuyLoading() {
        progressBuy.visibility = View.VISIBLE
        // Skeleton placeholder cards ride the loading state; the error/empty glyph stays hidden.
        skeleton.visibility = View.VISIBLE
        stateIcon.visibility = View.GONE
        btnRetry.visibility = View.GONE
        stateView.text = getString(R.string.buy_loading)
        stateView.visibility = View.VISIBLE
        tariffsHeader.visibility = View.GONE
    }

    private fun showError() {
        progressBuy.visibility = View.GONE
        skeleton.visibility = View.GONE
        stateIcon.visibility = View.VISIBLE
        btnRetry.visibility = View.VISIBLE
        stateView.text = getString(R.string.buy_error)
        stateView.visibility = View.VISIBLE
        tariffsHeader.visibility = View.GONE
    }

    private fun showEmpty() {
        progressBuy.visibility = View.GONE
        skeleton.visibility = View.GONE
        stateIcon.visibility = View.VISIBLE
        btnRetry.visibility = View.GONE
        stateView.text = getString(R.string.buy_empty)
        stateView.visibility = View.VISIBLE
        tariffsHeader.visibility = View.GONE
    }

    private fun hideState() {
        progressBuy.visibility = View.GONE
        // Tariffs rendered: retire both the skeleton and the state glyph.
        skeleton.visibility = View.GONE
        stateIcon.visibility = View.GONE
        btnRetry.visibility = View.GONE
        stateView.visibility = View.GONE
    }

    private fun renderTariffs(groups: List<TariffGroupDto>) {
        val hasAny = groups.any { it.tariffs.isNotEmpty() }
        if (!hasAny) {
            showEmpty()
            return
        }
        hideState()
        tariffsHeader.visibility = View.VISIBLE

        // Rebuild once; selection is then mutated in place.
        tariffsContainer.removeAllViews()
        checkMarks.clear()
        optionRows.clear()
        val inflater = LayoutInflater.from(this)

        groups.forEach { group ->
            group.tariffs.forEach { tariff ->
                addTariffCard(inflater, tariffsContainer, group.emoji, tariff)
            }
        }
    }

    private fun addTariffCard(
        inflater: LayoutInflater,
        parent: ViewGroup,
        emoji: String,
        tariff: TariffDto,
    ) {
        val card = inflater.inflate(R.layout.item_buy_tariff, parent, false) as MaterialCardView
        val header = card.findViewById<View>(R.id.header_tariff)
        val tvEmoji = card.findViewById<TextView>(R.id.tv_group_emoji)
        val tvName = card.findViewById<TextView>(R.id.tv_tariff_name)
        val tvInfo = card.findViewById<TextView>(R.id.tv_tariff_info)
        val ivCheck = card.findViewById<ImageView>(R.id.iv_check)
        val llOptions = card.findViewById<LinearLayout>(R.id.ll_price_options)

        // The design retired the emoji chrome: keep tv_group_emoji GONE in both branches so the
        // star/group glyph never shows, regardless of whether the group carries an emoji.
        if (emoji.isBlank()) {
            tvEmoji.visibility = View.GONE
        } else {
            tvEmoji.text = emoji
            tvEmoji.visibility = View.GONE
        }
        tvName.text = tariff.name

        val trafficStr = if (tariff.isUnlimitedTraffic() || (tariff.trafficLimitBytes ?: 0L) <= 0L) {
            getString(R.string.buy_unlimited)
        } else {
            formatBytes(tariff.trafficLimitBytes ?: 0L)
        }
        tvInfo.text = getString(R.string.buy_tariff_devices, tariff.includedDevices) +
            " · " + getString(R.string.buy_tariff_traffic, trafficStr)

        val key = tariffKey(tariff)
        checkMarks[key] = ivCheck

        // Build the duration/price option rows (hidden until the tariff is selected).
        val rows = mutableListOf<Pair<PriceOptionDto, View>>()
        val options = optionsOf(tariff)
        options.forEach { option ->
            val row = inflater.inflate(R.layout.item_buy_option, llOptions, false)
            val tvDur = row.findViewById<TextView>(R.id.tv_option_duration)
            val tvPrice = row.findViewById<TextView>(R.id.tv_option_price)
            tvDur.text = getString(R.string.buy_option_duration, option.durationDays)
            tvPrice.text = formatMoney(option.price, tariff.currency)
            row.setOnClickListener { selectOption(tariff, option) }
            llOptions.addView(row)
            rows.add(option to row)
        }
        optionRows[key] = rows

        card.setOnClickListener { selectTariff(tariff, card, llOptions) }
        header.setOnClickListener { selectTariff(tariff, card, llOptions) }

        parent.addView(card)
    }

    // endregion

    // region selection

    private fun selectTariff(tariff: TariffDto, card: MaterialCardView, llOptions: LinearLayout) {
        val alreadySelected = selectedTariff?.let { tariffKey(it) == tariffKey(tariff) } == true
        if (alreadySelected) return

        selectedTariff = tariff
        selectedOption = null
        extraDevices = 0

        // Reset every card to the neutral look, then highlight/expand this one.
        for (i in 0 until tariffsContainer.childCount) {
            val c = tariffsContainer.getChildAt(i) as? MaterialCardView ?: continue
            c.strokeColor = resolveAttrColor(com.google.android.material.R.attr.colorOutlineVariant)
            c.strokeWidth = dp(1)
            c.findViewById<View>(R.id.ll_price_options)?.visibility = View.GONE
        }
        checkMarks.forEach { (_, iv) -> iv.visibility = View.INVISIBLE }

        card.strokeColor = resolveAttrColor(androidx.appcompat.R.attr.colorPrimary)
        card.strokeWidth = dp(2)
        llOptions.visibility = View.VISIBLE
        checkMarks[tariffKey(tariff)]?.visibility = View.VISIBLE

        clearOptionHighlights(tariffKey(tariff))

        // A tariff with a single option → preselect it for a shorter flow.
        val options = optionsOf(tariff)
        if (options.size == 1) {
            selectOption(tariff, options.first())
        } else {
            checkoutCard.visibility = View.GONE
        }
    }

    private fun selectOption(tariff: TariffDto, option: PriceOptionDto) {
        selectedTariff = tariff
        selectedOption = option
        highlightOption(tariffKey(tariff), option)
        setupExtraDevices(tariff)
        checkoutCard.visibility = View.VISIBLE
        updateTotal()
    }

    private fun highlightOption(key: String, option: PriceOptionDto) {
        optionRows[key]?.forEach { (opt, view) ->
            val selected = optionKey(opt) == optionKey(option)
            view.setBackgroundResource(
                if (selected) R.drawable.bg_buy_option_selected else R.drawable.bg_buy_option
            )
        }
    }

    private fun clearOptionHighlights(key: String) {
        optionRows[key]?.forEach { (_, view) ->
            view.setBackgroundResource(R.drawable.bg_buy_option)
        }
    }

    private fun setupExtraDevices(tariff: TariffDto) {
        val max = tariff.maxExtraDevices
        if (max <= 0) {
            extraDevicesRow.visibility = View.GONE
        } else {
            extraDevicesRow.visibility = View.VISIBLE
        }
        extraDevices = extraDevices.coerceIn(0, maxOf(0, max))
        renderExtraDevices(tariff)
    }

    private fun changeExtraDevices(delta: Int) {
        val tariff = selectedTariff ?: return
        val max = tariff.maxExtraDevices
        extraDevices = (extraDevices + delta).coerceIn(0, maxOf(0, max))
        renderExtraDevices(tariff)
        updateTotal()
    }

    private fun renderExtraDevices(tariff: TariffDto) {
        tvExtraCount.text = getString(R.string.buy_extra_devices_count, extraDevices)

        // Make the stepper bounds visible: dim + disable the button that can't move further.
        val max = maxOf(0, tariff.maxExtraDevices)
        setStepperEnabled(btnDevMinus, extraDevices > 0)
        setStepperEnabled(btnDevPlus, extraDevices < max)

        val cost = extraDevices * tariff.pricePerExtraDevice
        if (extraDevices > 0 && cost > 0.0) {
            tvExtraCost.text = getString(
                R.string.buy_extra_devices_cost,
                formatMoney(cost, tariff.currency),
            )
            tvExtraCost.visibility = View.VISIBLE
        } else {
            tvExtraCost.visibility = View.GONE
        }
    }

    /**
     * The single source of truth for the price: the option price plus the cost of any extra
     * devices. This exact value is both shown as «Итого» and sent as the charged `amount`, so the
     * displayed total and the amount the user is charged can never drift apart. When
     * [extraDevices] is 0 (or [TariffDto.pricePerExtraDevice] is 0) this is just `option.price`.
     */
    private fun currentTotal(tariff: TariffDto, option: PriceOptionDto): Double =
        option.price + extraDevices * tariff.pricePerExtraDevice

    private fun updateTotal() {
        val tariff = selectedTariff ?: return
        val option = selectedOption ?: return
        tvTotal.text = formatMoney(currentTotal(tariff, option), tariff.currency)
    }

    // endregion

    // region checkout

    private fun onPayClicked() {
        val tariff = selectedTariff
        val option = selectedOption
        if (tariff == null || option == null) {
            toast(getString(R.string.buy_select_option_first))
            return
        }

        val methods = viewModel.publicConfig.value?.plategaMethods?.map { it.id to it.label } ?: emptyList()
        if (methods.isEmpty()) {
            toastError(getString(R.string.buy_no_methods))
            return
        }

        val profile = viewModel.profile.value
        val balanceLabel = profile?.let { formatMoney(it.balance, it.currency) }

        PaymentMethodSheet.show(
            supportFragmentManager,
            getString(R.string.buy_pick_method_title),
            balanceLabel,
            methods,
        ) { methodId -> onMethodPicked(tariff, option, methodId) }
    }

    private fun onMethodPicked(tariff: TariffDto, option: PriceOptionDto, methodId: String) {
        // Arm the diagnostic: whichever request we fire, a failure now becomes a visible dialog.
        awaitingPaymentError = true
        val deviceCount = extraDevices.takeIf { it > 0 }
        val currency = tariff.currency.ifBlank { "RUB" }
        // Charge exactly the displayed «Итого» (option price + extra devices), never just the
        // bare option price — otherwise the extra-device cost is silently dropped from checkout.
        val amount = currentTotal(tariff, option)
        if (methodId == PaymentMethodSheet.ID_BALANCE) {
            val req = PaymentRequestDto(
                tariffId = tariff.id,
                tariffPriceOptionId = option.id,
                deviceCount = deviceCount,
                amount = amount,
                currency = currency,
            )
            viewModel.payWithBalance(req) {
                awaitingPaymentError = false
                toastSuccess(getString(R.string.buy_success))
                finish()
            }
        } else {
            val req = PaymentRequestDto(
                tariffId = tariff.id,
                tariffPriceOptionId = option.id,
                deviceCount = deviceCount,
                amount = amount,
                currency = currency,
                paymentMethod = methodId.toIntOrNull(),
            )
            viewModel.buy(req) { init ->
                awaitingPaymentError = false
                openCheckout(init)
            }
        }
    }

    /** Dialog with the raw HTTP code + sanitized backend detail for a failed payment. */
    private fun showPaymentErrorDialog(error: ApiError) {
        val code = when (error) {
            is ApiError.Unauthorized -> "401/403"
            is ApiError.Server -> error.code.toString()
            is ApiError.RateLimited -> "429"
            is ApiError.ServiceUnavailable -> "502/503"
            is ApiError.NotFound -> "404"
            is ApiError.Gone -> "410"
            is ApiError.Timeout -> "timeout"
            is ApiError.Network -> "network"
            else -> "—"
        }
        val detail = when (error) {
            is ApiError.Unauthorized -> error.detail
            is ApiError.Server -> error.detail
            else -> null
        }
        val body = if (detail.isNullOrBlank()) {
            getString(R.string.account_payment_error_body_nodetail, code)
        } else {
            getString(R.string.account_payment_error_body, code, detail)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.account_payment_error_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Opens the provider checkout URL, mirroring AccountFragment.openCheckout. */
    private fun openCheckout(init: PaymentInitDto) {
        val url = init.paymentUrl
        if (url.isBlank()) {
            toastError(getString(R.string.buy_no_browser))
            return
        }
        val uri = Uri.parse(url)
        pendingPayment = true
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, uri)
            toast(getString(R.string.buy_checkout_return))
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                toast(getString(R.string.buy_checkout_return))
            } catch (e2: ActivityNotFoundException) {
                pendingPayment = false
                toastError(getString(R.string.buy_no_browser))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingPayment) startPaymentPolling()
    }

    /**
     * After returning from a browser checkout, re-poll profile/subscriptions a few times over ~40s
     * while showing a pending hint. The backend only confirms PAID via webhook, so the tab returning
     * proves nothing — mirrors [AccountFragment.startPaymentPolling].
     */
    private fun startPaymentPolling() {
        if (pollJob?.isActive == true) return
        tvPending.visibility = View.VISIBLE
        pollJob = lifecycleScope.launch {
            repeat(5) {
                viewModel.refreshProfile()
                viewModel.loadSubscriptions()
                delay(8000L)
            }
            pendingPayment = false
            tvPending.visibility = View.GONE
        }
    }

    // endregion

    // region helpers

    private fun optionsOf(tariff: TariffDto): List<PriceOptionDto> {
        val options = tariff.priceOptions.sortedBy { it.sortOrder }
        return if (options.isNotEmpty()) {
            options
        } else {
            // Fall back to the tariff's own duration/price as a single synthetic option.
            listOf(PriceOptionDto(id = tariff.id, durationDays = tariff.durationDays, price = tariff.price))
        }
    }

    private fun tariffKey(tariff: TariffDto): String =
        tariff.id.ifBlank { tariff.name }

    private fun optionKey(option: PriceOptionDto): String =
        option.id.ifBlank { "${option.durationDays}/${option.price}" }

    private fun setStepperEnabled(button: MaterialButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.4f
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun resolveAttrColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    // endregion

    companion object {
        private fun formatMoney(amount: Double, currency: String): String {
            val n = if (amount % 1.0 == 0.0) amount.toLong().toString()
            else String.format(Locale.US, "%.2f", amount)
            return if (currency.isBlank()) n else "$n ${currencySymbol(currency)}"
        }

        /** Maps an ISO currency code to a trailing symbol: RUB→₽, USD→$, EUR→€, KZT→₸, UAH→₴. */
        private fun currencySymbol(currency: String): String = when (currency.uppercase(Locale.US)) {
            "RUB" -> "₽"
            "USD" -> "$"
            "EUR" -> "€"
            "KZT" -> "₸"
            "UAH" -> "₴"
            else -> currency
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
    }
}
