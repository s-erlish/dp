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
import com.v2ray.ang.extension.toSpeedString
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
    private const val QUERY_INTERVAL_MS = 3000L

    private var lastQueryTime = 0L

    /**
     * The rate last actually written into the shade, so an unchanged one is not re-posted.
     * Cleared whenever [mBuilder] is replaced — a new builder carries no speed line yet.
     * @see updateSpeedNotificationOnce
     */
    private var lastPushedSpeed: Pair<Long, Long>? = null
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

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                updateSpeedNotificationOnce()
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
            // A START THAT ARRIVES WHILE A SESSION IS UP MUST NOT BLANK THE ROW IN THE SHADE.
            // `CoreVpnService` promotes itself to the foreground with `null` before it knows which
            // server it is about to run — the 5-second deadline does not wait for that — and with a
            // live tunnel underneath, building from `null` would replace «🇵🇱 Poland  00:11:52» with
            // a title-less notification for as long as the duplicate start took to be refused. When
            // there is nothing new to say and something already said, re-post that.
            val existing = mBuilder
            if (currentConfig == null && existing != null) {
                existing.build()
            } else {
                buildRichNotification(service, channelId, currentConfig)
            }
        } catch (e: Exception) {
            // Any failure while assembling the rich notification must not prevent the mandatory
            // startForeground() call. Fall back to a minimal, always-valid notification.
            LogUtil.e(AppConfig.TAG, "showNotification: rich notification build failed, using fallback", e)
            mBuilder = null
            lastPushedSpeed = null
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
     * THE ONGOING VPN NOTIFICATION. Two states, and nothing in either of them that is not asked for.
     *
     *   COLLAPSED   🇵🇱 Poland                                    00:11:52
     *   EXPANDED    🇵🇱 Poland                                    00:11:52
     *               ↓ 1,2 MB/s   ↑ 240 KB/s
     *               [ Остановить ]
     *
     * That shape is the owner's, to the line: «при раскрытии уведомления этого надо чтобы писало
     * скорость, больше ничего не надо».
     *
     * WHAT CHANGED AND WHY:
     *
     *  - **«Перезапуск службы» is gone.** A notification action is the shortest path in the product
     *    and it was spending it on a developer's recovery gesture, one tap away from «Остановить» —
     *    the one thing a user actually reaches for here, and the one they must not miss. Restarting
     *    is still reachable everywhere it belongs; `AppConfig.MSG_STATE_RESTART` and its broadcast
     *    are untouched, only this button is.
     *  - **No empty second line.** The content text was set to `""` on the way out
     *    ([stopSpeedNotification]), which does not remove the line, it prints an empty one. The
     *    collapsed notification carries the title and the chronometer, and nothing where a sentence
     *    would be.
     *  - **The speed is IN the notification now**, in the expanded view only. It used to be
     *    deliberately excluded — the comment in [updateSpeedNotificationOnce] said so — and that
     *    decision is reversed rather than left contradicting the code. It costs no new work: the
     *    stats job already ticks every [QUERY_INTERVAL_MS] for Главная, so the same tick writes the
     *    line, at the same rate, and `setOnlyAlertOnce` keeps it silent.
     *
     * The uptime is the system's own chronometer, so it counts without a single push from us.
     */
    private fun buildRichNotification(service: Service, channelId: String, currentConfig: ProfileItem?): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

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
            // БОЛЬШОГО ЗНАЧКА ЗДЕСЬ НЕТ, И ЭТО НЕ УПУЩЕНИЕ. Он тут был ровно один заход:
            // «и в шторке уведомлений» я прочитал как «положи логотип в цвете», а цвет в шторке
            // держит только largeIcon. Но под него система резервирует место и разворачивает
            // строку в крупную раскладку — «почему в шторке теперь оно огромным стало и не
            // сворачивается, логотипа вроде раньше справа не было».
            //
            // Знак и так на месте: setSmallIcon выше — это марка, которую система рисует из
            // альфы и красит setColor'ом. Одного раза достаточно.
            .setColor(ContextCompat.getColor(service, R.color.notification_badge))
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

        mBuilder = builder
        // The rate reads zero until the first tick lands, three seconds in — honest, and it means
        // the expanded height never changes under the user's thumb by growing a line. The memo is
        // set to match, so an idle tunnel's first tick has nothing new to say and stays silent.
        applySpeed(builder, service, 0L, 0L)
        lastPushedSpeed = 0L to 0L
        return builder.build()
    }

    /**
     * Minimal, always-valid foreground notification used when the rich build fails. It only
     * needs a small icon and a title to satisfy the foreground-service requirement.
     */
    private fun buildFallbackNotification(service: Service, channelId: String): Notification {
        return NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setColor(ContextCompat.getColor(service, R.color.notification_badge))
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
        lastPushedSpeed = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
    }

    /**
     * Stops the speed notification.
     *
     * The rate is returned to zero rather than blanked. Blanking it wrote `""` into the content
     * text, which prints an EMPTY line rather than removing one — the second line the owner
     * reported — and a stopped meter that reads «↓ 0 KB/s ↑ 0 KB/s» is also simply true.
     */
    fun stopSpeedNotification() {
        speedNotificationJob?.let {
            it.cancel()
            speedNotificationJob = null
            pushSpeed(0L, 0L)
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
     * Writes the live rate into the EXPANDED notification and re-posts it.
     *
     * The line goes on [NotificationCompat.BigTextStyle] alone and the content text stays unset, so
     * the collapsed row keeps carrying the server and the chronometer and nothing else. `bigText`
     * is what the expanded view renders; `setContentText` is what the collapsed one does, and the
     * two are deliberately not the same string here.
     */
    private fun pushSpeed(downPerSec: Long, upPerSec: Long) {
        val builder = mBuilder ?: return
        val service = getService() ?: return
        applySpeed(builder, service, downPerSec, upPerSec)
        getNotificationManager()?.notify(NOTIFICATION_ID, builder.build())
        lastPushedSpeed = downPerSec to upPerSec
    }

    /** The expanded view's one line: «↓ 1,2 MB/s   ↑ 240 KB/s». */
    private fun applySpeed(
        builder: NotificationCompat.Builder,
        context: Context,
        downPerSec: Long,
        upPerSec: Long,
    ) {
        // The same formatter Главная and every other rate in the product uses, so one speed can
        // never read two different ways in one app.
        val line = context.getString(
            R.string.notification_speed,
            downPerSec.toSpeedString(),
            upPerSec.toSpeedString(),
        )
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(line))
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
     *
     * ## A NOTIFICATION THAT SAYS THE SAME THING IS NOT RE-POSTED
     *
     * This runs every [QUERY_INTERVAL_MS] for as long as a tunnel is up — 1200 rounds an hour, and
     * a tunnel is up for hours — and it used to end every single one of them with a
     * `NotificationManager.notify()`, i.e. a binder round trip into system_server that re-inflates
     * the row, whether or not a digit had changed. On an idle tunnel every one of those posted the
     * identical «↓ 0 B/s ↑ 0 B/s».
     *
     * Upstream had a guard for exactly this and this fork kept only its plumbing: `lastZeroSpeed`
     * was threaded in and out of this function and never branched on. It is replaced by the
     * stronger form of the same idea — remember the pair actually pushed, and post only when the
     * new one differs — which also covers a steady rate, not just a zero one. [showNotification]
     * clears the memo when it rebuilds the builder, so a fresh row always gets its line back.
     *
     * THE BROADCAST IS NOT GUARDED, and must not be: `MSG_STATE_SPEED_UPDATE` is Главная's only
     * source for the two figures, it is a local broadcast rather than a system call, and swallowing
     * it would freeze the strip on the last rate the tunnel ever saw.
     */
    private fun updateSpeedNotificationOnce() {
        val queryTime = System.currentTimeMillis()
        val sinceLastQueryIn = (queryTime - lastQueryTime)

        // If the query interval is too short, skip this round to avoid excessive CPU usage
        if (sinceLastQueryIn < QUERY_INTERVAL_MS) {
            LogUtil.w(AppConfig.TAG, "Query interval too short: ${sinceLastQueryIn}ms, skipping")
            lastQueryTime = queryTime
            return
        }
        val sinceLastQueryInSeconds = sinceLastQueryIn / 1000.0

        // THE DIRECT SIDE IS NOT SUMMED ANY MORE. Upstream separates proxied from direct traffic
        // because it reports both; this product reports one rate — what went through the tunnel —
        // and the direct pair was accumulated on every tick, over every outbound the core knows,
        // and then read by nothing but a "is everything zero" test that itself had no reader.
        var proxyUplink = 0L
        var proxyDownlink = 0L

        CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
            if (!stat.tag.startsWith(AppConfig.TAG_PROXY)) return@forEach
            when (stat.direction) {
                AppConfig.UPLINK -> proxyUplink += stat.value
                AppConfig.DOWNLINK -> proxyDownlink += stat.value
            }
        }

        // ONE TICK, TWO CONSUMERS. The rate goes to Главная's ledger and, since the owner asked for
        // it, to the expanded notification — from the SAME measurement at the SAME interval. Adding
        // a second job or a faster push for the shade would have paid twice for one number.
        getService()?.let { svc ->
            val downPerSec = (proxyDownlink / sinceLastQueryInSeconds).toLong()
            val upPerSec = (proxyUplink / sinceLastQueryInSeconds).toLong()
            MessageUtil.sendMsg2UI(svc, AppConfig.MSG_STATE_SPEED_UPDATE, longArrayOf(downPerSec, upPerSec))
            if (downPerSec to upPerSec != lastPushedSpeed) pushSpeed(downPerSec, upPerSec)
        }

        lastQueryTime = queryTime
    }

    /**
     * Gets the service instance.
     * @return The service instance.
     */
    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.get()?.getService()
    }
}