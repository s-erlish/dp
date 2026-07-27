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
import androidx.fragment.app.activityViewModels
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
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
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

    /**
     * The shell's shared server state, scoped to the ACTIVITY — the same instance the Главная and
     * Серверы lists read. Signing out removes the account's подписки and their серверы from the
     * store, and this is what makes the lists on the other tabs stop showing them (A1).
     */
    private val mainViewModel: MainViewModel by activityViewModels()

    /** Adapter backing the subscription carousel (one page per sub). */
    private lateinit var subAdapter: SubscriptionPagerAdapter

    /** Last subscription list published; the first page is the active/root sub. */
    private var currentSubs: List<SubInfoDto> = emptyList()
    private var latestProfile: UserProfileDto? = null

    /** Carousel page the hero time block describes. Every sub has its own expiry. */
    private var selectedSubIndex: Int = 0

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

    // The retry bar from a failed sign-out, held so a wipe that finishes late (the watchdog gave
    // up, the work did not) can take back a message that has stopped being true.
    private var signOutBar: Snackbar? = null

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
        // The payment-method pick arrives as DATA, re-delivered to whichever view is alive when it
        // lands (D11). The sheet used to call a lambda that had captured this fragment's binding;
        // rotating with the sheet open and then picking a method invoked it against a view tree
        // that no longer existed.
        parentFragmentManager.setFragmentResultListener(
            REQUEST_TOP_UP_METHOD,
            viewLifecycleOwner,
        ) { _, bundle -> onTopUpMethodPicked(bundle) }
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
        signOutBar?.dismiss()
        signOutBar = null
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
                override fun onPageSelected(position: Int) {
                    selectedSubIndex = position
                    updateDotSelection(position)
                    // The hero figure belongs to the subscription the user is looking at, not to
                    // whichever one happens to be first.
                    renderTimeBlock()
                }
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
                // A top-up in flight owns the «Пополнить» control until the provider answers, so
                // a slow connection cannot be mistaken for a dead button and paid for twice (D10).
                launch { viewModel.paymentInFlight.collect { renderTopUpBusy(it) } }
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
        // The session did go away, so a "не удалось выйти" bar from a watchdog that fired early is
        // now a lie about the screen the user is looking at. Take it back.
        signOutBar?.dismiss()
        signOutBar = null
        // Idempotent, and the only thing that clears the ViewModel on the 401 route (an explicit
        // sign-out has already done it by the time we get here).
        viewModel.clearAccountData()
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
        selectedSubIndex = 0
        subAdapter.submit(emptyList())
        binding.llSubDots.removeAllViews()
        binding.llSubDots.isVisible = false
        binding.tvRowValueDevices.visibility = View.GONE
        binding.tvRowValueHistory.visibility = View.GONE
        renderProfile(null)
        renderHeroState()

        // A1. The sign-out wipe has already removed this account's подписки and their серверы from
        // the store (AccountSession.wipe -> SubscriptionSyncManager.removeAllManaged); what it
        // cannot do is repaint a list it does not own. Without this the Главная and Серверы tabs
        // went on showing — and offering to select — серверы that no longer exist anywhere but in
        // an in-memory cache, until something else happened to reload it.
        //
        // Safe on the OTHER route into this method too. An expired token takes
        // AccountSession.endSession, which deliberately deletes nothing; re-reading the store then
        // paints back exactly the same list. That distinction is the whole point of the two
        // methods and this call does not blur it: it re-reads, it never removes.
        mainViewModel.reloadServerList()
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
            // The whole ROW goes with it: now that the balance is a labelled value, leaving the label
            // behind would state «Баланс» followed by nothing, which reads as a broken figure rather
            // than as an absent one.
            balanceAnimator?.cancel()
            lastBalance = null
            binding.tvBalance.text = ""
            binding.rowBalance.isVisible = false
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
        binding.rowBalance.isVisible = true
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
        // A shorter list must not leave the hero pointing past its end.
        if (selectedSubIndex >= list.size) selectedSubIndex = 0
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
        renderTimeBlock()
    }

    /**
     * The tab's ONE Display figure, and it is time — not money (23-account-rework.md 1.2). The user
     * opens Аккаунт because the подписка is running out; the wallet answers that question only
     * indirectly, so the balance dropped to a labelled value and this took the hero slot.
     *
     * The silhouette is CONSTANT in every variant that has a date — label, figure + word, optional
     * detail — and only the COLOUR changes with health, so the card does not reflow as an account
     * ages. Above 30 days it states the date and stops; from 8 to 30 it adds the count as a detail
     * line; at 7 and below the count BECOMES the figure and the date drops to the detail, because
     * that is the window in which time turns into a decision.
     *
     * Perpetual and unknown have no date to set, so they render two text lines instead and the tab
     * shows no Display figure at all — the only two states in which that is true.
     */
    private fun renderTimeBlock() {
        val b = _binding ?: return
        val sub = currentSubs.getOrNull(selectedSubIndex) ?: currentSubs.firstOrNull()
        if (sub == null || !b.groupSubCarousel.isVisible) {
            b.llTimeBlock.isVisible = false
            return
        }
        b.llTimeBlock.isVisible = true

        val onSurface = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
        val amber = resolveThemeColor(R.attr.warning)
        val red = ContextCompat.getColor(requireContext(), R.color.color_destructive_text)

        val date = parseYmd(sub.expireAtIso)
        val daysLeft = date?.let { daysUntil(it) }

        // Two-line variants: nothing to set as a figure.
        if (date == null || daysLeft == null) {
            renderTimeLines(R.string.account_time_unknown, R.string.account_time_unknown_sub, onSurface)
            return
        }
        if (date.year >= PERPETUAL_YEAR || daysLeft > PERPETUAL_DAYS) {
            renderTimeLines(R.string.account_time_perpetual, R.string.account_time_perpetual_sub, onSurface)
            return
        }

        b.tvTimeLabel.isVisible = true
        b.tvTimeFigure.isVisible = true

        val dateWord = monthWord(date)
        when {
            daysLeft < 0 -> {
                b.tvTimeLabel.setText(R.string.account_time_expired)
                b.tvTimeLabel.setTextColor(red)
                b.tvTimeFigure.text = date.day.toString()
                b.tvTimeWord.text = dateWord
                b.tvTimeDetail.isVisible = false
                tintFigure(red)
            }

            daysLeft == 0 -> {
                b.tvTimeLabel.setText(R.string.account_time_today)
                b.tvTimeLabel.setTextColor(amber)
                b.tvTimeFigure.text = date.day.toString()
                b.tvTimeWord.text = dateWord
                b.tvTimeDetail.setText(R.string.account_time_detail_today)
                b.tvTimeDetail.isVisible = true
                tintFigure(amber)
            }

            daysLeft <= URGENT_DAYS -> {
                b.tvTimeLabel.setText(R.string.account_time_left)
                b.tvTimeLabel.setTextColor(amber)
                b.tvTimeFigure.text = daysLeft.toString()
                b.tvTimeWord.text = resources.getQuantityString(R.plurals.account_days, daysLeft)
                b.tvTimeDetail.text = getString(R.string.account_time_detail_until, longDate(date))
                b.tvTimeDetail.isVisible = true
                tintFigure(amber)
            }

            else -> {
                b.tvTimeLabel.setText(R.string.account_time_active)
                b.tvTimeLabel.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                b.tvTimeFigure.text = date.day.toString()
                b.tvTimeWord.text = dateWord
                if (daysLeft <= COUNTDOWN_FROM_DAYS) {
                    val count = resources.getQuantityString(R.plurals.account_days, daysLeft)
                    b.tvTimeDetail.text = getString(R.string.account_time_detail_left, daysLeft, count)
                    b.tvTimeDetail.isVisible = true
                } else {
                    b.tvTimeDetail.isVisible = false
                }
                tintFigure(onSurface)
            }
        }
    }

    /** The date-less variants: a Title line and a Subtitle line, no figure, no label. */
    private fun renderTimeLines(titleRes: Int, subtitleRes: Int, color: Int) {
        val b = _binding ?: return
        b.tvTimeLabel.isVisible = false
        b.tvTimeFigure.isVisible = false
        b.tvTimeWord.setText(titleRes)
        b.tvTimeWord.setTextColor(color)
        b.tvTimeDetail.setText(subtitleRes)
        b.tvTimeDetail.isVisible = true
    }

    /** Colour is the state channel here, so the figure and its word always move together. */
    private fun tintFigure(color: Int) {
        val b = _binding ?: return
        b.tvTimeFigure.setTextColor(color)
        b.tvTimeWord.setTextColor(color)
    }

    /**
     * The word beside the figure: a genitive month, plus the year when it is not this one. Set in
     * the UI face, never the figure face — Space Grotesk carries no Cyrillic at all
     * (23-account-rework.md 4.2), so a Russian month in it would render as fallback glyphs.
     */
    private fun monthWord(date: Ymd): String {
        val months = resources.getStringArray(R.array.account_months_genitive)
        val name = months.getOrNull(date.month - 1) ?: return date.year.toString()
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        return if (date.year == thisYear) name else getString(R.string.account_month_year, name, date.year)
    }

    /** «3 августа» — one phrase, one face, for the detail line. */
    private fun longDate(date: Ymd): String = getString(R.string.account_date_long, date.day, monthWord(date))

    private fun resolveThemeColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
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
     *
     * The amount rides ALONG with the pick rather than being captured in a callback, so the charge
     * is right even if this fragment's view is rebuilt while the sheet is open (D11).
     */
    private fun showPaymentMethodSheet(amount: Double) {
        val methods = viewModel.publicConfig.value?.plategaMethods?.map { it.id to it.label } ?: emptyList()
        if (methods.isEmpty()) {
            toastError(R.string.account_checkout_no_browser)
            return
        }
        PaymentMethodSheet.show(
            parentFragmentManager,
            REQUEST_TOP_UP_METHOD,
            getString(R.string.account_top_up_title),
            null,
            methods,
            Bundle().apply { putDouble(EXTRA_TOP_UP_AMOUNT, amount) },
        )
    }

    private fun onTopUpMethodPicked(result: Bundle) {
        val id = result.getString(PaymentMethodSheet.RESULT_METHOD_ID) ?: return
        val amount = result.getDouble(EXTRA_TOP_UP_AMOUNT, 0.0)
        if (amount <= 0.0) {
            toastError(R.string.account_top_up_invalid)
            return
        }
        if (viewModel.paymentInFlight.value) return
        if (id == PaymentMethodSheet.ID_BALANCE) {
            viewModel.payWithBalance(PaymentRequestDto(amount = amount, currency = "RUB")) {
                toastSuccess(R.string.account_top_up_success)
                viewModel.refreshProfile()
                viewModel.loadSubscriptions()
            }
        } else {
            awaitingPaymentError = true
            viewModel.buy(
                PaymentRequestDto(amount = amount, currency = "RUB", paymentMethod = id.toIntOrNull()),
                onInit = ::openCheckout,
            )
        }
    }

    /**
     * «Пополнить» while a charge is in flight: it stops taking taps and says what it is doing, so
     * a slow provider is never mistaken for a dead control (D10). The request itself refuses to
     * run twice regardless — see [AccountViewModel.paymentInFlight].
     */
    private fun renderTopUpBusy(busy: Boolean) {
        val b = _binding ?: return
        b.btnTopUp.isEnabled = !busy
        b.btnTopUp.text = getString(if (busy) R.string.account_pay_in_progress else R.string.account_top_up)
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
     * The sign-out did not complete: either the wipe threw, or it stopped making progress long
     * enough that [AccountViewModel.logout]'s watchdog stopped waiting on it. Both leave the user
     * signed in with nothing half-removed that a retry cannot finish (every step of the wipe is
     * idempotent), and both read the same from here, so they get one message and one action
     * rather than a diagnosis the user cannot act on differently.
     *
     * This is the branch that keeps a stalled sign-out from being a spinner with no end: the row
     * goes back to being tappable, the spinner stops, and the way forward is on screen.
     *
     * 00-rules.md 1.4.8: a failure the user can act on is a Snackbar with an action, never a
     * Toast. Anchored above the bottom bar so the action is reachable. Held in [signOutBar] so a
     * late-finishing wipe can dismiss it instead of leaving a contradiction on screen.
     */
    private fun onSignOutFailed() {
        endSignOutBusy()
        val b = _binding ?: return
        val bar = Snackbar.make(b.root, R.string.account_logout_failed, Snackbar.LENGTH_LONG)
            .setAction(R.string.account_retry) { beginSignOut() }
        // Only anchor to a bar that is actually on screen; the bottom nav is hidden during
        // onboarding, and anchoring to a gone view drops the snackbar off the bottom edge.
        activity?.findViewById<View>(R.id.bottom_nav)?.takeIf { it.isVisible }
            ?.let { bar.setAnchorView(it) }
        signOutBar = bar
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

    private companion object {
        /** Fragment-result key for the top-up method pick. */
        const val REQUEST_TOP_UP_METHOD = "account_top_up_method"

        /** The amount travels with the pick, so a rebuilt view still charges the right sum. */
        const val EXTRA_TOP_UP_AMOUNT = "extra_top_up_amount"

        /** Mirror of the desktop's IsEffectivelyPerpetual: a sentinel expiry, not a real date. */
        const val PERPETUAL_YEAR = 2099
        const val PERPETUAL_DAYS = 3650

        /** At and below this the day COUNT becomes the hero figure (23-account-rework.md 4.3). */
        const val URGENT_DAYS = 7

        /** Above this a count is machine output — the card states the date and stops. */
        const val COUNTDOWN_FROM_DAYS = 30
    }
}

/** A calendar date, which is the granularity the user reads an expiry at. */
private data class Ymd(val year: Int, val month: Int, val day: Int)

/**
 * Takes the DATE part of an ISO-8601 timestamp and reads it as a local calendar date.
 *
 * Deliberately not an instant: the card says «Активна до 3 августа», and the day a подписка ends is
 * what the user is counting, not the wall-clock moment inside it. Parsing the offset would buy
 * precision nobody reads and a timezone bug on every device that is not on the backend's clock.
 */
private fun parseYmd(iso: String?): Ymd? {
    if (iso.isNullOrBlank()) return null
    val parts = iso.substringBefore('T').substringBefore(' ').split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    return Ymd(year, month, day)
}

/**
 * Whole days from today to [target] in the device's own zone: negative once it is past, 0 on the
 * day itself. Both ends are snapped to midnight and the quotient is rounded, so the hour a DST
 * change adds or removes cannot move a boundary by a day.
 */
private fun daysUntil(target: Ymd): Int {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayMs = cal.timeInMillis
    cal.set(target.year, target.month - 1, target.day, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val targetMs = cal.timeInMillis
    return Math.round((targetMs - todayMs).toDouble() / 86_400_000.0).toInt()
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
