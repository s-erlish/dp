package com.v2ray.ang.util

import com.v2ray.ang.dto.entities.ProfileItem

/**
 * Resolves a country flag emoji for a server, Happ/Incy style.
 *
 * Layered strategy (cheapest first, all offline):
 *  1. A flag emoji already present in the server remark (e.g. "🇳🇱 Amsterdam").
 *  2. An explicit country marker in the remark — a bracketed "[NL]" code, a known country or
 *     city name, or an upper-case code in the leading token ("NL - Amsterdam").
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
     * Converts a 2-letter country code (e.g. "NL") to its flag emoji. Aliases such as the
     * non-ISO "UK" are normalised first, otherwise the pair renders as boxed letters.
     */
    fun codeToFlag(code: String): String {
        if (code.length != 2) return GLOBE
        val upper = normalizeCode(code.uppercase())
        val a = upper[0]
        val b = upper[1]
        if (a !in 'A'..'Z' || b !in 'A'..'Z') return GLOBE
        val sb = StringBuilder()
        sb.appendCodePoint(REGIONAL_BASE + (a - 'A'))
        sb.appendCodePoint(REGIONAL_BASE + (b - 'A'))
        return sb.toString()
    }

    /**
     * Parses a country code from a remark. Only an explicit marker counts — a bracketed code,
     * a known country/city name (English or Russian), or an upper-case code in the leading token.
     * Matching any two-letter token turned "No limit" into Norway, "IT support" into Italy and
     * "in-1" into India — a missing flag is cheaper than a wrong one. Returns ISO-2 or null.
     */
    fun parseCountryCode(remark: String?): String? {
        if (remark.isNullOrBlank()) return null
        bracketedCode(remark)?.let { return it }
        val lower = remark.lowercase()
        NAME_TABLES.forEach { table ->
            table.forEach { (name, code) ->
                if (containsWord(lower, name)) return code
            }
        }
        return leadingCode(remark)
    }

    /** "[NL] Amsterdam" or "Amsterdam (NL)" — the brackets are the marker, so case is free. */
    private fun bracketedCode(remark: String): String? {
        BRACKETED_CODE.findAll(remark).forEach { m ->
            val c = m.groupValues[1].uppercase()
            if (isKnownCode(c)) return normalizeCode(c)
        }
        return null
    }

    /**
     * "NL - Amsterdam", "US·LA", "DE", "US East". The code must open the remark in upper case
     * and not be glued to a longer word; what follows it may be a separator, nothing, or a
     * place name — but not prose, which is what makes "IT support" and "NO LIMIT" globes.
     */
    private fun leadingCode(remark: String): String? {
        var start = 0
        while (start < remark.length && !remark[start].isLetter()) start++
        if (start + 2 > remark.length) return null
        val a = remark[start]
        val b = remark[start + 1]
        if (a !in 'A'..'Z' || b !in 'A'..'Z') return null
        var after = start + 2
        if (after < remark.length && remark[after].isLetter()) return null
        while (after < remark.length && remark[after].isWhitespace()) after++
        if (after < remark.length && remark[after].isLetter() && looksLikeProse(remark, after)) return null
        val c = "$a$b"
        return if (isKnownCode(c)) normalizeCode(c) else null
    }

    /**
     * Tells "US East"/"DE Berlin"/"IR تهران" (place names) from "IT support"/"NO LIMIT" (prose)
     * by the word starting at [at]: a lower-case opener or an all-upper word is prose, a
     * capitalised word with lower-case letters — or a caseless script — is a name.
     */
    private fun looksLikeProse(remark: String, at: Int): Boolean {
        if (remark[at].isLowerCase()) return true
        var i = at
        var sawUpper = false
        while (i < remark.length && remark[i].isLetter()) {
            if (remark[i].isLowerCase()) return false
            if (remark[i].isUpperCase()) sawUpper = true
            i++
        }
        return sawUpper
    }

    /** Word-boundaried search, so "india" doesn't match "Indiana" nor "usa" "Usain". */
    private fun containsWord(haystack: String, needle: String): Boolean {
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) return false
            val before = at == 0 || !haystack[at - 1].isLetter()
            val end = at + needle.length
            val after = end == haystack.length || !haystack[end].isLetter()
            if (before && after) return true
            from = at + 1
        }
    }

    private fun isKnownCode(code: String) = code in ISO2_CODES || code in CODE_ALIASES

    private fun normalizeCode(code: String) = CODE_ALIASES[code] ?: code

    private val BRACKETED_CODE = Regex("""[\[(]\s*([A-Za-z]{2})\s*[)\]]""")

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

    // The same table in Russian, because this deployment's panel is Russian and writes remarks
    // like "Германия" or "Нидерланды 2" — which had no marker the English table or the upper-case
    // leading-code rule could see, so every Russian-named host fell through to the globe. Cyrillic
    // cannot collide with the ASCII prose the leading-code rule exists to reject ("IT support"),
    // and the match is word-boundaried like the English one, so this adds flags without adding
    // wrong ones. Declined forms ("Германии") deliberately miss: a globe, not a guess.
    private val COUNTRY_NAME_TO_CODE_RU = linkedMapOf(
        "нидерланды" to "NL", "голландия" to "NL", "амстердам" to "NL",
        "германия" to "DE", "франкфурт" to "DE",
        "сша" to "US", "америка" to "US",
        "великобритания" to "GB", "британия" to "GB", "англия" to "GB", "лондон" to "GB",
        "франция" to "FR", "париж" to "FR",
        "финляндия" to "FI", "хельсинки" to "FI",
        "швеция" to "SE", "стокгольм" to "SE",
        "дания" to "DK",
        "норвегия" to "NO",
        "польша" to "PL",
        "латвия" to "LV",
        "литва" to "LT",
        "эстония" to "EE",
        "россия" to "RU", "москва" to "RU",
        "украина" to "UA",
        "турция" to "TR", "стамбул" to "TR",
        "япония" to "JP", "токио" to "JP",
        "сингапур" to "SG",
        "гонконг" to "HK",
        "корея" to "KR",
        "канада" to "CA",
        "швейцария" to "CH",
        "испания" to "ES",
        "италия" to "IT",
        "австрия" to "AT",
        "чехия" to "CZ",
        "иран" to "IR",
        "индия" to "IN",
        "австралия" to "AU",
        "бразилия" to "BR",
        "оаэ" to "AE", "эмираты" to "AE", "дубай" to "AE",
    )

    private val NAME_TABLES = listOf(COUNTRY_NAME_TO_CODE, COUNTRY_NAME_TO_CODE_RU)

    private val ISO2_CODES = setOf(
        "NL", "DE", "US", "GB", "FR", "FI", "SE", "DK", "NO", "PL", "LV", "LT", "EE",
        "RU", "UA", "TR", "JP", "SG", "HK", "KR", "CA", "CH", "ES", "IT", "AT", "CZ", "IR",
        "IN", "AU", "BR", "AE",
    )

    // Codes panels actually write that are not ISO 3166-1 alpha-2, so they have no flag emoji.
    private val CODE_ALIASES = mapOf("UK" to "GB")
}
