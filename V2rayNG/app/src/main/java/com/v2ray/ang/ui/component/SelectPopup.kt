package com.v2ray.ang.ui.component

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.v2ray.ang.R
import com.v2ray.ang.util.reducedMotion

/**
 * THE SELECT POPUP — «окошко выбора» (handoff README §6, «Выбор из списка»).
 *
 * It replaces the single-choice `AlertDialog` on every settings row that picks one value out of a
 * short, fixed list: **Режим · DNS · Пинг · Оформление · Язык · Автообновление подписки ·
 * Доменная стратегия · Интервал · Сортировка.** A dialog for a two-item choice interrupts the
 * screen, dims everything the user was reading and asks them to travel to the middle of the display
 * and back; this opens the list where the value already is.
 *
 * ## Using it
 *
 * ```kotlin
 * SelectPopup.show(
 *     anchor = binding.rowMode,                       // the row the popup hangs off
 *     options = listOf("TUN", "Только прокси"),
 *     selectedIndex = idx,
 *     widthRes = R.dimen.select_popup_w_mode,         // sized to the LONGEST option
 *     valueView = binding.valueMode,                  // dims while the popup is open
 *     caret = binding.caretMode,                      // rotates 180° while the popup is open
 * ) { picked ->
 *     applyMode(picked)
 * }
 * ```
 *
 * `valueView` and `caret` are optional; pass them and the row plays its half of the interaction,
 * omit them and only the flyout moves. `onPick` fires with the chosen index and the popup closes
 * itself — a caller never dismisses it by hand, and re-reads its own state afterwards exactly as it
 * would have after a dialog.
 *
 * The trailing glyph on a select row is a CARET (`@drawable/ic_arrow_drop_down`), not the chevron a
 * navigation row carries: a chevron promises a screen, and this row does not open one. That is the
 * prototype's own distinction (`chev: pop ? CARET : CHEV`).
 *
 * ## What §6 asks for, and where each requirement is answered
 *
 * | §6 | Here |
 * |---|---|
 * | anchored top-right, offset 48 / 8 | [position], `select_popup_offset_*` |
 * | width by the longest option | `widthRes`, `select_popup_w_*` |
 * | SOLID fill one step above the card | `@drawable/bg_select_popup` |
 * | radius 16, 1dp outline, shadow 20×44 | `@drawable/bg_select_popup` + `select_popup_elevation` |
 * | the open row rises above its neighbours | [liftAnchor] |
 * | the section card must not clip it | [unclipAncestors], and the popup is hosted at the content root |
 * | reveal is a CLIP top→bottom, never a scale | [reveal] |
 * | the row's value dims while it is open | [dimValue] |
 * | the caret turns 180° over 300ms | [turnCaret] |
 * | option ≥38dp, radius 11, selected = step up + 16dp check | `@layout/view_select_popup_item` |
 * | exactly one popup open at a time | [current], and [show] closes the previous one |
 *
 * ## The three README §11 grabli this component is built around
 *
 * - **grabl 3, «текст дёргается»** — the reveal is a clip and never a scale. A scale re-rasterises
 *   the option labels every frame and they crawl. `View.setClipBounds` moves no pixels the text
 *   lives in; it only changes how much of the already-drawn surface is visible.
 * - **grabl 4, «окошко срезается снизу»** — the popup is added to the window's content root, so no
 *   card, list or scroll container is in a position to trim it. [unclipAncestors] additionally
 *   turns off `clipChildren` / `clipToPadding` all the way up, so the same component still behaves
 *   if a screen later hosts it inside the card. `clipToOutline` is deliberately left alone: it is
 *   what gives a section card its rounded corner, and switching it off would square the card off
 *   for as long as the popup was open.
 * - **grabl 5, «сквозь окошко виден текст»** — a solid fill, plus [liftAnchor] raising the open row
 *   above its siblings. Z-order among siblings follows elevation from API 21, so no `bringToFront`
 *   is needed and nothing about the view hierarchy is reordered — which matters, because reordering
 *   is not reversible without rebuilding the row.
 */
object SelectPopup {

    /** The one open popup, or null. §6: «Открыто всегда одно окошко». */
    private var current: Session? = null

