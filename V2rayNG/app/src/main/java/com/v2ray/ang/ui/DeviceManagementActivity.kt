package com.v2ray.ang.ui

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.auth.AccountRepository
import com.v2ray.ang.auth.dto.DeviceDto
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
            val uuid = remnawaveUuid ?: resolveUuid()
            if (uuid.isNullOrBlank()) {
                hideLoading()
                showEmptyState(getString(R.string.devices_error_no_subscription), isError = true)
                return@launch
            }
            remnawaveUuid = uuid

            repo.getDevices(uuid)
                .onSuccess { render(it.items) }
                .onFailure {
                    showEmptyState(getString(R.string.devices_error_generic), isError = true)
                }
            hideLoading()
        }
    }

    /** Resolve the active subscription's remnawaveUuid via the repo (first non-blank one). */
    private suspend fun resolveUuid(): String? =
        repo.loadSubscriptions().getOrNull()
            ?.items
            ?.firstOrNull { it.remnawaveUuid.isNotBlank() }
            ?.remnawaveUuid

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
    }

    companion object {
        const val EXTRA_REMNAWAVE_UUID = "extra_remnawave_uuid"
    }
}
