package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemEditorSectionBinding
import com.v2ray.ang.databinding.ViewRowCardBinding
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.ui.component.RowBinder

/**
 * The app list of «Прокси по приложениям» and of the routing rule's app picker (A-18, A-19).
 *
 * Two things about the old adapter were defects rather than choices, and both are gone:
 *
 * 1. **A system app was marked by gluing `** ` onto its name in Kotlin.** That is a grouping problem
 *    solved with punctuation - it does not sort, it does not translate, it reads as a typo, and it
 *    put Latin asterisks in front of a Russian label. The list is grouped instead: «Ваши приложения»
 *    then «Системные приложения», each under a real section header ([ItemEditorSectionBinding]).
 * 2. **The boolean was a `MaterialCheckBox` inside a card**, while every other boolean in the
 *    product is a switch inside a row. It is now [RowBinder.Trailing.Toggle], so the whole row is
 *    the target, the switch is not separately focusable, and TalkBack hears a checkable node
 *    instead of a button.
 *
 * Selection is not owned here: [isSelected] and [onToggle] are passed in, so the per-app screen can
 * back it with its MMKV-persisting view model and the picker with an in-memory set, and neither has
 * to reimplement the row.
 */
class PerAppProxyAdapter(
    private val isSelected: (String) -> Boolean,
    private val onToggle: (String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** A list entry: either a group header or one app. Headers are data, not decoration. */
    private sealed interface Entry {
        data class Section(val title: CharSequence) : Entry
        data class App(val info: AppInfo) : Entry
    }

    /** The apps currently displayed, in list order. «Выбрать все» and «Инвертировать» act on these. */
    var apps: List<AppInfo> = emptyList()
        private set

    private var entries: List<Entry> = emptyList()

    /**
     * Replaces the list and rebuilds the grouping.
     *
     * @param userLabel the header above non-system apps, or null to leave that group unheaded (the
     *   picker, whose first entry is the «неопознанные приложения» pseudo-app, uses null).
     */
    @SuppressLint("NotifyDataSetChanged")
    fun submit(apps: List<AppInfo>, userLabel: CharSequence?, systemLabel: CharSequence?) {
        this.apps = apps
        val user = apps.filterNot { it.isSystemApp }
        val system = apps.filter { it.isSystemApp }
        entries = buildList {
            if (user.isNotEmpty()) {
                userLabel?.let { add(Entry.Section(it)) }
                user.forEach { add(Entry.App(it)) }
            }
            if (system.isNotEmpty()) {
                systemLabel?.let { add(Entry.Section(it)) }
                system.forEach { add(Entry.App(it)) }
            }
        }
        notifyDataSetChanged()
    }

    /** Redraws every switch after a bulk change (select all, invert, auto-select, import). */
    @SuppressLint("NotifyDataSetChanged")
    fun refreshSelection() {
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = entries.size

    override fun getItemViewType(position: Int): Int =
        if (entries[position] is Entry.Section) VIEW_TYPE_SECTION else VIEW_TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SECTION) {
            SectionViewHolder(ItemEditorSectionBinding.inflate(inflater, parent, false))
        } else {
            AppViewHolder(ViewRowCardBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = entries[position]) {
            is Entry.Section -> (holder as SectionViewHolder).binding.sectionTitle.text = entry.title
            is Entry.App -> (holder as AppViewHolder).bind(entry.info)
        }
    }

    private class SectionViewHolder(val binding: ItemEditorSectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    private inner class AppViewHolder(val binding: ViewRowCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            RowBinder.bind(
                root = binding.row.root,
                title = app.appName,
                subtitle = app.packageName,
                // A placeholder the binder can tint and size; the real launcher icon replaces it
                // below. Passing a non-zero glyph is what keeps the 40dp tile - and therefore the
                // 68dp text origin - identical to every other row in the product.
                glyph = R.drawable.ic_per_apps_24dp,
                trailing = RowBinder.Trailing.Toggle(
                    checked = isSelected(app.packageName),
                    onCheckedChange = { onToggle(app.packageName) },
                ),
            )
            // The launcher icon is already coloured; the binder's neutral tint would flatten it to
            // a grey silhouette, so it is cleared here rather than never applied - a recycled row
            // that showed the placeholder must not keep that tint. The slot comes from the view
            // binding rather than a second findViewById sweep: this list is 200 rows on a real
            // device and it is scrolled fast.
            binding.row.rowTile.setImageDrawable(app.appIcon)
            ImageViewCompat.setImageTintList(binding.row.rowTile, null)
        }
    }

    private companion object {
        const val VIEW_TYPE_SECTION = 0
        const val VIEW_TYPE_APP = 1
    }
}
