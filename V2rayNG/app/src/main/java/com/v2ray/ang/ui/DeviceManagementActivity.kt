package com.v2ray.ang.ui

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.auth.AccountRepository
import com.v2ray.ang.auth.ApiError
import com.v2ray.ang.auth.dto.DeviceDto
import com.v2ray.ang.auth.dto.SubInfoDto
import com.v2ray.ang.databinding.ActivityDevicesBinding
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.adapter.DeviceAdapter
import com.v2ray.ang.viewmodel.AccountViewModel
import kotlinx.coroutines.launch

/**
 * Device Management screen: lists the devices (HWIDs) bound to the user's subscription and lets
 * them remove a device after a confirmation dialog.
 *
 * The subscription UUID may be supplied via [EXTRA_REMNAWAVE_UUID]; if absent the first
 * subscription with a non-blank remnawaveUuid is resolved from the account.
 *
 * Device read/delete are not exposed on [AccountViewModel], so they go through
 * [AccountRepository] directly (both return [Result]); the ViewModel is still used to resolve
 * the active subscription.
 */
class DeviceManagementActivity : BaseActivity() {

    private val binding by lazy { ActivityDevicesBinding.inflate(layoutInflater) }
    private val viewModel: AccountViewModel by viewModels()
    private val repo = AccountRepository()

    private val adapter by lazy { DeviceAdapter(::confirmDelete) }

    private var remnawaveUuid: String? = null

    /** How many devices the ACTIVE subscription reports as connected (from /subscription/all). */
    private var expectedCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.devices_title))

        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = adapter

        remnawaveUuid = intent.getStringExtra(EXTRA_REMNAWAVE_UUID)?.takeIf { it.isNotBlank() }
        loadDevices()
    }

    private fun loadDevices() {
        showLoading()
        showEmpty(false)
        lifecycleScope.launch {
            var uuid = remnawaveUuid
            if (uuid.isNullOrBlank()) {
                val sub = resolveActiveSub()
                uuid = sub?.remnawaveUuid?.takeIf { it.isNotBlank() }
                expectedCount = sub?.connectedDevices ?: 0
            }
            if (uuid.isNullOrBlank()) {
                hideLoading()
                showEmptyState(getString(R.string.devices_error_no_subscription), isError = true)
                return@launch
            }
            remnawaveUuid = uuid

            repo.getDevices(uuid)
                .onSuccess { result ->
                    render(result.devices)
                    // The list parsed empty but the subscription says devices ARE connected:
                    // the /client/devices response shape doesn't match. Surface the raw response
                    // so the real backend contract can be diagnosed and fixed precisely.
                    if (result.devices.isEmpty() && expectedCount > 0) {
                        showDiagnostic(
                            getString(R.string.devices_diag_empty, expectedCount),
                            result.httpCode,
                            result.rawBody,
                        )
                    }
                }
                .onFailure { error ->
                    showEmptyState(getString(R.string.devices_error_generic), isError = true)
                    val (code, detail) = httpDetailOf(error)
                    showDiagnostic(getString(R.string.devices_diag_failed), code, detail)
                }
            hideLoading()
        }
    }

    /** Resolve the active subscription (first with a non-blank remnawaveUuid) via the repo. */
    private suspend fun resolveActiveSub(): SubInfoDto? =
        repo.loadSubscriptions().getOrNull()
            ?.items
            ?.firstOrNull { it.remnawaveUuid.isNotBlank() }

    /** Extracts a best-effort HTTP status + sanitized body from an [ApiError] for diagnostics. */
    private fun httpDetailOf(error: Throwable): Pair<Int, String?> = when (error) {
        is ApiError.Server -> error.code to error.detail
        is ApiError.Unauthorized -> 401 to error.detail
        is ApiError.NotFound -> 404 to null
        is ApiError.Gone -> 410 to null
        is ApiError.RateLimited -> 429 to null
        is ApiError.ServiceUnavailable -> 503 to null
        else -> 0 to null
    }

    /** Small dialog with the raw HTTP status + response body, safe to screenshot and share. */
    private fun showDiagnostic(summary: String, httpCode: Int, body: String?) {
        val codeLine = if (httpCode > 0) getString(R.string.devices_diag_http, httpCode) else ""
        val bodyText = body?.trim()?.takeIf { it.isNotBlank() } ?: getString(R.string.devices_diag_no_body)
        val message = listOf(summary, codeLine, bodyText).filter { it.isNotBlank() }.joinToString("\n\n")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.devices_diag_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun render(devices: List<DeviceDto>) {
        adapter.submit(devices)
        if (devices.isEmpty()) {
            showEmptyState(getString(R.string.devices_empty), isError = false)
        } else {
            showEmpty(false)
        }
    }

    private fun confirmDelete(device: DeviceDto) {
        val name = device.deviceModel?.takeIf { it.isNotBlank() }
            ?: device.platform?.takeIf { it.isNotBlank() }
            ?: getString(R.string.devices_unknown_model)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.devices_delete_title)
            .setMessage(getString(R.string.devices_delete_message, name))
            .setPositiveButton(R.string.devices_delete_confirm) { _, _ -> deleteDevice(device) }
            .setNegativeButton(R.string.devices_delete_cancel, null)
            .show()
    }

    private fun deleteDevice(device: DeviceDto) {
        val uuid = remnawaveUuid
        if (uuid.isNullOrBlank() || device.hwid.isBlank()) {
            toastError(R.string.devices_error_delete)
            return
        }
        showLoading()
        lifecycleScope.launch {
            repo.deleteDevice(device.hwid, uuid)
                .onSuccess {
                    toastSuccess(R.string.devices_deleted)
                    loadDevices()
                }
                .onFailure {
                    hideLoading()
                    toastError(R.string.devices_error_delete)
                }
        }
    }

    /** Shows the empty/error panel with [message], hiding the list. */
    private fun showEmptyState(message: String, isError: Boolean) {
        binding.tvDevicesEmptyTitle.text = message
        binding.tvDevicesEmptyHint.visibility = if (isError) View.GONE else View.VISIBLE
        showEmpty(true)
    }

    private fun showEmpty(show: Boolean) {
        binding.layoutDevicesEmpty.visibility = if (show) View.VISIBLE else View.GONE
        binding.rvDevices.visibility = if (show) View.GONE else View.VISIBLE
        // Hide the "devices connected to your subscription" subtitle while the empty/error
        // overlay is up — it contradicts "no devices" / "subscription not found".
        binding.tvDevicesSubtitle.visibility = if (show) View.GONE else View.VISIBLE
    }

    companion object {
        const val EXTRA_REMNAWAVE_UUID = "extra_remnawave_uuid"
    }
}
