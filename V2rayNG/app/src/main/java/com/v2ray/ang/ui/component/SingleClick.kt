package com.v2ray.ang.ui.component

import android.os.SystemClock
import android.view.View
import com.v2ray.ang.R
import com.v2ray.ang.util.pressHaptic
import com.v2ray.ang.util.tickHaptic

/**
 * R9 - "double-press is impossible by construction" (22-components.md 1/R9 and 2.8).
 *
 * A double submit is not prevented by remembering to disable a button; it is prevented by there
 * being no way to attach a listener that does not carry the guard. This file is that way, and it
 * is the ONLY place under `ui` allowed to call [View.setOnClickListener] directly - the review
 * (the glob is spelled out in prose here on purpose: Kotlin block comments NEST, so writing the
 * two-star wildcard form inside a KDoc opens a comment that never closes and the file stops
 * parsing at EOF)
 * grep is `grep -rn 'setOnClickListener' ui/`, and every hit outside this file is a defect.
 *
 * The window is [R.integer.input_debounce] 500ms, declared once in `res/values/motion.xml`, and
 * the last-accepted timestamp lives on the view itself under [R.id.tag_last_click], declared once
 * in `res/values/ids.xml`. Neither is re-declared here. Storing the stamp on the view rather than
 * in a screen-level field is what makes the guard survive RecyclerView recycling and configuration
 * change without any per-screen state.
 *
 * Layering, per 22-components.md 2.8: this is layer 3. Layer 1 is a command that reports its own
 * in-flight state (prefer it for anything that touches the network - it also gets the R8 loading
 * treatment for free), and layer 2 is the shell's `navigationInFlight` flag. A control gets the
 * strongest layer that applies; this one always applies.
 */

/**
 * Which of the two sanctioned haptics a tap fires. 00-rules.md 8.10 and
 * 32-master-plan-android.md 7.6 close this set: nothing else in the product vibrates, and in
 * particular a plain row tap, a navigation push and a scroll do not.
 *
 * - [PRESS] - `View.pressHaptic()`: connect, disconnect, purchase confirm, destructive confirm.
 * - [TICK]  - `View.tickHaptic()`: tab switch, stepper increment, segmented change, switch toggle.
 * - [NONE]  - everything else, which is most things. This is the default on purpose.
 */
enum class Haptic { NONE, PRESS, TICK }

/**
 * Attaches a click listener that cannot fire twice inside [R.integer.input_debounce] 500ms.
 *
 * Use this instead of `setOnClickListener` everywhere. The double tap that opens two
 * `BuyTariffActivity` instances, sends two payment requests or pushes the same screen twice is
 * dropped by the guard rather than by the caller remembering to write one.
 *
 * ```kotlin
 * binding.btnBuy.onSingleClick(Haptic.PRESS) { startActivity(intent) }
 * binding.rowDns.onSingleClick { openDnsSettings() }
 * ```
 *
 * The guard is per-view: two different views that start the same screen each get their own 500ms
 * window, so a screen whose CTA is duplicated (a card and a button that do the same thing) still
 * needs the shell's navigation flag or a command that gates itself.
 *
 * @param haptic which sanctioned haptic to fire, [Haptic.NONE] by default. Fired only on an
 *   ACCEPTED tap, never on one the guard swallows - a buzz with no consequence teaches the user
 *   that the app is unreliable.
 * @param action what the tap does. It receives the view so a shared listener can tell callers apart.
 */
fun View.onSingleClick(haptic: Haptic = Haptic.NONE, action: (View) -> Unit) {
    setOnClickListener { v ->
        if (!v.acceptClick()) return@setOnClickListener
        when (haptic) {
            Haptic.PRESS -> v.pressHaptic()
            Haptic.TICK -> v.tickHaptic()
            Haptic.NONE -> Unit
        }
        action(v)
    }
}

/**
 * The guard itself, for the handful of call sites that cannot use [onSingleClick] because the
 * platform hands them the callback (a menu item, a dialog button, an `OnItemClickListener`).
 *
 * Returns true and opens a new 500ms window when the tap should be honoured; returns false when it
 * arrived inside the window of the previous accepted tap. Call it first and return early:
 *
 * ```kotlin
 * override fun onOptionsItemSelected(item: MenuItem): Boolean {
 *     if (!toolbar.acceptClick()) return true
 *     ...
 * }
 * ```
 */
fun View.acceptClick(): Boolean {
    val now = SystemClock.elapsedRealtime()
    val last = getTag(R.id.tag_last_click) as? Long ?: 0L
    if (now - last < resources.getInteger(R.integer.input_debounce)) return false
    setTag(R.id.tag_last_click, now)
    return true
}

/**
 * Detaches the listener and makes the view non-interactive again, clearing the debounce stamp so
 * a recycled view does not inherit the previous item's window.
 *
 * The binders in this package call this for every affordance they are NOT asked to show, which is
 * how "exactly one trailing element" holds for a recycled row.
 */
fun View.clearClick() {
    setOnClickListener(null)
    isClickable = false
    isFocusable = false
    setTag(R.id.tag_last_click, null)
}
