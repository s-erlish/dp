package com.v2ray.ang.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi

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
 * ============================ HAPTICS ============================
 *
 * THE DEFECT THIS SHAPE EXISTS FOR. The owner: «на самсунге нет вибрации, например на моем ксяоми
 * вся вибрация есть, а вот на самсунге вообще вибрации в приложении нет». Same APK, same taps, one
 * OEM buzzes and the other is silent — and the app could not tell, because it was throwing the
 * request over the wall and never looking at what came back.
 *
 * `View.performHapticFeedback(constant)` is not "vibrate". It is a REQUEST, phrased in a vocabulary
 * the device firmware is free not to speak, and it answers with a Boolean. The framework
 * (`PhoneWindowManager.performHapticFeedback`) returns **false**, having played nothing at all, in
 * exactly four cases:
 *
 *  1. the view is not attached (`mAttachInfo == null`) — checked inside [View] before the IPC;
 *  2. the view's own `android:hapticFeedbackEnabled` is false — likewise checked in [View];
 *  3. `Settings.System.HAPTIC_FEEDBACK_ENABLED` is 0, i.e. **the user turned touch vibration off**;
 *  4. the build's `getVibrationEffect(effectId)` switch has no case for the constant we passed and
 *     falls through to `default: return null` — **the OEM does not speak that word**.
 *
 * Case 4 is the split. `VIRTUAL_KEY` and `CLOCK_TICK` are ancient (API 3 and 21) but they are still
 * only *names*: the vibration behind them comes from an OEM-overlaid config, and One UI maps a
 * narrower set than MIUI does — `CLOCK_TICK` in particular resolves to `EFFECT_TEXTURE_TICK`, the
 * lightest thing the platform has and the first one a conservative OEM drops. `CONFIRM` (API 30,
 * the connect hero moment's word) is worse: on anything below API 30 it is a guaranteed no-op, and
 * `minSdk` here is 24. Every one of those failures returned false into a discarded value.
 *
 * SO THE RETURN VALUE IS THE GATE, and it is the ONE thing that keeps this from double-buzzing.
 * Every `return false` in the framework happens *before* `mVibrator.vibrate(...)` — false is a
 * promise that nothing was played. On a device that already works (the owner's Xiaomi) the first
 * request answers true, [playHaptic] returns immediately, and the fallback below is dead code at
 * runtime. It is reached only after the platform has said, in its own words, that it did nothing.
 *
 * WHAT THE FALLBACK DELIBERATELY DOES NOT DO is override the user. Cases 1–3 above are re-checked
 * in app space before a single motor pulse — an off-screen view, a view that opted out in XML, and
 * above all a person who switched touch vibration off in Settings get silence, because that is what
 * they asked for. `FLAG_IGNORE_GLOBAL_SETTING` would paper over case 3 and is exactly why it was
 * deprecated in API 33 and is ignored on new releases; it is not used here and must not be. Only
 * case 4 — the OEM not knowing the word — is compensated for, and it is compensated for by saying
 * the same thing in the only vocabulary every device understands: an explicit [VibrationEffect]
 * tagged `USAGE_TOUCH`, so the system still scales it by the user's own touch-feedback intensity.
 */

/**
 * Standard "button press" haptic. Use on primary taps/confirmations.
 *
 * `KEYBOARD_TAP` is the second rung rather than a different feel: both it and `VIRTUAL_KEY` resolve
 * to `EFFECT_CLICK`, but `VIRTUAL_KEY` is historically wired to the navigation-BAR key haptic, which
 * a gesture-navigation One UI build has no keys left to fire.
 */
fun View.pressHaptic() {
    playHaptic(Feel.PRESS, HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.KEYBOARD_TAP)
}

/**
 * Light "tick" haptic. Use for scrubbing, stepping, or incremental selection.
 *
 * `CONTEXT_CLICK` backs `CLOCK_TICK` up because it is the same weight one rung heavier —
 * `EFFECT_TICK` against `EFFECT_TEXTURE_TICK` — so a device that dropped the featherweight one still
 * answers with something that reads as a tick and not as a press.
 */
fun View.tickHaptic() {
    playHaptic(Feel.TICK, HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.CONTEXT_CLICK)
}

/**
 * The connect/disconnect confirmation — 32-master-plan-android.md 7.6's PRESS rung, asked for with
 * the platform's dedicated word first so a device that has a distinct "confirm" feel keeps it.
 *
 * `HapticFeedbackConstants.CONFIRM` arrived in API 30. Below that, and on any build that never
 * mapped it, the request is refused and this is [pressHaptic] — which is what 7.6 specifies for
 * connect anyway. Nothing is played twice: the second request is only made because the first
 * reported it played nothing.
 */
fun View.confirmHaptic() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    ) {
        return
    }
    pressHaptic()
}

