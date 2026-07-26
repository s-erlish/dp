package com.v2ray.ang.ui.component

import android.animation.Animator
import android.util.TypedValue
import android.view.View
import android.view.animation.Interpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.InterpolatorRes
import androidx.core.content.ContextCompat
import com.v2ray.ang.R
import com.v2ray.ang.util.reducedMotion
import java.util.WeakHashMap

/**
 * Plumbing shared by every binder in this package: slot lookup, theme-attribute colours, the
 * shared interpolators, and the reduced-motion gate. Nothing here is a component; it is the layer
 * that lets the components below stay short.
 *
 * ## Why slots are resolved by NAME
 *
 * The reusable layouts (`res/layout/view_row.xml`, `view_empty_state.xml`, …) are being written by
 * a different agent in the same wave and did not exist when this package was written, so their
 * generated view-binding classes and `R.id` constants could not be referenced without failing the
 * build. Resolution therefore goes through the resource table by name, once per name per process.
 *
 * The list of candidate names for each slot is the ONE thing to change when those layouts land:
 * pin each list to the single real id and the lookup becomes exact. The runtime cost is one
 * `getIdentifier` per distinct name for the life of the process; everything after that is an
 * ordinary `findViewById` on a shallow row.
 *
 * The resource package is read back from a resource this module is certain of rather than from
 * `context.packageName`, because the two differ here: the namespace is `com.v2ray.ang` while the
 * installed applicationId is `com.departamentvpn.app.fdroid`.
 *
 * Not thread-safe, and does not need to be: binders run on the main thread.
 */
internal object Slots {

    private val ids = HashMap<String, Int>()
    private var resourcePackage: String? = null

    /** Resolves a slot id by name, or 0 when the current layouts declare no such id. */
    private fun idOf(host: View, name: String): Int = ids.getOrPut(name) {
        val res = host.resources
        val pkg = resourcePackage ?: res.getResourcePackageName(R.id.tag_last_click)
            .also { resourcePackage = it }
        res.getIdentifier(name, "id", pkg)
    }

    /** The first of [names] that both exists in the resource table and is present under [root]. */
    fun find(root: View, names: Array<out String>): View? {
        for (name in names) {
            val id = idOf(root, name)
            if (id == 0) continue
            root.findViewById<View>(id)?.let { return it }
        }
        return null
    }

    fun text(root: View, names: Array<out String>): TextView? = find(root, names) as? TextView

    fun image(root: View, names: Array<out String>): ImageView? = find(root, names) as? ImageView

    /**
     * The loud failure. A slot the caller explicitly asked for and the layout does not carry is a
     * layout/binder mismatch, i.e. a programming error, and it is reported at the first bind rather
     * than silently drawing a row with a missing affordance.
     */
    fun <T : View> requireSlot(view: T?, slot: String, names: Array<out String>): T =
        view ?: error(
            "Departament component: the layout does not declare the \"$slot\" slot " +
                "(looked for ${names.joinToString(" / ")}). " +
                "Either the wrong layout was passed to the binder, or Slots' candidate id names " +
                "need pinning to the real ids in res/layout/view_*.xml."
        )
}

/**
 * Resolves a colour that the theme carries as an attribute. Colours come from `?attr/...`, never
 * from a raw hex and never from `@color/md_theme_*` directly, because the latter two are what break
 * the light and mono themes (00-rules.md 6, 32-master-plan-android.md 2.2).
 */
@ColorInt
internal fun View.themeColor(@AttrRes attr: Int): Int {
    val value = TypedValue()
    if (!context.theme.resolveAttribute(attr, value, true)) return 0
    return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
}

/** One of the four curves in `res/interpolator/`. No binder declares a curve of its own. */
internal fun View.curve(@InterpolatorRes interpolator: Int): Interpolator =
    android.view.animation.AnimationUtils.loadInterpolator(context, interpolator)

/** A motion duration token from `res/values/motion.xml`, in ms. No binder hard-codes one. */
internal fun View.durationOf(token: Int): Long = resources.getInteger(token).toLong()

/**
 * The reduced-motion contract in one call (00-rules.md 8.8, 32-master-plan-android.md 7.5).
 *
 * Runs [animate] when the OS animation scale is non-zero, and [snap] - the END STATE, never a
 * halfway house - when it is zero. Every imperative animation in this package goes through here;
 * one that does not is a P1 accessibility defect.
 */
internal inline fun View.motion(snap: () -> Unit, animate: () -> Unit) {
    if (reducedMotion()) snap() else animate()
}

/**
 * Per-view animator bookkeeping, so a rebound view cannot end up with two animators fighting over
 * the same property. A [WeakHashMap] rather than `setTag`, because tag keys must be declared id
 * resources and `res/values/ids.xml` belongs to another wave.
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
