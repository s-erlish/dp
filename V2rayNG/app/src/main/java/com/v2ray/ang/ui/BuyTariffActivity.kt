package com.v2ray.ang.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
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
import com.v2ray.ang.util.reducedMotion
import com.v2ray.ang.viewmodel.AccountViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * «Купить подписку» — the buy-subscription flow. The user picks a tariff, a duration/price
 * option and (optionally) extra devices, then taps the CTA to choose a payment method.
 *
 * Data comes from [AccountViewModel]: [AccountViewModel.tariffs] + [AccountViewModel.loadTariffs]
 * for the catalog and [AccountViewModel.publicConfig] + [AccountViewModel.loadPublicConfig] for
 * the payment methods; [AccountViewModel.profile] provides the wallet balance shown in the sheet.
 *
 * **The selection is held as KEYS, not as objects** (D09/D11). Every re-render of the catalog
 * rebuilds the card views, and a rotation rebuilds the whole activity; an object reference kept
 * across either is a reference to a card nobody can see. Holding `selectedTariffKey` /
 * `selectedOptionKey` and re-deriving the highlighted views from them in [applySelection] means a
 * catalog re-emission (which a payment error causes, because the error is combined into the same
 * render) repaints the same selection instead of quietly dropping it while the checkout card kept
 * offering to charge for it.
 */
class BuyTariffActivity : BaseActivity() {

    private val viewModel: AccountViewModel by viewModels()

    // Selection state — keys, resolved against the live catalog on demand (see the class comment).
    private var selectedTariffKey: String? = null
    private var selectedOptionKey: String? = null
    private var extraDevices: Int = 0

    // The rendered catalog, indexed the same way the selection is keyed.
    private val tariffsByKey = LinkedHashMap<String, TariffDto>()
    private val cardViews = LinkedHashMap<String, MaterialCardView>()
    private val optionContainers = LinkedHashMap<String, LinearLayout>()
    // Per-tariff check marks so the whole list needn't be rebuilt on re-select.
    private val checkMarks = mutableMapOf<String, ImageView>()
    // Per-tariff option rows, so the selected row can be highlighted without a rebuild.
    private val optionRows = mutableMapOf<String, MutableList<Pair<PriceOptionDto, View>>>()

    // Shape of the catalog currently on screen. An identical re-emission is repainted, not rebuilt.
    private var renderedSignature: String? = null

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

        savedInstanceState?.let {
            selectedTariffKey = it.getString(STATE_TARIFF_KEY)
            selectedOptionKey = it.getString(STATE_OPTION_KEY)
            extraDevices = it.getInt(STATE_EXTRA_DEVICES, 0)
            pendingPayment = it.getBoolean(STATE_PENDING_PAYMENT, false)
            awaitingPaymentError = it.getBoolean(STATE_AWAITING_ERROR, false)
        }

        btnDevMinus.setOnClickListener { changeExtraDevices(-1) }
        btnDevPlus.setOnClickListener { changeExtraDevices(+1) }
        btnPay.setOnClickListener { onPayClicked() }
        btnRetry.setOnClickListener { reload() }

        // The method pick comes back as DATA, on this activity's own FragmentManager, and the
        // listener is registered by whichever instance is alive (D11). The sheet used to hand its
        // answer to a lambda that had captured the activity which opened it — after a rotation that
        // activity is destroyed, and picking a method crashed the app.
        supportFragmentManager.setFragmentResultListener(REQUEST_PAY_METHOD, this) { _, bundle ->
            onMethodPicked(bundle)
        }

