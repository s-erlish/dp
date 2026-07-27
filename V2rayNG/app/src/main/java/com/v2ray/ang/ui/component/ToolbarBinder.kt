package com.v2ray.ang.ui.component

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.v2ray.ang.R

/**
 * The seamless sub-page toolbar - `res/layout/view_toolbar.xml` bound in one call (owner request
 * 0.4.6, 00-rules.md 4.8, 22-components.md 12.1, 32-master-plan-android.md 8.6).
 *
 * ```
 * [ 16 ][ back, 24 glyph ][ 16 ][ Title 16/700 ][ flex ][ action (0-1) ][ 16 ]
 * ```
 *
 * The header is not a bar, it is the top of the page: the same background as the page, no
 * elevation, no divider, no shadow, no scrim, no `liftOnScroll`. That is the layout's business.
 * What this binder owns is the three things a screen gets wrong when it wires a header by hand:
 *
 * - **The back affordance is either present with a name and a working close, or absent with the
 *   title moved to the gutter.** There is no third state, and there is no back button whose
 *   `contentDescription` somebody forgot.
 * - **At most one action.** [bind] takes one, so a second one has nowhere to go; a screen that
 *   genuinely needs two puts them behind an overflow.
 * - **The hairline is the only permitted boundary**, it ships at alpha 0, and it fades in only once
 *   the content behind it has scrolled ([attachTo]). It never appears at rest.
 */
object ToolbarBinder {

    /**
     * Binds a sub-page header: back arrow, title, and at most one action.
     *
     * ```kotlin
     * ToolbarBinder.bind(
     *     root = binding.toolbar.toolbar,
     *     title = getString(R.string.routing_title),
     *     activity = this,
     *     actionIcon = R.drawable.ic_add_24dp,
     *     actionDescription = getString(R.string.routing_add_cd),
     *     onAction = { addRule() },
     * )
     * ```
     *
     * @param root the inflated header, i.e. `@id/toolbar`.
     * @param title the page's title. Ramp role Title 16/700, one line, ellipsised at the end. The
     *   most repeated defect in the app is a Russian title drawn in the Latin wordmark face, which
     *   carries no Cyrillic at all; the layout's `textAppearance` is what fixes it, so do not
     *   override it here.
     * @param activity the host. When it is non-null the back arrow is shown and closes the screen
     *   through [SubPage.close], so the exit transition matches the entrance. Pass null on a TAB
     *   screen, which has no back affordance: the leading gap then takes over and the title still
     *   starts at the 16dp gutter.
     * @param actionIcon the one trailing action's glyph, or `0` for none.
     * @param actionDescription the action's name, stating the ACTION and not the object -
     *   «Добавить сервер», not «Сервер». Required whenever [actionIcon] is set: an icon-only
     *   control with no accessible name is a P1 defect (00-rules.md 14.3).
     * @param onAction what the action does, routed through [onSingleClick].
     */
    fun bind(
        root: View,
        title: CharSequence,
        activity: Activity? = null,
        @DrawableRes actionIcon: Int = 0,
        actionDescription: CharSequence? = null,
        onAction: ((View) -> Unit)? = null,
    ) {
        require(actionIcon == 0 || (actionDescription != null && onAction != null)) {
            "Departament toolbar: an action needs a glyph, a name and a listener. An icon-only " +
                "control with no accessible name is a P1 (00-rules.md 14.3)."
        }

        val slots = ToolbarSlots.of(root)
        slots.title.text = title

        if (activity == null) {
            slots.back.clearClick()
            slots.back.visibility = View.GONE
            // 12 here plus the title's own 4 puts a tab screen's title on the 16dp gutter.
            slots.leadingGap.visibility = View.VISIBLE
        } else {
            slots.back.visibility = View.VISIBLE
            slots.leadingGap.visibility = View.GONE
            slots.back.onSingleClick { SubPage.close(activity) }
        }

        if (actionIcon == 0) {
            slots.action.clearClick()
            slots.action.visibility = View.GONE
        } else {
            slots.action.setIconResource(actionIcon)
            slots.action.contentDescription = actionDescription
            slots.action.visibility = View.VISIBLE
            onAction?.let { slots.action.onSingleClick(action = it) }
        }
    }

    /**
     * Fades the hairline in when [scroller] has moved off the top and back out when it returns,
     * over `motion_state` 220ms on `ease_standard` in and `motion_state_exit` 165ms out - exit is
     * 75% of enter, here as everywhere.
     *
     * This is the only thing about the header that is allowed to change on scroll: no colour, no
     * elevation, no shadow, no collapsing hero, no scroll-driven alpha on anything else
     * (32-master-plan-android.md 7.4).
     *
     * Call it once, from `onCreate`. A `RecyclerView` keeps every listener it is given, so calling
     * it per data load stacks duplicates that all animate the same hairline.
     */
    fun attachTo(root: View, scroller: RecyclerView) {
        val hairline = ToolbarSlots.of(root).hairline
        scroller.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                hairline.fade(recyclerView.canScrollVertically(-1))
            }
        })
        hairline.fade(scroller.canScrollVertically(-1))
    }

    /** [attachTo], for a screen whose content is a `NestedScrollView` rather than a list. */
    fun attachTo(root: View, scroller: NestedScrollView) {
        val hairline = ToolbarSlots.of(root).hairline
        scroller.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { view, _, _, _, _ ->
                hairline.fade(view.canScrollVertically(-1))
            }
        )
        hairline.fade(scroller.canScrollVertically(-1))
    }

    private fun View.fade(show: Boolean) {
        val target = if (show) 1f else 0f
        if (alpha == target) return
        motion(snap = { alpha = target }) {
            animate()
                .alpha(target)
                .setDuration(
                    durationOf(if (show) R.integer.motion_state else R.integer.motion_state_exit)
                )
                .setInterpolator(curve(R.interpolator.ease_standard))
                .start()
        }
    }
}

/** The toolbar's child views, resolved once from `view_toolbar.xml`. */
class ToolbarSlots private constructor(
    val root: View,
    val back: MaterialButton,
    val leadingGap: View,
    val title: TextView,
    val action: MaterialButton,
    val hairline: View,
) {

    companion object {

        private const val LAYOUT = "res/layout/view_toolbar.xml"

        fun of(root: View): ToolbarSlots = ToolbarSlots(
            root = root,
            back = root.slot(R.id.toolbar_back, LAYOUT, "toolbar_back"),
            leadingGap = root.slot(R.id.toolbar_leading_gap, LAYOUT, "toolbar_leading_gap"),
            title = root.slot(R.id.toolbar_title, LAYOUT, "toolbar_title"),
            action = root.slot(R.id.toolbar_action, LAYOUT, "toolbar_action"),
            hairline = root.slot(R.id.toolbar_hairline, LAYOUT, "toolbar_hairline"),
        )
    }
}
