package com.v2ray.ang.tv

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
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
 * Phone-side "Отправить на TV" screen.
 *
 * New flow (scan-first): the user scans the TV's pairing QR FIRST, then picks which of
 * their subscriptions to send. Once a subscription is ticked and «Отправить» pressed,
 * its URL is POSTed to the TV's one-shot LAN listener, authorized by the one-time token
 * embedded in the QR. The secret sub URL is never shown on screen.
 */
class TvSendActivity : BaseActivity() {

    private val binding by lazy { ActivityTvSendBinding.inflate(layoutInflater) }

    // Must be registered before the activity reaches STARTED (registerForActivityResult
    // requirement), so it is created here in onCreate rather than lazily on click.
    private lateinit var scannerHelper: QRCodeScannerHelper

    private var subscriptions: List<SubscriptionCache> = emptyList()

    // Rendezvous info from the scanned TV QR; kept so the follow-up "pick + send" step
    // knows where to POST.
    private var pairInfo: TvPairingProtocol.PairInfo? = null

    // Auto-launch the scanner only once (avoids re-scanning on rotation/resume).
    private var scanLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scannerHelper = QRCodeScannerHelper(this)
        setContentViewWithToolbar(binding.root, title = getString(R.string.tv_send_title))

        subscriptions = MmkvManager.decodeSubscriptions()
            .filter { it.subscription.url.isNotEmpty() }

        binding.btnScan.setOnClickListener { startScan() }
        binding.btnSend.setOnClickListener { sendSelected() }

        if (subscriptions.isEmpty()) {
            binding.btnScan.isEnabled = false
            setStatus(getString(R.string.tv_send_no_subs))
        }
    }

    override fun onResume() {
        super.onResume()
        // "Tapping «Отправить на TV» launches the scan directly": auto-open the scanner once.
        if (!scanLaunched && subscriptions.isNotEmpty()) {
            scanLaunched = true
            binding.root.post { startScan() }
        }
    }

    private fun startScan() {
        if (subscriptions.isEmpty()) {
            toast(R.string.tv_send_no_subs)
            return
        }
        scannerHelper.launch { scanResult ->
            val info = TvPairingProtocol.parsePairUri(scanResult)
            if (info == null) {
                setStatus(getString(R.string.tv_send_scanning_invalid))
                toastError(R.string.tv_send_scanning_invalid)
                return@launch
            }
            pairInfo = info
            showPicker()
        }
    }

    /** Reveals the subscription picker after a successful TV scan. */
    private fun showPicker() {
        binding.tvStatus.visibility = View.GONE
        binding.radioSubs.removeAllViews()
        subscriptions.forEachIndexed { index, cache ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = cache.subscription.remarks.ifEmpty { getString(R.string.app_name) }
                tag = index
                minHeight = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
                setPadding(paddingLeft + 12, paddingTop, paddingRight, paddingBottom)
            }
            binding.radioSubs.addView(rb)
        }
        binding.radioSubs.setOnCheckedChangeListener { _, checkedId ->
            binding.btnSend.isEnabled = checkedId != -1
        }
        binding.layoutPick.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false
    }

    private fun selectedSubscription(): SubscriptionCache? {
        val checkedId = binding.radioSubs.checkedRadioButtonId
        if (checkedId == -1) return null
        val index = binding.radioSubs.findViewById<RadioButton>(checkedId)?.tag as? Int ?: return null
        return subscriptions.getOrNull(index)
    }

    private fun sendSelected() {
        val info = pairInfo
        if (info == null) {
            toastError(R.string.tv_send_scanning_invalid)
            return
        }
        val sub = selectedSubscription()
        if (sub == null) {
            toast(R.string.tv_send_pick_title)
            return
        }
        sendToTv(info, sub)
    }

    private fun sendToTv(info: TvPairingProtocol.PairInfo, sub: SubscriptionCache) {
        setStatus(getString(R.string.tv_send_sending))
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
