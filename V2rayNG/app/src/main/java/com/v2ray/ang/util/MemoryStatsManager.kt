package com.v2ray.ang.util

import android.os.Debug

/**
 * Lightweight helper to report this process's memory footprint.
 *
 * Uses total PSS (proportional set size) — the same number `dumpsys meminfo` reports and the
 * honest "how much RAM am I using" figure (Java heap + native + shared pages, proportionally).
 * Cheap enough to poll every couple of seconds while a screen is visible.
 */
object MemoryStatsManager {

    /** Current process memory in MB (total PSS). */
    fun currentPssMb(): Int {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss / 1024 // KB -> MB
    }

    enum class Level { NORMAL, ELEVATED, HIGH }

    /**
     * Buckets a memory figure so the UI can show a green/amber/red status.
     * Tuned to a light VPN client (iOS Network Extensions cap ~15 MB; a healthy Android UI
     * process sits well under ~150 MB).
     */
    fun levelFor(mb: Int): Level = when {
        mb <= 150 -> Level.NORMAL
        mb <= 300 -> Level.ELEVATED
        else -> Level.HIGH
    }
}
