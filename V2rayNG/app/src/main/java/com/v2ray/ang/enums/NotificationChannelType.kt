package com.v2ray.ang.enums

import androidx.annotation.StringRes
import com.v2ray.ang.R

/**
 * The notification channels this app owns, other than the ongoing VPN one.
 *
 * THE NAME IS A STRING RESOURCE, and that is the point of the change. A channel name is not an
 * internal label: it is the row the user reads in Android's own notification settings for the app,
 * next to «departament VPN». Upstream hard-coded «Subscription Update Service» and «Core Test
 * Service» as English literals here, so a Russian-only product listed two English service names in
 * the system UI. They are resources now, so they speak the interface's language like everything else.
 */
enum class NotificationChannelType(
    val channelId: String,
    @param:StringRes val channelNameRes: Int,
    val notificationId: Int
) {
    SUBSCRIPTION_UPDATE(
        channelId = "subscription_update_channel",
        channelNameRes = R.string.notification_channel_subscription,
        notificationId = 13
    ),
    CORE_TEST(
        channelId = "core_test_channel",
        channelNameRes = R.string.notification_channel_core_test,
        notificationId = 12
    )
}
