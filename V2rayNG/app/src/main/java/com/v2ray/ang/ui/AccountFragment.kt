package com.v2ray.ang.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.R
import com.v2ray.ang.auth.ApiError
import com.v2ray.ang.auth.dto.PaymentInitDto
import com.v2ray.ang.auth.dto.PaymentRequestDto
import com.v2ray.ang.auth.dto.PriceOptionDto
import com.v2ray.ang.auth.dto.PromoDto
import com.v2ray.ang.auth.dto.PublicConfigDto
import com.v2ray.ang.auth.dto.SubInfoDto
import com.v2ray.ang.auth.dto.TariffDto
import com.v2ray.ang.auth.dto.UserProfileDto
import com.v2ray.ang.databinding.ActivityAccountBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.adapter.AccountSubscriptionsAdapter
import com.v2ray.ang.ui.adapter.PaymentsAdapter
import com.v2ray.ang.ui.adapter.TariffAdapter
import com.v2ray.ang.util.AvatarManager
import com.v2ray.ang.viewmodel.AccountViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Account / Payments screen for the departament backend, hosted IN-PLACE as the Account bottom-nav
 * tab (no sliding Activity, no toolbar). Shows the profile + balance, subscriptions, the tariff
 * catalog, account actions (upgrade / add-devices / promo / trial) and the payment history.
 * Purchases open a provider checkout in a Custom Tab; a PAID result only ever arrives via webhook,
 * so on return we re-poll rather than assume success.
 */
class AccountFragment : Fragment() {

    // Nullable binding so the view refs are released in onDestroyView (avoids leaking the whole
    // account view tree while the tab is detached).
    private var _binding: ActivityAccountBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccountViewModel by viewModels()

    private val subsAdapter by lazy {
        AccountSubscriptionsAdapter(::onSubscriptionSelected, ::onAutoRenewToggled)
    }
    private val tariffAdapter by lazy { TariffAdapter(::onBuyTariff) }
    private val paymentsAdapter = PaymentsAdapter()

    private var activeSub: SubInfoDto? = null
    private var extraDevices = 1
    private var latestProfile: UserProfileDto? = null
    private var latestTariffGroups: List<com.v2ray.ang.auth.dto.TariffGroupDto> = emptyList()
    private var publicConfig: PublicConfigDto? = null

    private var pendingPayment = false
    private var pollJob: Job? = null

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
        setupRecyclers()
        wireActions()
        observeState()
        loadAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclers() {
        binding.rvSubscriptions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSubscriptions.adapter = subsAdapter
        binding.rvTariffs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTariffs.adapter = tariffAdapter
        binding.rvPayments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPayments.adapter = paymentsAdapter
        updateDeviceStepperUi()
    }

    private fun wireActions() {
        binding.btnTopUp.setOnClickListener { showTopUpDialog() }
        binding.btnCopyReferral.setOnClickListener { copyReferralCode() }
        binding.btnUpgrade.setOnClickListener { showUpgradeDialog() }
        binding.btnDevMinus.setOnClickListener {
            extraDevices -= 1
            updateDeviceStepperUi()
        }
        binding.btnDevPlus.setOnClickListener {
            extraDevices += 1
            updateDeviceStepperUi()
        }
        binding.btnAddDevices.setOnClickListener { doAddDevices() }
        binding.btnCheckPromo.setOnClickListener { doCheckPromo() }
        binding.btnTrial.setOnClickListener { doActivateTrial() }
        binding.avatarContainer.setOnClickListener { showAvatarOptions() }
        binding.imgAvatarEdit.setOnClickListener { showAvatarOptions() }
    }

