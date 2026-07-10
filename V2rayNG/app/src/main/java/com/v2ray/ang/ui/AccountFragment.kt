package com.v2ray.ang.ui

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.auth.ApiError
import com.v2ray.ang.auth.dto.PaymentDto
import com.v2ray.ang.auth.dto.PaymentInitDto
import com.v2ray.ang.auth.dto.PaymentRequestDto
import com.v2ray.ang.auth.dto.SubInfoDto
import com.v2ray.ang.auth.dto.UserProfileDto
import com.v2ray.ang.databinding.ActivityAccountBinding
import com.v2ray.ang.databinding.DialogTopUpBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.util.AvatarManager
import com.v2ray.ang.util.reducedMotion
import com.v2ray.ang.viewmodel.AccountViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Account HUB, hosted IN-PLACE as the Account bottom-nav tab (no sliding Activity, no toolbar).
 * Mirrors the Settings tab: a compact profile card (name / balance / referral / top-up), a single
 * subscription summary (active sub OR "нет активной подписки", never both), and grouped entry cards
 * that open the devices / buy / history sub-screens. Purchases open a provider checkout in a Custom
 * Tab; a PAID result only ever arrives via webhook, so on return we re-poll rather than assume success.
 */
class AccountFragment : Fragment() {

    // Nullable binding so the view refs are released in onDestroyView (avoids leaking the whole
    // account view tree while the tab is detached).
    private var _binding: ActivityAccountBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccountViewModel by viewModels()

    /** The subscription rendered in the summary card (the first/most-relevant one). */
    private var activeSub: SubInfoDto? = null
    private var latestProfile: UserProfileDto? = null

    private var pendingPayment = false
    private var pollJob: Job? = null

    // Last balance figure actually shown, so a re-render counts UP from it instead of hard-swapping.
    // Null until the first paint (which sets the value instantly — no spin on open).
    private var lastBalance: Double? = null
    private var balanceAnimator: ValueAnimator? = null

    // Set right before a purchase/top-up call so the NEXT error emission is surfaced as the real
    // "Ошибка оплаты" diagnostic dialog instead of the friendly toast. Cleared on success.
    private var awaitingPaymentError = false

    // Gallery picker for a custom avatar. GetContent grants a one-shot read grant, which is
    // enough since we copy the bytes into app storage immediately. Registered at construction
    // (valid for a Fragment), never after STARTED.
    private val pickAvatar =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> onAvatarPicked(uri) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = ActivityAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The bottom nav overlays tab content, so keep the last card scrollable clear of it (this
        // tab has no inset listener of its own like the Home/Servers lists do).
        binding.scrollRoot.clipToPadding = false
        binding.scrollRoot.updatePadding(bottom = (96 * resources.displayMetrics.density).toInt())
        wireActions()
        observeState()
        loadAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        balanceAnimator?.cancel()
        balanceAnimator = null
        _binding = null
    }

    private fun wireActions() {
        binding.btnTopUp.setOnClickListener { showTopUpDialog() }
        binding.btnCopyReferral.setOnClickListener { copyReferralCode() }
        binding.avatarContainer.setOnClickListener { showAvatarOptions() }
        binding.imgAvatarEdit.setOnClickListener { showAvatarOptions() }
        binding.switchAutoRenew.setOnClickListener {
            val sub = activeSub ?: return@setOnClickListener
            onAutoRenewToggled(sub, binding.switchAutoRenew.isChecked)
        }
        binding.rowDevices.setOnClickListener { openSubScreen(DeviceManagementActivity::class.java) }
        binding.rowBuy.setOnClickListener { openSubScreen(BuyTariffActivity::class.java) }
        binding.rowHistory.setOnClickListener { openSubScreen(PaymentHistoryActivity::class.java) }
        // No-active-sub CTA: same destination as the buy row; only shown in the empty state.
        binding.btnNoSubBuy.setOnClickListener { openSubScreen(BuyTariffActivity::class.java) }
    }

    private fun openSubScreen(target: Class<*>) {
        startActivity(Intent(requireContext(), target))
    }

