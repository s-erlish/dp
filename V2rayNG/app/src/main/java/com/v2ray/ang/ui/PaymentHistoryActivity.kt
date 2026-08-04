package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.R
import com.v2ray.ang.auth.AccountCache
import com.v2ray.ang.auth.ApiError
import com.v2ray.ang.auth.dto.PaymentDto
import com.v2ray.ang.databinding.ActivityPaymentHistoryBinding
import com.v2ray.ang.ui.adapter.PaymentsAdapter
import com.v2ray.ang.ui.component.SubPage
import com.v2ray.ang.ui.component.ToolbarBinder
import com.v2ray.ang.viewmodel.AccountViewModel
import kotlinx.coroutines.launch

/**
 * Dedicated, full-screen payment history. Replaces the cramped inline "показать ещё" list that
 * lived on the Account tab: here the whole list is shown and scrolls, and the BaseActivity toolbar
 * gives a real back affordance to close the screen.
 */
class PaymentHistoryActivity : BaseActivity() {

    private val binding by lazy { ActivityPaymentHistoryBinding.inflate(layoutInflater) }
    private val viewModel: AccountViewModel by viewModels()
    private val paymentsAdapter = PaymentsAdapter()

    /** Distinguishes "still loading, list legitimately empty" from "loaded, nothing to show". */
    private var loaded = false

    /**
     * True while the list on screen came from [AccountCache] and no network load has run. In this
     * state the [viewModel]'s payments StateFlow still holds its empty seed, which it replays to the
     * collector every time the screen returns to STARTED; those empty emissions must not clobber the
     * cached rows. Pull-to-refresh clears this so a genuinely empty result can render.
     */
    private var showingCache = false

    override fun onCreate(savedInstanceState: Bundle?) {
        SubPage.installTransitions(this)
        super.onCreate(savedInstanceState)
        // README §7's lekalo draws the title at 24sp/700 UNDER the back control, which
        // activity_base's 16sp MaterialToolbar cannot do — so the header is @layout/view_sub_header
        // inside this screen's own layout. The screen never used the base progress bar; its own
        // @id/progress_history sits over the list, where the rows it is waiting for will appear.
        setContentView(binding.root)
        ToolbarBinder.bind(
            root = binding.toolbar.root,
            title = getString(R.string.history_title),
            activity = this,
        )
        // The PAGE scrolls now, not the list (§7 wants the card to end where the operations
        // do), so the hairline listens to the NestedScrollView the rows sit in.
        ToolbarBinder.attachTo(binding.toolbar.root, binding.mainContent)
        binding.toolbar.toolbarNote.setText(R.string.account_hub_history_sub)
        binding.toolbar.toolbarNote.isVisible = true

        binding.rvPayments.layoutManager = LinearLayoutManager(this)
        binding.rvPayments.adapter = paymentsAdapter

        // Empty-state CTA: send the user straight into the buy flow.
        binding.btnHistoryBuy.setOnClickListener {
            startActivity(Intent(this, BuyTariffActivity::class.java))
        }

        binding.refreshLayout.setColorSchemeColors(resolveThemeColor(R.attr.iconTintBlue))
        binding.refreshLayout.setOnRefreshListener {
            loaded = false
            // Explicit refresh: leave cache-only mode so a genuinely empty result can render.
            showingCache = false
            viewModel.loadPayments()
        }

        observeState()

        // Serve fresh (< 1h) cached payments instantly and skip the initial network load; the
        // pull-to-refresh above still forces a reload that repopulates the cache.
        val cached = AccountCache.getPayments()
        if (cached != null) {
            loaded = true
            showingCache = true
            binding.progressHistory.visibility = View.GONE
            paymentsAdapter.submit(cached)
            if (cached.isEmpty()) {
                showEmptyBlock(getString(R.string.history_empty), withBuyCta = true)
            } else {
                hideEmptyBlock()
            }
        } else {
            showHistoryLoading()
            viewModel.loadPayments()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.payments.collect { renderPayments(it) } }
                launch { viewModel.error.collect { renderError(it) } }
            }
        }
    }

    private fun renderPayments(list: List<PaymentDto>) {
        // While showing cached rows (no network load run yet), ignore the ViewModel's empty seed
        // emission — replayed on every return to STARTED — so it can't wipe the cached list.
        if (showingCache && list.isEmpty() && !binding.refreshLayout.isRefreshing) return
        showingCache = false
        loaded = true
        binding.refreshLayout.isRefreshing = false
        binding.progressHistory.visibility = View.GONE
        paymentsAdapter.submit(list)
        AccountCache.putPayments(list)
        if (list.isEmpty()) {
            showEmptyBlock(getString(R.string.history_empty), withBuyCta = true)
        } else {
            hideEmptyBlock()
        }
    }

    private fun renderError(error: ApiError?) {
        if (error == null) return
        binding.refreshLayout.isRefreshing = false
        binding.progressHistory.visibility = View.GONE
        loaded = true
        // Only surface the error banner in the empty area if we have nothing to show. The buy CTA
        // stays hidden here — this is an error, not a genuine "no payments yet" empty state.
        if (paymentsAdapter.itemCount == 0) {
            showEmptyBlock(getString(messageFor(error)), withBuyCta = false)
        }
        viewModel.clearError()
    }

    private fun messageFor(error: ApiError): Int = when (error) {
        is ApiError.Network, ApiError.Timeout, ApiError.ServiceUnavailable ->
            R.string.history_error_network

        else -> R.string.history_error_generic
    }

    private fun showHistoryLoading() {
        if (loaded) return
        binding.progressHistory.visibility = View.VISIBLE
        hideEmptyBlock()
    }

    /**
     * Reveals the centred empty/error block ([R.id.ll_empty_state]) with [message]. [withBuyCta]
     * shows the «Купить подписку» button — only for the genuine "no payments yet" empty state, not
     * for errors.
     */
    private fun showEmptyBlock(message: String, withBuyCta: Boolean) {
        binding.tvEmpty.text = message
        binding.tvEmpty.visibility = View.VISIBLE
        binding.btnHistoryBuy.visibility = if (withBuyCta) View.VISIBLE else View.GONE
        binding.llEmptyState.visibility = View.VISIBLE
    }

    private fun hideEmptyBlock() {
        binding.tvEmpty.visibility = View.GONE
        binding.btnHistoryBuy.visibility = View.GONE
        binding.llEmptyState.visibility = View.GONE
    }

    /** Resolves a theme colour attr (e.g. [R.attr.iconTintBlue]) to an ARGB int for the current theme. */
    private fun resolveThemeColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