    /**
     * Opens the popup under [anchor].
     *
     * @param anchor the row the value belongs to. Its top-right corner is what the popup hangs off,
     *   and it is the view that lifts above its neighbours while the popup is open.
     * @param options the labels, in the order they are shown. Index is the contract with [onPick].
     * @param selectedIndex the current value's index, or -1 when nothing is selected.
     * @param widthRes the CONTENT width, from the `select_popup_w_*` set — sized to the longest
     *   option so the body cannot reflow when the selection changes. Defaults to
     *   `select_popup_w_default` for the three pickers §6 gives no measurement for.
     * @param valueView the row's value text. Dims to `colorOnSurfaceVariant` while the popup is
     *   open and returns to `colorOnSurface` when it closes.
     * @param caret the row's trailing caret. Turns 180° while the popup is open.
     * @param onPick fires with the chosen index; the popup has already closed. Not called when the
     *   user dismisses without choosing, and not called when the user re-picks the current value.
     */
    fun show(
        anchor: View,
        options: List<CharSequence>,
        selectedIndex: Int,
        @DimenRes widthRes: Int = R.dimen.select_popup_w_default,
        valueView: TextView? = null,
        caret: View? = null,
        onPick: (Int) -> Unit,
    ) {
        // Tapping the row that is already open closes it, which is what a user expects of a
        // control that toggles, and is also the "only one open" rule at its simplest.
        val reopeningSame = current?.anchor === anchor
        dismiss()
        if (reopeningSame) return

        val host = anchor.contentRoot() ?: return
        val res = anchor.resources

        val popup = LayoutInflater.from(anchor.context)
            .inflate(R.layout.view_select_popup, host, false) as ViewGroup
        // Invisible until [Session.reveal] has a measured height to unroll to. Without this
        // the body draws one frame at translationX 0 — the left edge of the screen — because
        // its own width is not known until the first layout pass.
        popup.alpha = 0f

        val contentWidth = res.getDimensionPixelSize(widthRes)
        val padding = res.getDimensionPixelSize(R.dimen.select_popup_padding)
        val stroke = res.getDimensionPixelSize(R.dimen.stroke_hairline)

        val session = Session(anchor, popup, valueView, caret)

        options.forEachIndexed { index, label ->
            popup.addView(
                option(popup, label, index == selectedIndex, contentWidth) {
                    val chosen = index
                    dismiss()
                    if (chosen != selectedIndex) onPick(chosen)
                }
            )
        }

        val catcher = touchCatcher(host)
        host.addView(catcher)
        host.addView(
            popup,
            FrameLayout.LayoutParams(
                contentWidth + 2 * (padding + stroke),
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
        )

        session.catcher = catcher
        current = session

        session.liftAnchor()
        session.unclipAncestors()
        session.dimValue(open = true)
        session.turnCaret(open = true)
        session.follow(host)
        session.reveal(open = true)
        session.grabBack(catcher)
    }

    /** Closes whatever is open. Safe to call when nothing is. */
    fun dismiss() {
        current?.close()
        current = null
    }

    /** True while a popup is open — for [anchor] specifically when one is given. */
    fun isShowing(anchor: View? = null): Boolean =
        current != null && (anchor == null || current?.anchor === anchor)

    // ---------------------------------------------------------------------------------------

    private fun option(
        parent: ViewGroup,
        label: CharSequence,
        selected: Boolean,
        width: Int,
        onClick: () -> Unit,
    ): View {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_select_popup_item, parent, false)
        view.layoutParams.width = width

        view.findViewById<TextView>(R.id.select_option_label).text = label

        // The check slot never changes VISIBILITY, only alpha: hiding it would let the popup
        // change width between one selection and the next, which is the reflow §6 forbids.
        view.findViewById<ImageView>(R.id.select_option_check).alpha = if (selected) 1f else 0f

        view.isActivated = selected
        view.pressFeedback(R.anim.press_button)
        view.onSingleClick { onClick() }

        // The state is ANNOUNCED, not only drawn: without this the list reads to TalkBack as a
        // stack of identical buttons with no indication of which one is in force.
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isSelected = selected
            }
        })
        return view
    }

    /**
     * The full-bleed, fully transparent view that closes the popup on a tap outside it.
     *
     * NOT a scrim: nothing is dimmed. A select popup is not a modal decision, it is a value being
     * changed in place, and darkening the screen behind it would give it the weight of the dialog
     * it exists to replace.
     */
    private fun touchCatcher(host: ViewGroup): View = View(host.context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        setOnClickListener { dismiss() }
    }

    /** The window's content view, which is a FrameLayout and therefore positions by coordinates. */
    private fun View.contentRoot(): ViewGroup? =
        rootView.findViewById(android.R.id.content)

    // ---------------------------------------------------------------------------------------

    /**
     * One open popup and everything it has to put back.
     *
     * Every field that ends in `Saved` is state belonging to a view this component did not create
     * and must return untouched — the rule being that a transient surface may borrow a property of
     * the screen it floats over, but never keep it.
     */
    private class Session(
        val anchor: View,
        val popup: ViewGroup,
        val valueView: TextView?,
        val caret: View?,
    ) {
        var catcher: View? = null

        private var elevationSaved = 0f
        private var outlineSaved: ViewOutlineProvider? = null
        private val clipSaved = mutableListOf<Triple<ViewGroup, Boolean, Boolean>>()
        private var valueColorSaved: Int? = null
        private var focusSaved: View? = null
        private var preDraw: ViewTreeObserver.OnPreDrawListener? = null
        private var valueAnimator: ValueAnimator? = null
        private var revealAnimator: ValueAnimator? = null

        /**
         * README §11 grabl 5. Elevation only, no `bringToFront`: sibling draw order follows
         * elevation from API 21 and minSdk here is 24, and reordering children is the one version
         * of this that cannot be undone without rebuilding the row.
         *
         * The outline provider goes to null for the duration so the lift costs Z-order and nothing
         * else — a row with elevation and a background outline would start casting a shadow onto
         * the card it sits in, which is a shadow this product does not have anywhere else.
         */
        fun liftAnchor() {
            elevationSaved = anchor.elevation
            outlineSaved = anchor.outlineProvider
            anchor.outlineProvider = null
            anchor.elevation = anchor.resources
                .getDimension(R.dimen.select_popup_row_elevation)
        }

        /**
         * README §11 grabl 4. `clipChildren` / `clipToPadding` off from the anchor's parent up to
         * the content root, so nothing between the row and the window can trim the flyout.
         *
         * `clipToOutline` is left ALONE on purpose: it is what rounds a section card's corners, and
         * turning it off would square the card off for as long as the popup was open. The popup
         * does not need it — it is hosted at the content root, above every card in the tree.
         */
        fun unclipAncestors() {
            var parent = anchor.parent
            while (parent is ViewGroup) {
                clipSaved += Triple(parent, parent.clipChildren, parent.clipToPadding)
                parent.clipChildren = false
                parent.clipToPadding = false
                if (parent.id == android.R.id.content) break
                parent = parent.parent
            }
        }

        /**
         * §6: «Значение в строке гаснет до приглушённого, пока окошко открыто».
         *
         * The value steps down to `colorOnSurfaceVariant` while the list of alternatives is on
         * screen, because for those few hundred milliseconds it is one option among several rather
         * than the value. Over `motion_state` 220, §8's «Смена цвета».
         *
         * CLOSING RESTORES THE COLOUR THE ROW ALREADY HAD, and does not assume `colorOnSurface`.
         * The prototype draws a select row's resting value at full strength and only a NAVIGATION
         * row's at the muted tone, but the shipped rows are all still on the muted one; forcing
         * them up on close would leave whichever row had been opened permanently brighter than its
         * neighbours. Making the resting value full-strength is the Настройки wave's change to
         * make, on every select row at once, and this component must not do it one row at a time
         * behind their back.
         */
        fun dimValue(open: Boolean) {
            val view = valueView ?: return
            val from = view.currentTextColor
            if (open && valueColorSaved == null) valueColorSaved = from
            val to = if (open) {
                view.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            } else {
                valueColorSaved ?: return
            }
            if (from == to) return

            valueAnimator?.cancel()
            view.motion(snap = { view.setTextColor(to) }) {
                valueAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
                    duration = view.durationOf(R.integer.motion_state)
                    interpolator = view.curve(R.interpolator.ease_out_quart)
                    addUpdateListener { view.setTextColor(it.animatedValue as Int) }
                    start()
                }
            }
        }

        /** §6: the caret turns 180° over `motion_caret` 300ms. */
        fun turnCaret(open: Boolean) {
            val view = caret ?: return
            val target = if (open) 180f else 0f
            view.motion(snap = { view.rotation = target }) {
                view.animate()
                    .rotation(target)
                    .setDuration(view.durationOf(R.integer.motion_caret))
                    .setInterpolator(view.curve(R.interpolator.ease_out_quart))
                    .start()
            }
        }

        /**
         * §6: «Раскрытие — срез сверху вниз (clip), 260 мс, плюс прозрачность 180 мс. Масштаб не
         * использовать — от него текст внутри дёргается.»
         *
         * `setClipBounds` changes how much of an already-drawn surface is painted; it moves nothing
         * the glyphs live in, so the labels are rasterised exactly once and cannot crawl. A scale
         * would re-rasterise them on every frame, which is grabl 3.
         *
         * The clip runs 260 and the opacity 180 on purpose — the body is fully opaque before it has
         * finished unrolling, so the reveal reads as a blind being drawn rather than a card fading
         * up.
         */
        fun reveal(open: Boolean, onEnd: (() -> Unit)? = null) {
            val width = popup.width.takeIf { it > 0 } ?: popup.measuredWidth
            val height = popup.height.takeIf { it > 0 } ?: popup.measuredHeight
            if (height <= 0) {
                // Not laid out yet: run on the next pass, with nothing visible in between.
                popup.alpha = 0f
                popup.post { reveal(open, onEnd) }
                return
            }

            revealAnimator?.cancel()
            if (popup.reducedMotion()) {
                popup.clipBounds = null
                popup.alpha = if (open) 1f else 0f
                onEnd?.invoke()
                return
            }

            val from = if (open) 0 else height
            val to = if (open) height else 0
            popup.alpha = if (open) 0f else 1f
            popup.animate()
                .alpha(if (open) 1f else 0f)
                .setDuration(popup.durationOf(R.integer.motion_popup_fade))
                .setInterpolator(popup.curve(R.interpolator.ease_out_quart))
                .start()

            revealAnimator = ValueAnimator.ofInt(from, to).apply {
                duration = popup.durationOf(R.integer.motion_popup)
                interpolator = popup.curve(R.interpolator.ease_out_quart)
                addUpdateListener {
                    popup.clipBounds = Rect(0, 0, width, it.animatedValue as Int)
                }
                start()
            }
            if (onEnd != null) {
                popup.postDelayed(onEnd, popup.durationOf(R.integer.motion_popup))
            }
        }

        /**
         * Keeps the popup pinned to its row for as long as it is open.
         *
         * A settings screen is a scroll container, so the anchor moves; re-measuring on every
         * pre-draw is a handful of arithmetic per frame and means the flyout never separates from
         * the value it belongs to. Dismissing on scroll was the alternative and it is worse — a
         * two-pixel drag while reaching for an option should not close the list.
         */
        fun follow(host: ViewGroup) {
            position(host)
            preDraw = ViewTreeObserver.OnPreDrawListener {
                position(host)
                true
            }
            anchor.viewTreeObserver.addOnPreDrawListener(preDraw)
        }

        /** §6: top 48dp below the row's top edge, right edge 8dp in from the row's right edge. */
        private fun position(host: ViewGroup) {
            val bounds = Rect(0, 0, anchor.width, anchor.height)
            host.offsetDescendantRectToMyCoords(anchor, bounds)
            val res = anchor.resources
            val top = res.getDimensionPixelSize(R.dimen.select_popup_offset_top)
            val right = res.getDimensionPixelSize(R.dimen.select_popup_offset_right)
            val width = popup.width.takeIf { it > 0 } ?: popup.measuredWidth
            popup.translationX = (bounds.right - right - width).toFloat()
            popup.translationY = (bounds.top + top).toFloat()
        }

        /**
         * Back closes the popup instead of leaving the screen.
         *
         * The touch catcher takes focus and listens for the key, which is the smallest thing that
         * works from a plain view with no access to the activity's dispatcher. The previously
         * focused view is put back on close, so keyboard and D-pad users end up where they started.
         */
        fun grabBack(catcher: View) {
            focusSaved = anchor.rootView.findFocus()
            catcher.isFocusableInTouchMode = true
            catcher.requestFocus()
            catcher.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
        }

        /** Reverses every borrowed property, then takes the popup off the screen. */
        fun close() {
            preDraw?.let { anchor.viewTreeObserver.removeOnPreDrawListener(it) }
            preDraw = null

            dimValue(open = false)
            turnCaret(open = false)

            val host = popup.parent as? ViewGroup
            reveal(open = false) {
                host?.removeView(popup)
                catcher?.let { host?.removeView(it) }
                anchor.elevation = elevationSaved
                anchor.outlineProvider = outlineSaved
                clipSaved.forEach { (group, children, padding) ->
                    group.clipChildren = children
                    group.clipToPadding = padding
                }
                clipSaved.clear()
                // Only when nothing has opened in the meantime: tapping straight from one select
                // row to the next runs this close 260ms after the NEXT popup took focus, and
                // handing focus back then would drop the new popup's Back handling.
                if (current == null) focusSaved?.requestFocus()
                focusSaved = null
            }
        }
    }
}