    private fun loadAll() {
        viewModel.refreshProfile()
        viewModel.loadSubscriptions()
        viewModel.loadTariffs()
        viewModel.loadPayments()
        viewModel.loadPublicConfig()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.profile.collect { renderProfile(it) } }
                launch { viewModel.subscriptions.collect { renderSubscriptions(it) } }
                launch { viewModel.tariffs.collect { renderTariffs(it) } }
                launch { viewModel.payments.collect { renderPayments(it) } }
                launch { viewModel.publicConfig.collect { publicConfig = it } }
                launch { viewModel.loading.collect { if (it) showLoading() else hideLoading() } }
                launch { viewModel.error.collect { renderError(it) } }
            }
        }
    }

    // The in-place Account tab has no toolbar/app bar, so there is no global spinner surface like
    // BaseActivity's. Loading stays silent here; the flows are quick and the payment re-poll uses
    // tv_pending for its own progress hint.
    private fun showLoading() {}

    private fun hideLoading() {}

    // Context-scoped toast helpers so the ported (Context.toast) calls work from a Fragment.
    private fun toast(message: Int) = requireContext().toast(message)
    private fun toastError(message: Int) = requireContext().toastError(message)
    private fun toastSuccess(message: Int) = requireContext().toastSuccess(message)

    // region rendering

    private fun renderProfile(profile: UserProfileDto?) {
        latestProfile = profile
        if (profile == null) {
            binding.tvEmail.text = ""
            binding.tvTelegram.setText(R.string.account_no_telegram)
            binding.tvBalance.text = formatMoney(0.0, "")
            binding.tvReferral.visibility = View.GONE
            binding.btnTrial.visibility = View.GONE
            AvatarManager.setMonogram(binding.tvAvatarInitial, null)
            AvatarManager.applyAvatar(viewLifecycleOwner.lifecycleScope, requireContext(), binding.imgAvatar, binding.tvAvatarInitial, null)
            return
        }
        // Primary line prefers the Telegram display name, then the @nick, then the account e-mail.
        val uname = profile.telegramUsername?.takeIf { it.isNotBlank() }
        val handle = uname?.let { "@$it" }
        val display = profile.telegramName?.takeIf { it.isNotBlank() }
        val primary = display ?: handle ?: profile.email
        binding.tvEmail.text = primary
        // Secondary line keeps the @username/email identity. When a real display name is shown
        // above, put the @handle (or e-mail) beneath it; otherwise this matches the prior behaviour.
        binding.tvTelegram.text = when {
            display != null && handle != null -> handle
            display != null && profile.email.isNotBlank() -> profile.email
            uname != null && profile.email.isNotBlank() -> profile.email
            uname != null -> getString(R.string.account_telegram, uname)
            else -> getString(R.string.account_no_telegram)
        }
        AvatarManager.setMonogram(binding.tvAvatarInitial, primary)
        AvatarManager.applyAvatar(viewLifecycleOwner.lifecycleScope, requireContext(), binding.imgAvatar, binding.tvAvatarInitial, profile)
        binding.tvBalance.text = formatMoney(profile.balance, profile.currency)
        if (profile.referralCode.isNotBlank()) {
            binding.tvReferral.visibility = View.VISIBLE
            binding.tvReferral.text = getString(R.string.account_referral, profile.referralCode)
        } else {
            binding.tvReferral.visibility = View.GONE
        }
        binding.btnTrial.visibility = if (!profile.trialUsed) View.VISIBLE else View.GONE
    }

    private fun renderSubscriptions(list: List<SubInfoDto>) {
        subsAdapter.submit(list)
        val empty = list.isEmpty()
        binding.tvSubsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvSubscriptions.visibility = if (empty) View.GONE else View.VISIBLE

        // Keep the active selection if still present; otherwise default to the first sub, which
        // renders as the most-emphasized (blue-outlined) card at the top of the list.
        val current = activeSub
        val resolved = list.firstOrNull { it.remnawaveUuid == current?.remnawaveUuid } ?: list.firstOrNull()
        activeSub = resolved
        subsAdapter.activeUuid = resolved?.remnawaveUuid
        updateActiveSubUi()
    }

    private fun renderTariffs(list: List<com.v2ray.ang.auth.dto.TariffGroupDto>) {
        latestTariffGroups = list
        tariffAdapter.submit(list)
        val empty = list.isEmpty()
        binding.tvTariffsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvTariffs.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun renderPayments(list: List<com.v2ray.ang.auth.dto.PaymentDto>) {
        paymentsAdapter.submit(list)
        val empty = list.isEmpty()
        binding.tvPaymentsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvPayments.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun renderError(error: ApiError?) {
        if (error == null) return
        toastError(messageFor(error))
        viewModel.clearError()
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

    // region active subscription + stepper

    private fun onSubscriptionSelected(sub: SubInfoDto) {
        activeSub = sub
        subsAdapter.activeUuid = sub.remnawaveUuid
        updateActiveSubUi()
    }

    private fun updateActiveSubUi() {
        val sub = activeSub
        if (sub == null) {
            binding.tvActiveSub.setText(R.string.account_active_sub_none)
        } else {
            val name = sub.displayName?.takeIf { it.isNotBlank() }
                ?: sub.tariffDisplayName?.takeIf { it.isNotBlank() }
                ?: getString(R.string.account_subs_header)
            binding.tvActiveSub.text = getString(R.string.account_active_sub, name)
        }
        updateDeviceStepperUi()
    }

    /** Max extra devices allowed = 7 total minus the active sub's current total. */
    private fun maxExtraDevices(): Int {
        val total = activeSub?.totalDevices ?: return 0
        return (7 - total).coerceAtLeast(0)
    }

    private fun updateDeviceStepperUi() {
        val max = maxExtraDevices()
        extraDevices = if (max < 1) 0 else extraDevices.coerceIn(1, max)
        binding.tvAddDevicesCount.text = getString(R.string.account_add_devices_count, extraDevices)
        binding.btnDevPlus.isEnabled = extraDevices < max
        binding.btnDevMinus.isEnabled = extraDevices > 1
        binding.btnAddDevices.isEnabled = max >= 1 && extraDevices >= 1
    }

    // endregion

    // region actions

    private fun onAutoRenewToggled(sub: SubInfoDto, checked: Boolean) {
        viewModel.toggleAutoRenew(sub.id, checked) { viewModel.loadSubscriptions() }
    }

    private fun onBuyTariff(tariff: TariffDto, option: PriceOptionDto?) {
        val req = PaymentRequestDto(
            tariffId = tariff.id,
            tariffPriceOptionId = option?.id,
            paymentMethod = null,
        )
        viewModel.buy(req, ::openCheckout)
    }

    private fun showTopUpDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.account_top_up_hint)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.account_top_up_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val amount = input.text?.toString()?.trim()?.toDoubleOrNull()
                if (amount != null && amount > 0.0) {
                    viewModel.buy(PaymentRequestDto(amount = amount), ::openCheckout)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        AlertDialog.Builder(requireContext())
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

    private fun showUpgradeDialog() {
        val active = activeSub
        if (active == null) {
            toast(R.string.account_upgrade_no_sub)
            return
        }
        val tariffs = latestTariffGroups.flatMap { it.tariffs }
        if (tariffs.isEmpty()) {
            toast(R.string.account_tariffs_empty)
            return
        }
        val labels = tariffs.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.account_upgrade_title)
            .setItems(labels) { _, which ->
                val target = tariffs[which]
                viewModel.upgrade(target.id, "platega", active.remnawaveUuid, null, ::openCheckout)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doAddDevices() {
        val active = activeSub
        if (active == null) {
            toast(R.string.account_upgrade_no_sub)
            return
        }
        val max = maxExtraDevices()
        if (max < 1) {
            toast(R.string.account_add_devices_limit)
            return
        }
        val count = extraDevices.coerceIn(1, max)
        viewModel.addDevices(active.type, active.id, count, "platega", null, ::openCheckout)
    }

    private fun doCheckPromo() {
        val code = binding.etPromo.text?.toString()?.trim().orEmpty()
        if (code.isEmpty()) return
        viewModel.checkPromo(code) { showPromoResult(it) }
    }

    private fun showPromoResult(promo: PromoDto) {
        binding.tvPromoResult.visibility = View.VISIBLE
        binding.tvPromoResult.text = when {
            promo.discountPercent != null ->
                getString(R.string.account_promo_discount, promo.discountPercent.toInt())

            promo.durationDays != null ->
                getString(R.string.account_promo_free_days, promo.durationDays)

            else -> getString(R.string.account_promo_invalid)
        }
    }

    private fun doActivateTrial() {
        viewModel.activateTrial {
            toastSuccess(R.string.account_trial_activated)
            viewModel.refreshProfile()
            viewModel.loadSubscriptions()
        }
    }

    // endregion

    // region checkout

    /** Opens the provider checkout URL. Never logs the URL. */
    private fun openCheckout(init: PaymentInitDto) {
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
     * After returning from a checkout, re-poll subscriptions/payments/profile a few times over
     * ~48s. The backend confirms PAID only via webhook — the tab returning proves nothing.
     */
    private fun startPaymentPolling() {
        if (pollJob?.isActive == true) return
        binding.tvPending.visibility = View.VISIBLE
        pollJob = viewLifecycleOwner.lifecycleScope.launch {
            repeat(6) {
                viewModel.loadSubscriptions()
                viewModel.loadPayments()
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
    return if (currency.isBlank()) n else "$n $currency"
}
