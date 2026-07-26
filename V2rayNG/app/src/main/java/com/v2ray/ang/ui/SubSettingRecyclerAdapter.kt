package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ViewRowBinding
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.ui.component.RowBinder
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.SubscriptionsViewModel

/**
 * One subscription group per row (A-17).
 *
 * The old row carried FOUR targets - the row, an edit glyph, a share glyph, a delete glyph, plus a
 * switch - in a card, and hid three of them with `INVISIBLE` when the group had no URL, which left
 * the geometry holding space for controls that were not there. §4.5 allows one trailing element, so
 * everything the group can do lives behind one overflow: enable, edit, QR, copy, and delete under a
 * hairline in the destructive tone.
 *
 * The state a user needs at a glance is in the subtitle instead: whether the group is off, when it
 * last updated, and its URL - or «Локальная группа» when it has none, which is the case the old row
 * expressed by hiding half of itself.
 */
class SubSettingRecyclerAdapter(
    private val viewModel: SubscriptionsViewModel,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<SubSettingRecyclerAdapter.SubViewHolder>(), ItemTouchHelperAdapter {

    /** Set by the activity: the overflow needs the activity's dialogs and sheets, not the adapter's. */
    var onOverflow: ((subId: String, position: Int) -> Unit)? = null

    override fun getItemCount() = viewModel.getAll().size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubViewHolder =
        SubViewHolder(ViewRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: SubViewHolder, position: Int) {
        val entry = viewModel.getAll()[position]
        val subItem = entry.subscription
        val context = holder.itemView.context

        val subtitle = if (subItem.url.isBlank()) {
            context.getString(R.string.subs_local)
        } else {
            val updated = subItem.lastUpdated.takeIf { it > 0 }
                ?.let { context.getString(R.string.subs_updated, Utils.formatTimestamp(it)) }
                ?: context.getString(R.string.subs_never_updated)
            listOfNotNull(
                context.getString(R.string.subs_disabled).takeIf { !subItem.enabled },
                updated,
            ).joinToString(" · ")
        }

        RowBinder.bind(
            root = holder.binding.root,
            title = subItem.remarks,
            subtitle = subtitle,
            glyph = R.drawable.ic_subscriptions_24dp,
            trailing = RowBinder.Trailing.IconAction(
                icon = R.drawable.ic_more_vert_24dp,
                contentDescription = context.getString(R.string.editor_more_actions),
                onClick = {
                    val index = holder.bindingAdapterPosition
                    if (index != RecyclerView.NO_POSITION) onOverflow?.invoke(entry.guid, index)
                },
            ),
        )
    }

    inner class SubViewHolder(val binding: ViewRowBinding) :
        RecyclerView.ViewHolder(binding.root), ItemTouchHelperViewHolder {

        override fun onItemSelected() {
            itemView.isActivated = true
        }

        override fun onItemClear() {
            itemView.isActivated = false
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        viewModel.swap(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        adapterListener?.onRefreshData()
    }

    override fun onItemDismiss(position: Int) {
    }
}