        observe()
        reload()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TARIFF_KEY, selectedTariffKey)
        outState.putString(STATE_OPTION_KEY, selectedOptionKey)
        outState.putInt(STATE_EXTRA_DEVICES, extraDevices)
        outState.putBoolean(STATE_PENDING_PAYMENT, pendingPayment)
        outState.putBoolean(STATE_AWAITING_ERROR, awaitingPaymentError)
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
        // The in-flight flag lives on the ViewModel, which is retained across a rotation, so the
        // busy CTA comes back busy on the other side of one (D10).
        lifecycleScope.launch {
            viewModel.paymentInFlight.collect { renderPayBusy(it) }
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
        // One loading signal, not three. The skeleton silhouette IS the loading affordance, so the
        // circular spinner and the "Загрузка" label are NOT stacked on top of it — that redundancy
        // (spinner + label + skeleton, then a hard pop to real cards) is what read as janky. The
        // glyph + label are reserved for the skeleton-less states (error/empty) below.
        stateIcon.visibility = View.GONE
        btnRetry.visibility = View.GONE
        stateView.visibility = View.GONE
        tariffsHeader.visibility = View.GONE
        // Reset alpha in case a prior skeleton→content cross-fade was interrupted by a reload.
        skeleton.alpha = 1f
        skeleton.visibility = View.VISIBLE
    }

    private fun showError() {
        skeleton.visibility = View.GONE
        stateIcon.visibility = View.VISIBLE
        btnRetry.visibility = View.VISIBLE
        stateView.text = getString(R.string.buy_error)
        stateView.visibility = View.VISIBLE
        tariffsHeader.visibility = View.GONE
    }

    private fun showEmpty() {
        skeleton.visibility = View.GONE
        stateIcon.visibility = View.VISIBLE
        btnRetry.visibility = View.GONE
        stateView.text = getString(R.string.buy_empty)
        stateView.visibility = View.VISIBLE
        tariffsHeader.visibility = View.GONE
    }

    /**
     * Shape of the catalog: which tariffs, in which order, each with which options. Two renders
     * with the same signature would build byte-identical cards, so the second one repaints the
     * selection instead of tearing the list down and putting it back (D09).
     */
    private fun signatureOf(groups: List<TariffGroupDto>): String =
        groups.joinToString("|") { group ->
            group.tariffs.joinToString(",") { tariff ->
                tariffKey(tariff) + "#" + optionsOf(tariff).joinToString("+") { optionKey(it) }
            }
        }

    private fun renderTariffs(groups: List<TariffGroupDto>) {
        val hasAny = groups.any { it.tariffs.isNotEmpty() }
        if (!hasAny) {
            showEmpty()
            return
        }

        // The state glyph/label never coexist with real content — retire them immediately.
        stateIcon.visibility = View.GONE
        btnRetry.visibility = View.GONE
        stateView.visibility = View.GONE

        val signature = signatureOf(groups)
        if (signature == renderedSignature && tariffsContainer.childCount > 0) {
            // Same catalog, re-emitted. This is the path a payment error takes: the error is
            // combined into this render, and rebuilding here is exactly what used to reset every
            // card to neutral while the checkout card went on offering the invisible selection.
            tariffsHeader.visibility = View.VISIBLE
            skeleton.visibility = View.GONE
            applySelection()
            return
        }

        // Was the skeleton what the user is currently looking at? Only then do we cross-fade;
        // a plain re-render (the catalog flow re-emits after content is already up) swaps in place.
        val fromSkeleton = skeleton.visibility == View.VISIBLE

        // Build the real cards FIRST so the fade reveals a fully-rendered list, not a blank frame.
        // Rebuild once; selection is then mutated in place.
        tariffsHeader.visibility = View.VISIBLE
        tariffsContainer.removeAllViews()
        tariffsByKey.clear()
        cardViews.clear()
        optionContainers.clear()
        checkMarks.clear()
        optionRows.clear()
        val inflater = LayoutInflater.from(this)

        groups.forEach { group ->
            group.tariffs.forEach { tariff ->
                addTariffCard(inflater, tariffsContainer, group.emoji, tariff)
            }
        }
        renderedSignature = signature

        // Re-apply whatever was selected before the rebuild (a rotation, or the first render after
        // a restore). A key that no longer exists in the catalog resolves to nothing and the
        // checkout card closes, which is the honest outcome.
        applySelection()

        if (fromSkeleton) {
            crossFadeSkeletonToContent()
        } else {
            // Already on content: no animation, just make sure everything is at full opacity.
            skeleton.visibility = View.GONE
            skeleton.alpha = 1f
            tariffsHeader.alpha = 1f
            tariffsContainer.alpha = 1f
        }
    }

    /**
     * Gentle skeleton → content hand-off: fade the placeholder silhouette out while the real tariff
     * list fades in over [R.integer.motion_state], instead of a hard visibility pop. Gated by
     * reduced motion (via [reducedMotion], which reads the OS ANIMATOR_DURATION_SCALE like every
     * other animated screen in the app): when motion is off we swap instantly.
     */
    private fun crossFadeSkeletonToContent() {
        if (skeleton.reducedMotion()) {
            skeleton.visibility = View.GONE
            skeleton.alpha = 1f
            tariffsHeader.alpha = 1f
            tariffsContainer.alpha = 1f
            return
        }

        val duration = resources.getInteger(R.integer.motion_state).toLong()
        val easeOut = AnimationUtils.loadInterpolator(this, R.interpolator.ease_out_quart)

        skeleton.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(easeOut)
            .withEndAction {
                skeleton.visibility = View.GONE
                skeleton.alpha = 1f
            }
            .start()

        tariffsHeader.alpha = 0f
        tariffsContainer.alpha = 0f
        tariffsHeader.animate().alpha(1f).setDuration(duration).setInterpolator(easeOut).start()
        tariffsContainer.animate().alpha(1f).setDuration(duration).setInterpolator(easeOut).start()
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
        tariffsByKey[key] = tariff
        cardViews[key] = card
        optionContainers[key] = llOptions
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

        card.setOnClickListener { selectTariff(tariff) }
        header.setOnClickListener { selectTariff(tariff) }

        parent.addView(card)
    }

    // endregion

    // region selection

    private fun selectedTariff(): TariffDto? = selectedTariffKey?.let { tariffsByKey[it] }

    private fun selectedOption(): PriceOptionDto? {
        val tariff = selectedTariff() ?: return null
        val key = selectedOptionKey ?: return null
        return optionsOf(tariff).firstOrNull { optionKey(it) == key }
    }

    /**
     * Selecting a tariff is IDEMPOTENT. Tapping the tariff that is already selected used to return
     * early, which was fine while the cards it had highlighted were still on screen — and useless
     * once a re-render had replaced them, because the state said "selected" and nothing was
     * painted. Now a repeat tap keeps the chosen option and simply re-applies the selection (D09).
     */
    private fun selectTariff(tariff: TariffDto) {
        val key = tariffKey(tariff)
        if (selectedTariffKey != key) {
            selectedTariffKey = key
            selectedOptionKey = null
            extraDevices = 0
            // A tariff with a single option → preselect it for a shorter flow.
            val options = optionsOf(tariff)
            if (options.size == 1) selectedOptionKey = optionKey(options.first())
        }
        applySelection()
    }

    private fun selectOption(tariff: TariffDto, option: PriceOptionDto) {
        selectedTariffKey = tariffKey(tariff)
        selectedOptionKey = optionKey(option)
        applySelection()
    }

    /**
     * Paints the current selection onto the cards that exist right now: exactly one card carries
     * the accent stroke, its check mark and its open option list; every other card is neutral and
     * closed. The checkout card is shown if and only if a tariff AND an option resolve — so it can
     * never offer to charge for something the user cannot see.
     */
    private fun applySelection() {
        // Drop keys the current catalog no longer contains.
        if (selectedTariff() == null) {
            selectedTariffKey = null
            selectedOptionKey = null
        } else if (selectedOption() == null) {
            selectedOptionKey = null
        }

        val neutral = resolveAttrColor(com.google.android.material.R.attr.colorOutlineVariant)
        val accent = resolveAttrColor(androidx.appcompat.R.attr.colorPrimary)
        val selected = selectedTariffKey

        for ((key, card) in cardViews) {
            val isSelected = key == selected
            card.strokeColor = if (isSelected) accent else neutral
            card.strokeWidth = if (isSelected) dp(2) else dp(1)
            optionContainers[key]?.visibility = if (isSelected) View.VISIBLE else View.GONE
            checkMarks[key]?.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        }

        val tariff = selectedTariff()
        val option = selectedOption()
        if (selected != null) {
            if (option != null) highlightOption(selected, option) else clearOptionHighlights(selected)
        }

        if (tariff != null && option != null) {
            setupExtraDevices(tariff)
            checkoutCard.visibility = View.VISIBLE
            updateTotal(tariff, option)
        } else {
            checkoutCard.visibility = View.GONE
        }
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
        val tariff = selectedTariff() ?: return
        val max = tariff.maxExtraDevices
        extraDevices = (extraDevices + delta).coerceIn(0, maxOf(0, max))
        renderExtraDevices(tariff)
        selectedOption()?.let { updateTotal(tariff, it) }
    }

    private fun renderExtraDevices(tariff: TariffDto) {
        tvExtraCount.text = getString(R.string.buy_extra_devices_count, extraDevices)

        // Make the stepper bounds visible: dim + disable the button that can't move further.
        // A payment in flight freezes the stepper too — the amount being charged is already fixed,
        // so a control that appears to change it would be lying.
        val busy = viewModel.paymentInFlight.value
        val max = maxOf(0, tariff.maxExtraDevices)
        setStepperEnabled(btnDevMinus, !busy && extraDevices > 0)
        setStepperEnabled(btnDevPlus, !busy && extraDevices < max)

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

    private fun updateTotal(tariff: TariffDto, option: PriceOptionDto) {
        tvTotal.text = formatMoney(currentTotal(tariff, option), tariff.currency)
    }

    // endregion

    // region checkout

    /**
     * The CTA's in-flight state (D10). The screen used to change in NO way between the tap and the
     * provider's answer — on a slow connection that is a button which appears not to have worked,
     * and the second tap is a second charge. Now the button says what it is doing, stops taking
     * taps, and the declared indicator beside it finally does the job it was declared for.
     */
    private fun renderPayBusy(busy: Boolean) {
        btnPay.isEnabled = !busy
        btnPay.text = getString(if (busy) R.string.account_pay_in_progress else R.string.buy_pay)
        btnPay.contentDescription = btnPay.text
        progressBuy.visibility = if (busy) View.VISIBLE else View.GONE
        selectedTariff()?.let { renderExtraDevices(it) }
    }

    private fun onPayClicked() {
        // Belt to the ViewModel's braces: the request itself refuses to start twice, and the CTA
        // refuses to ask.
        if (viewModel.paymentInFlight.value) return

        val tariff = selectedTariff()
        val option = selectedOption()
        if (tariff == null || option == null) {
            toast(R.string.buy_select_option_first)
            return
        }

        val methods = viewModel.publicConfig.value?.plategaMethods?.map { it.id to it.label } ?: emptyList()
        if (methods.isEmpty()) {
            toastError(R.string.buy_no_methods)
            return
        }

        val profile = viewModel.profile.value
        val balanceLabel = profile?.let { formatMoney(it.balance, it.currency) }

        // Everything the charge needs travels WITH the pick, so the request is never rebuilt from
        // selection state that a rotation reset while the sheet was open (D11).
        val payload = Bundle().apply {
            putString(EXTRA_TARIFF_ID, tariff.id)
            putString(EXTRA_OPTION_ID, option.id)
            putInt(EXTRA_EXTRA_DEVICES, extraDevices)
            putDouble(EXTRA_AMOUNT, currentTotal(tariff, option))
            putString(EXTRA_CURRENCY, tariff.currency.ifBlank { "RUB" })
        }

        PaymentMethodSheet.show(
            supportFragmentManager,
            REQUEST_PAY_METHOD,
            getString(R.string.buy_pick_method_title),
            balanceLabel,
            methods,
            payload,
        )
    }

    private fun onMethodPicked(result: Bundle) {
        val methodId = result.getString(PaymentMethodSheet.RESULT_METHOD_ID) ?: return
        if (viewModel.paymentInFlight.value) return

        val tariffId = result.getString(EXTRA_TARIFF_ID).orEmpty()
        val optionId = result.getString(EXTRA_OPTION_ID).orEmpty()
        val deviceCount = result.getInt(EXTRA_EXTRA_DEVICES, 0).takeIf { it > 0 }
        // Charge exactly the displayed «Итого» (option price + extra devices), never just the
        // bare option price — otherwise the extra-device cost is silently dropped from checkout.
        val amount = result.getDouble(EXTRA_AMOUNT, 0.0)
        val currency = result.getString(EXTRA_CURRENCY)?.ifBlank { null } ?: "RUB"
        if (tariffId.isBlank() || amount <= 0.0) {
            toastError(R.string.buy_select_option_first)
            return
        }

        // Arm the diagnostic: whichever request we fire, a failure now becomes a visible dialog.
        awaitingPaymentError = true
        val req = PaymentRequestDto(
            tariffId = tariffId,
            tariffPriceOptionId = optionId.ifBlank { null },
            deviceCount = deviceCount,
            amount = amount,
            currency = currency,
            paymentMethod = if (methodId == PaymentMethodSheet.ID_BALANCE) null else methodId.toIntOrNull(),
        )

        if (methodId == PaymentMethodSheet.ID_BALANCE) {
            viewModel.payWithBalance(req) {
                awaitingPaymentError = false
                toastSuccess(R.string.buy_success)
                finish()
            }
        } else {
            viewModel.buy(req) { init ->
                awaitingPaymentError = false
                openCheckout(init)
            }
        }
    }

    /** Dialog with a designed, code-free reason for a failed payment. */
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
            toastError(R.string.buy_no_browser)
            return
        }
        val uri = Uri.parse(url)
        pendingPayment = true
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, uri)
            toast(R.string.buy_checkout_return)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                toast(R.string.buy_checkout_return)
            } catch (e2: ActivityNotFoundException) {
                pendingPayment = false
                toastError(R.string.buy_no_browser)
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
        private const val REQUEST_PAY_METHOD = "buy_pay_method"

        private const val EXTRA_TARIFF_ID = "extra_tariff_id"
        private const val EXTRA_OPTION_ID = "extra_option_id"
        private const val EXTRA_EXTRA_DEVICES = "extra_extra_devices"
        private const val EXTRA_AMOUNT = "extra_amount"
        private const val EXTRA_CURRENCY = "extra_currency"

        private const val STATE_TARIFF_KEY = "state_tariff_key"
        private const val STATE_OPTION_KEY = "state_option_key"
        private const val STATE_EXTRA_DEVICES = "state_extra_devices"
        private const val STATE_PENDING_PAYMENT = "state_pending_payment"
        private const val STATE_AWAITING_ERROR = "state_awaiting_error"

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
