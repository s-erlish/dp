package com.v2ray.ang.handler

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import androidx.work.workDataOf
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.NotificationHelper
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object SubscriptionUpdater {

    /** Launch-time work runs once per process, not on every activity recreate. */
    private val launchTasksRun = AtomicBoolean(false)

    // -------------------------------------------------------------------------
    // Public API — the only methods external callers should ever use
    // -------------------------------------------------------------------------

    /**
     * Sync all subscription tasks with current settings.
     *
     * Startup/boot callers should use the default mode so existing periodic work is kept.
     * Use forceReschedule=true only when the next run time needs to be recalculated from
     * the latest persisted subscription state (for example after a manual refresh); that mode also
     * skips the once-per-process launch tasks in [runLaunchTasks].
     * Call from: MainActivity.onCreate(), BootReceiver.onReceive().
     */
    fun sync(
        context: Context = AngApplication.application,
        forceReschedule: Boolean = false
    ) {
        val existingWorkPolicy =
            if (forceReschedule) {
                ExistingPeriodicWorkPolicy.REPLACE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

        MmkvManager.decodeSubscriptions().forEach { sub ->
            scheduleOne(
                context = context,
                subId = sub.guid,
                shouldRun = sub.subscription.autoUpdate,
                existingWorkPolicy = existingWorkPolicy
            )
        }
        LogUtil.i(
            AppConfig.TAG,
            "SubscriptionUpdater: sync complete forceReschedule=$forceReschedule"
        )

        // The default mode is the app's launch/boot entry point (see above), which is also where
        // the provider screen's "at launch" promises have to be kept. A settings change reschedules
        // with forceReschedule=true and must not replay them.
        if (!forceReschedule) {
            runLaunchTasks(context)
        }
    }

    /**
     * Sync a single subscription's task.
     * Call from: SubEditActivity after saving, after a manual update (to reset the timer).
     */
    fun syncOne(context: Context = AngApplication.application, subId: String) {
        val subItem = MmkvManager.decodeSubscription(subId) ?: return
        scheduleOne(
            context = context,
            subId = subId,
            shouldRun = subItem.autoUpdate,
            existingWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE
        )
    }

    /**
     * Cancel the auto-update task for a single subscription.
     * Call from: when a subscription is deleted.
     */
    fun cancelOne(context: Context = AngApplication.application, subId: String) {
        RemoteWorkManager.getInstance(context)
            .cancelUniqueWork(taskName(subId))
    }

    // -------------------------------------------------------------------------
    // Internal scheduling logic
    // -------------------------------------------------------------------------

    private fun taskName(subId: String) = "${AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME}_$subId"

    /**
     * The work the provider screen promises "at launch": server ordering, an immediate refresh of
     * every subscription and a latency test, each behind its own switch.
     */
    private fun runLaunchTasks(context: Context) {
        if (!launchTasksRun.compareAndSet(false, true)) return

        // Latencies measured late in the previous session reach the stored order only here, which
        // runs before the host activity loads the list.
        SettingsManager.applyServerSortOrder()

        if (SettingsManager.isUpdateSubscriptionOnLaunch()) {
            updateAllNow(context)
        }
        if (SettingsManager.isPingOnLaunch()) {
            requestLatencyTest(context)
        }
    }

    /**
     * Enqueues an immediate one-shot refresh of every enabled subscription, leaving the periodic
     * schedule untouched. It ignores per-subscription auto-update on purpose: "update on launch" is
     * its own promise to the user, not a second switch on top of that one.
     */
    private fun updateAllNow(context: Context) {
        val rw = RemoteWorkManager.getInstance(context)
        MmkvManager.decodeSubscriptions()
            .filter { it.subscription.enabled }
            .forEach { sub ->
                val request = OneTimeWorkRequestBuilder<UpdateTask>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(workDataOf(KEY_SUB_ID to sub.guid, KEY_FORCE to true))
                    .addTag(AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME)
                    .build()

                rw.enqueueUniqueWork(
                    "${taskName(sub.guid)}_launch",
                    ExistingWorkPolicy.KEEP,
                    request
                )
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: launch refresh enqueued for ${sub.guid}")
            }
    }

    /**
     * Runs the batch latency test for [subId] (all servers when empty) through the same test
     * service the servers screen uses, so results land in the one place every screen reads.
     *
     * Android can refuse to start that foreground service from the background, in which case
     * [MessageUtil] logs it and the previous results simply stand.
     */
    private fun requestLatencyTest(context: Context, subId: String = "") {
        MessageUtil.sendMsg2TestService(
            context,
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START, subscriptionId = subId)
        )
    }

    private fun scheduleOne(
        context: Context,
        subId: String,
        shouldRun: Boolean,
        existingWorkPolicy: ExistingPeriodicWorkPolicy
    ) {
        val rw = RemoteWorkManager.getInstance(context)
        if (!shouldRun) {
            rw.cancelUniqueWork(taskName(subId))
            LogUtil.d(AppConfig.TAG, "SubscriptionUpdater: cancelled task for $subId")
            return
        }

        val subItem = MmkvManager.decodeSubscription(subId) ?: return

        val intervalMinutes = maxOf(
            AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES,
            subItem.updateInterval
        )

        // Base initial delay on the last successful update time persisted in subscription.
        val lastUpdated = subItem.lastUpdated
        val intervalMillis = intervalMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val initialDelayMillis = if (lastUpdated <= 0L) {
            0L
        } else {
            maxOf(0L, lastUpdated + intervalMillis - now)
        }

        val request = PeriodicWorkRequestBuilder<UpdateTask>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(KEY_SUB_ID to subId))
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .addTag(AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME)
            .build()

        rw.enqueueUniquePeriodicWork(
            taskName(subId),
            existingWorkPolicy,
            request
        )

        LogUtil.i(
            AppConfig.TAG,
            "SubscriptionUpdater: scheduled [$subId] interval=${intervalMinutes}min " +
                    "initialDelay=${initialDelayMillis / 1000}s policy=$existingWorkPolicy"
        )
    }

    // -------------------------------------------------------------------------
    // Worker
    // -------------------------------------------------------------------------

    private const val KEY_SUB_ID = "subId"

    /** Set by [updateAllNow]: refresh even when this subscription does not auto-update. */
    private const val KEY_FORCE = "force"

    class UpdateTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        @SuppressLint("MissingPermission")
        override suspend fun doWork(): Result {
            val subId = inputData.getString(KEY_SUB_ID)
            LogUtil.i(AppConfig.TAG, "SubscriptionUpdater automatic update starting: $subId")

            if (subId.isNullOrEmpty()) {
                LogUtil.w(AppConfig.TAG, "SubscriptionUpdater: missing subId in worker input")
                return Result.success()
            }

            val subItem = MmkvManager.decodeSubscription(subId)
            if (subItem == null) {
                LogUtil.w(AppConfig.TAG, "SubscriptionUpdater: no subscription found for $subId")
                return Result.success()
            }

            if (!subItem.autoUpdate && !inputData.getBoolean(KEY_FORCE, false)) {
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: auto-update disabled for $subId, skip")
                return Result.success()
            }

            // The global User-Agent fallback is NOT applied here: updateConfigViaSub writes this
            // item back to storage (lastUpdated, provider directives), so anything set on it now
            // would be persisted as that subscription's own User-Agent and would survive clearing
            // the global one. It resolves per-sub → global → operator default at the fetch itself.
            val sub = SubscriptionCache(subId, subItem)

            // Notify about update start.
            //
            // THE NAME IS RESOLVED, NEVER THE RAW REMARK. `subItem.remarks` is storage, and older
            // builds stored upstream's «import sub» in it — which this line then formatted into the
            // shade verbatim, so the user's phone told them «Обновляем «import sub»».
            // [SubscriptionNaming] is the one place that answers what a подписка is called, and it
            // refuses every placeholder; when it has nothing real to offer, the copy becomes a whole
            // sentence instead of quoting an empty string.
            if (SettingsManager.isNotifyOnSubscriptionUpdate()) {
                val name = SubscriptionNaming.nameOf(subItem)
                NotificationHelper.notify(
                    NotificationChannelType.SUBSCRIPTION_UPDATE,
                    applicationContext,
                    applicationContext.getString(R.string.notification_subscription_title),
                    if (name == null) {
                        applicationContext.getString(R.string.msg_updating_subscription_unnamed)
                    } else {
                        applicationContext.getString(R.string.msg_updating_subscription, name)
                    }
                )
            }
            AngConfigManager.updateConfigViaSub(sub)

            // Clear notification — unconditional, so one posted before the switch was turned off
            // still goes away.
            NotificationHelper.cancel(NotificationChannelType.SUBSCRIPTION_UPDATE, applicationContext)

            // The refresh rewrote this subscription's server list in the provider's order.
            SettingsManager.applyServerSortOrder()

            if (SettingsManager.isPingOnSubscriptionUpdate()) {
                requestLatencyTest(applicationContext, subId)
            }

            return Result.success()
        }
    }
}