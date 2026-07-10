package com.v2ray.ang.util

import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Small, pure motion helpers shared across the app. No wiring, no side effects
 * beyond the single call each function names.
 *
 * The motion tempo lives in res/values/motion.xml (motion_press_in / _out / _state /
 * _reveal / _stagger) and the easing in res/interpolator (ease_out_quint / _quart /
 * ease_standard). Press feedback is declared in res/anim/press_scale.xml and applied
 * via android:stateListAnimator. These helpers exist so imperative animations honor the
 * same reduced-motion contract and offer consistent haptics.
 */
object MotionUtils {

    /**
     * True when the OS animation duration scale is non-zero. When the user turns
     * animations off (Developer options "Animator duration scale" = Off, or the
     * "Remove animations" accessibility setting), this returns false and callers
     * should jump straight to the end state instead of animating.
     */
    fun animationsEnabled(context: Context): Boolean =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) != 0f
}

/**
 * Extension form of [MotionUtils.animationsEnabled] for use from a Context receiver.
 * `if (animationsEnabled()) animate() else jumpToEnd()`
 */
fun Context.animationsEnabled(): Boolean =
    Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) != 0f

/**
 * True when motion should be reduced/skipped for this view's context. Convenience
 * inverse of [animationsEnabled] so call sites read naturally:
 * `if (view.reducedMotion()) view.jumpToEnd()`
 */
fun View.reducedMotion(): Boolean = !context.animationsEnabled()

/**
 * Standard "button press" haptic. Use on primary taps/confirmations.
 */
fun View.pressHaptic() {
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

/**
 * Light "tick" haptic. Use for scrubbing, stepping, or incremental selection.
 */
fun View.tickHaptic() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}
