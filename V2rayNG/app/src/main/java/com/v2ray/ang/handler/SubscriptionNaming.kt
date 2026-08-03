package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem

/**
 * THE ONE ANSWER TO "WHAT IS THIS ПОДПИСКА CALLED".
 *
 * Every surface that prints a подписка's name comes here: the card on Главная, the group heading in
 * the server list, the subscription-update notification, and anything added later. Before this
 * object the ranking lived in `HomeFragment.metaTitle` — inside a Fragment, so the background worker
 * that posts the notification could not reach it, and the shade ended up formatting the RAW remark.
 * With `"import sub"` stored as that remark, the notification literally read «Обновляем «import sub»».
 *
 * TWO RULES, AND THEY ARE THE OWNER'S:
 *
 * 1. **A placeholder is not a name.** «import sub» is upstream's English default, «Default» is the
 *    linkless local container's, and «departament vpn» is the service label the backend returns on
 *    EVERY подписка. None of them identifies anything, so none of them is ever shown — and, since
 *    [PLACEHOLDERS] is also what the update path asks before adopting the провайдер's `profile-title`,
 *    an install that already stored one heals itself on its next refresh.
 * 2. **There is no rename to fall back on.** Editing a подписка is not a feature
 *    (OWNER-DECISION-2026-08-02 §5), so a bad automatic name would be permanent. The automatic name
 *    is the only name, which is exactly why a placeholder must never be allowed to stick.
 */
object SubscriptionNaming {

    /**
     * Lower-cased strings that name no подписка. Compared against a trimmed, lower-cased candidate.
     *
     * `"import sub"` and `"default"` are the two placeholders older builds STORED, so they are here
     * to heal existing installs, not because anything writes them any more. `"departament"` /
     * `"departament vpn"` are the generic service label — the same string on every подписка of this
     * deployment, which is why `SubInfoDto.tariffBadgeName` refuses it for the tariff badge too.
     */
    val PLACEHOLDERS = setOf("default", "import sub", "departament", "departament vpn")

    /**
     * [candidate] when it actually names a подписка, else null. Blank and every [PLACEHOLDERS]
     * entry are refused.
     */
    fun realName(candidate: String?): String? {
        val trimmed = candidate?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return trimmed.takeIf { it.lowercase() !in PLACEHOLDERS }
    }

    /** True when nothing on [sub] names it yet, so the провайдер's title may be adopted into it. */
    fun isUnnamed(sub: SubscriptionItem): Boolean = realName(sub.remarks) == null

    /**
     * The name to PRINT, in the one ranking the whole app uses:
     *
     *  1. the nickname the user set in the cabinet ([accountDisplayName]),
     *  2. the провайдер's own `profile-title`,
     *  3. the stored remark,
     *  4. the backend's per-sub label («Подписка #2», [accountDefaultLabel]) — below the провайдер's
     *     title on purpose, because it is generated rather than chosen,
     *  5. «Подписка».
     *
     * The two account fields are optional: only screens holding a live account payload can supply
     * them, and every other caller still gets the same answer for the same stored data.
     */
    fun titleOf(
        context: Context,
        sub: SubscriptionItem,
        accountDisplayName: String? = null,
        accountDefaultLabel: String? = null,
    ): String = nameOf(sub, accountDisplayName, accountDefaultLabel)
        ?: context.getString(R.string.home_sub_untitled)

    /**
     * [titleOf] WITHOUT the fallback: null when nothing here actually names the подписка.
     *
     * Copy that quotes the name needs this and not [titleOf] — «Обновляем «Подписка»» quotes a
     * generic noun back at the user as if it were a title. A caller in that position picks a
     * different, whole sentence instead.
     */
    fun nameOf(
        sub: SubscriptionItem,
        accountDisplayName: String? = null,
        accountDefaultLabel: String? = null,
    ): String? = realName(accountDisplayName)
        ?: realName(sub.profileTitle)
        ?: realName(sub.remarks)
        ?: realName(accountDefaultLabel)

    /**
     * [titleOf] for a caller that has only the local id — the background updater's case. Reads the
     * stored item; an id that names nothing still answers «Подписка» rather than an empty string,
     * because the strings around it are sentences.
     */
    fun titleOf(context: Context, subId: String): String {
        val sub = MmkvManager.decodeSubscription(subId)
            ?: return context.getString(R.string.home_sub_untitled)
        return titleOf(context, sub)
    }
}
