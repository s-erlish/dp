package com.v2ray.ang.core

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.dto.OutboundTrafficStat
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.service.CoreProxyOnlyService
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.service.DialerNativeService
import com.v2ray.ang.service.DialerWebviewService
import com.v2ray.ang.service.IDialerService
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.ProcessFinder
import java.lang.ref.SoftReference
import java.net.InetSocketAddress

object CoreServiceManager {

    /**
     * Wall-clock millis the running tunnel came up, or 0 when nothing is up.
     *
     * The key is the one the UI has always used, so a session that is live across the upgrade keeps
     * its clock instead of restarting at zero.
     */
    private const val KEY_SESSION_STARTED_AT = "cache_connection_start_time"

    private val coreController: CoreController = CoreNativeManager.newCoreController(CoreCallback())
    private val mMsgReceive = ReceiveMessageHandler()
    private var currentConfig: ProfileItem? = null
    private var processFinder: XrayProcessFinder? = null
    private var browserDialer: IDialerService? = null

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            val service = value?.get()?.getService()
            CoreNativeManager.initCoreEnv(service)
            if (service != null && processFinder == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                processFinder = XrayProcessFinder(service)
                coreController.registerProcessFinder(processFinder)
            }
        }

    /**
     * Starts the V2Ray service from a toggle action.
     * @param context The context from which the service is started.
     * @return True if the service was started successfully, false otherwise.
     */
    fun startVServiceFromToggle(context: Context): Boolean {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            context.toast(R.string.app_tile_first_use)
            return false
        }
        try {
            startContextService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: ${e.message}", e)
            context.toast(e.message ?: e.javaClass.simpleName)
            return false
        }
        return true
    }

    /**
     * Starts the V2Ray service.
     * @param context The context from which the service is started.
     * @param guid The GUID of the server configuration to use (optional).
     */
    fun startVService(context: Context, guid: String? = null) {
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: startVService from ${context::class.java.simpleName}")

        // A CALLER'S GUID IS ACCEPTED ONLY IF IT NAMES A SERVER THAT EXISTS. The one caller that
        // passes one is Tasker, which stores the guid in a task the user built weeks ago — and
        // every подписка refresh since has deleted the profile and minted a new guid in its place.
        // Writing that dead guid replaced a perfectly good selection with nothing, so the automation
        // failed AND left the app unable to connect afterwards until the user re-picked a server.
        // A guid we cannot honour changes nothing; the start below then runs on the selection the
        // user already has, which is the closest thing to what was asked for.
        if (guid != null) {
            if (MmkvManager.decodeServerConfig(guid) != null) {
                MmkvManager.setSelectServer(guid)
            } else {
                LogUtil.w(AppConfig.TAG, "StartCore-Manager: requested server no longer exists, keeping the current selection")
            }
        }

        try {
            startContextService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: ${e.message}", e)
            context.toast(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Stops the V2Ray service.
     * @param context The context from which the service is stopped.
     */
    fun stopVService(context: Context) {
        //context.toast(R.string.toast_services_stop)
        MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    /**
     * Checks if the V2Ray service is running.
     * @return True if the service is running, false otherwise.
     */
    fun isRunning() = coreController.isRunning

    /**
     * **When the CURRENT tunnel came up**, in wall-clock millis, or 0 when none is up.
     *
     * The session clock on Главная counts from here, and the whole point is that here is not the
     * UI. It used to be: `HomeFragment` stamped the instant when it first observed a running state
     * and cleared it when it observed a stopped one — and `MainViewModel.startListenBroadcast()`
     * publishes `isRunning = false` on every single activity start, BEFORE the service has answered
     * the registration handshake. So closing the app and coming back cleared the stamp, the real
     * «running» arrived a moment later, found nothing stored, and stamped `now`. The counter
     * measured how long the screen had been open. The owner: «когда закрываешь приложение и
     * заходишь назад, время сессии сбивается».
     *
     * Written by [markSessionStarted] / [markSessionStopped] on either side of the core loop, which
     * is the only pair of moments that means anything: the tunnel is up, or it is not. The screen
     * is a reader now, and an optimistic guess it makes about the state it has not been told yet
     * cannot destroy the fact.
     *
     * MMKV in `MULTI_PROCESS_MODE`, because that matters here: the core runs in
     * `:RunSoLibV2RayDaemon` and the UI does not.
     */
    fun sessionStartedAt(): Long = MmkvManager.decodeSettingsLong(KEY_SESSION_STARTED_AT, 0L)

    /**
     * Stamps the session start at the moment the core is confirmed running.
     *
     * Every arrival here is a NEW tunnel — [startCoreLoop] refuses outright while one is already
     * up — so this always writes. Switching server, and the auto-fallback switching it for you,
     * both come through here and both are new sessions, which is what the counter should say.
     *
     * A start that throws never reaches this line, so a failed attempt leaves no clock behind.
     */
    private fun markSessionStarted() {
        MmkvManager.encodeSettings(KEY_SESSION_STARTED_AT, System.currentTimeMillis())
    }

    /** Ends the session: there is no tunnel, so there is no clock. */
    private fun markSessionStopped() {
        MmkvManager.encodeSettings(KEY_SESSION_STARTED_AT, 0L)
    }

    /**
     * Gets the name of the currently running server.
     * @return The name of the running server.
     */
    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    /**
     * Starts the context service for V2Ray.
     * Chooses between VPN service or Proxy-only service based on user settings.
     * @param context The context from which the service is started.
     * @throws IllegalStateException if the core is already running, no server is selected,
     *   server config cannot be decoded, or server configuration is invalid.
     * @throws Exception if the foreground service fails to start.
     */
    @Throws(Exception::class)
    private fun startContextService(context: Context) {
        if (coreController.isRunning) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Core already running")
            return
        }

        val guid = MmkvManager.getSelectServer()
            ?: run {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: No server selected")
                error(context.getString(R.string.app_tile_first_use))
            }

        val config = MmkvManager.decodeServerConfig(guid)
            ?: run {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to decode server config")
                error(context.getString(R.string.toast_config_file_invalid))
            }

        if (!config.configType.isComplexType()
            && !Utils.isValidUrl(config.server)
            && !Utils.isPureIpAddress(config.server.orEmpty())
        ) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Invalid server configuration")
            error(context.getString(R.string.toast_config_file_invalid))
        }

        // refresh socks port when enabled dynamic socks port
        SettingsManager.refreshRuntimeSocksPort()

//        val result = V2rayConfigUtil.getV2rayConfig(context, guid)
//        if (!result.status) error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            context.toast(R.string.toast_warning_pref_proxysharing_short)
        } else {
            // The old system-style «Запуск служб» toast is suppressed here (mirrors the already
            // commented stop toast above): the connect screen now shows a single custom neutral
            // gray «Подключение…» toast from MainActivity instead. Proxy-sharing warning kept.
            //context.toast(R.string.toast_services_start)
        }

        val isVpnMode = SettingsManager.isVpnMode()
        val intent = if (isVpnMode) {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting VPN service")
            Intent(context.applicationContext, CoreVpnService::class.java)
        } else {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting Proxy service")
            Intent(context.applicationContext, CoreProxyOnlyService::class.java)
        }

        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Missing permission to start foreground service", e)
            throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Foreground service start not allowed", e)
                throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
            }
            throw e
        }
    }

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     * Starts the V2Ray core service.
     */
    fun startCoreLoop(vpnInterface: ParcelFileDescriptor?): Boolean {
        if (coreController.isRunning) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Core already running")
            return false
        }

        val service = getService()
        if (service == null) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Service is null")
            return false
        }

        try {
            doStartCoreLoop(service, vpnInterface)
            return true
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: $message", e)
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, message)
            NotificationManager.cancelNotification()
            return false
        }
    }

    @Throws(Exception::class)
    private fun doStartCoreLoop(service: Service, vpnInterface: ParcelFileDescriptor?) {
        val guid = MmkvManager.getSelectServer() ?: error("No server selected")
        val config = MmkvManager.decodeServerConfig(guid) ?: error("Failed to decode server config")

        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting core loop for ${config.remarks}")
        val result = CoreConfigManager.getV2rayConfig(service, guid)
        LogUtil.d(AppConfig.TAG, result.content)
        if (!result.status) {
            error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })
        }

        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
        mFilter.addAction(Intent.ACTION_SCREEN_ON)
        mFilter.addAction(Intent.ACTION_SCREEN_OFF)
        mFilter.addAction(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(service, mMsgReceive, mFilter, Utils.receiverFlags())

        currentConfig = config
        var tunFd = vpnInterface?.fd ?: 0
        val dialerAddr = if (currentConfig?.browserDialerMode.isNullOrEmpty()) {
            ""
        } else {
            "127.0.0.1:${Utils.findRandomFreePort()}"
        }
        if (SettingsManager.isUsingHevTun()) {
            tunFd = 0
        }

        NotificationManager.showNotification(currentConfig)
        CoreNativeManager.reconcileBrowserDialer(dialerAddr)

        // Memory limit: SettingsManager.isMemoryLimitEnabled()/getMemoryLimit() (MB) hold the
        // user's requested soft cap. Enforcement WOULD be applied here, right before the core
        // loop starts (e.g. a Go-side runtime/debug.SetMemoryLimit or a GOMEMLIMIT env read at
        // core init). The prebuilt libv2ray/AndroidLibXrayLite binding currently exposes no such
        // setter, so no cap can be applied from Kotlin. Wiring it up requires a core-lib change
        // (add an exported setter to libv2ray, then call it here with getMemoryLimit()*1024*1024
        // when isMemoryLimitEnabled() is true, or leave unbounded when disabled).
        coreController.startLoop(result.content, tunFd)

        if (!coreController.isRunning) {
            error("Core failed to start")
        }

        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }
        if (config.browserDialerMode == "OkHttp") {
            browserDialer = DialerNativeService()
            browserDialer!!.start(service, dialerAddr)
        } else if (config.browserDialerMode == "WebView") {
            browserDialer = DialerWebviewService()
            browserDialer!!.start(service, dialerAddr)
        }

        // The session clock starts HERE, before the UI is told anything — see [sessionStartedAt].
        // The screen reads this instant; it does not decide it.
        markSessionStarted()

        MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
        NotificationManager.startSpeedNotification()
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core started successfully")
    }

    /**
     * Stops the V2Ray core service.
     * Unregisters broadcast receivers, stops notifications, and shuts down plugins.
     * @return True if the core was stopped successfully, false otherwise.
     */
    fun stopCoreLoop(): Boolean {
        val service = getService() ?: return false

        // The session is over the moment the stop is asked for, whichever branch below announces
        // it. Cleared here rather than beside MSG_STATE_STOP_SUCCESS so the coroutine branch and
        // the immediate one cannot disagree about it.
        markSessionStopped()

        // MSG_STATE_STOP_SUCCESS is what tells the UI the tunnel is down, and the UI starts the next
        // core as soon as it arrives. So it must not be sent while stopLoop() is still running — that
        // made switching servers a race the new core could lose: startCoreLoop() would find the old
        // core still running and either tear the tunnel down entirely (VPN mode) or, because
        // CoreProxyOnlyService ignored the result, silently keep the PREVIOUS server up while the UI
        // showed the new one. Announce the stop only once it has actually happened.
        if (coreController.isRunning) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coreController.stopLoop()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop V2Ray loop", e)
                }
                MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
            }
        } else {
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        }

        // Close existing browser dialer
        CoreNativeManager.reconcileBrowserDialer("")
        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }

        NotificationManager.cancelNotification()

        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to unregister receiver", e)
        }

        return true
    }

    /**
     * Queries and resets all outbound traffic counters in one core call.
     * Go side format: tag,direction,value;tag,direction,value;
     */
    fun queryAllOutboundTrafficStats(): List<OutboundTrafficStat> {
        val payload = coreController.queryAllOutboundTrafficStats()

        val result = ArrayList<OutboundTrafficStat>()

        payload.split(';').forEach { entry ->
            if (entry.isBlank()) return@forEach

            val parts = entry.split(',', limit = 3)
            if (parts.size != 3) return@forEach

            val value = parts[2].toLongOrNull() ?: return@forEach

            result.add(
                OutboundTrafficStat(
                    tag = parts[0],
                    direction = parts[1],
                    value = value,
                )
            )
        }
//        LogUtil.d(AppConfig.TAG, "Queried outbound traffic stats: $result")
        return result
    }

    /**
     * Measures the connection delay for the current V2Ray configuration.
     * Tests with primary URL first, then falls back to alternative URL if needed.
     *
     * ## THE SECOND HALF OF THIS USED TO BE A SECOND NETWORK REQUEST WITH NO READER
     *
     * Upstream answers a successful probe by ALSO fetching the exit IP (`SpeedtestManager
     * .getRemoteIPInfo`, an HTTP call through the local proxy to `api.ip.sb`) and broadcasting a
     * human-readable sentence — «Успешно, задержка 123 мс» plus the IP — as
     * `MSG_MEASURE_DELAY_SUCCESS`, for a status line this product does not have. In this fork that
     * sentence reached exactly one observer, `HomeFragment`'s `updateTestResultAction`, and that
     * observer never looked at the string: it threw the whole list at `notifyDataSetChanged()`,
     * having first re-decoded every подписка out of MMKV to rebuild the section headers. So the leg
     * cost, EVERY THIRTY SECONDS FOR AS LONG AS A TUNNEL IS UP:
     *
     *  - one extra HTTP request through the proxy — 120 an hour, radio included, discarded on
     *    arrival, to a service the user cannot even point elsewhere any more (`pref_ip_api_url` is
     *    off the settings screen by 12-settings.md 6.1);
     *  - two full main-thread list rebuilds, one per broadcast, for a message that names no server
     *    and changes no stored delay.
     *
     * What the screen actually reads is the NUMBER, and it arrives on its own channel
     * ([AppConfig.MSG_STATE_DELAY_RESULT] -> `MainViewModel.delayResultAction` ->
     * `HomeFragment.onDelayResult`), which is untouched: the «мс» figure and the «сервер не
     * отвечает» condition behind three consecutive misses work exactly as before. Only the leg with
     * no reader is gone.
     */
    private fun measureV2rayDelay() {
        if (coreController.isRunning == false) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val service = getService() ?: return@launch
            var time = -1L
            var errorStr = ""

            // A PROBE THAT MISSES IS A READING, NOT A FAULT — and this one runs every 30 seconds
            // for as long as the tunnel is up, so getting its severity wrong is not a detail.
            //
            // The FIRST attempt is expected to fail on plenty of healthy connections: that is the
            // entire reason there is a second URL to fall back to. It was written as an ERROR with
            // a stack trace under it, twice per probe, which is the same false alarm the подписка
            // fetch used to raise from its own first-attempt-through-the-proxy (see
            // `AngConfigManager.updateConfigViaSub`). «Журнал» filled with red while the connection
            // was working perfectly.
            //
            // So: the first miss is one INFO line naming the reason, the second is a WARN — the
            // tunnel really is not answering, which is worth seeing — and neither carries a trace,
            // because the reason is in the message and the стек of a timeout tells nobody anything.
            try {
                time = coreController.measureDelay(SettingsManager.getDelayTestUrl())
            } catch (e: Exception) {
                errorStr = e.message?.substringAfter("\":") ?: "empty message"
                LogUtil.i(AppConfig.TAG, "StartCore-Manager: primary delay URL did not answer ($errorStr)")
            }
            if (time == -1L) {
                try {
                    time = coreController.measureDelay(SettingsManager.getDelayTestUrl(true))
                } catch (e: Exception) {
                    errorStr = e.message?.substringAfter("\":") ?: "empty message"
                    LogUtil.w(AppConfig.TAG, "StartCore-Manager: the tunnel answered neither delay URL ($errorStr)")
                }
            }

            // The reading, and nothing else. `errorStr` stays because it is what the two log lines
            // above say; it is no longer formatted into a sentence for a surface that does not
            // exist. @see the note above this function.
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_DELAY_RESULT, time)
        }
    }

    /**
     * Gets the current service instance.
     * @return The current service instance, or null if not available.
     */
    private fun getService(): Service? {
        return serviceControl?.get()?.getService()
    }

    /**
     * Core callback handler implementation for handling V2Ray core events.
     * Handles startup, shutdown, socket protection, and status emission.
     */
    private class CoreCallback : CoreCallbackHandler {
        /**
         * Called when V2Ray core starts up.
         * @return 0 for success, any other value for failure.
         */
        override fun startup(): Long {
            return 0
        }

        /**
         * Called when V2Ray core shuts down.
         * @return 0 for success, any other value for failure.
         */
        override fun shutdown(): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            return try {
                serviceControl.stopService()
                0
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop service", e)
                -1
            }
        }

        /**
         * Called when V2Ray core emits status information.
         * @param l Status code.
         * @param s Status message.
         * @return Always returns 0.
         */
        override fun onEmitStatus(l: Long, s: String?): Long {
            return 0
        }
    }

    /**
     * Process finder implementation for Xray core.
     * Uses ConnectivityManager to find the owning UID of a connection based on network parameters.
     */
    private class XrayProcessFinder(context: Context) : ProcessFinder {
        private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

        override fun findProcessByConnection(network: String, srcIP: String, srcPort: Long, destIP: String, destPort: Long): Long {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1L
            if (cm == null) return -1L
            val proto = when (network) {
                "tcp" -> OsConstants.IPPROTO_TCP
                "udp" -> OsConstants.IPPROTO_UDP
                else -> return -1L
            }

            if (destIP.isBlank() || destPort == 0L) {
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to :$destPort, (no dest)")
                return -1L
            }

            return try {
                val uid = cm.getConnectionOwnerUid(
                    proto,
                    InetSocketAddress(srcIP, srcPort.toInt()),
                    InetSocketAddress(destIP, destPort.toInt())
                ).toLong()
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid")
                //LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid,${PackageUidResolver.uidToPackageName(uid.toString())}")

                uid
            } catch (_: Exception) {
                -1L
            }
        }
    }

    /**
     * Broadcast receiver for handling messages sent to the service.
     * Handles registration, service control, and screen events.
     */
    private class ReceiveMessageHandler : BroadcastReceiver() {
        /**
         * Handles received broadcast messages.
         * Processes service control messages and screen state changes.
         * @param ctx The context in which the receiver is running.
         * @param intent The intent being received.
         */
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    if (coreController.isRunning) {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_RUNNING, "")
                    } else {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_NOT_RUNNING, "")
                    }
                }

                AppConfig.MSG_UNREGISTER_CLIENT -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_START -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_STOP -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Stop service")
                    serviceControl.stopService()
                }

                AppConfig.MSG_STATE_RESTART -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Restart service")
                    serviceControl.stopService()
                    Thread.sleep(500L)
                    startVService(serviceControl.getService())
                }

                AppConfig.MSG_MEASURE_DELAY -> {
                    measureV2rayDelay()
                }
            }

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen off")
                    NotificationManager.stopSpeedNotification()
                }

                Intent.ACTION_SCREEN_ON -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen on")
                    NotificationManager.startSpeedNotification()
                }
            }
        }
    }
}