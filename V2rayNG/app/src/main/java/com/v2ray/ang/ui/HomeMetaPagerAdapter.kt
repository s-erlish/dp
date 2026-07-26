package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.LayoutSubscriptionMetaBarBinding
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager

/**
 * Главная's subscription card as a swipeable carousel: one page per подписка.
 *
 * This file was deleted by the wave that replaced the card with a «Подписка» navigation row; the
 * owner overruled that on 2026-07-26 («как выглядела главная по функционалу такая и должна
 * остаться»), so it is back, unchanged in behaviour.
 *
 * Each page reuses the shared [LayoutSubscriptionMetaBarBinding] and is painted by [bindPage]
 * (HomeFragment's `bindMetaBar`), so the single-подписка case looks identical to one static card.
 *
 * **Per-page actions carry the page's own subscription id.** That is the whole reason the carousel
 * exists rather than one card with global buttons: pin, delete, support and Telegram act on the
 * подписка whose card is under the thumb, never on "the first one". The id is handed to [bindPage]
 * for the same reason — the card's NAME is resolved per подписка, and a decoded [SubscriptionItem]
 * is a fresh object every time, so it cannot be matched back to its own id by identity.
 *
 * Only the three genuinely list-wide actions - collapse the list, ping every server, refresh every
 * подписка - are page-independent, and they are declared as such here rather than inferred at the
 * call site.
 *
 * The adapter holds no ViewModel reference: everything it needs arrives as a lambda.
 */
class HomeMetaPagerAdapter(
    private val bindPage: (LayoutSubscriptionMetaBarBinding, String, SubscriptionItem?) -> Unit,
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

    /** Replaces the pages with [ids] and repaints. */
    @SuppressLint("NotifyDataSetChanged")
    fun submit(ids: List<String>) {
        subIds.clear()
        subIds.addAll(ids)
        notifyDataSetChanged()
    }

    /**
     * Repaints every page in place, for a change that alters what a card SAYS rather than which
     * cards exist — the account answering with its own nicknames is the one that matters.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun repaint() {
        if (subIds.isEmpty()) return
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = subIds.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = LayoutSubscriptionMetaBarBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        // The ViewPager2 owns the horizontal gutters (padding) and the inter-page gap, so drop the
        // card's own margins — a match_parent page plus the gutters would overflow the viewport.
        (binding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.width = ViewGroup.LayoutParams.MATCH_PARENT
            it.setMargins(0, 0, 0, 0)
        }
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val subId = subIds[position]
        val meta = holder.binding
        // Bound even when the подписка has just left the store: [bindPage] hides an empty card, and
        // the actions below are re-pointed at THIS position's id either way. Returning early here
        // instead would leave a recycled holder wired to the id it last showed — so the long press
        // that deletes a подписка could act on a different one from the card under the thumb.
        bindPage(meta, subId, MmkvManager.decodeSubscription(subId))
        meta.btnCollapse.rotation = if (collapsed()) -90f else 0f
        meta.btnCollapse.setOnClickListener { onToggleList() }
        meta.btnPing.setOnClickListener { onPingAll() }
        meta.btnRefresh.setOnClickListener { onRefreshAll() }
        meta.btnPin.setOnClickListener { onTogglePin(subId) }
        meta.btnSupport.setOnClickListener { onOpenSupport(subId) }
        meta.btnTelegram.setOnClickListener { onOpenTelegram(subId) }
        // Deleting a подписка, from the card that shows it. A long press is a hidden affordance and
        // the card's own layout has no free slot for a visible control, so HomeFragment says out
        // loud that it is here; a visible trailing action is filed with that layout's owner.
        meta.root.setOnLongClickListener { onDeleteSub(subId); true }
    }

    class VH(val binding: LayoutSubscriptionMetaBarBinding) : RecyclerView.ViewHolder(binding.root)
}
