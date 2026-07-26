package com.v2ray.ang.ui.component

import android.content.res.ColorStateList
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.button.MaterialButton
import com.v2ray.ang.R

/**
 * The universal row (22-components.md 8, 32-master-plan-android.md 8.1) - the single most repeated
 * object in the product, and the reason this package exists: the same structure is hand-inlined
 * over a hundred times today and has drifted 2 to 4dp apart between copies.
 *
 * Geometry belongs to `res/layout/view_row.xml` and `@style/Widget.Departament.Row`:
 *
 * ```
 * [ 16 gutter ][ 40 tile, r12, 22 glyph ][ 12 ][ text column, weight 1 ][ ONE trailing ][ 16 ]
 * ```
 *
 * This binder owns the row's CONTENT and its STATE, and it enforces the two rules geometry cannot:
 *
 * 1. **Exactly one trailing element.** [Trailing] is a closed set, and the binder hides every
 *    affordance it was not asked for on every bind. Two trailing elements are not "discouraged"
 *    here, they are unreachable. The same reset is what makes recycling safe: a row that was a
 *    switch last frame cannot keep its switch when it is rebound as a chevron.
 * 2. **The row's job is not split in two.** When the trailing element is itself the action
 *    ([Trailing.IconAction], [Trailing.ActionButton]) the row is not clickable, per 22-components
 *    8.4 - two targets that do different things is the `layout_subscription_meta_bar` mistake.
 *    Passing `onClick` alongside one of those throws rather than shipping the defect.
 *
 * The rest follows the spec: the whole row is the touch target, the row GROWS with a two-line
 * subtitle instead of clipping (the layout declares `minHeight`, never `height`), press feedback is
 * the background step in `@drawable/bg_row` and never a scale (R5), text is styled by applying a
 * ramp role and never by an inline size or face, and colours come from theme attributes.
 */
object RowBinder {

    /**
     * The closed tile system (D-5): three colours and no fourth. Purple / orange / yellow / green
     * encode a category system this product does not have.
     *
     * At most three coloured tiles on one screen, and in practice a screen has one accent tile or
     * none - 56 of 65 tiles in the shipped app are blue, which is the defect this closes.
     *
     * [DESTRUCTIVE] is for a genuinely destructive CATEGORY, not for a destructive row: a
     * destructive row keeps a [NEUTRAL] tile and carries its red in the title
     * ([RowTone.DESTRUCTIVE]), because a red tile plus red text is the same signal twice and turns
     * the row into an alarm (22-components 8.6).
     */
    enum class TileRole { NEUTRAL, ACCENT, DESTRUCTIVE }

    /** The title's ramp role. [DESTRUCTIVE] resolves to `@color/color_destructive_text`. */
    enum class RowTone { DEFAULT, DESTRUCTIVE }

    /**
     * The trailing affordance, and the promise it makes to the user (32-master-plan-android.md 8.1).
     * Exactly one per row. The type is what enforces that.
     */
    sealed interface Trailing {

        /** A read-only fact. Not clickable, no ripple, no press feedback, not focusable. */
        data object None : Trailing

        /** Tapping pushes a screen. A 20dp `ic_chevron_right` in `?attr/colorOnSurfaceVariant`. */
        data object Chevron : Trailing

        /**
         * Tapping expands content inline, right here. The same chevron, rotated 0 -> 90.
         * [bind] sets the resting angle; [animateExpand] performs the transition.
         */
        data class Expand(val expanded: Boolean) : Trailing

        /**
         * Tapping changes the value in place - no screen, no dialog. This is the "cycle"
         * affordance; pass `ic_unfold_more` once that glyph exists in `res/drawable/`.
         *
         * The glyph is normally decorative - the row's name and value carry the meaning - so
         * [contentDescription] is normally null and the glyph is then hidden from TalkBack.
         */
        data class Glyph(
            @DrawableRes val icon: Int,
            val contentDescription: CharSequence? = null,
        ) : Trailing

