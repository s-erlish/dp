package com.v2ray.ang.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import com.v2ray.ang.AngApplication
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.Notice
import com.v2ray.ang.ui.NoticePolicy
import java.io.Serializable
import java.net.URI
import java.util.Locale

val Context.v2RayApplication: AngApplication?
    get() = applicationContext as? AngApplication

/*
 * ============================================================================
 * THE THREE DOORS THE OLD NOTIFICATION LAYER CAME THROUGH.
 * ============================================================================
 *
 * These names stay because ~50 call sites across upstream's screens use them, and a rename would
 * be fifty edits that the next merge undoes. What changed is what is behind them: NOT `Toasty`
 * (the green tick / red cross / system capsule the owner asked to have removed outright — «это же
 * старые от в2рей уведомления… их убрать надо совсем») but `NoticePolicy`, which decides whether
 * anything reaches a human at all, and `Notice`, the ONE bottom surface that shows it when it
 * does. Read `NoticePolicy.kt`; the rules and the reasoning live there.
 *
 * The short version, for anyone porting an upstream call site:
 *
 *   - `toastSuccess(…)` shows NOTHING, ever. The state change is the confirmation.
 *   - `toast(…)` / `toastError(…)` show the string ONLY if its resource id is on the policy's
 *     allow-list. A new upstream message is silent until somebody reads it and lists it.
 *   - the `CharSequence` overloads show nothing at all, because a built string has no id the
 *     policy can recognise, and everything that is built by concatenation today is a counter, a
 *     node summary or an exception message. To say something, pass a resource id.
 */

/** A note about what happened, if the policy recognises it. @see NoticePolicy */
fun Context.toast(message: Int) = Notice.say(this, message, NoticePolicy.Kind.INFO)

/** Built text has no identity the policy can check, so it is never shown. @see NoticePolicy */
@Suppress("UNUSED_PARAMETER")
fun Context.toast(message: CharSequence) = Unit

/** Success is silent — the screen behind the message already said it. @see NoticePolicy */
@Suppress("UNUSED_PARAMETER")
fun Context.toastSuccess(message: Int) = Unit

/** Success is silent — the screen behind the message already said it. @see NoticePolicy */
@Suppress("UNUSED_PARAMETER")
fun Context.toastSuccess(message: CharSequence) = Unit

/** A failure the user has to act on, if the policy recognises it. @see NoticePolicy */
fun Context.toastError(message: Int) = Notice.say(this, message, NoticePolicy.Kind.FAILURE)

/** Built text has no identity the policy can check, so it is never shown. @see NoticePolicy */
@Suppress("UNUSED_PARAMETER")
fun Context.toastError(message: CharSequence) = Unit

const val THRESHOLD = 1000L
const val DIVISOR = 1024.0

/**
 * Converts a Long value to a speed string.
 *
 * @return The speed string.
 */
fun Long.toSpeedString(): String = this.toTrafficString() + "/s"

/**
 * Converts a Long value to a traffic string.
 *
 * @return The traffic string.
 */
fun Long.toTrafficString(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var size = this.toDouble()
    var unitIndex = 0
    while (size >= THRESHOLD && unitIndex < units.size - 1) {
        size /= DIVISOR
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", size, units[unitIndex])
}

val URI.idnHost: String
    get() = host?.replace("[", "")?.replace("]", "").orEmpty()

/**
 * Removes all whitespace from the string.
 *
 * @return The string without whitespace.
 */
fun String?.removeWhiteSpace(): String? = this?.replace(" ", "")

/**
 * Returns null if the string is null or blank, otherwise returns the string itself.
 *
 * @return The string or null.
 */
fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

/**
 * Converts the string to a Long value, or returns 0 if the conversion fails.
 *
 * @return The Long value.
 */
fun String.toLongEx(): Long = toLongOrNull() ?: 0

/**
 * Listens for package changes and executes a callback when a change occurs.
 *
 * @param onetime Whether to unregister the receiver after the first callback.
 * @param callback The callback to execute when a package change occurs.
 * @return The BroadcastReceiver that was registered.
 */
fun Context.listenForPackageChanges(onetime: Boolean = true, callback: () -> Unit) =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            callback()
            if (onetime) context.unregisterReceiver(this)
        }
    }.apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            })
        }
    }

/**
 * Retrieves a serializable object from the Bundle.
 *
 * @param key The key of the serializable object.
 * @return The serializable object, or null if not found.
 */
inline fun <reified T : Serializable> Bundle.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getSerializable(key) as? T
}

/**
 * Retrieves a serializable object from the Intent.
 *
 * @param key The key of the serializable object.
 * @return The serializable object, or null if not found.
 */
inline fun <reified T : Serializable> Intent.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getSerializableExtra(key) as? T
}

/**
 * Checks if the CharSequence is not null and not empty.
 *
 * @return True if the CharSequence is not null and not empty, false otherwise.
 */
fun CharSequence?.isNotNullEmpty(): Boolean = !this.isNullOrBlank()

fun String.concatUrl(vararg paths: String): String {
    val builder = StringBuilder(this.trimEnd('/'))

    paths.forEach { path ->
        val trimmedPath = path.trim('/')
        if (trimmedPath.isNotEmpty()) {
            builder.append('/').append(trimmedPath)
        }
    }

    return builder.toString()
}

/**
 * Checks if the config type is a group type (PolicyGroup or ProxyChain).
 *
 * @return True if the config type is PolicyGroup or ProxyChain, false otherwise.
 */
fun EConfigType.isGroupType(): Boolean {
    return this == EConfigType.POLICYGROUP || this == EConfigType.PROXYCHAIN
}

/**
 * Checks if the config type is a complex type (Custom, PolicyGroup, or ProxyChain).
 *
 * @return True if the config type is Custom, PolicyGroup, or ProxyChain, false otherwise.
 */
fun EConfigType.isComplexType(): Boolean {
    return this == EConfigType.CUSTOM || this == EConfigType.POLICYGROUP || this == EConfigType.PROXYCHAIN
}
