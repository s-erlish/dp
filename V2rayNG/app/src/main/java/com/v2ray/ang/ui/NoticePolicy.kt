package com.v2ray.ang.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import com.v2ray.ang.R

/**
 * ============================================================================
 * WHO IS ALLOWED TO INTERRUPT THE USER.
 * ============================================================================
 *
 * The owner, on the whole layer rather than on one message:
 *
 *     «все вот такие уведомления нужно в целом убрать, это же старые от в2рей уведомления,
 *      и по qr коду когда добавляешь там внизу уведомление красное и тд, их убрать надо совсем»
 *
 * WHAT HE IS LOOKING AT. Upstream v2rayNG answers almost every action with a `Toasty` popup — a
 * green tick, a red cross, a system-chrome capsule floating over the app — and the sentence inside
 * it is written for the person who wrote the parser: «Обновлено серверов: 13 (успешно: 1, ошибки:
 * 0, пропущено: 1)», «Ошибка», a raw `e.message`, a `[VLESS] Finland(***12:443)` node summary.
 * Those popups are the single loudest tell that this app is a rebranded fork, and there were
 * FIFTY-ODD of them reachable from screens departament ships.
 *
 * THE DESKTOP SOLVED THIS SHAPE FIRST and its note is the one worth copying
 * (`v2rayN.Desktop/Common/NoticePolicy.cs`): filter the CHANNEL, not the surface. There, the
 * message still reaches the log and the connect screen still reads the failure reason; only the
 * popup is refused. Same here — every call site keeps compiling, every message still reaches
 * `LogUtil`, and this object decides what a human ever sees.
 *
 * THE FOUR RULES, and they are the whole policy:
 *
 *  1. **Success is silent.** The timestamp moved, the list repainted, the editor closed, the
 *     card redrew — that IS the confirmation, and it is a better one than a word that covers the
 *     thing it is confirming. `toastSuccess` reaches no surface at all, in any screen.
 *
 *  2. **A failure the user must act on gets ONE short sentence**, in the product's voice, on the
 *     ONE bottom feedback surface, above the navigation. An invalid QR, an unimportable
 *     clipboard, a подписка that returned nothing, a refresh that could not reach the operator:
 *     silence there would leave a tap with no answer, which is the opposite defect.
 *
 *  3. **Nothing reaches a human unless it is on [ALLOWED].** This is an allow-list and not a
 *     block-list on purpose: the next upstream merge will add `toast(R.string.something)` call
 *     sites, and every one of them is silent here until a person puts the id in this table and
 *     reads the sentence it prints. That is the property the owner asked for — that the layer
 *     cannot come back by itself.
 *
 *  4. **A message with no string id is machine text**, and machine text is never shown. Counters
 *     («…успешно: 1, ошибки: 0…»), node summaries, exception messages and file paths all arrive
 *     through the `CharSequence` door because they are all built by concatenation. Anything the
 *     product genuinely means to say has a resource id and can be listed above. [isMachineText]
 *     is a second net under the same door for the cases that get a formatted id anyway.
 *
 * The IN-FLIGHT SIGNAL is not on this channel and is not filtered by it. «Обновляем данные…» is a
 * state, not a notification — the owner's own G2 rule is that an action in flight must show
 * itself — so it has its own entry point, [progress], on the same bottom surface where he asked
 * for it («это уведомление нужно снизу над панелью навигации»).
 */
object NoticePolicy {

    /** What a message is FOR. The policy's first question, before it looks at the text. */
    enum class Kind {
        /** Something worked. Never shown — rule 1. */
        SUCCESS,

        /** A refusal, a dead end, or a condition the user has to resolve. */
        FAILURE,

        /** Neither — a note about what just happened, or did not. */
        INFO,
    }

    /**
     * Every string this product is allowed to say out loud, and nothing else is.
     *
     * Each one is a sentence a person wrote for a person, each one answers an action the user
     * took, and each one names something the screen behind it cannot show on its own. Adding an
     * id here is a design decision: read the string first, and if it reports a count, names a
     * field, or quotes a parser, it does not belong on a screen at all.
     */
    private val ALLOWED: Set<Int> = setOf(
        // The add paths — QR, clipboard, file, deep link. The one the owner named.
        R.string.notice_add_failed,
        R.string.import_sub_empty,
        R.string.import_sub_duplicate,
        R.string.import_sub_foreign,
        // Refreshing a подписка. Success is the new timestamp on the card; only failure speaks.
        R.string.notice_refresh_failed,
        // The clipboard: the one outcome with no visible result of any kind.
        R.string.notice_copied,
        R.string.notice_copy_failed,
        // Dead ends the app cannot resolve for the user.
        R.string.toast_require_file_manager,
        R.string.toast_permission_denied,
        R.string.app_tile_first_use,
        R.string.toast_warning_pref_proxysharing_short,
        // Backup and restore: the file either exists afterwards or it does not.
        R.string.backup_failed,
        R.string.backup_restore_failed,
        R.string.backup_cloud_failed,
        // The account tab. Copying to the clipboard is the one action in the product with NO
        // visible result at all — nothing on the screen changes — so it is the one confirmation
        // that survives rule 1.
        R.string.account_referral_copied,
        R.string.account_sub_autorenew_failed,
        // The buy flow — departament's own screens, and each of these blocks a tap.
        R.string.buy_select_option_first,
        R.string.buy_no_methods,
        R.string.buy_no_browser,
        R.string.buy_checkout_return,
        // Editors and settings: a refusal with a reason.
        R.string.srv_config_invalid,
        R.string.editor_url_invalid,
        R.string.editor_failed,
        R.string.asset_name_duplicate,
        R.string.asset_copy_failed,
        R.string.asset_download_failed,
        R.string.srv_delete_selected,
        R.string.template_locked_toast,
        R.string.toast_action_not_allowed,
        R.string.settings_always_on_hint,
        R.string.settings_always_on_unavailable,
        R.string.settings_sub_auto_update_empty,
        // Sending a подписка to the TV.
        R.string.tv_send_no_subs,
        R.string.tv_send_pick_title,
        R.string.tv_send_scanning_invalid,
    )

