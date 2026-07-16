package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.FlagUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val QUERY_INTERVAL_MS = 3000L

    private var lastQueryTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: NotificationManager? = null

    /**
     * Starts the speed notification.
     * @param currentConfig The current profile configuration.
     */
    fun startSpeedNotification() {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) return
        if (speedNotificationJob != null || CoreServiceManager.isRunning() == false) return

        var lastZeroSpeed = false

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                lastZeroSpeed = updateSpeedNotificationOnce(lastZeroSpeed)
                delay(QUERY_INTERVAL_MS)
            }
        }
    }

    /**
     * Shows the notification and promotes the service to the foreground.
     *
     * A foreground service MUST call startForeground() within ~5s with a valid notification or
     * the system kills the process (which strands the UI on "Подключение…" and shows an app
     * crash). This method is therefore hardened so it can NEVER throw: if building the rich
     * notification (flag title, chronometer, actions) fails for any reason, it falls back to a
     * minimal valid notification, and startForeground() itself is guarded. Missing/invalid
     * drawables, a cleared service reference, PendingIntent issues, or a foreground-service
     * policy exception can no longer take down the VPN process.
     *
     * @param currentConfig The current profile configuration.
     * @return true if the service was promoted to the foreground, false otherwise.
     */
    fun showNotification(currentConfig: ProfileItem?): Boolean {
        val service = getService() ?: run {
            LogUtil.e(AppConfig.TAG, "showNotification: service reference is null; cannot start foreground")
            return false
        }

        // Reset last query time to avoid querying stats too soon after showing the notification
        lastQueryTime = System.currentTimeMillis()

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    createNotificationChannel()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "showNotification: failed to create channel", e)
                    AppConfig.RAY_NG_CHANNEL_ID
                }
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        val notification = try {
            buildRichNotification(service, channelId, currentConfig)
        } catch (e: Exception) {
            // Any failure while assembling the rich notification must not prevent the mandatory
            // startForeground() call. Fall back to a minimal, always-valid notification.
            LogUtil.e(AppConfig.TAG, "showNotification: rich notification build failed, using fallback", e)
            mBuilder = null
            buildFallbackNotification(service, channelId)
        }

        return try {
            service.startForeground(NOTIFICATION_ID, notification)
            true
        } catch (e: Exception) {
            // startForeground can throw ForegroundServiceStartNotAllowedException / SecurityException /
            // InvalidForegroundServiceTypeException on newer Android. Swallow so the caller can decide
            // how to recover instead of crashing the whole service process.
            LogUtil.e(AppConfig.TAG, "showNotification: startForeground failed", e)
            false
        }
    }

    /**
     * Builds the rich ongoing notification (flag + server name title, live uptime chronometer,
     * stop/restart actions) and caches its builder in [mBuilder] for later speed updates.
     */
    private fun buildRichNotification(service: Service, channelId: String, currentConfig: ProfileItem?): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        restartV2RayIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags)

        // Resolving the flag/name is pure string work but is defensively guarded so a malformed
        // remark can never abort the notification build.
        val title = currentConfig?.let { cfg ->
            try {
                "${FlagUtil.resolveFlag(cfg)} ${FlagUtil.stripLeadingFlag(cfg.remarks)}"
            } catch (e: Exception) {
                cfg.remarks
            }
        }
        val builder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setColor(ContextCompat.getColor(service, R.color.icon_blue))
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            // System-rendered live uptime stopwatch (no per-second push, battery-free).
            .setWhen(System.currentTimeMillis())
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_notif_stop,
                service.getString(R.string.notification_action_stop_v2ray),
                stopV2RayPendingIntent
            )
            .addAction(
                R.drawable.ic_notif_restart,
                service.getString(R.string.title_service_restart),
                restartV2RayPendingIntent
            )

        mBuilder = builder
        return builder.build()
    }

    /**
     * Minimal, always-valid foreground notification used when the rich build fails. It only
     * needs a small icon and a title to satisfy the foreground-service requirement.
     */
    private fun buildFallbackNotification(service: Service, channelId: String): Notification {
        return NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setColor(ContextCompat.getColor(service, R.color.icon_blue))
            .setContentTitle(service.getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Cancels the notification.
     */
    fun cancelNotification() {
        val service = getService() ?: return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)

        mBuilder = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
    }

    /**
     * Stops the speed notification.
     */
    fun stopSpeedNotification() {
        speedNotificationJob?.let {
            it.cancel()
            speedNotificationJob = null
            updateNotification("", 0, 0)
        }
    }

    /**
     * Creates a notification channel for Android O and above.
     * @return The channel ID.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val channelName = AppConfig.RAY_NG_CHANNEL_NAME
        // IMPORTANCE_LOW keeps the ongoing notification pinned and visible (with the live
        // uptime chronometer) while staying silent.
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_LOW
        )
        chan.lightColor = Color.DKGRAY
        chan.setShowBadge(false)
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        getNotificationManager()?.createNotificationChannel(chan)
        return channelId
    }

    /**
     * Updates the notification with the given content text and traffic data.
     * @param contentText The content text.
     * @param proxyTraffic The proxy traffic.
     * @param directTraffic The direct traffic.
     */
    private fun updateNotification(contentText: String?, proxyTraffic: Long, directTraffic: Long) {
        if (mBuilder != null) {
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            mBuilder?.setContentText(contentText)
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    /**
     * Gets the notification manager.
     * @return The notification manager.
     */
    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    /**
     * Updates the speed notification once.
     * Queries traffic stats, separates proxy and direct, and updates the notification.
     * @param lastZeroSpeed The previous zero speed state.
     * @return The current zero speed state.
     */
    private fun updateSpeedNotificationOnce(lastZeroSpeed: Boolean): Boolean {
        val queryTime = System.currentTimeMillis()
        val sinceLastQueryIn = (queryTime - lastQueryTime)

        // If the query interval is too short, skip this round to avoid excessive CPU usage
        if (sinceLastQueryIn < QUERY_INTERVAL_MS) {
            LogUtil.w(AppConfig.TAG, "Query interval too short: ${sinceLastQueryIn}ms, skipping")
            lastQueryTime = queryTime
            return lastZeroSpeed
        }
        val sinceLastQueryInSeconds = sinceLastQueryIn / 1000.0

        var proxyUplink = 0L
        var proxyDownlink = 0L
        var directUplink = 0L
        var directDownlink = 0L

        CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
            when {
                stat.tag == AppConfig.TAG_DIRECT -> {
                    when (stat.direction) {
                        AppConfig.UPLINK -> directUplink += stat.value
                        AppConfig.DOWNLINK -> directDownlink += stat.value
                    }
                }

                stat.tag.startsWith(AppConfig.TAG_PROXY) -> {
                    when (stat.direction) {
                        AppConfig.UPLINK -> proxyUplink += stat.value
                        AppConfig.DOWNLINK -> proxyDownlink += stat.value
                    }
                }
            }
        }

        val proxyTotal = proxyUplink + proxyDownlink
        val directTotal = directUplink + directDownlink
        val zeroSpeed = proxyTotal + directTotal == 0L

        // Push the live proxy speed to the main screen hero panel (bytes/second). This is the
        // ONLY consumer of the speed job now: the ongoing notification deliberately shows just the
        // server name + live uptime chronometer (no per-second speed lines), so we never write
        // speed text back into the notification here.
        getService()?.let { svc ->
            val downPerSec = (proxyDownlink / sinceLastQueryInSeconds).toLong()
            val upPerSec = (proxyUplink / sinceLastQueryInSeconds).toLong()
            MessageUtil.sendMsg2UI(svc, AppConfig.MSG_STATE_SPEED_UPDATE, longArrayOf(downPerSec, upPerSec))
        }

        lastQueryTime = queryTime
        return zeroSpeed
    }

    /**
     * Gets the service instance.
     * @return The service instance.
     */
    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.get()?.getService()
    }
}