/**
 * The two sanctioned feels, and the explicit effect each one degrades to. Kept private: screens
 * speak `Haptic.PRESS` / `Haptic.TICK` (see `ui/component/SingleClick.kt`) and the three functions
 * above, never this. The set stays closed at two — 32-master-plan-android.md 7.6 — because
 * [confirmHaptic] is the PRESS feel asked for by a better name, not a third thing.
 *
 * @param predefined the platform's own named effect, used from API 29. `createPredefined` already
 *   carries a generic fallback for hardware that lacks the primitive, so this is the better feel
 *   wherever it exists.
 * @param millis the plain motor pulse for API 26–28, and the whole story below 26 where
 *   [VibrationEffect] does not exist yet. 20ms reads as a click, 12ms as a tick.
 */
private enum class Feel(val predefined: Int, val millis: Long) {
    PRESS(VibrationEffect.EFFECT_CLICK, 20L),
    TICK(VibrationEffect.EFFECT_TICK, 12L),
}

/**
 * Ask the system, in its own vocabulary, newest word first; fall back only once it has said it did
 * nothing. See the block comment above for why the Boolean is trustworthy and why this cannot buzz
 * twice.
 */
private fun View.playHaptic(feel: Feel, vararg constants: Int) {
    for (constant in constants) {
        if (performHapticFeedback(constant)) return
    }
    // The two gates [View] applies before it ever reaches the system. Re-checked here rather than
    // assumed, so the fallback declines for the same reasons the request did.
    if (!isAttachedToWindow || !isHapticFeedbackEnabled) return
    if (!context.touchHapticsAllowed()) return
    context.vibrateOnce(feel)
}

/**
 * The user's own answer, read rather than overridden.
 *
 * `HAPTIC_FEEDBACK_ENABLED` is the toggle every OEM settings app writes — on One UI it is «Звуки и
 * вибрация → Системные звуки и вибрация → Вибрация при касании». `haptic_feedback_intensity` is the
 * separate slider Android 12 added beside it; it is `@SystemApi` on `Settings.System` so the key is
 * spelled out here, and a build that does not have it simply returns the default and is allowed.
 *
 * When either says off, the fallback does not run. An app that vibrates after being told not to is
 * not fixing a bug, it is being one.
 *
 * DEPRECATION is suppressed for one constant. `Settings.System.HAPTIC_FEEDBACK_ENABLED` is marked
 * deprecated because a third-party app may no longer WRITE it; reading it is still how the framework
 * itself gates touch feedback, and there is no public replacement — `Vibrator` exposes no intensity
 * query. Retyping the same key as a string literal to silence the marker would hide that, so the
 * typed constant stays and the reason is written down instead.
 */
@Suppress("DEPRECATION")
private fun Context.touchHapticsAllowed(): Boolean {
    val resolver = contentResolver
    if (Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return Settings.System.getInt(resolver, HAPTIC_FEEDBACK_INTENSITY, INTENSITY_DEFAULT) != INTENSITY_OFF
}

/**
 * One pulse, tagged as touch feedback so the platform keeps the last word on how strong it is.
 *
 * `USAGE_TOUCH` matters: an untagged vibration is treated as a generic alarm-ish one and ignores the
 * user's touch-feedback intensity, which would make the fallback louder than the haptics it is
 * standing in for. Below API 33 the same usage is expressed through `AudioAttributes`
 * (`USAGE_ASSISTANCE_SONIFICATION` is what `VibrationAttributes` maps to `USAGE_TOUCH`).
 *
 * From API 31 the vibrator is reached through [VibratorManager] — on a multi-actuator device
 * `getSystemService(Vibrator::class.java)` and `getDefaultVibrator()` are not guaranteed to be the
 * same object, and the latter is the one the platform itself drives touch feedback with.
 *
 * DEPRECATION is suppressed for exactly two calls, each the only overload that exists below the API
 * that replaced it: `vibrate(VibrationEffect, AudioAttributes)` under 33 and `vibrate(long)` under 26.
 */
@Suppress("DEPRECATION")
private fun Context.vibrateOnce(feel: Feel) {
    val vibrator = touchVibrator() ?: return
    if (!vibrator.hasVibrator()) return
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> vibrator.vibrate(
            feel.effect(),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> vibrator.vibrate(
            feel.effect(),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )

        else -> vibrator.vibrate(feel.millis)
    }
}

private fun Context.touchVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }

@RequiresApi(Build.VERSION_CODES.O)
private fun Feel.effect(): VibrationEffect =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(predefined)
    } else {
        VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
    }

/** `Settings.System.HAPTIC_FEEDBACK_INTENSITY` — `@SystemApi`, so the key is named here. */
private const val HAPTIC_FEEDBACK_INTENSITY = "haptic_feedback_intensity"

/** `Vibrator.VIBRATION_INTENSITY_OFF` / `_MEDIUM`, likewise not public. */
private const val INTENSITY_OFF = 0
private const val INTENSITY_DEFAULT = 2
