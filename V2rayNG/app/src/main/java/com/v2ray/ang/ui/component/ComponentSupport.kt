package com.v2ray.ang.ui.component

import android.animation.Animator
import android.util.TypedValue
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.IntegerRes
import androidx.annotation.InterpolatorRes
import androidx.core.content.ContextCompat
import com.v2ray.ang.util.reducedMotion
import java.util.WeakHashMap

/**
 * Plumbing shared by every binder in this package: theme-attribute colours, the shared curves and
 * duration tokens, the reduced-motion gate, and per-view animator bookkeeping.
 *
 * Nothing here is a component. It exists so the binders below never hard-code a duration, never
 * name a curve of their own, and never animate without checking whether the user wants motion.
 */

/**
 * Resolves a colour the theme carries as an attribute.
 *
 * Colours come from `?attr/...`, never from a raw hex and never from `@color/md_theme_*` directly:
 * the first two are what break the light and the mono themes (00-rules.md 6,
 * 32-master-plan-android.md 2.2). Returns 0 when the attribute is not mapped in the current theme,
 * which is a theme bug and shows up immediately as a black glyph.
 */
@ColorInt
internal fun View.themeColor(@AttrRes attr: Int): Int {
    val value = TypedValue()
    if (!context.theme.resolveAttribute(attr, value, true)) return 0
    return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
}

/**
 * One of the four curves in `res/interpolator/` - `ease_out_quart` for press, `ease_out_quint` for
 * reveal and settle, `ease_standard` for two-way crossfade, `ease_out_expo` for the single auth to
 * home hand-off. No binder declares a curve of its own (00-rules.md 8.3).
 */
internal fun View.curve(@InterpolatorRes interpolator: Int): Interpolator =
    AnimationUtils.loadInterpolator(context, interpolator)

/** A duration token from `res/values/motion.xml`, in ms. No binder hard-codes one (00-rules.md 3.7). */
internal fun View.durationOf(@IntegerRes token: Int): Long = resources.getInteger(token).toLong()

/**
 * The reduced-motion contract in one call (00-rules.md 8.8, 32-master-plan-android.md 7.5).
 *
 * Runs [animate] when the OS animation scale is non-zero and [snap] - the END STATE, never a
 * halfway house - when it is zero. Every imperative animation in this package goes through here; an
 * animation that does not is a P1 accessibility defect.
 */
internal inline fun View.motion(snap: () -> Unit, animate: () -> Unit) {
    if (reducedMotion()) snap() else animate()
}

/**
 * Per-view animator bookkeeping, so a rebound view cannot end up with two animators fighting over
 * the same property - the classic RecyclerView flicker.
 *
 * A [WeakHashMap] rather than `setTag`, because a tag key must be a declared id resource and
 * `res/values/ids.xml` belongs to another wave; the one id it does declare, `tag_last_click`, is
 * spoken for by the double-press guard.
 */
internal object RunningAnimators {

    private val running = WeakHashMap<View, Animator>()

    fun set(view: View, animator: Animator) {
        cancel(view)
        running[view] = animator
        animator.start()
    }

    fun cancel(view: View) {
        running.remove(view)?.cancel()
    }
}

/**
 * The loud failure for a missing slot. Every binder here is bound to one layout file; a null child
 * means the caller passed the wrong root, which is a programming error and is reported at the first
 * bind rather than silently drawing a component with a hole in it.
 */
internal fun <T : View> View.slot(id: Int, layout: String, name: String): T {
    val view = findViewById<T>(id)
    return view ?: error(
        "Departament component: \"$name\" is missing. The view passed to this binder is not a " +
            "$layout - pass the root of that layout (the <include>'s own view), not its parent."
    )
}
