
package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.util.FlagUtil
import com.v2ray.ang.databinding.ItemRecyclerFooterBinding
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.databinding.ItemSectionHeaderBinding
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.template.TemplateManager
import com.v2ray.ang.util.JsonUtil
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
     * Retained for host-activity API compatibility. The long-press server-actions menu was
     * removed, so this callback is no longer invoked by the adapter.
     */
    var onItemLongClick: ((String) -> Unit)? = null

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

        // Selection can have been changed by something that owns no list — a subscription import,
        // fast-connect, or the service starting with an explicit guid. Re-read it on every rebuild
        // so a single-row refresh can never leave a stale row painted as selected.
        val latestSelection = MmkvManager.getSelectServer()
        val selectionChanged = latestSelection != selectedGuid
        selectedGuid = latestSelection

        val flat = targetGuid?.let { flatPositionOf(it) } ?: -1
        if (flat >= 0 && !selectionChanged) notifyItemChanged(flat) else notifyDataSetChanged()
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

    /**
     * The guid this adapter currently paints as selected. Selection lives in MMKV, but MMKV cannot
     * notify, and it is written from several places that do not own a list (subscription import,
     * fast-connect, service start). Mirroring it here lets [syncSelection] repaint exactly the rows
     * that changed — and, crucially, detect the case where the previously selected row is no longer
     * findable, which used to leave two rows painted as selected at once.
     */
    private var selectedGuid: String? = MmkvManager.getSelectServer()

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
        val guid = cache.guid
        val profile = cache.profile

        binding.tvFlag.text = FlagUtil.resolveFlag(profile)
        binding.tvName.text = FlagUtil.stripLeadingFlag(profile.remarks)

        // Protocol chips: blue primary, grey transport·security.
        binding.tvType.text = primaryProtocol(guid, profile)
        binding.tvStatistics.text = transportSecurity(guid, profile)

        // Latency text only (colored by result). testDelayMillis == -2L is the "testing" sentinel
        // set by MainActivity at ping start: show a spinner in place of the ms value until the real
        // per-server result overwrites it.
        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        val delay = aff?.testDelayMillis ?: 0L
        val testing = delay == -2L
        binding.progressPing.visibility = if (testing) View.VISIBLE else View.GONE
        binding.tvTestResult.visibility = if (testing) View.GONE else View.VISIBLE
        binding.tvTestResult.text = if (testing) "" else aff?.getTestDelayString().orEmpty()
        // Ping colours resolved from theme attrs so ThemeOverlay.Mono greys them out.
        val pingAttr = if (delay < 0L) R.attr.pingBad else R.attr.pingGood
        binding.tvTestResult.setTextColor(MaterialColors.getColor(binding.tvTestResult, pingAttr))

        // Selection: blue rounded outline via bg_server_row selected state.
        // Indicator bar tint via theme attr (mono-safe).
        // Painted from the mirrored [selectedGuid], not straight from MMKV, so that a row can never
        // render a selection state the adapter has not been told about.
        val selected = guid == selectedGuid
        binding.infoContainer.isSelected = selected
        binding.layoutIndicator.setBackgroundColor(
            if (selected) MaterialColors.getColor(binding.layoutIndicator, R.attr.indicatorColor)
            else Color.TRANSPARENT
        )

        binding.infoContainer.setOnClickListener {
            adapterListener?.onSelectServer(guid)
        }
        // Long-press server-actions menu removed: long-press is a no-op (no listener set).
    }

    private fun primaryProtocol(guid: String, profile: ProfileItem): String {
        return when (profile.configType) {
            EConfigType.POLICYGROUP -> "Auto"
            EConfigType.PROXYCHAIN -> "Chain"
            // A CUSTOM profile that wraps a single proxy outbound (e.g. a Remnawave XRAY_JSON
            // server) should show its real protocol; fall back to "Custom" only when the config
            // has no single identifiable proxy outbound.
            EConfigType.CUSTOM -> customProtoInfo(guid)?.protocol?.uppercase() ?: "Custom"
            else -> profile.configType.name
        }
    }

    private fun transportSecurity(guid: String, profile: ProfileItem): String {
        val parts = mutableListOf<String>()
        if (profile.configType == EConfigType.CUSTOM) {
            val info = customProtoInfo(guid) ?: return ""
            info.network?.let { if (it.isNotBlank()) parts.add(it.uppercase()) }
            info.security?.let { if (it.isNotBlank()) parts.add(it.uppercase()) }
            return parts.joinToString(" · ")
        }
        if (profile.configType.isComplexType()) return ""
        profile.network?.let { net ->
            if (net.isNotBlank()) parts.add(net.uppercase())
        }
        profile.security?.let { sec ->
            if (sec.isNotBlank()) parts.add(sec.uppercase())
        }
        return parts.joinToString(" · ")
    }

    /** Protocol/transport/security parsed from a CUSTOM profile's wrapped xray-json outbound. */
    private data class CustomProtoInfo(
        val protocol: String,
        val network: String?,
        val security: String?,
    )

    // Parsing the stored raw config on every row bind would be wasteful, so cache per guid.
    // A `null` value is cached too (config has no single identifiable outbound → show "Custom").
    private val customProtoCache = HashMap<String, CustomProtoInfo?>()

    private fun customProtoInfo(guid: String): CustomProtoInfo? {
        if (customProtoCache.containsKey(guid)) return customProtoCache[guid]
        val info = try {
            val raw = TemplateManager.decodeRuntimeRaw(guid) ?: MmkvManager.decodeServerRaw(guid)
            val outbound = raw
                ?.let { JsonUtil.fromJsonSafe(it, V2rayConfig::class.java) }
                ?.getProxyOutbound()
            outbound?.let {
                CustomProtoInfo(
                    protocol = it.protocol,
                    network = it.streamSettings?.network,
                    security = it.streamSettings?.security,
                )
            }
        } catch (e: Exception) {
            null
        }
        customProtoCache[guid] = info
        return info
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
        syncSelection(toGuid, previous = fromGuid)
    }

    /**
     * Repaints selection to match [guid] (defaults to whatever MMKV holds).
     *
     * Refreshing only the two affected rows is the cheap path, but it is only correct when BOTH
     * rows are currently in [rows]. The old row can be missing — it may sit inside a collapsed
     * section, or the list may have been rebuilt by a subscription update since it was selected —
     * and a missed refresh leaves it painted as selected next to the new one, which is the
     * "two servers selected at once" defect. So: fall back to a full refresh whenever either row
     * cannot be located.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun syncSelection(guid: String? = MmkvManager.getSelectServer(), previous: String? = selectedGuid) {
        if (guid == selectedGuid && previous == selectedGuid) return
        selectedGuid = guid

        val fromPos = previous?.let { flatPositionOf(it) } ?: -1
        val toPos = guid?.let { flatPositionOf(it) } ?: -1

        val fromResolved = previous == null || fromPos >= 0
        val toResolved = guid == null || toPos >= 0
        if (!fromResolved || !toResolved) {
            notifyDataSetChanged()
            return
        }
        if (fromPos >= 0) notifyItemChanged(fromPos)
        if (toPos >= 0 && toPos != fromPos) notifyItemChanged(toPos)
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
