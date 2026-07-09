package com.v2ray.ang.tv

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityTvSendBinding
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.QRCodeScannerHelper
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Phone-side "Send to TV" screen.
 *
 * The phone is the trusted, signed-in sender: the user picks one of their existing
 * subscriptions, scans the TV's pairing QR, and this screen POSTs the selected
 * subscription URL to the TV's one-shot LAN listener, authorized by the one-time
 * token embedded in the QR. The secret sub URL is never shown on screen.
 */
class TvSendActivity : BaseActivity() {

    private val binding by lazy { ActivityTvSendBinding.inflate(layoutInflater) }

    // Must be registered before the activity reaches STARTED (registerForActivityResult
    // requirement), so it is created here in onCreate rather than lazily on click.
    private lateinit var scannerHelper: QRCodeScannerHelper

    private var subscriptions: List<SubscriptionCache> = emptyList()
    private var selected: SubscriptionCache? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scannerHelper = QRCodeScannerHelper(this)
        setContentViewWithToolbar(binding.root, title = getString(R.string.tv_send_title))

        subscriptions = MmkvManager.decodeSubscriptions()
            .filter { it.subscription.url.isNotEmpty() }

        binding.tvInstructions.text = getString(R.string.tv_send_instructions)
        binding.tvSelected.text = getString(R.string.tv_send_none_selected)

        binding.btnPickSub.setOnClickListener { pickSubscription() }
        binding.btnScan.setOnClickListener { scanAndSend() }

        if (subscriptions.isEmpty()) {
            binding.tvStatus.text = getString(R.string.tv_send_no_subs)
            binding.btnPickSub.isEnabled = false
            binding.btnScan.isEnabled = false
        }
    }

    private fun pickSubscription() {
        if (subscriptions.isEmpty()) {
            toast(R.string.tv_send_no_subs)
            return
        }
        val labels = subscriptions.map { cache ->
            cache.subscription.remarks.ifEmpty { cache.subscription.url }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.tv_send_pick)
            .setItems(labels) { _, which ->
                selected = subscriptions[which]
                binding.tvSelected.text = getString(
                    R.string.tv_send_selected,
                    labels[which]
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun scanAndSend() {
        val sub = selected
        if (sub == null) {
            toast(R.string.tv_send_none_selected)
            return
        }
        scannerHelper.launch { scanResult ->
            val info = TvPairingProtocol.parsePairUri(scanResult)
            if (info == null) {
                toastError(R.string.tv_send_scanning_invalid)
                return@launch
            }
            sendToTv(info, sub)
        }
    }

    private fun sendToTv(info: TvPairingProtocol.PairInfo, sub: SubscriptionCache) {
        binding.tvStatus.text = getString(R.string.tv_send_sending)
        showLoading()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                postSubscription(info, sub)
            }
            hideLoading()
            when (result) {
                SendResult.SUCCESS -> setStatus(getString(R.string.tv_send_success))
                SendResult.DUPLICATE -> setStatus(getString(R.string.tv_send_duplicate))
                SendResult.UNAUTHORIZED -> setStatus(getString(R.string.tv_send_unauthorized))
                SendResult.UNREACHABLE -> setStatus(getString(R.string.tv_send_unreachable))
                SendResult.FAILED -> setStatus(getString(R.string.tv_send_failed))
            }
        }
    }

    private fun setStatus(message: String) {
        binding.tvStatus.text = message
        binding.tvStatus.visibility = View.VISIBLE
    }

    private enum class SendResult { SUCCESS, DUPLICATE, UNAUTHORIZED, UNREACHABLE, FAILED }

    private fun postSubscription(info: TvPairingProtocol.PairInfo, sub: SubscriptionCache): SendResult {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()

            val json = TvPairingProtocol.buildRequestJson(
                url = sub.subscription.url,
                remarks = sub.subscription.remarks
            )
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("http://${info.ip}:${info.port}${TvPairingProtocol.PAIR_PATH}")
                .addHeader("Authorization", TvPairingProtocol.BEARER_PREFIX + info.token)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> SendResult.SUCCESS
                    409 -> SendResult.DUPLICATE
                    401, 410 -> SendResult.UNAUTHORIZED
                    else -> SendResult.FAILED
                }
            }
        } catch (e: java.net.ConnectException) {
            LogUtil.e(AppConfig.TAG, "TvSendActivity: cannot reach TV", e)
            SendResult.UNREACHABLE
        } catch (e: java.net.SocketTimeoutException) {
            LogUtil.e(AppConfig.TAG, "TvSendActivity: timeout reaching TV", e)
            SendResult.UNREACHABLE
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "TvSendActivity: send failed", e)
            SendResult.FAILED
        }
    }
}