    private fun loadAll() {
        viewModel.refreshProfile()
        viewModel.loadSubscriptions()
        viewModel.loadPublicConfig()
        // Needed to resolve the active sub's tariff badge name (Base/Plus) from its tariffId.
        viewModel.loadTariffs()
        // Feeds the history row's trailing last-payment date.
        viewModel.loadPayments()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.profile.collect { renderProfile(it) } }
                launch { viewModel.subscriptions.collect { renderSubscriptions(it) } }
                // Re-render the active sub when the tariff catalog arrives so the badge resolves.
                launch { viewModel.tariffs.collect { updateActiveSubUi() } }
                launch { viewModel.payments.collect { renderHistoryValue(it) } }
                launch { viewModel.error.collect { renderError(it) } }
            }
        }
    }

    // Context-scoped toast helpers so the ported (Context.toast) calls work from a Fragment.
    private fun toast(message: Int) = requireContext().toast(message)
    private fun toastError(message: Int) = requireContext().toastError(message)
    private fun toastSuccess(message: Int) = requireContext().toastSuccess(message)

    // region rendering

    private fun renderProfile(profile: UserProfileDto?) {
        latestProfile = profile
        if (profile == null) {
            binding.tvEmail.text = ""
            // Null/error state: blank the balance rather than showing a fake "Баланс: 0 ₽". Forget the
            // last figure so a profile arriving later paints instantly instead of counting up from stale.
            balanceAnimator?.cancel()
            lastBalance = null
            binding.tvBalance.text = ""
            binding.tvReferral.visibility = View.GONE
            binding.btnCopyReferral.visibility = View.GONE
            AvatarManager.setMonogram(binding.tvAvatarInitial, null)
            AvatarManager.applyAvatar(viewLifecycleOwner.lifecycleScope, requireContext(), binding.imgAvatar, binding.tvAvatarInitial, null)
            return
        }
        // Name prefers the Telegram display name, then the @nick, then the account e-mail.
        val uname = profile.telegramUsername?.takeIf { it.isNotBlank() }
        val handle = uname?.let { "@$it" }
        val display = profile.telegramName?.takeIf { it.isNotBlank() }
        val email = profile.email.takeIf { it.isNotBlank() }
        val primary = display ?: handle ?: email.orEmpty()
        binding.tvEmail.text = primary
        AvatarManager.setMonogram(binding.tvAvatarInitial, primary)
        AvatarManager.applyAvatar(viewLifecycleOwner.lifecycleScope, requireContext(), binding.imgAvatar, binding.tvAvatarInitial, profile)
        val previousBalance = lastBalance
        if (previousBalance == null || previousBalance == profile.balance) {
            // First paint (or an unchanged re-render): land the figure instantly, no count-up spin.
            binding.tvBalance.text = getString(R.string.account_balance_inline, formatMoney(profile.balance, profile.currency))
        } else {
            animateMoney(binding.tvBalance, previousBalance, profile.balance, profile.currency)
        }
        lastBalance = profile.balance
        if (profile.referralCode.isNotBlank()) {
            binding.tvReferral.visibility = View.VISIBLE
            binding.btnCopyReferral.visibility = View.VISIBLE
            binding.tvReferral.text = getString(R.string.account_referral, profile.referralCode)
        } else {
            binding.tvReferral.visibility = View.GONE
            binding.btnCopyReferral.visibility = View.GONE
        }
    }

    /**
     * Counts the balance figure UP from [from] to [to] over ~motion_reveal, ease_out_quart, formatting
     * every frame through the same money formatter so the ₽ sign and tabular figures stay put. Any
     * in-flight count-up is cancelled first so rapid balance changes don't stack. Reduced motion (or a
     * no-op delta): the final value lands instantly.
     */
    private fun animateMoney(textView: TextView, from: Double, to: Double, currency: String) {
        balanceAnimator?.cancel()
        // Whole-ruble balances count on integers so the tabular figure never flickers decimals mid-flight;
        // fractional balances keep their 2-decimal shape the formatter already applies.
        val wholeOnly = from % 1.0 == 0.0 && to % 1.0 == 0.0
        val show = { value: Double ->
            val shown = if (wholeOnly) Math.round(value).toDouble() else value
            textView.text = getString(R.string.account_balance_inline, formatMoney(shown, currency))
        }
        if (textView.reducedMotion() || from == to) {
            show(to)
            return
        }
        balanceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = resources.getInteger(R.integer.motion_reveal).toLong()
            interpolator = AnimationUtils.loadInterpolator(textView.context, R.interpolator.ease_out_quart)
            addUpdateListener { anim ->
                show(from + (to - from) * anim.animatedFraction)
            }
            start()
        }
    }

    private fun renderSubscriptions(list: List<SubInfoDto>) {
        // Keep the active selection if still present; otherwise default to the first sub.
        val current = activeSub
        activeSub = list.firstOrNull { it.remnawaveUuid == current?.remnawaveUuid } ?: list.firstOrNull()
        updateActiveSubUi()
    }

    /** Renders ONE coherent state: the active-sub details, or the "нет активной подписки" empty line. */
    private fun updateActiveSubUi() {
        val sub = activeSub
        if (sub == null) {
            binding.groupActiveSub.visibility = View.GONE
            binding.tvNoSub.visibility = View.VISIBLE
            // Empty state: offer the buy CTA and clear the device row's trailing value.
            binding.btnNoSubBuy.visibility = View.VISIBLE
            binding.tvRowValueDevices.visibility = View.GONE
            return
        }
        binding.tvNoSub.visibility = View.GONE
        binding.btnNoSubBuy.visibility = View.GONE
        binding.groupActiveSub.visibility = View.VISIBLE

        binding.tvSubName.text = sub.displayName?.takeIf { it.isNotBlank() }
            ?: sub.tariffDisplayName?.takeIf { it.isNotBlank() }
            ?: sub.defaultLabel?.takeIf { it.isNotBlank() }
            ?: getString(R.string.account_subs_header)

        // Tariff badge (Base/Plus): prefer the catalog name matched on tariffId, else the sub's own
        // product-name fallback. Only shown when a real tariff name resolves.
        val tariffBadge = viewModel.tariffNameFor(sub.tariffId) ?: sub.tariffBadgeName()
        if (tariffBadge.isNullOrBlank()) {
            binding.tvTariffBadge.visibility = View.GONE
        } else {
            binding.tvTariffBadge.text = tariffBadge
            binding.tvTariffBadge.visibility = View.VISIBLE
        }

        if (sub.expireAtIso.isNullOrBlank()) {
            binding.tvSubExpiry.visibility = View.GONE
        } else {
            binding.tvSubExpiry.visibility = View.VISIBLE
            binding.tvSubExpiry.text = getString(R.string.account_expires, formatIsoDate(sub.expireAtIso))
        }

        // /subscription/all carries no live "connected devices" count (that lives on GET
        // /client/devices -> total) and no raw remnawave record; `connectedDevices` is therefore
        // always 0 here. Show the subscription's own device figures instead: deviceCount out of the
        // total slots, with ∞ when the plan is unlimited.
        val unlimitedDevices = sub.subscription?.raw()?.isUnlimitedDevices() == true
        val totalDevicesStr = if (unlimitedDevices) getString(R.string.account_unlimited) else sub.totalDevices.toString()
        binding.tvSubDevices.text = getString(R.string.account_devices, sub.deviceCount.toString(), totalDevicesStr)

        // Devices row's trailing "N / M" slot — bare figures (the row already carries its label).
        binding.tvRowValueDevices.text = "${sub.deviceCount} / $totalDevicesStr"
        binding.tvRowValueDevices.visibility = View.VISIBLE

        // Auto-renew — set state without firing the click handler for programmatic changes.
        binding.switchAutoRenew.isChecked = sub.autoRenewEnabled
        // The toggle PATCHes /client/secondary-subscriptions/{id}/auto-renew, so it needs a real
        // subscription id. A primary sub synthesized purely from /client/subscription (when /all
        // has no root entry) has no such id — reflect state but disable the toggle in that case.
        binding.switchAutoRenew.isEnabled = sub.id.isNotBlank()
    }

    /** Fills the history row's trailing slot with the most recent payment date, or hides it. */
    private fun renderHistoryValue(payments: List<PaymentDto>) {
        val latestIso = payments.maxByOrNull { it.createdAt }?.createdAt
        val date = formatIsoDate(latestIso)
        if (date.isBlank()) {
            binding.tvRowValueHistory.visibility = View.GONE
        } else {
            binding.tvRowValueHistory.text = date
            binding.tvRowValueHistory.visibility = View.VISIBLE
        }
    }

    private fun renderError(error: ApiError?) {
        if (error == null) return
        // A purchase/top-up just failed: show the REAL backend reason so it can be screenshotted.
        // Everything else keeps the friendly RU toast.
        if (awaitingPaymentError) {
            awaitingPaymentError = false
            showPaymentErrorDialog(error)
        } else {
            toastError(messageFor(error))
        }
        viewModel.clearError()
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
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_payment_error_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun messageFor(error: ApiError): Int = when (error) {
        is ApiError.ServiceUnavailable -> R.string.account_error_service_unavailable
        is ApiError.Network -> R.string.account_error_network
        is ApiError.Unauthorized -> R.string.account_error_unauthorized
        is ApiError.RateLimited -> R.string.account_error_rate_limited
        is ApiError.Timeout -> R.string.account_error_timeout
        else -> R.string.account_error_generic
    }

    // endregion

    // region actions

    private fun onAutoRenewToggled(sub: SubInfoDto, checked: Boolean) {
        // Remember the state to restore on failure.
        val previous = sub.autoRenewEnabled
        val onError: (ApiError) -> Unit = { error ->
            // Don't leave the switch visually toggled on a failed request.
            binding.switchAutoRenew.isChecked = previous
            showAutoRenewErrorDialog(error)
        }
        val reload = { viewModel.loadSubscriptions() }
        // The active/root subscription toggles auto-renew via the id-less primary endpoint;
        // secondary subscriptions target their own id.
        if (sub.type.equals("root", ignoreCase = true)) {
            viewModel.togglePrimaryAutoRenew(checked, onError = onError, onDone = { reload() })
        } else if (sub.id.isNotBlank()) {
            viewModel.toggleAutoRenew(sub.id, checked, onError = onError, onDone = { reload() })
        } else {
            // A secondary sub with no id to target — revert the visual toggle and bail.
            binding.switchAutoRenew.isChecked = sub.autoRenewEnabled
        }
    }

    /** Diagnostic dialog with the raw HTTP status + sanitized backend detail for a failed toggle. */
    private fun showAutoRenewErrorDialog(error: ApiError) {
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
        val body = if (detail.isNullOrBlank()) "HTTP: $code" else "HTTP: $code\n\n$detail"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Автопродление — ошибка")
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showTopUpDialog() {
        val dialogBinding = DialogTopUpBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_top_up_title)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val amount = dialogBinding.etTopUp.text?.toString()?.trim()?.toDoubleOrNull()
                if (amount != null && amount > 0.0) {
                    showPaymentMethodSheet(amount)
                } else {
                    toastError(R.string.account_top_up_invalid)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Lets the user pick a Platega payment method (СБП / карта) for the entered top-up amount.
     * The balance option is deliberately withheld (balanceLabel = null): a top-up ADDS to the
     * balance, so paying it FROM the balance would be circular.
     */
    private fun showPaymentMethodSheet(amount: Double) {
        val methods = viewModel.publicConfig.value?.plategaMethods?.map { it.id to it.label } ?: emptyList()
        PaymentMethodSheet.show(
            parentFragmentManager,
            getString(R.string.account_top_up_title),
            null,
            methods,
        ) { id ->
            if (id == "balance") {
                viewModel.payWithBalance(PaymentRequestDto(amount = amount, currency = "RUB")) {
                    toastSuccess(R.string.account_top_up_success)
                    viewModel.refreshProfile()
                    viewModel.loadSubscriptions()
                }
            } else {
                awaitingPaymentError = true
                viewModel.buy(PaymentRequestDto(amount = amount, currency = "RUB", paymentMethod = id.toIntOrNull()), ::openCheckout)
            }
        }
    }

    private fun copyReferralCode() {
        val code = latestProfile?.referralCode?.takeIf { it.isNotBlank() } ?: return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("referral", code))
        toast(R.string.account_referral_copied)
    }

    // region avatar

    private fun showAvatarOptions() {
        val hasCustom = AvatarManager.hasCustomAvatar(requireContext())
        val items = if (hasCustom) {
            arrayOf(getString(R.string.account_avatar_gallery), getString(R.string.account_avatar_remove))
        } else {
            arrayOf(getString(R.string.account_avatar_gallery))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_change_avatar)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> launchAvatarPicker()
                    1 -> {
                        AvatarManager.clearCustomAvatar(requireContext())
                        AvatarManager.applyAvatar(viewLifecycleOwner.lifecycleScope, requireContext(), binding.imgAvatar, binding.tvAvatarInitial, latestProfile)
                        toast(R.string.account_avatar_updated)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchAvatarPicker() {
        try {
            pickAvatar.launch("image/*")
        } catch (e: ActivityNotFoundException) {
            toastError(R.string.account_avatar_error)
        }
    }

    private fun onAvatarPicked(uri: Uri?) {
        if (uri == null) return
        if (AvatarManager.saveCustomAvatar(requireContext(), uri)) {
            AvatarManager.applyAvatar(viewLifecycleOwner.lifecycleScope, requireContext(), binding.imgAvatar, binding.tvAvatarInitial, latestProfile)
            toast(R.string.account_avatar_updated)
        } else {
            toastError(R.string.account_avatar_error)
        }
    }

    // endregion

    // endregion

    // region checkout

    /** Opens the provider checkout URL. Never logs the URL. */
    private fun openCheckout(init: PaymentInitDto) {
        // The payment request itself succeeded (a checkout URL was issued): drop the diagnostic arm.
        awaitingPaymentError = false
        val url = init.paymentUrl
        if (url.isBlank()) {
            toastError(R.string.account_checkout_no_browser)
            return
        }
        val uri = Uri.parse(url)
        pendingPayment = true
        try {
            CustomTabsIntent.Builder().build().launchUrl(requireContext(), uri)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: ActivityNotFoundException) {
                pendingPayment = false
                toastError(R.string.account_checkout_no_browser)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingPayment) startPaymentPolling()
    }

    /**
     * After returning from a checkout, re-poll subscriptions/profile a few times over ~48s. The
     * backend confirms PAID only via webhook — the tab returning proves nothing.
     */
    private fun startPaymentPolling() {
        if (pollJob?.isActive == true) return
        binding.tvPending.visibility = View.VISIBLE
        pollJob = viewLifecycleOwner.lifecycleScope.launch {
            repeat(6) {
                viewModel.loadSubscriptions()
                viewModel.refreshProfile()
                delay(8000L)
            }
            pendingPayment = false
            binding.tvPending.visibility = View.GONE
        }
    }

    // endregion
}

private fun formatMoney(amount: Double, currency: String): String {
    val n = if (amount % 1.0 == 0.0) amount.toLong().toString()
    else String.format(Locale.US, "%.2f", amount)
    return "$n ${currencySymbol(currency)}"
}

/**
 * Maps a currency CODE to its display symbol. This is a RUB-only product: the backend can return
 * "USD" (or a blank/unknown value) for accounts created via the site, but the balance is always in
 * rubles, so "RUB", "", "USD" and anything unrecognised all render as the ruble sign. Only genuinely
 * distinct currencies keep their own symbol.
 */
private fun currencySymbol(currency: String): String = when (currency.trim().uppercase(Locale.US)) {
    "EUR" -> "€"
    "KZT" -> "₸"
    "UAH" -> "₴"
    // "RUB", "", "USD" and any unknown value → treat as rubles.
    else -> "₽"
}

private fun formatIsoDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val datePart = iso.substringBefore('T')
    val parts = datePart.split('-')
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else datePart
}
