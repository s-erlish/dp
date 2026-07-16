package com.v2ray.ang.util

/**
 * Parsed representation of the `subscription-userinfo` HTTP response header returned by
 * Remnawave / 3x-ui / Marzban / Happ / Hiddify panels, e.g.:
 *
 *   subscription-userinfo: upload=4520000000; download=210000000000; total=536870912000; expire=1749954800
 *
 * All keys are optional. `total`/`upload`/`download` are bytes, `expire` is epoch seconds.
 */
data class SubscriptionUserInfo(
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
) {
    companion object {
        /**
         * Parses the raw header value. Returns null if nothing usable was found.
         */
        fun parse(raw: String?): SubscriptionUserInfo? {
            if (raw.isNullOrBlank()) return null
            val map = raw.split(';')
                .mapNotNull { part ->
                    val i = part.indexOf('=')
                    if (i <= 0) return@mapNotNull null
                    val k = part.substring(0, i).trim().lowercase()
                    val v = part.substring(i + 1).trim().toLongOrNull() ?: return@mapNotNull null
                    k to v
                }.toMap()
            if (map.isEmpty()) return null
            return SubscriptionUserInfo(
                upload = map["upload"] ?: 0,
                download = map["download"] ?: 0,
                total = map["total"] ?: 0,
                expire = map["expire"] ?: 0,
            )
        }
    }
}
