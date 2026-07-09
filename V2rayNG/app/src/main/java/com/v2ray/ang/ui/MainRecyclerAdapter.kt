
package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.util.FlagUtil
import com.v2ray.ang.databinding.ItemRecyclerFooterBinding
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.databinding.ItemSectionHeaderBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.viewmodel.MainViewModel

class MainRecyclerAdapter(
    private val mainViewModel: MainViewModel,
    private val adapterListener: MainAdapterListener?
) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>(), ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    /** A flat row: either a provider section header or a server. */
    private sealed class Row {
        data class Header(val subId: String, val remarks: String, val count: Int) : Row()
        data class Server(val cache: ServersCache) : Row()
    }

    private var servers: List<ServersCache> = emptyList()
    private var subs: List<GroupMapItem> = emptyList()
    private var showHeaders = false
    private val collapsed = mutableSetOf<String>()
    private var rows: List<Row> = emptyList()

    /**
     * Feeds the adapter with the flat server list plus the provider groups used to build
     * section headers. Headers are suppressed when [showHeaders] is false or there is a
     * single (or no) provider — Home already shows the meta bar as the provider header.
     *
     * @param index server index in [newServers]; when >= 0 only that row is refreshed.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun setSections(
        newServers: List<ServersCache>,
        newSubs: List<GroupMapItem>,
        showHeaders: Boolean,
        index: Int = -1
    ) {
        this.servers = newServers.toList()
        this.subs = newSubs
        this.showHeaders = showHeaders
        val targetGuid = if (index in this.servers.indices) this.servers[index].guid else null
        rebuildRows()
        val flat = targetGuid?.let { flatPositionOf(it) } ?: -1
        if (flat >= 0) notifyItemChanged(flat) else notifyDataSetChanged()
    }

    /** Backward-compatible shim: flat list, no section headers. */
    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: MutableList<ServersCache>?, position: Int = -1) {
        setSections(newData ?: emptyList(), emptyList(), showHeaders = false, index = position)
    }

    private fun rebuildRows() {
        val list = mutableListOf<Row>()
        val useHeaders = showHeaders && distinctProviderCount() > 1
        if (!useHeaders) {
            servers.forEach { list.add(Row.Server(it)) }
            rows = list
            return
        }

        // Ordered provider groups (pinned-first, per subs), then a "Local" group for the rest.
        val orderedSubIds = subs.map { it.id }.filter { it.isNotEmpty() }
        val remarksById = subs.associate { it.id to it.remarks }
        val grouped = servers.groupBy { it.profile.subscriptionId }

        for (subId in orderedSubIds) {
            val bucket = grouped[subId] ?: continue
            if (bucket.isEmpty()) continue
            val remarks = remarksById[subId]?.takeIf { it.isNotBlank() }
                ?: bucket.firstOrNull()?.profile?.remarks.orEmpty()
            list.add(Row.Header(subId, remarks, bucket.size))
            if (!collapsed.contains(subId)) bucket.forEach { list.add(Row.Server(it)) }
        }

        // Servers without a matching provider (local / unsubscribed).
        val localBucket = servers.filter { it.profile.subscriptionId.let { id -> id.isEmpty() || !orderedSubIds.contains(id) } }
        if (localBucket.isNotEmpty()) {
            val localId = ""
            list.add(Row.Header(localId, "", localBucket.size))
            if (!collapsed.contains(localId)) localBucket.forEach { list.add(Row.Server(it)) }
        }

        rows = list
    }

    private fun distinctProviderCount(): Int =
        servers.map { it.profile.subscriptionId }.distinct().size

    private fun flatPositionOf(guid: String): Int =
        rows.indexOfFirst { it is Row.Server && it.cache.guid == guid }

    /** Toggles collapse state across all provider sections. */
    @SuppressLint("NotifyDataSetChanged")
    fun toggleCollapseAll() {
        val allSubIds = rows.filterIsInstance<Row.Header>().map { it.subId }
        if (allSubIds.isEmpty()) return
        val anyExpanded = allSubIds.any { !collapsed.contains(it) }
        if (anyExpanded) collapsed.addAll(allSubIds) else collapsed.clear()
        rebuildRows()
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size + 1

    override fun getItemViewType(position: Int): Int {
        if (position == rows.size) return VIEW_TYPE_FOOTER
        return when (rows[position]) {
            is Row.Header -> VIEW_TYPE_HEADER
            is Row.Server -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false))
            VIEW_TYPE_ITEM -> MainViewHolder(ItemRecyclerMainBinding.inflate(inflater, parent, false))
            else -> FooterViewHolder(ItemRecyclerFooterBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> bindHeader(holder, rows[position] as Row.Header)
            is MainViewHolder -> bindServer(holder, position, (rows[position] as Row.Server).cache)
            else -> {}
        }
    }

    private fun bindHeader(holder: HeaderViewHolder, header: Row.Header) {
        val context = holder.binding.root.context
        val title = header.remarks.ifBlank { context.getString(R.string.servers_section_local) }
        holder.binding.sectionTitle.text = title
        holder.binding.sectionCount.text = header.count.toString()
        val isCollapsed = collapsed.contains(header.subId)
        holder.binding.sectionChevron.rotation = if (isCollapsed) -90f else 0f
        holder.binding.sectionHeaderRoot.setOnClickListener {
            if (collapsed.contains(header.subId)) collapsed.remove(header.subId) else collapsed.add(header.subId)
            rebuildRows()
            notifyDataSetChanged()
        }
    }

    private fun bindServer(holder: MainViewHolder, position: Int, cache: ServersCache) {
        val binding = holder.itemMainBinding
        val context = binding.root.context
        val guid = cache.guid
        val profile = cache.profile

        binding.tvFlag.text = FlagUtil.resolveFlag(profile)
        binding.tvName.text = FlagUtil.stripLeadingFlag(profile.remarks)

        // Protocol chips: blue primary, gold JSON, grey transport·security.
        binding.tvType.text = primaryProtocol(profile)
        val complex = profile.configType.isComplexType()
        binding.tvJson.visibility = if (complex) View.VISIBLE else View.GONE
        binding.tvStatistics.text = transportSecurity(profile)

        // Subscription remarks badge (only meaningful in all-servers mode).
        val subRemarks = getSubscriptionRemarks(profile)
        binding.tvSubscription.text = subRemarks
        binding.layoutSubscription.visibility = if (subRemarks.isEmpty()) View.GONE else View.VISIBLE

        // Ping dot + latency text.
        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        val delay = aff?.testDelayMillis ?: 0L
        binding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
        val pingColor = if (delay < 0L) R.color.colorPingRed else R.color.colorPing
        binding.tvTestResult.setTextColor(ContextCompat.getColor(context, pingColor))
        val dotColor = when {
            delay > 0L -> R.color.colorPing
            delay < 0L -> R.color.colorPingRed
            else -> R.color.colorPingRed // untested / n/a
        }
        binding.dotPing.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, dotColor))
        binding.dotPing.visibility = if (delay == 0L) View.INVISIBLE else View.VISIBLE

        // Selection: blue rounded outline via bg_server_row selected state.
        val selected = guid == MmkvManager.getSelectServer()
        binding.infoContainer.isSelected = selected
        binding.layoutIndicator.setBackgroundResource(if (selected) R.color.colorIndicator else 0)

        binding.infoContainer.setOnClickListener {
            adapterListener?.onSelectServer(guid)
        }
        // Long-press opens the full share/edit/remove bottom sheet (inline actions removed).
        binding.infoContainer.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            adapterListener?.onShare(guid, profile, pos, true)
            true
        }
    }

    private fun primaryProtocol(profile: ProfileItem): String {
        return when (profile.configType) {
            EConfigType.POLICYGROUP -> "Auto"
            EConfigType.PROXYCHAIN -> "Chain"
            EConfigType.CUSTOM -> "Custom"
            else -> profile.configType.name
        }
    }

    private fun transportSecurity(profile: ProfileItem): String {
        if (profile.configType.isComplexType()) return ""
        val parts = mutableListOf<String>()
        profile.network?.let { net ->
            if (net.isNotBlank()) parts.add(net.uppercase())
        }
        profile.security?.let { sec ->
            if (sec.isNotBlank()) parts.add(sec.uppercase())
        }
        return parts.joinToString(" · ")
    }

    private fun getSubscriptionRemarks(profile: ProfileItem): String {
        val subRemarks =
            if (mainViewModel.subscriptionId.isEmpty())
                MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()
            else
                null
        return subRemarks?.toString() ?: ""
    }

    /** Removes a server row by guid and rebuilds the flat list. */
    @SuppressLint("NotifyDataSetChanged")
    fun removeServerSub(guid: String, position: Int = -1) {
        servers = servers.filterNot { it.guid == guid }
        rebuildRows()
        notifyDataSetChanged()
    }

    /** Refreshes the two rows involved in a selection change (guids). */
    fun setSelectServer(fromGuid: String?, toGuid: String?) {
        fromGuid?.let { flatPositionOf(it).takeIf { p -> p >= 0 }?.let { p -> notifyItemChanged(p) } }
        toGuid?.let { flatPositionOf(it).takeIf { p -> p >= 0 }?.let { p -> notifyItemChanged(p) } }
    }

    /** Flat adapter position of a server guid, or -1. */
    fun positionOfGuid(guid: String): Int = flatPositionOf(guid)

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root), ItemTouchHelperViewHolder

    class HeaderViewHolder(val binding: ItemSectionHeaderBinding) :
        BaseViewHolder(binding.root)

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
        BaseViewHolder(itemFooterBinding.root)

    // Drag is disabled in the grouped all-servers list (see impl doc): no ItemTouchHelper is
    // attached, so these are inert. Kept to satisfy the ItemTouchHelperAdapter contract.
    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean = false

    override fun onItemMoveCompleted() {
        // do nothing
    }

    override fun onItemDismiss(position: Int) {
    }
}
