package com.v2ray.ang.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.v2ray.ang.R
import com.v2ray.ang.enums.NotificationChannelType

/**
 * Unified notification helper for different notification channels.
 * Supports both regular notifications and foreground service notifications.
 *
 * Performance: NotificationManager is cached. Builder is created once per update.
 * Safe for high-frequency updates (100+ times/second).
 */
object NotificationHelper {

    // Cached instances for performance
    private var cachedNotificationManager: NotificationManager? = null
    private val builderCache = mutableMapOf<Int, NotificationCompat.Builder>()

    /**
     * Notify with a regular notification (non-foreground).
     *
     * @param channelType The notification channel type (defines channelId, notificationId, etc.)
     * @param context The context for building the notification
     * @param title The notification title
     * @param content The notification content text
     */
    fun notify(
        channelType: NotificationChannelType,
        context: Context,
        title: String,
        content: String
    ) {
        ensureChannelCreated(channelType, context)
        val notificationManager = getNotificationManager(context)
        val builder = buildNotificationBuilder(channelType, context, title, content)
        notificationManager.notify(channelType.notificationId, builder.build())
    }

    /**
     * Update an existing notification's content.
     * Optimized for high-frequency updates (100+/sec).
     * Reuses cached Builder to minimize allocation overhead.
     *
     * @param channelType The notification channel type
     * @param context The context
     * @param content The new content text
     */
    fun updateNotification(
        channelType: NotificationChannelType,
        context: Context,
        content: String
    ) {
        val notificationManager = getNotificationManager(context)

        // Get or create builder from cache
        val builder = builderCache.getOrPut(channelType.notificationId) {
            buildNotificationBuilder(channelType, context, "", content)
        }

        // Update only the content text (fast operation)
        builder.setContentText(content)
        notificationManager.notify(channelType.notificationId, builder.build())
    }

    /**
     * Start a foreground service with a notification.
     *
     * @param service The service to set as foreground
     * @param channelType The notification channel type
     * @param title The notification title
     * @param content The notification content text
     */
    fun startForeground(
        service: Service,
        channelType: NotificationChannelType,
        title: String,
        content: String
    ) {
        ensureChannelCreated(channelType, service)
        val builder = buildNotificationBuilder(channelType, service, title, content)
        service.startForeground(channelType.notificationId, builder.build())
    }

    /**
     * Stop the foreground notification for a service, and forget the builder that drove it.
     *
     * The cache used to outlive the notification: a second batch reused the builder the first one
     * left behind, so whatever text was last written stayed on the row until something overwrote
     * it. Dropping it here means the next start builds a fresh, empty one.
     *
     * @param service The service to stop foreground on
     */
    fun stopForeground(service: Service) {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        builderCache.clear()
    }

    /**
     * Cancel a notification and clean up cached builder.
     *
     * @param channelType The notification channel type
     * @param context The context
     */
    fun cancel(
        channelType: NotificationChannelType,
        context: Context
    ) {
        getNotificationManager(context).cancel(channelType.notificationId)
        builderCache.remove(channelType.notificationId)  // Clean up cache
    }

    // ====== Private helper methods ======

    private fun getNotificationManager(context: Context): NotificationManager {
        if (cachedNotificationManager == null) {
            cachedNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return cachedNotificationManager!!
    }

    /**
     * Creates the channel if it is missing, and clears away the id this type used to have.
     *
     * A channel's importance is fixed the moment it is created — `createNotificationChannel` will
     * not lower it later — so a type that has to become quieter has to move to a new id, and the
     * old one has to go with it or the user is left reading two rows for one thing in the system's
     * notification settings. See [NotificationChannelType.CORE_TEST].
     */
    private fun ensureChannelCreated(channelType: NotificationChannelType, context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        channelType.legacyChannelId?.let { old ->
            if (notificationManager.getNotificationChannel(old) != null) {
                notificationManager.deleteNotificationChannel(old)
            }
        }
        if (notificationManager.getNotificationChannel(channelType.channelId) != null) return

        val channel = NotificationChannel(
            channelType.channelId,
            context.getString(channelType.channelNameRes),
            channelType.importance
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * A BLANK [content] LEAVES THE LINE OUT, rather than printing an empty one — the same
     * distinction `NotificationManager.stopSpeedNotification` had to learn about the ongoing VPN
     * notification. It matters here because the latency check now has nothing to say: its
     * notification exists to satisfy the foreground-service requirement and carries the app's name
     * and nothing else.
     *
     * The priority follows the channel rather than being one figure for every channel, so a
     * min-importance channel is also min-priority on the versions that read that instead.
     */
    private fun buildNotificationBuilder(
        channelType: NotificationChannelType,
        context: Context,
        title: String,
        content: String
    ): NotificationCompat.Builder {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelType.channelId
        } else {
            ""
        }

        val displayTitle = title.ifEmpty { context.getString(R.string.app_name) }
        val priority = if (channelType.importance <= NotificationManager.IMPORTANCE_MIN) {
            NotificationCompat.PRIORITY_MIN
        } else {
            NotificationCompat.PRIORITY_LOW
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(displayTitle)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Android 12+ holds a foreground-service notification back for ten seconds when it is
            // not marked immediate — longer than most latency batches run for, so the shade often
            // never shows this one at all.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
        if (content.isNotEmpty()) builder.setContentText(content)
        return builder
    }
}

