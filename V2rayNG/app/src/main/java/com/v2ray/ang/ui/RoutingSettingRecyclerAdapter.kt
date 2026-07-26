package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ViewRowBinding
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.ui.component.RowBinder
import com.v2ray.ang.viewmodel.RoutingSettingsViewModel
import androidx.recyclerview.widget.RecyclerView

/**
 * One routing rule per row (A-20).
 *
 * The old row carried three targets in a space 56dp tall: the row itself, a 24dp edit glyph in a
 * 40dp box, and a switch stacked underneath it - all inside a bordered card, inside a 12dp-inset
 * wrapper, with a green tile that encoded nothing. §4.5 allows a row exactly one trailing element,
 * so the row is a `Row.Navigation` and every per-rule action - «Включено», «Защищено», delete -
 * lives on the rule form the chevron opens, which is where A-21 puts them anyway. Nothing became
 * unreachable; the rule form gained the two toggles it was missing.
 *
 * The subtitle is what makes the list auditable without opening anything: state first when it is not
 * the default, then what the rule matches, then where it sends the traffic.
 *
 * Long-press still starts a drag. Its feedback used to be `Color.LTGRAY` painted straight onto the
 * item view - a raw literal that is invisible in the light theme and wrong in the mono one. It is
 * now `isActivated`, which `@drawable/bg_row` already draws as the 12% accent fill.
 */
class RoutingSettingRecyclerAdapter(
    private val viewModel: RoutingSettingsViewModel,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<RoutingSettingRecyclerAdapter.RuleViewHolder>(),
    ItemTouchHelperAdapter {

    override fun getItemCount() = viewModel.getAll().size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder =
        RuleViewHolder(ViewRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        val ruleset = viewModel.getAll()[position]
        val context = holder.itemView.context

        RowBinder.bind(
            root = holder.binding.root,
            title = ruleset.remarks.orEmpty().ifBlank { ruleset.outboundTag.orEmpty() },
            subtitle = summarise(ruleset, context.resources),
            glyph = R.drawable.ic_routing_24dp,
            trailing = RowBinder.Trailing.Chevron,
            onClick = { adapterListener?.onEdit("", holder.bindingAdapterPosition) },
        )
    }

    /**
     * «Выключено · google.com, ads · direct». State only when it is NOT the default, because a row
     * that says «Включено» on every line has spent a line saying nothing.
     */
    private fun summarise(ruleset: RulesetItem, res: android.content.res.Resources): CharSequence {
        val matcher = listOfNotNull(
            ruleset.domain?.takeIf { it.isNotEmpty() },
            ruleset.ip?.takeIf { it.isNotEmpty() },
            ruleset.process?.takeIf { it.isNotEmpty() },
        ).firstOrNull()?.joinToString(", ") ?: ruleset.port ?: ruleset.protocol?.joinToString(", ")

        return listOfNotNull(
            res.getString(R.string.routing_rule_off).takeIf { !ruleset.enabled },
            res.getString(R.string.routing_rule_locked).takeIf { ruleset.locked == true },
            matcher,
            ruleset.outboundTag,
        ).joinToString(" · ")
    }

    inner class RuleViewHolder(val binding: ViewRowBinding) :
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
