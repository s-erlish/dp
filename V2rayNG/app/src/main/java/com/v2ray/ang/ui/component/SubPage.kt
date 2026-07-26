package com.v2ray.ang.ui.component

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentTransaction
import com.v2ray.ang.R
import com.v2ray.ang.util.animationsEnabled

/**
 * The sub-page transition, applied so that it cannot be applied wrongly.
 *
 * The motion is fixed by 00-rules.md 3.7 / 8 and 32-master-plan-android.md 7.3, and it is the whole
 * vocabulary a sub-page gets:
 *
 * | | translation | alpha | duration | curve |
 * |---|---|---|---|---|
 * | enter | 16dp to 0 | 0 to 1 | `motion_reveal` 300 | `ease_out_quint` |
 * | exit  | 0 to 16dp | 1 to 0 | `motion_reveal_exit` 225 | `ease_standard` |
 *
 * Exit is 75% of enter, which is why the two numbers differ and why neither of them is 150. Only
 * transform and alpha move; 00-rules.md 8.7 forbids animating layout properties for effect. There
 * is no page-load choreography beyond this (8.9): a screen appears, it does not perform.
 *
 * **Reduced motion is why this object exists instead of two bare resource ids.** Every entry point
 * checks `MotionUtils.animationsEnabled()` and substitutes a 0ms no-op, so the page snaps to its
 * end state for a user who has turned animations off. Navigation that hands `R.anim.subpage_enter`
 * straight to the platform skips that check and is a P1 accessibility defect (00-rules.md 8.8), so
 * pass the intent to [open] rather than animating by hand.
 */
object SubPage {

    /**
     * Starts [intent] as a sub-page and plays the entrance. Use this everywhere a row, a card or a
     * button pushes a screen.
     *
     * ```kotlin
     * binding.rowServers.onSingleClick {
     *     SubPage.open(this, Intent(this, ServerActivity::class.java))
     * }
     * ```
     *
     * Together with [onSingleClick] on the trigger this is also the practical form of R9 layer 2:
     * the tap that would have stacked a second `BuyTariffActivity` is dropped by the 500ms guard
     * before it ever reaches `startActivity`.
     */
    fun open(activity: Activity, intent: Intent) {
        activity.startActivity(intent)
        applyTransition(activity)
    }

    /** [open], for a sub-page that returns a result through the Activity Result API. */
    fun open(activity: Activity, launcher: ActivityResultLauncher<Intent>, intent: Intent) {
        launcher.launch(intent)
        applyTransition(activity)
    }

    /**
     * Finishes the current sub-page and plays the exit. Use it for the toolbar's back arrow and for
     * any «Отмена» / «Готово» that closes the screen, so the close reads as the open reversed.
     */
    fun close(activity: Activity) {
        activity.finish()
        applyTransition(activity)
    }

    /**
     * The API 34+ form, for an activity that would rather own its transitions than trust its
     * callers: call it from `onCreate` before `setContentView` and the platform uses this pair in
     * both directions, including for the predictive-back gesture, which `overridePendingTransition`
     * cannot drive. A no-op below API 34, where [open] and [close] already cover both directions.
     */
    fun installTransitions(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            installModernTransitions(activity, activity.animationsEnabled())
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun installModernTransitions(activity: Activity, animated: Boolean) {
        val enter = if (animated) R.anim.subpage_enter else 0
        val exit = if (animated) R.anim.subpage_exit else 0
        activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, enter, exit)
        activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, enter, exit)
    }

    /*
     * One pair covers both directions: the arriving screen always enters and the leaving screen
     * always exits, whether the stack is growing or shrinking.
     *
     * overridePendingTransition is deprecated as of API 34 in favour of overrideActivityTransition,
     * which only the OPENED activity may call. It is still honoured, and it is the only form
     * available to the CALLER, which is where a push is written. installTransitions() is the modern
     * path for an activity that wants to declare its own.
     */
    @Suppress("DEPRECATION")
    private fun applyTransition(activity: Activity) {
        if (activity.animationsEnabled()) {
            activity.overridePendingTransition(R.anim.subpage_enter, R.anim.subpage_exit)
        } else {
            activity.overridePendingTransition(0, 0)
        }
    }
}

/**
 * The fragment form of the same pair, applied in both directions so a back press reverses exactly
 * what the push did. Honours reduced motion like every other entry point in this file.
 *
 * ```kotlin
 * supportFragmentManager.commit {
 *     subPageAnimations(requireContext())
 *     replace(R.id.content, DetailFragment())
 *     addToBackStack(null)
 * }
 * ```
 */
fun FragmentTransaction.subPageAnimations(context: Context): FragmentTransaction =
    if (context.animationsEnabled()) {
        setCustomAnimations(
            R.anim.subpage_enter,
            R.anim.subpage_exit,
            R.anim.subpage_enter,
            R.anim.subpage_exit,
        )
    } else {
        this
    }
