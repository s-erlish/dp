package com.v2ray.ang.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.R
import com.v2ray.ang.auth.ApiError
import com.v2ray.ang.auth.dto.PaymentDto
import com.v2ray.ang.databinding.ActivityPaymentHistoryBinding
import com.v2ray.ang.ui.adapter.PaymentsAdapter
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(
            binding.root,
            showHomeAsUp = true,
            title = getString(R.string.history_title),
        )

        binding.rvPayments.layoutManager = LinearLayoutManager(this)
        binding.rvPayments.adapter = paymentsAdapter

        binding.refreshLayout.setColorSchemeColors(resolveThemeColor(R.attr.iconTintBlue))
        binding.refreshLayout.setOnRefreshListener {
            loaded = false
            viewModel.loadPayments()
        }

        observeState()
        showHistoryLoading()
        viewModel.loadPayments()
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
        loaded = true
        binding.refreshLayout.isRefreshing = false
        binding.progressHistory.visibility = View.GONE
        paymentsAdapter.submit(list)
        binding.tvEmpty.text = getString(R.string.history_empty)
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderError(error: ApiError?) {
        if (error == null) return
        binding.refreshLayout.isRefreshing = false
        binding.progressHistory.visibility = View.GONE
        loaded = true
        // Only surface the error banner in the empty area if we have nothing to show.
        if (paymentsAdapter.itemCount == 0) {
            binding.tvEmpty.text = getString(messageFor(error))
            binding.tvEmpty.visibility = View.VISIBLE
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
        binding.tvEmpty.visibility = View.GONE
    }

    /** Resolves a theme colour attr (e.g. [R.attr.iconTintBlue]) to an ARGB int for the current theme. */
    private fun resolveThemeColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
