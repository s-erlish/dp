package com.v2ray.ang.util

/**
 * Lightweight helper to report this process's memory footprint.
 *
 * Reports the app's live Java heap usage (allocated - free) in MB — the number users recognize
 * as "app memory" and the one comparable clients display. It excludes shared framework pages
 * (which dominate total PSS and are not really "the app"), so it reads much lower and honestly
 * reflects the UI process's own footprint. Cheap enough to poll every couple of seconds.
 */
object MemoryStatsManager {

    /** Current UI-process memory in MB (Java heap in use). */
    fun currentUsedMb(): Int {
        val rt = Runtime.getRuntime()
        val used = rt.totalMemory() - rt.freeMemory()
        return (used / (1024L * 1024L)).toInt()
    }

    enum class Level { NORMAL, ELEVATED, HIGH }

    /**
     * Buckets a memory figure so the UI can show a green/amber/red status.
     * Tuned to Java-heap usage of a light VPN client's UI process.
     */
    fun levelFor(mb: Int): Level = when {
        mb <= 120 -> Level.NORMAL
        mb <= 220 -> Level.ELEVATED
        else -> Level.HIGH
    }
}
