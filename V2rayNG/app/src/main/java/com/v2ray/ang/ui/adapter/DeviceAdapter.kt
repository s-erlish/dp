package com.v2ray.ang.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.auth.dto.DeviceDto
import com.v2ray.ang.databinding.ItemDeviceBinding

/**
 * Renders the devices bound to a subscription (by HWID). Each row shows the device
 * name/model, a platform + last-active meta line and the truncated HWID, plus a trash
 * button that asks the host to remove the device.
 */
class DeviceAdapter(
    private val onDelete: (DeviceDto) -> Unit,
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    private val items = mutableListOf<DeviceDto>()

    fun submit(list: List<DeviceDto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        val b = holder.binding

        b.tvDeviceName.text = item.deviceModel?.takeIf { it.isNotBlank() }
            ?: item.platform?.takeIf { it.isNotBlank() }
            ?: ctx.getString(R.string.devices_unknown_model)

        val platform = item.platform?.takeIf { it.isNotBlank() }
        val lastActive = formatIsoDate(item.lastActiveAt)
        val meta = when {
            lastActive.isNotBlank() && platform != null ->
                "$platform · " + ctx.getString(R.string.devices_last_active, lastActive)
            lastActive.isNotBlank() -> ctx.getString(R.string.devices_last_active, lastActive)
            platform != null -> platform
            else -> item.appVersion?.takeIf { it.isNotBlank() }.orEmpty()
        }
        b.tvDeviceMeta.text = meta
        b.tvDeviceMeta.visibility = if (meta.isBlank()) View.GONE else View.VISIBLE

        b.tvDeviceHwid.text = ctx.getString(R.string.devices_hwid, item.hwid)
        b.tvDeviceHwid.visibility = if (item.hwid.isBlank()) View.GONE else View.VISIBLE

        // Handoff README §7 draws the devices as rows in ONE card, so the rule between
        // two rows belongs to the row below it — and never above the first, which would
        // draw a line across the card's own top edge.
        b.deviceDivider.visibility = if (position == 0) View.GONE else View.VISIBLE

        b.btnDeviceDelete.setOnClickListener { onDelete(item) }
    }

    class VH(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)
}

/** ISO-8601 (or date-only) -> dd.MM.yyyy. Returns "" for blank/unparseable input. */
private fun formatIsoDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val datePart = iso.substringBefore('T')
    val parts = datePart.split('-')
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else datePart
}