        /**
         * A boolean applied immediately. The ROW owns the switch: tapping anywhere on the row
         * toggles it, the switch is not separately focusable, and the state is announced as a
         * checkable node rather than only drawn. Fires `tickHaptic()` per 00-rules.md 8.10.
         */
        data class Toggle(
            val checked: Boolean,
            val onCheckedChange: (Boolean) -> Unit,
        ) : Trailing

        /**
         * This item is the current selection (22-components 18). The check slot is ALWAYS reserved
         * and the glyph changes alpha, so selecting an item never reflows the list. This is the one
         * sanctioned pairing: a marker row may also carry a `value` - the server row's ping figure
         * beside its check.
         */
        data class Marker(val selected: Boolean) : Trailing

        /**
         * A 40dp icon button that performs the action in place. The row itself is inert.
         * [contentDescription] is not optional: an icon-only control with no name is a P1 defect.
         */
        data class IconAction(
            @DrawableRes val icon: Int,
            val contentDescription: CharSequence,
            val onClick: (View) -> Unit,
        ) : Trailing

        /**
         * A Tertiary button inside the row - the only place a Tertiary lives in a row
         * (22-components 8.4): «Привязать Telegram» -> «Привязать». The row itself is inert.
         */
        data class ActionButton(
            val label: CharSequence,
            val onClick: (View) -> Unit,
        ) : Trailing
    }

    /**
     * Binds one row.
     *
     * ```kotlin
     * RowBinder.bind(
     *     root = binding.rowDns.root,
     *     title = getString(R.string.settings_dns),
     *     glyph = R.drawable.ic_dns,
     *     value = "Cloudflare",
     *     trailing = RowBinder.Trailing.Chevron,
     *     onClick = { openDns() },
     * )
     * ```
     *
     * @param root the inflated row. Its children are resolved once; see [RowSlots].
     * @param title the row's name. Ramp role Title 16/700, max 2 lines, never ALL-CAPS.
     * @param subtitle six words on what the row DOES, or null. A subtitle that restates its title
     *   is deleted, not written; it states what is on when it is on, never a negation.
     * @param glyph the 22dp tile glyph. `0` hides the tile entirely, which is the correct shape for
     *   a plain (untiled) group - the row then starts at the 16dp gutter.
     * @param tileRole one of the three tile colours. Neutral unless the row is genuinely categorical.
     * @param tone the title's ramp role; [RowTone.DESTRUCTIVE] for «Удалить сервер», «Выйти».
     * @param value the row's current value - «DNS · Cloudflare». A settings list a user can audit by
     *   scrolling once, without opening anything, is the single best idea in the reference app.
     * @param valueIsNumeric true for a quantity, which puts the value on the Numeric role (tabular
     *   figures, so a live number does not reflow on every tick).
     * @param trailing the one affordance. Everything else is hidden.
     * @param enabled false draws the whole row at 0.38 and takes it out of the touch order (R6).
     * @param haptic normally [Haptic.NONE]: a row tap does not vibrate. A switch does, and the
     *   binder supplies that itself.
     * @param onClick what tapping the row does. Routed through [onSingleClick], so a double tap
     *   cannot fire it twice. MUST be null when the trailing element is itself the action.
     */
    fun bind(
        root: View,
        title: CharSequence,
        subtitle: CharSequence? = null,
        @DrawableRes glyph: Int = 0,
        tileRole: TileRole = TileRole.NEUTRAL,
        tone: RowTone = RowTone.DEFAULT,
        value: CharSequence? = null,
        valueIsNumeric: Boolean = false,
        trailing: Trailing = Trailing.Chevron,
        enabled: Boolean = true,
        haptic: Haptic = Haptic.NONE,
        onClick: ((View) -> Unit)? = null,
    ) {
        require(onClick == null || !trailing.rowIsInert()) {
            "Departament row: this trailing element owns the action, so the row itself must not " +
                "be clickable (22-components 8.4). Pass onClick = null."
        }
        require(trailing !is Trailing.Toggle || value == null) {
            "Departament row: a toggle row shows a title, an optional subtitle and the switch " +
                "(22-components 8.5). A value beside a switch is two trailing elements."
        }

        val slots = RowSlots.of(root)
        bindTile(slots, glyph, tileRole)
        bindText(slots, title, subtitle, tone)
        bindValue(slots, value, valueIsNumeric)
        resetTrailing(slots)
        bindTrailing(slots, trailing)
        bindInteraction(root, slots, trailing, enabled, haptic, onClick)
    }

