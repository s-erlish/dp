package com.v2ray.ang.ui

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.v2ray.ang.R
import com.v2ray.ang.auth.AccountSession
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

    /** Adapter backing the subscription carousel (one page per sub). */
    private lateinit var subAdapter: SubscriptionPagerAdapter

    /** Last subscription list published; the first page is the active/root sub. */
    private var currentSubs: List<SubInfoDto> = emptyList()
    private var latestProfile: UserProfileDto? = null

    // True until the FIRST real load result lands (a non-null profile, a non-empty sub list, or an
    // error). Gates the loading skeleton so a genuinely-empty account resolves to the empty state
    // rather than spinning forever, while a cold open (and a retry) still shows the silhouette.
    private var pendingFirstLoad = true

    // Looping alpha pulse on the loading skeleton; cancelled when the skeleton is not the shown state.
    private var skeletonAnimator: ValueAnimator? = null

    private var pendingPayment = false
    private var pollJob: Job? = null

    // Sign-out. The wipe is local and usually lands in a few milliseconds, so the row's trailing
    // spinner is scheduled 300ms out and cancelled if the work finishes first — a spinner that
    // flashes for one frame reads as a glitch (00-rules.md 7.3).
    private var signOutSpinnerJob: Job? = null

    // Last logged-in/out value this view has rendered. Null until the first emission, so the
    // StateFlow's initial replay is not mistaken for a transition. Drives the reset that keeps a
    // dropped session (sign-out, or a 401 on the identity endpoint) from leaving the previous
    // user's name, balance and subscriptions painted on a fragment that is never recreated.
    private var renderedLoggedIn: Boolean? = null

    // Set when the session drops; consumed on the next sign-in so the tab cold-loads (skeleton,
    // then real data) instead of re-showing whatever it last rendered.
    private var needsColdLoad = false

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
        setupPager()
        wireActions()
        observeState()
        renderHeroState()
        loadAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        balanceAnimator?.cancel()
        balanceAnimator = null
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        signOutSpinnerJob?.cancel()
        signOutSpinnerJob = null
        _binding = null
    }

    /**
     * Wires the subscription carousel: one page per sub, a space_12 gap between pages, and a page-
     * change callback that moves the dot selection. Neighbour-peek padding and the dots themselves
     * are (re)applied per list in [renderSubscriptions] since they depend on the page count.
     */
    private fun setupPager() {
        subAdapter = SubscriptionPagerAdapter(
            // Badge resolution order: catalog by tariffId, then catalog by the renewing price-option
            // id (correct after a Base→Plus upgrade), then the sub's own non-generic display name.
            // Null/blank hides the badge so a WRONG tariff is never shown.
            resolveBadge = { sub ->
                viewModel.tariffNameFor(sub.tariffId)
                    ?: viewModel.tariffNameForPriceOptionId(sub.tariffPriceOptionId)
                    ?: sub.tariffBadgeName()
            },
            // Live connected-device count comes from GET /client/devices for the active sub.
            resolveUsedDevices = { viewModel.deviceCount.value ?: 0 },
        )
        binding.vpSubscriptions.apply {
            adapter = subAdapter
            offscreenPageLimit = 1
            clipToPadding = false
            setPageTransformer(
                CompositePageTransformer().apply {
                    addTransformer(MarginPageTransformer(resources.getDimensionPixelSize(R.dimen.space_12)))
                },
            )
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = updateDotSelection(position)
            })
        }
    }

    private fun wireActions() {
        binding.btnTopUp.setOnClickListener { showTopUpDialog() }
        // The whole referral row copies the code (the trailing glyph is decorative).
        binding.rowReferral.setOnClickListener { copyReferralCode() }
        binding.avatarContainer.setOnClickListener { showAvatarOptions() }
        binding.imgAvatarEdit.setOnClickListener { showAvatarOptions() }
        binding.rowDevices.setOnClickListener { openSubScreen(DeviceManagementActivity::class.java) }
        binding.rowBuy.setOnClickListener { openSubScreen(BuyTariffActivity::class.java) }
        binding.rowHistory.setOnClickListener { openSubScreen(PaymentHistoryActivity::class.java) }
        binding.rowLogout.setOnClickListener { confirmSignOut() }
        // Empty-state CTA: same destination as the buy row.
        binding.btnBuyFirst.setOnClickListener { openSubScreen(BuyTariffActivity::class.java) }
        // Cold-load error: re-run the initial load (and re-show the skeleton while it retries).
        binding.btnRetryLoad.setOnClickListener {
            pendingFirstLoad = true
            viewModel.clearError()
            renderHeroState()
            loadAll()
        }
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
                launch { viewModel.profile.collect { renderProfile(it); renderHeroState() } }
                launch { viewModel.subscriptions.collect { renderSubscriptions(it) } }
                // Re-render the cards when the tariff catalog arrives so the badge resolves.
                launch {
                    viewModel.tariffs.collect {
                        subAdapter.notifyDataSetChanged()
                        renderHeroState()
                    }
                }
                // Refresh the device figures when the real connected-device count resolves.
                launch {
                    viewModel.deviceCount.collect {
                        subAdapter.notifyDataSetChanged()
                        renderDevicesRowValue()
                    }
                }
                launch { viewModel.payments.collect { renderHistoryValue(it) } }
                // Skeleton is driven by loading (+ the first-load gate) via renderHeroState.
                launch { viewModel.loading.collect { renderHeroState() } }
                launch { viewModel.error.collect { renderError(it) } }
                // The tab's fragment is added once and then only shown/hidden, so it outlives the
                // session. Without this, signing out (or a 401) would leave the previous account's
                // data painted, and signing in as somebody else would show it to them.
                launch { viewModel.account.collect { onAccountState(it) } }
            }
        }
    }

    /**
     * Reacts to logged-in/out TRANSITIONS only; the StateFlow's initial replay just records the
     * current value, because [onViewCreated] has already kicked off the first load.
     */
    private fun onAccountState(state: AccountSession.AccountState) {
        val loggedIn = state is AccountSession.AccountState.LoggedIn
        val previous = renderedLoggedIn
        renderedLoggedIn = loggedIn
        if (previous == null || previous == loggedIn) return
        if (loggedIn) {
            if (!needsColdLoad) return
            needsColdLoad = false
            pendingFirstLoad = true
            renderHeroState()
            loadAll()
        } else {
            onSessionCleared()
        }
    }

    /**
     * Blanks every rendered value after the session goes away. Covers both routes to a dropped
     * session: the user signing out here, and [AccountSession.wipe] firing from the repository
     * when the identity endpoint returns 401.
     */
    private fun onSessionCleared() {
        endSignOutBusy()
        pollJob?.cancel()
        pollJob = null
        pendingPayment = false
        awaitingPaymentError = false
        binding.tvPending.visibility = View.GONE
        // The empty hero, not the skeleton: there is nothing loading and nothing to wait for, and
        // a pulse animator left looping on a tab the user can no longer open is pure battery.
        pendingFirstLoad = false
        needsColdLoad = true
        latestProfile = null
        currentSubs = emptyList()
        subAdapter.submit(emptyList())
        binding.llSubDots.removeAllViews()
        binding.llSubDots.isVisible = false
        binding.tvRowValueDevices.visibility = View.GONE
        binding.tvRowValueHistory.visibility = View.GONE
        renderProfile(null)
        renderHeroState()
    }

    // Context-scoped toast helpers so the ported (Context.toast) calls work from a Fragment.
    private fun toast(message: Int) = requireContext().toast(message)
    private fun toastError(message: Int) = requireContext().toastError(message)
    private fun toastSuccess(message: Int) = requireContext().toastSuccess(message)

    // region rendering

    private fun renderProfile(profile: UserProfileDto?) {
        latestProfile = profile
        if (profile == null) {
            binding.tvUsername.text = ""
            // Null/error state: blank the balance rather than showing a fake "Баланс: 0 ₽". Forget the
            // last figure so a profile arriving later paints instantly instead of counting up from stale.
            balanceAnimator?.cancel()
            lastBalance = null
            binding.tvBalance.text = ""
            binding.rowReferral.visibility = View.GONE
            AvatarManager.setMonogram(binding.tvAvatarInitial, null)
            AvatarManager.applyAvatar(viewLifecycleOwner.lifecycleScope, requireContext(), binding.imgAvatar, binding.tvAvatarInitial, null)
            return
        }
        // A real profile landed — leave the loading skeleton behind.
        pendingFirstLoad = false
        // Name prefers the Telegram @handle, then the display name, then the account e-mail.
        val handle = profile.telegramUsername?.takeIf { it.isNotBlank() }?.let { "@$it" }
        val display = profile.telegramName?.takeIf { it.isNotBlank() }
        val email = profile.email.takeIf { it.isNotBlank() }
        val primary = handle ?: display ?: email.orEmpty()
        binding.tvUsername.text = primary
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
            binding.rowReferral.visibility = View.VISIBLE
            binding.tvReferral.text = getString(R.string.account_referral, profile.referralCode)
        } else {
            binding.rowReferral.visibility = View.GONE
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
        currentSubs = list
        if (list.isNotEmpty()) pendingFirstLoad = false
        subAdapter.submit(list)

        val count = list.size
        // Neighbour-peek padding only makes sense when there's a neighbour to peek at.
        val peek = if (count > 1) resources.getDimensionPixelSize(R.dimen.space_16) else 0
        binding.vpSubscriptions.setPadding(peek, 0, peek, 0)

        buildDots(count)
        binding.llSubDots.isVisible = count > 1

        renderDevicesRowValue()
        // Fetch the REAL connected-device count for the active (first/root) sub and pre-warm
        // AccountCache so the Devices sub-screen opens instantly. Cache-first inside.
        list.firstOrNull()?.remnawaveUuid?.takeIf { it.isNotBlank() }?.let { viewModel.loadDevices(it) }

        renderHeroState()
    }

    /** Fills the management "Устройства" row's trailing "N / M" slot from the active sub, or hides it. */
    private fun renderDevicesRowValue() {
        val sub = currentSubs.firstOrNull()
        if (sub == null) {
            binding.tvRowValueDevices.visibility = View.GONE
            return
        }
        val unlimitedDevices = sub.subscription?.raw()?.isUnlimitedDevices() == true
        val totalDevicesStr = if (unlimitedDevices) getString(R.string.account_unlimited) else sub.totalDevices.toString()
        val usedDevices = viewModel.deviceCount.value ?: 0
        binding.tvRowValueDevices.text = "$usedDevices / $totalDevicesStr"
        binding.tvRowValueDevices.visibility = View.VISIBLE
    }

    /**
     * Rebuilds the carousel page dots to match [count] pages (nothing shown for 0/1 page). Each dot
     * is a small View backed by dot_inactive/dot_active, sized dot_size / dot_size_active, with a
     * space_4 gap between them; the current page's dot is the active accent.
     */
    private fun buildDots(count: Int) {
        val container = binding.llSubDots
        container.removeAllViews()
        if (count <= 1) return
        val size = resources.getDimensionPixelSize(R.dimen.dot_size)
        val activeSize = resources.getDimensionPixelSize(R.dimen.dot_size_active)
        val gap = resources.getDimensionPixelSize(R.dimen.space_4)
        val current = binding.vpSubscriptions.currentItem
        for (i in 0 until count) {
            val selected = i == current
            val dim = if (selected) activeSize else size
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dim, dim).apply {
                    if (i > 0) marginStart = gap
                }
                setBackgroundResource(if (selected) R.drawable.dot_active else R.drawable.dot_inactive)
            }
            container.addView(dot)
        }
    }

    /** Swaps the dot backgrounds/sizes so only [position]'s dot reads as active. */
    private fun updateDotSelection(position: Int) {
        val container = binding.llSubDots
        val size = resources.getDimensionPixelSize(R.dimen.dot_size)
        val activeSize = resources.getDimensionPixelSize(R.dimen.dot_size_active)
        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i)
            val selected = i == position
            dot.setBackgroundResource(if (selected) R.drawable.dot_active else R.drawable.dot_inactive)
            val dim = if (selected) activeSize else size
            dot.layoutParams = dot.layoutParams.apply {
                width = dim
                height = dim
            }
        }
    }

    /** The four mutually-exclusive hero children. */
    private enum class Hero { SKELETON, EMPTY, CAROUSEL, ERROR }

    /**
     * Shows EXACTLY ONE hero child (the other three go GONE):
     *  - CAROUSEL when there are subscriptions to show;
     *  - SKELETON while the FIRST load is still in flight (no profile yet);
     *  - ERROR on a cold-load failure (no profile, an error, not loading);
     *  - EMPTY otherwise (loaded, no subscriptions, no cold-load error).
     */
    private fun renderHeroState() {
        val subs = currentSubs
        val profile = latestProfile
        val error = viewModel.error.value
        val loading = viewModel.loading.value
        val coldLoading = pendingFirstLoad || loading
        val state = when {
            subs.isNotEmpty() -> Hero.CAROUSEL
            coldLoading && profile == null -> Hero.SKELETON
            profile == null && error != null -> Hero.ERROR
            else -> Hero.EMPTY
        }
        binding.groupSubSkeleton.isVisible = state == Hero.SKELETON
        binding.groupSubEmpty.isVisible = state == Hero.EMPTY
        binding.groupSubCarousel.isVisible = state == Hero.CAROUSEL
        binding.groupAccountError.isVisible = state == Hero.ERROR
        if (state == Hero.SKELETON) startSkeletonPulse() else stopSkeletonPulse()
    }

    /**
     * Loops the skeleton's alpha 0.45↔1.0 (~900ms) so it reads as "loading". Reduced motion: hold a
     * static ~0.7 alpha instead (no animation), honouring the same reducedMotion gate the rest of the
     * screen uses.
     */
    private fun startSkeletonPulse() {
        val skeleton = binding.groupSubSkeleton
        if (skeleton.reducedMotion()) {
            skeletonAnimator?.cancel()
            skeletonAnimator = null
            skeleton.alpha = 0.7f
            return
        }
        if (skeletonAnimator?.isRunning == true) return
        skeletonAnimator = ValueAnimator.ofFloat(0.45f, 1.0f).apply {
            duration = 900L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { skeleton.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopSkeletonPulse() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        binding.groupSubSkeleton.alpha = 1f
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
        if (error == null) {
            renderHeroState()
            return
        }
        pendingFirstLoad = false
        // A COLD-LOAD failure (nothing to show yet) is surfaced by the error hero card, NOT a toast;
        // keep the error set so the card stays until the user retries. A payment/top-up diagnostic
        // still wins even in that state.
        val coldLoad = latestProfile == null && currentSubs.isEmpty()
        if (coldLoad && !awaitingPaymentError) {
            renderHeroState()
            return
        }
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

    // region sign out

    /**
     * 00-rules.md 7.5 prefers act-plus-undo over a confirmation, and this is the case it exempts:
     * signing out is not usefully undoable. "Отменить" would have to re-import the subscription
     * servers, restart their auto-update workers and re-establish the tunnel it had just torn
     * down, from a token it has already destroyed. So a confirm dialog is correct here, and it
     * follows the library's rules: the primary action is the verb it performs, «Выйти», never
     * «OK» and never a Да/Нет pair; cancel sits on the left and holds focus, so an Enter on a
     * keyboard or a TV remote can never sign somebody out by reflex.
     *
     * The body has two shapes because the tab does: it says the tunnel will drop only when there
     * is a tunnel to drop.
     */
    private fun confirmSignOut() {
        val body = if (viewModel.isTunnelRunning()) {
            R.string.account_logout_body_connected
        } else {
            R.string.account_logout_body
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_logout_title)
            .setMessage(body)
            .setNegativeButton(R.string.account_logout_cancel, null)
            .setPositiveButton(R.string.account_row_logout) { _, _ -> beginSignOut() }
            .create()
        dialog.show()
        // Departament.Dialog.Button.Destructive (styles.xml) is Departament.Dialog.Button plus
        // exactly this colour. MaterialAlertDialog styles all three buttons from the theme
        // overlay, so applying the one delta here beats replacing the button bar with a custom
        // view to get a per-button style.
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
            setTextColor(ContextCompat.getColor(context, R.color.color_destructive_text))
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.requestFocus()
    }

    /**
     * Runs the sign-out. Acknowledgement is immediate (the dialog closes and the row stops taking
     * taps, so the control is never left pressed with no response); the trailing spinner is
     * scheduled 300ms out and usually never appears, because the work is local (00-rules.md 7.3).
     *
     * There is no success branch here on purpose: the session flipping to LoggedOut is what ends
     * the busy state, via [onSessionCleared], and the visible change is the confirmation. A
     * Snackbar saying "you signed out" on top of a screen that just emptied itself is noise.
     */
    private fun beginSignOut() {
        val b = _binding ?: return
        b.rowLogout.isClickable = false
        b.rowLogout.contentDescription = getString(R.string.account_logout_progress)
        signOutSpinnerJob?.cancel()
        signOutSpinnerJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(300L)
            _binding?.pbLogout?.isVisible = true
        }
        viewModel.logout(onFailure = ::onSignOutFailed)
    }

    /** Clears the busy state. Safe to call when the view is already gone. */
    private fun endSignOutBusy() {
        signOutSpinnerJob?.cancel()
        signOutSpinnerJob = null
        _binding?.let {
            it.pbLogout.isVisible = false
            it.rowLogout.isClickable = true
            it.rowLogout.contentDescription = getString(R.string.account_row_logout)
        }
    }

    /**
     * The wipe threw, so the user is still signed in and nothing was half-removed that a retry
     * cannot finish (every step of the wipe is idempotent). 00-rules.md 1.4.8: a failure the user
     * can act on is a Snackbar with an action, never a Toast. Anchored above the bottom bar so
     * the action is reachable.
     *
     * There is no "the call never returned" branch to design: this backend has no logout endpoint
     * and issues a non-refreshable token, so sign-out never touches the network.
     */
    private fun onSignOutFailed() {
        endSignOutBusy()
        val b = _binding ?: return
        val bar = Snackbar.make(b.root, R.string.account_logout_failed, Snackbar.LENGTH_LONG)
            .setAction(R.string.account_retry) { beginSignOut() }
        activity?.findViewById<View>(R.id.bottom_nav)?.let { bar.setAnchorView(it) }
        bar.show()
    }

    // endregion

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
