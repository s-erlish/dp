package com.v2ray.ang.util

import com.v2ray.ang.dto.entities.ProfileItem

/**
 * Resolves a country flag emoji for a server, Happ/Incy style.
 *
 * Layered strategy (cheapest first, all offline):
 *  1. A flag emoji already present in the server remark (e.g. "🇳🇱 Amsterdam").
 *  2. A 2-letter ISO country code / common country name parsed from the remark.
 *  3. A globe fallback.
 *
 * Rendering a regional-indicator emoji pair in a TextView is zero-asset and matches how
 * Remnawave/Happ/Incy show flags.
 */
object FlagUtil {

    private const val GLOBE = "🌐" // 🌐

    /** Regional indicator base; 'A' -> 0x1F1E6. */
    private const val REGIONAL_BASE = 0x1F1E6

    /**
     * Returns a flag emoji for the given profile, or a globe if none can be derived.
     */
    fun resolveFlag(profile: ProfileItem): String {
        val remark = profile.remarks
        extractFlagEmoji(remark)?.let { return it }
        parseCountryCode(remark)?.let { return codeToFlag(it) }
        return GLOBE
    }

    /**
     * Extracts an existing regional-indicator flag emoji (two consecutive indicators) from [text].
     */
    fun extractFlagEmoji(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        var i = 0
        val len = text.length
        while (i < len) {
            val cp = text.codePointAt(i)
            if (cp in REGIONAL_BASE..(REGIONAL_BASE + 25)) {
                val next = i + Character.charCount(cp)
                if (next < len) {
                    val cp2 = text.codePointAt(next)
                    if (cp2 in REGIONAL_BASE..(REGIONAL_BASE + 25)) {
                        return text.substring(i, next + Character.charCount(cp2))
                    }
                }
            }
            i += Character.charCount(cp)
        }
        return null
    }

    /**
     * Removes a leading flag emoji (and a following separator) from a remark so the name
     * doesn't duplicate the flag shown in the tile. No-op when there is no leading flag.
     */
    fun stripLeadingFlag(remark: String): String {
        val t = remark.trimStart()
        if (t.isEmpty()) return remark
        val cp = t.codePointAt(0)
        if (cp in REGIONAL_BASE..(REGIONAL_BASE + 25)) {
            val flag = extractFlagEmoji(t)
            if (flag != null && t.startsWith(flag)) {
                return t.substring(flag.length)
                    .trimStart(' ', '-', '·', '|', ':', '\t')
                    .ifBlank { remark }
            }
        }
        return remark
    }

    /**
     * Converts a 2-letter ISO country code (e.g. "NL") to its flag emoji.
     */
    fun codeToFlag(code: String): String {
        if (code.length != 2) return GLOBE
        val upper = code.uppercase()
        val a = upper[0]
        val b = upper[1]
        if (a !in 'A'..'Z' || b !in 'A'..'Z') return GLOBE
        val sb = StringBuilder()
        sb.appendCodePoint(REGIONAL_BASE + (a - 'A'))
        sb.appendCodePoint(REGIONAL_BASE + (b - 'A'))
        return sb.toString()
    }

    /**
     * Parses a country code from a remark: a leading/wrapped 2-letter code, or a known
     * country name. Returns an ISO-2 code or null.
     */
    fun parseCountryCode(remark: String?): String? {
        if (remark.isNullOrBlank()) return null
        val lower = remark.lowercase()
        COUNTRY_NAME_TO_CODE.forEach { (name, code) ->
            if (lower.contains(name)) return code
        }
        // A standalone 2-letter token like "NL", "US" (word-boundaried).
        Regex("\\b([A-Za-z]{2})\\b").findAll(remark).forEach { m ->
            val c = m.groupValues[1].uppercase()
            if (ISO2_CODES.contains(c)) return c
        }
        return null
    }

    // Common country names (English) → ISO-2. Kept small and offline; extend as needed.
    private val COUNTRY_NAME_TO_CODE = linkedMapOf(
        "netherlands" to "NL", "amsterdam" to "NL",
        "germany" to "DE", "frankfurt" to "DE",
        "united states" to "US", "usa" to "US", "america" to "US",
        "united kingdom" to "GB", "britain" to "GB", "london" to "GB",
        "france" to "FR", "paris" to "FR",
        "finland" to "FI", "helsinki" to "FI",
        "sweden" to "SE", "stockholm" to "SE",
        "denmark" to "DK",
        "norway" to "NO",
        "poland" to "PL",
        "latvia" to "LV",
        "lithuania" to "LT",
        "estonia" to "EE",
        "russia" to "RU", "moscow" to "RU",
        "ukraine" to "UA",
        "turkey" to "TR", "istanbul" to "TR",
        "japan" to "JP", "tokyo" to "JP",
        "singapore" to "SG",
        "hong kong" to "HK",
        "korea" to "KR",
        "canada" to "CA",
        "switzerland" to "CH",
        "spain" to "ES",
        "italy" to "IT",
        "austria" to "AT",
        "czech" to "CZ",
        "iran" to "IR",
        "india" to "IN",
        "australia" to "AU",
        "brazil" to "BR",
        "emirates" to "AE", "dubai" to "AE",
    )

    private val ISO2_CODES = setOf(
        "NL", "DE", "US", "GB", "UK", "FR", "FI", "SE", "DK", "NO", "PL", "LV", "LT", "EE",
        "RU", "UA", "TR", "JP", "SG", "HK", "KR", "CA", "CH", "ES", "IT", "AT", "CZ", "IR",
        "IN", "AU", "BR", "AE",
    )
}