    /**
     * The expand transition for a [Trailing.Expand] row: the chevron rotates 0 -> 90 over
     * `motion_state` 220ms on `ease_standard` (a rotation between two resting angles is a state
     * change, not a reveal). Reduced motion snaps to the end angle.
     *
     * Call it from the row's own click listener after flipping your expanded flag. [bind] only sets
     * the resting angle, so a rebind never replays the animation.
     */
    fun animateExpand(root: View, expanded: Boolean) {
        val chevron = RowSlots.of(root).chevron ?: return
        val target = if (expanded) EXPANDED_DEGREES else 0f
        chevron.motion(snap = { chevron.rotation = target }) {
            chevron.animate()
                .rotation(target)
                .setDuration(chevron.durationOf(R.integer.motion_state))
                .setInterpolator(chevron.curve(R.interpolator.ease_standard))
                .start()
        }
    }

    private fun Trailing.rowIsInert(): Boolean =
        this is Trailing.None || this is Trailing.IconAction || this is Trailing.ActionButton

    private fun bindTile(slots: RowSlots, @DrawableRes glyph: Int, role: TileRole) {
        val tile = slots.tile ?: return
        if (glyph == 0) {
            tile.visibility = View.GONE
            slots.tileGap?.visibility = View.GONE
            return
        }
        tile.visibility = View.VISIBLE
        slots.tileGap?.visibility = View.VISIBLE

        val background = when (role) {
            TileRole.NEUTRAL -> R.drawable.bg_tile_neutral
            TileRole.ACCENT -> R.drawable.bg_tile_accent
            TileRole.DESTRUCTIVE -> R.drawable.bg_tile_destructive
        }
        // setBackgroundResource re-derives padding from the drawable, which would drop the padding
        // that centres a 22dp glyph in the 40dp tile. Keep exactly what the style declared.
        val left = tile.paddingLeft
        val top = tile.paddingTop
        val right = tile.paddingRight
        val bottom = tile.paddingBottom
        tile.setBackgroundResource(background)
        tile.setPadding(left, top, right, bottom)

        val glyphView = slots.tileGlyph ?: tile as? ImageView ?: return
        glyphView.setImageResource(glyph)
        ImageViewCompat.setImageTintList(glyphView, ColorStateList.valueOf(tintFor(glyphView, role)))
        // The tile restates the title in picture form; TalkBack reads the title.
        glyphView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun tintFor(view: View, role: TileRole): Int = when (role) {
        TileRole.NEUTRAL -> ContextCompat.getColor(view.context, R.color.icon_glyph_neutral)
        TileRole.ACCENT -> view.themeColor(androidx.appcompat.R.attr.colorPrimary)
        TileRole.DESTRUCTIVE -> view.themeColor(androidx.appcompat.R.attr.colorError)
    }

    private fun bindText(
        slots: RowSlots,
        title: CharSequence,
        subtitle: CharSequence?,
        tone: RowTone,
    ) {
        slots.title?.let {
            it.text = title
            it.setTextAppearance(
                when (tone) {
                    RowTone.DEFAULT -> R.style.TextAppearance_App_Title
                    RowTone.DESTRUCTIVE -> R.style.TextAppearance_App_Title_Destructive
                }
            )
        }
        slots.subtitle?.let {
            it.text = subtitle ?: ""
            it.visibility = if (subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun bindValue(slots: RowSlots, value: CharSequence?, numeric: Boolean) {
        val view = slots.value ?: return
        if (value.isNullOrEmpty()) {
            view.text = ""
            view.visibility = View.GONE
            return
        }
        view.text = value
        view.visibility = View.VISIBLE
        view.setTextAppearance(
            if (numeric) R.style.TextAppearance_App_Numeric else R.style.TextAppearance_App_Subtitle
        )
    }

    /** Every affordance off, every listener detached. This is what makes "exactly one" true. */
    private fun resetTrailing(slots: RowSlots) {
        slots.chevron?.visibility = View.GONE
        slots.trailingGlyph?.visibility = View.GONE
        slots.marker?.visibility = View.GONE
        slots.toggle?.let {
            it.setOnCheckedChangeListener(null)
            it.visibility = View.GONE
        }
        slots.iconAction?.let {
            it.clearClick()
            it.visibility = View.GONE
        }
        slots.actionButton?.let {
            it.clearClick()
            it.visibility = View.GONE
        }
    }

    private fun bindTrailing(slots: RowSlots, trailing: Trailing) {
        when (trailing) {
            is Trailing.None -> Unit

            is Trailing.Chevron -> slots.chevronOrThrow().showGlyph(rotation = 0f)

            is Trailing.Expand -> slots.chevronOrThrow()
                .showGlyph(rotation = if (trailing.expanded) EXPANDED_DEGREES else 0f)

            is Trailing.Glyph -> {
                val view = Slots.requireSlot(
                    slots.trailingGlyph, "trailing glyph", RowSlots.TRAILING_GLYPH
                )
                view.setImageResource(trailing.icon)
                view.contentDescription = trailing.contentDescription
                view.importantForAccessibility = if (trailing.contentDescription == null) {
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
                } else {
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES
                }
                view.visibility = View.VISIBLE
            }

            is Trailing.Toggle -> {
                val view = Slots.requireSlot(slots.toggle, "switch", RowSlots.TOGGLE)
                view.visibility = View.VISIBLE
                view.isChecked = trailing.checked
                // The row owns the switch: one node, one target, one announcement.
                view.isClickable = false
                view.isFocusable = false
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                view.setOnCheckedChangeListener { _, checked -> trailing.onCheckedChange(checked) }
            }

            is Trailing.Marker -> {
                val view = Slots.requireSlot(slots.marker, "state marker", RowSlots.MARKER)
                // The slot stays reserved, so selection changes nothing about the layout.
                view.visibility = View.VISIBLE
                view.alpha = if (trailing.selected) 1f else 0f
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                // The fourth selection axis (22-components 18): weight 700 selected, 500 not.
                slots.title?.setTextAppearance(
                    if (trailing.selected) {
                        R.style.TextAppearance_App_Title
                    } else {
                        R.style.TextAppearance_App_Title_Medium
                    }
                )
            }

            is Trailing.IconAction -> {
                val view = Slots.requireSlot(slots.iconAction, "icon button", RowSlots.ICON_ACTION)
                view.visibility = View.VISIBLE
                view.contentDescription = trailing.contentDescription
                when (view) {
                    is MaterialButton -> view.setIconResource(trailing.icon)
                    is ImageView -> view.setImageResource(trailing.icon)
                    else -> view.setBackgroundResource(trailing.icon)
                }
                view.onSingleClick(action = trailing.onClick)
            }

            is Trailing.ActionButton -> {
                val view = Slots.requireSlot(
                    slots.actionButton, "action button", RowSlots.ACTION_BUTTON
                )
                view.visibility = View.VISIBLE
                view.text = trailing.label
                view.onSingleClick(action = trailing.onClick)
            }
        }
    }

    private fun RowSlots.chevronOrThrow(): ImageView =
        Slots.requireSlot(chevron, "chevron", RowSlots.CHEVRON)

    private fun ImageView.showGlyph(rotation: Float) {
        this.rotation = rotation
        visibility = View.VISIBLE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun bindInteraction(
        root: View,
        slots: RowSlots,
        trailing: Trailing,
        enabled: Boolean,
        haptic: Haptic,
        onClick: ((View) -> Unit)?,
    ) {
        root.isEnabled = enabled
        // R6: disabled is 0.38 on the WHOLE control, on both platforms.
        root.alpha = if (enabled) 1f else DISABLED_ALPHA
        root.isActivated = trailing is Trailing.Marker && trailing.selected

        val toggle = slots.toggle
        when {
            !enabled || trailing.rowIsInert() -> root.clearClick()

            trailing is Trailing.Toggle && toggle != null -> root.onSingleClick(Haptic.TICK) {
                toggle.isChecked = !toggle.isChecked
            }

            onClick != null -> {
                root.onSingleClick(haptic, onClick)
                root.isFocusable = true
            }

            else -> root.clearClick()
        }

        applySemantics(
            root = root,
            checkable = trailing is Trailing.Toggle,
            checked = trailing is Trailing.Toggle && trailing.checked,
            selected = trailing is Trailing.Marker && trailing.selected,
        )
    }

    /**
     * 00-rules.md 14.9: selected, disabled, expanded, loading and error are EXPOSED to TalkBack,
     * not only drawn. A delegate is installed on every bind, including the neutral case, so a
     * recycled row cannot keep the previous item's announcement.
     */
    private fun applySemantics(
        root: View,
        checkable: Boolean,
        checked: Boolean,
        selected: Boolean,
    ) {
        ViewCompat.setAccessibilityDelegate(root, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isCheckable = checkable
                info.isChecked = checked
                info.isSelected = selected
                if (checkable) info.className = SWITCH_CLASS
            }
        })
    }

    private const val DISABLED_ALPHA = 0.38f
    private const val EXPANDED_DEGREES = 90f
    private const val SWITCH_CLASS = "android.widget.Switch"
}

/**
 * The row's child views, resolved once.
 *
 * Two ways in, and both are supported on purpose:
 *
 * - [RowSlots.of] resolves them from the inflated root by id name. This is what [RowBinder.bind]
 *   uses, and what a screen agent gets for free.
 * - the constructor takes them directly, for a caller that already holds typed references from view
 *   binding and wants the lookup to be a compile-time fact rather than a name match.
 */
class RowSlots(
    val root: View,
    val tile: View? = null,
    val tileGlyph: ImageView? = null,
    val tileGap: View? = null,
    val title: TextView? = null,
    val subtitle: TextView? = null,
    val value: TextView? = null,
    val chevron: ImageView? = null,
    val toggle: CompoundButton? = null,
    val marker: ImageView? = null,
    val trailingGlyph: ImageView? = null,
    val iconAction: View? = null,
    val actionButton: TextView? = null,
) {

    companion object {

        // The candidate id names, most specific first. THIS IS THE ONE PLACE TO EDIT when
        // res/layout/view_row.xml lands: pin each list to the single real id.
        internal val TILE = arrayOf("row_tile", "tile", "iv_tile")
        internal val TILE_GLYPH = arrayOf("row_glyph", "tile_glyph", "glyph")
        internal val TILE_GAP = arrayOf("row_tile_gap", "tile_gap", "space_tile")
        internal val TITLE = arrayOf("row_title", "title", "tv_title")
        internal val SUBTITLE = arrayOf("row_subtitle", "subtitle", "tv_subtitle")
        internal val VALUE = arrayOf("row_value", "value", "tv_value")
        internal val CHEVRON = arrayOf("row_chevron", "chevron", "iv_chevron")
        internal val TOGGLE = arrayOf("row_switch", "switch_toggle", "toggle")
        internal val MARKER = arrayOf("row_marker", "state_marker", "marker", "iv_check")
        internal val TRAILING_GLYPH = arrayOf("row_trailing_glyph", "trailing_glyph")
        internal val ICON_ACTION = arrayOf("row_icon_action", "icon_action", "btn_row_icon")
        internal val ACTION_BUTTON = arrayOf("row_action", "btn_row_action", "action_button")

        /** Resolves every slot of an inflated `view_row.xml`. Absent slots stay null. */
        fun of(root: View): RowSlots = RowSlots(
            root = root,
            tile = Slots.find(root, TILE),
            tileGlyph = Slots.image(root, TILE_GLYPH),
            tileGap = Slots.find(root, TILE_GAP),
            title = Slots.text(root, TITLE),
            subtitle = Slots.text(root, SUBTITLE),
            value = Slots.text(root, VALUE),
            chevron = Slots.image(root, CHEVRON),
            toggle = Slots.find(root, TOGGLE) as? CompoundButton,
            marker = Slots.image(root, MARKER),
            trailingGlyph = Slots.image(root, TRAILING_GLYPH),
            iconAction = Slots.find(root, ICON_ACTION),
            actionButton = Slots.text(root, ACTION_BUTTON),
        )
    }
}