    /** Rule 1 + rule 3, in one question. */
    fun allows(kind: Kind, @StringRes id: Int): Boolean =
        kind != Kind.SUCCESS && id != 0 && ALLOWED.contains(id)

    /**
     * A node summary in upstream's debug format — `[VLESS] Finland(***12:443)`, `[Custom] [Xray]`.
     * Not a sentence: an internal caption, and it must not appear anywhere a person can read it.
     * Recognised by SHAPE, so it does not depend on the language of the parts around it. Ported
     * from `NoticePolicy.IsNodeSummary`.
     */
    fun isNodeSummary(text: CharSequence?): Boolean {
        val s = text?.trim() ?: return false
        if (s.isEmpty() || s[0] != '[') return false
        val close = s.indexOf(']')
        return close > 1 && s[1].isLetter()
    }

    /**
     * A tally rather than a sentence: «Обновлено серверов: 13 (успешно: 1, ошибки: 0, пропущено:
     * 1)», «Импортировано серверов: 4». The user did not ask how many records moved; the list
     * repainting says everything a count says, and says it about the thing he is looking at.
     *
     * Recognised as: two or more separate runs of digits, with a colon or a bracket between them.
     * A single figure inside an otherwise human sentence is not a counter and survives.
     */
    fun isCounterReport(text: CharSequence?): Boolean {
        val s = text ?: return false
        var groups = 0
        var separator = false
        var index = 0
        while (index < s.length) {
            val c = s[index]
            when {
                c.isDigit() -> {
                    groups++
                    while (index < s.length && s[index].isDigit()) index++
                    continue
                }

                groups > 0 && (c == ':' || c == '(' || c == ',' || c == ';') -> separator = true
            }
            index++
        }
        return groups >= 2 && separator
    }

    /**
     * Text produced by a machine for a machine: an exception, a stack frame, a file path, a JSON
     * body, a bare token with no words in it. `CoreServiceManager` toasts `e.message` twice, and
     * that is the shape it produces.
     */
    fun isMachineText(text: CharSequence?): Boolean {
        val s = text?.trim() ?: return true
        if (s.isEmpty()) return true
        if (s[0] == '{' || s[0] == '[') return true
        if (s.contains("Exception") || s.contains("java.") || s.contains("kotlin.")) return true
        // No space anywhere means it is not a sentence in any language this app ships.
        return !s.any { it.isWhitespace() }
    }

    /** Rule 4: the last gate the text itself has to pass, whatever id carried it. */
    fun allowsText(text: CharSequence?): Boolean =
        !isNodeSummary(text) && !isCounterReport(text) && !isMachineText(text)
}

/**
 * THE ONE FEEDBACK SURFACE. A themed Snackbar at the bottom of whatever screen is in front,
 * anchored above the navigation bar when the shell is showing one, so a message never covers the
 * thing it is about and never appears in the middle of the screen the way a Toast does.
 *
 * `@style/Widget.Departament.Snackbar` already dresses it: the product's radius, the product's
 * type, the inverse surface. There is no green variant and no red variant — an outcome is a
 * sentence, and the sentence carries its own weather.
 */
object Notice {

    /** 3s, the no-action duration (22-components.md 14). Long enough to read one sentence. */
    private const val DURATION_MS = 3000

    private var progressBar: Snackbar? = null

    /**
     * Says one sentence, if [NoticePolicy] lets it. Everything else in the app funnels here.
     *
     * @param id the string that will be shown — a resource id, never a built string, because the
     *   id is the only thing the policy can recognise (rule 3).
     */
    fun say(context: Context, @StringRes id: Int, kind: NoticePolicy.Kind) {
        if (!NoticePolicy.allows(kind, id)) return
        val activity = context.activity() ?: return
        val text = runCatching { activity.getString(id) }.getOrNull() ?: return
        if (!NoticePolicy.allowsText(text)) return
        show(activity, text, DURATION_MS)
    }

    /**
     * The IN-FLIGHT SIGNAL, and it is deliberately not policy-gated: an action the user started
     * and that has not finished must show itself, at the bottom, until it does. Indefinite, and
     * cleared by [clearProgress] — never by a timer, because a spinner that gives up while the
     * work continues is a lie.
     */
    fun progress(context: Context, text: CharSequence) {
        val activity = context.activity() ?: return
        val current = progressBar
        if (current != null && current.isShown) return
        progressBar = show(activity, text, Snackbar.LENGTH_INDEFINITE)
    }

    /** Takes the in-flight signal down. Safe to call when nothing is up. */
    fun clearProgress() {
        progressBar?.dismiss()
        progressBar = null
    }

    private fun show(activity: Activity, text: CharSequence, duration: Int): Snackbar? {
        val root = activity.findViewById<View>(android.R.id.content) ?: return null
        val bar = Snackbar.make(root, text, Snackbar.LENGTH_LONG)
        bar.duration = duration
        // Above the navigation, which is where he asked for it. Anchoring to a hidden view would
        // park the bar off the bottom of the screen, so the bar is only anchored when the shell is
        // actually showing one.
        val nav = activity.findViewById<View>(R.id.bottom_nav)
        if (nav != null && nav.isShown) bar.setAnchorView(nav)
        bar.show()
        return bar
    }

    private fun Context.activity(): Activity? {
        var context: Context? = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}
