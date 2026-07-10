package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.LayoutSubscriptionMetaBarBinding
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager

/**
 * Home provider meta bar as a swipeable carousel: one page per subscription. Each page reuses the
 * shared [LayoutSubscriptionMetaBarBinding] and is painted by [bindPage] (the activity's bindMetaBar),
 * so the single-subscription case looks identical to the old static plate. Per-page actions carry the
 * page's own subscription id; list-wide actions (collapse / ping / refresh) are page-independent. The
 * adapter holds no ViewModel reference — only the lambdas below.
 */
class HomeMetaPagerAdapter(
    private val bindPage: (LayoutSubscriptionMetaBarBinding, SubscriptionItem) -> Unit,
    private val onToggleList: () -> Unit,
    private val onPingAll: () -> Unit,
    private val onRefreshAll: () -> Unit,
    private val onTogglePin: (String) -> Unit,
    private val onDeleteSub: (String) -> Unit,
    private val onOpenSupport: (String) -> Unit,
    private val onOpenTelegram: (String) -> Unit,
    private val collapsed: () -> Boolean,
) : RecyclerView.Adapter<HomeMetaPagerAdapter.VH>() {

    private val subIds = mutableListOf<String>()

    fun submit(ids: List<String>) {
        subIds.clear()
        subIds.addAll(ids)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = subIds.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = LayoutSubscriptionMetaBarBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        // The ViewPager2 owns horizontal gutters (padding) and the inter-page gap, so drop the meta
        // bar's own margins — otherwise a match_parent page + gutters would overflow the viewport.
        (binding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.width = ViewGroup.LayoutParams.MATCH_PARENT
            it.setMargins(0, 0, 0, 0)
        }
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val subId = subIds[position]
        val sub = MmkvManager.decodeSubscription(subId) ?: return
        val meta = holder.binding
        bindPage(meta, sub)
        meta.btnCollapse.rotation = if (collapsed()) -90f else 0f
        meta.btnCollapse.setOnClickListener { onToggleList() }
        meta.btnPing.setOnClickListener { onPingAll() }
        meta.btnRefresh.setOnClickListener { onRefreshAll() }
        meta.btnPin.setOnClickListener { onTogglePin(subId) }
        meta.btnSupport.setOnClickListener { onOpenSupport(subId) }
        meta.btnTelegram.setOnClickListener { onOpenTelegram(subId) }
        meta.root.setOnLongClickListener { onDeleteSub(subId); true }
    }

    class VH(val binding: LayoutSubscriptionMetaBarBinding) : RecyclerView.ViewHolder(binding.root)
}
