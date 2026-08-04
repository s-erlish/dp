
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
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionNaming
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.template.TemplateManager
import com.v2ray.ang.ui.component.pressFeedback
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

        /**
         * What a failed check puts in the latency cell: a hyphen, in the failure colour, where a
         * number would be. Not a word yet because this file does not own the copy — the copy pass
         * is asked for `ping_result_failed` («нет») and should swap this for it.
         */
        private const val PING_FAILED = "-"
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
     * Opens the server-actions sheet for a row — edit, delete, share, QR, duplicate, make default.
     *
     * A LONG PRESS ANYWHERE ON THE ROW is the route, and the only one. A trailing «⋮» control was
     * added beside it as a visible second door (9728c5c, M-52) and the owner removed it: «почему у
     * серверов появились кнопки справа, если их вообще быть не должно». The row is back to the
     * reference shape — flag, name, protocol chip, JSON chip, transport, ping — and this handler is
     * what the long press calls. A host that leaves it null offers no actions at all, which is
     * correct: an operator-locked list has none to offer.
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
        // THE HEADING IS RESOLVED, NEVER THE RAW REMARK. A group named from storage printed
        // whatever the import happened to stamp — «import sub» among them. [SubscriptionNaming]
        // refuses every placeholder, so a подписка that has not been named yet falls through to the
        // server's own group name rather than announcing an English word to the user.
        val remarksById = subs.associate { it.id to SubscriptionNaming.realName(it.remarks) }
        val grouped = servers.groupBy { it.profile.subscriptionId }

        for (subId in orderedSubIds) {
            val bucket = grouped[subId] ?: continue
            if (bucket.isEmpty()) continue
            val remarks = remarksById[subId]
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
            VIEW_TYPE_HEADER -> HeaderViewHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false)).also {
                it.binding.sectionHeaderRoot.pressFeedback(R.anim.press_row)
            }
            VIEW_TYPE_ITEM -> MainViewHolder(ItemRecyclerMainBinding.inflate(inflater, parent, false)).also {
                // The press response, attached ONCE per view holder rather than on every bind.
                // The rung is the same @anim/press_row the layout names; what this adds is the
                // hardware layer for the duration of the rebound, which is README §11 grabl 1 —
                // a server row is nothing but text (the name, the chips, the transport, the
                // latency) and text re-rasterises on the 2% overshoot unless it is composited.
                it.itemMainBinding.infoContainer.pressFeedback(R.anim.press_row)
            }
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

    /**
     * THE ENTRANCE'S CLEAN-UP, and the only part of it this file has.
     *
     * Главная's assemble (handoff §3) slides the rows that are on screen in from −44dp, staggered
     * 85ms apart, once — `HomeFragment.playServerRowEntrance`, which drives the RecyclerView's
     * attached children directly and never touches bind, so the slide cannot replay on every
     * scroll. What bind cannot protect against is a row being recycled MID-SLIDE: the holder goes
     * back to the pool still carrying a translationX and a fractional alpha, and the next server
     * bound into it inherits both — a row that arrives already half off-screen and stays there,
     * for the rest of the session, with nothing in bind to explain it.
     *
     * So the transform is dropped exactly where the view leaves the screen. Cheap, unconditional,
     * and it costs nothing on the ordinary path where there was no animation to cancel.
     */
    override fun onViewRecycled(holder: BaseViewHolder) {
        super.onViewRecycled(holder)
        holder.itemView.animate().cancel()
        holder.itemView.translationX = 0f
        holder.itemView.alpha = 1f
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

        // Protocol chips: blue primary, gold JSON when the profile is an operator xray-json
        // template, grey transport·security. The JSON chip is the reference row's mark for a
        // template — chipJsonBg/chipJsonText were declared, themed in all three themes and read
        // by nothing until it came back.
        binding.tvType.text = primaryProtocol(guid, profile)
        binding.tvJson.visibility =
            if (profile.configType == EConfigType.CUSTOM) View.VISIBLE else View.GONE
        binding.tvStatistics.text = transportSecurity(guid, profile)

        // Latency: three states, three different cells (00-rules.md 15). Measuring is the spinner
        // and no number. A failed check is PING_FAILED in the failure colour — never the raw
        // negative marker, which is not a duration and would read as one. A server nobody has
        // measured yet is simply blank, and that blank is what tells it apart from a failure.
        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        val measuring = mainViewModel.isMeasuring(guid)
        val failed = aff?.pingResult == ServerAffiliationInfo.PingResult.FAILED
        binding.progressPing.visibility = if (measuring) View.VISIBLE else View.GONE
        binding.tvTestResult.visibility = if (measuring) View.GONE else View.VISIBLE
        val pingText = when {
            measuring -> ""
            failed -> PING_FAILED
            else -> aff?.getTestDelayString().orEmpty()
        }
        binding.tvTestResult.text = pingText
        // Ping colours resolved from theme attrs so ThemeOverlay.Mono greys them out.
        val pingAttr = if (failed) R.attr.pingBad else R.attr.pingGood
        val pingColor = MaterialColors.getColor(binding.tvTestResult, pingAttr)
        binding.tvTestResult.setTextColor(pingColor)
        // There is NO dot beside the figure. The colour on the number already carries the verdict,
        // and the owner had the second channel removed — «точка которая появляется она слишком
        // далеко от пинга, лучше ее просто убрать и все». Blank still means "never measured".

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

        // THE SEPARATOR, and it is carried on `activated` rather than drawn as a view (handoff
        // README §4, §11 grabl 3). @drawable/bg_server_row paints it as the top layer of the row's
        // own background — an inner shadow, never a border — so it costs no layout and cannot open
        // a gap in the selected row's outline.
        //
        // Two rows go without one: the SELECTED row, whose accent frame is already its edge, and
        // the first row under any heading — the very first of the list, and the first of each
        // provider section, because a section header is itself the break and a hairline right
        // under it would draw the same line twice.
        val followsAServer = position > 0 && rows[position - 1] is Row.Server
        binding.infoContainer.isActivated = !selected && followsAServer

        binding.infoContainer.setOnClickListener {
            adapterListener?.onSelectServer(guid)
        }
        // LONG-PRESS IS THE ROUTE TO THE ACTIONS SHEET — edit, share, QR, duplicate, delete.
        // Both hosts assign [onItemLongClick]; this invocation was once missing entirely, which
        // left all five unreachable, so it must not be dropped again.
        //
        // A trailing «⋮» button was added beside it in 9728c5c as a second, visible door. The
        // owner rejected it — «почему у серверов появились кнопки справа, если их вообще быть не
        // должно» — so the row is back to its reference shape and the long press is the route.
        binding.infoContainer.setOnLongClickListener {
            val handler = onItemLongClick ?: return@setOnLongClickListener false
            handler(guid)
            true
        }
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
