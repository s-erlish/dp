package com.v2ray.ang.ui.component

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.v2ray.ang.R

/**
 * The empty state (22-components.md 15, 32-master-plan-android.md 8.8). One grammar, replacing the
 * three the app ships today (a card on Account, a tile block on Devices, a `drawableTop` on a
 * TextView on Payment history).
 *
 * ```
 * [ 64 tile, r20, colorSurfaceContainerHighest, 32dp glyph in colorOnSurfaceVariant ]
 *                              16
 *                Title   Headline 24/700, centred, max 2 lines
 *                              8
 *                Line    Subtitle 13/400, centred, max 2 lines
 *                              24
 *                Action  ONE button
 * ```
 *
 * What the binder enforces, because these are the three things that go wrong:
 *
 * - **The tile is neutral.** An empty state is not the screen's primary action surface and does not
 *   spend the accent budget on decoration. The tile colour is not a parameter.
 * - **One action, or none.** Two buttons in an empty state is the current Account defect. The
 *   signature has room for exactly one.
 * - **The cause is passed in, never written into the layout.** An error state is this same
 *   silhouette with the alert glyph, the mapped cause and a Tertiary «Повторить»; the hard-wired
 *   «Что-то пошло не так» in `activity_account.xml` is what this replaces.
 *
 * Copy follows 00-rules.md 9.5: title says what is not here, one line says why or what it gives
 * you, one action. Never «Нет данных» alone, never an illustration, never an emoji. Strings belong
 * to the screen, so this binder takes `CharSequence` and owns no copy of its own.
 */
object EmptyStateBinder {

    /**
     * Binds an empty, error or offline-empty state.
     *
     * ```kotlin
     * EmptyStateBinder.bind(
     *     root = binding.emptyState.root,
     *     glyph = R.drawable.ic_globe_24dp,
     *     title = getString(R.string.servers_empty_title),
     *     line = getString(R.string.servers_empty_line),
     *     actionLabel = getString(R.string.servers_empty_action),
     *     onAction = { SubPage.open(this, providerIntent) },
     * )
     * ```
     *
     * @param root the inflated empty-state block.
     * @param glyph the 32dp neutral glyph.
     * @param title what is not here. Headline by default; pass `compact = true` inside a card or a
     *   list section, where the Title role is the right step.
     * @param line why it is not here, or what having it gives the user. One line, about 60
     *   characters, or null when the title says everything.
     * @param actionLabel the one action, or null for a state with nothing to do («Платежей пока
     *   нет»). An action that is the screen's own job is a Primary button; anything else is
     *   Secondary, and that choice belongs to the layout's style, not to this call.
     * @param compact true when the state sits inside a card or a list rather than owning the screen.
     * @param onAction what the action does, routed through [onSingleClick]. Required whenever
     *   [actionLabel] is set.
     */
    fun bind(
        root: View,
        @DrawableRes glyph: Int,
        title: CharSequence,
        line: CharSequence? = null,
        actionLabel: CharSequence? = null,
        compact: Boolean = false,
        onAction: ((View) -> Unit)? = null,
    ) {
        require((actionLabel == null) == (onAction == null)) {
            "Departament empty state: an action needs both a label and a listener. A button that " +
                "does nothing is worse than no button (00-rules.md 9.5)."
        }

        val slots = EmptyStateSlots.of(root)

        Slots.requireSlot(slots.tile, "empty-state glyph", EmptyStateSlots.TILE).let {
            it.setImageResource(glyph)
            // The glyph restates the title in picture form; the title is the accessible name.
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        Slots.requireSlot(slots.title, "empty-state title", EmptyStateSlots.TITLE).let {
            it.text = title
            it.setTextAppearance(
                if (compact) R.style.TextAppearance_App_Title else R.style.TextAppearance_App_Headline
            )
        }

        slots.line?.let {
            it.text = line ?: ""
            it.visibility = if (line.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        slots.action?.let { button ->
            if (actionLabel == null || onAction == null) {
                button.clearClick()
                button.visibility = View.GONE
            } else {
                button.text = actionLabel
                button.visibility = View.VISIBLE
                button.onSingleClick(action = onAction)
            }
        }

        root.visibility = View.VISIBLE
    }

    /** Hides the whole block. Use it when the content arrives, rather than hiding parts of it. */
    fun hide(root: View) {
        EmptyStateSlots.of(root).action?.clearClick()
        root.visibility = View.GONE
    }
}

/** The empty state's child views, resolved once. See [RowSlots] for why lookup is by name. */
class EmptyStateSlots(
    val root: View,
    val tile: ImageView? = null,
    val title: TextView? = null,
    val line: TextView? = null,
    val action: TextView? = null,
) {

    companion object {

        // Pin these to the real ids when res/layout/view_empty_state.xml lands.
        internal val TILE = arrayOf("empty_glyph", "empty_tile", "iv_empty")
        internal val TITLE = arrayOf("empty_title", "tv_empty_title")
        internal val LINE = arrayOf("empty_line", "empty_subtitle", "tv_empty_line")
        internal val ACTION = arrayOf("empty_action", "btn_empty_action")

        fun of(root: View): EmptyStateSlots = EmptyStateSlots(
            root = root,
            tile = Slots.image(root, TILE),
            title = Slots.text(root, TITLE),
            line = Slots.text(root, LINE),
            action = Slots.text(root, ACTION),
        )
    }
}
