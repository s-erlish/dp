package com.v2ray.ang.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.v2ray.ang.R
import com.v2ray.ang.databinding.SheetEditorActionsBinding
import com.v2ray.ang.databinding.ViewRowBinding
import com.v2ray.ang.ui.component.RowBinder

/**
 * The one action surface the sub-screen editors share (24-tab-conformance.md 0.2 H4).
 *
 * These screens used to hide their actions in two places a user cannot reach: a toolbar overflow on
 * an ActionBar (and A-38 takes the ActionBar away), and a bare `AlertDialog.setItems` list, which
 * §7.4 bans outright. Both collapse here. A sheet row IS `view_row.xml` bound through
 * [RowBinder], so the action a user taps in a sheet looks exactly like the settings row they tapped
 * to get here - one row vocabulary in the whole product, not one per surface.
 *
 * ```kotlin
 * EditorActionsSheet(this, getString(R.string.editor_actions_title))
 *     .action(R.string.routing_action_presets, R.drawable.ic_routing_24dp) { importPredefined() }
 *     .action(R.string.routing_action_export, R.drawable.ic_share_24dp) { export2Clipboard() }
 *     .destructive(R.string.routing_ed_delete) { confirmDelete() }
 *     .show()
 * ```
 *
 * Every action closes the sheet before it runs: a sheet that lingers over the screen it just changed
 * makes the user dismiss a thing they already finished with. Destructive rows go last, after a
 * hairline, and carry the red title rather than a red tile - a red tile plus red text is the same
 * signal twice (22-components 8.6).
 */
class EditorActionsSheet(
    private val context: Context,
    private val title: CharSequence,
) {

    private val binding = SheetEditorActionsBinding.inflate(LayoutInflater.from(context))
    private val dialog = BottomSheetDialog(context).apply { setContentView(binding.root) }
    private var hasDestructive = false

    init {
        binding.sheetTitle.text = title
    }

    /**
     * Adds one action row.
     *
     * @param label what happens, as a verb - «Обновить все», never «Обновление».
     * @param glyph the 22dp tile glyph, `0` for a plain (untiled) row. A group is tiled only when
     *   its rows carry glyphs that differ (view_row.xml), so pass 0 consistently or not at all.
     * @param subtitle six words on what the action does, when the label cannot say it alone.
     * @param enabled false draws the row at 0.38 and takes it out of the touch order; use it with a
     *   [subtitle] that says WHY, never on its own.
     */
    fun action(
        label: CharSequence,
        @DrawableRes glyph: Int = 0,
        subtitle: CharSequence? = null,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ): EditorActionsSheet = addRow(
        label = label,
        glyph = glyph,
        subtitle = subtitle,
        enabled = enabled,
        tone = RowBinder.RowTone.DEFAULT,
        onClick = onClick,
    )

    /** [action] with the string looked up for you. */
    fun action(
        labelRes: Int,
        @DrawableRes glyph: Int = 0,
        subtitle: CharSequence? = null,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ): EditorActionsSheet = action(context.getString(labelRes), glyph, subtitle, enabled, onClick)

    /**
     * Adds the destructive action. It is separated from the rest by a hairline and always sits last,
     * so «Удалить» is never adjacent to the action above it by accident.
     */
    fun destructive(labelRes: Int, onClick: () -> Unit): EditorActionsSheet {
        if (!hasDestructive) {
            hasDestructive = true
            binding.sheetRows.addView(hairline())
        }
        return addRow(
            label = context.getString(labelRes),
            glyph = 0,
            subtitle = null,
            enabled = true,
            tone = RowBinder.RowTone.DESTRUCTIVE,
            onClick = onClick,
        )
    }

    fun show() {
        dialog.show()
    }

    private fun addRow(
        label: CharSequence,
        @DrawableRes glyph: Int,
        subtitle: CharSequence?,
        enabled: Boolean,
        tone: RowBinder.RowTone,
        onClick: () -> Unit,
    ): EditorActionsSheet {
        val row = ViewRowBinding.inflate(LayoutInflater.from(context), binding.sheetRows, false)
        RowBinder.bind(
            root = row.root,
            title = label,
            subtitle = subtitle,
            glyph = glyph,
            tone = tone,
            trailing = RowBinder.Trailing.None,
            enabled = enabled,
            onClick = {
                dialog.dismiss()
                onClick()
            },
        )
        binding.sheetRows.addView(row.root)
        return this
    }

    private fun hairline(): View = View(context).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            context.resources.getDimensionPixelSize(R.dimen.stroke_hairline),
        ).apply {
            topMargin = context.resources.getDimensionPixelSize(R.dimen.space_8)
            bottomMargin = context.resources.getDimensionPixelSize(R.dimen.space_8)
        }
        setBackgroundColor(
            com.google.android.material.color.MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOutlineVariant,
            )
        )
    }
}
