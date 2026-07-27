package com.v2ray.ang.ui.component

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import com.v2ray.ang.R
import com.v2ray.ang.util.reducedMotion
import java.util.WeakHashMap

/**
 * The skeleton (22-components.md 16, 00-rules.md 15) - what a loading list looks like, and the end
 * of the centred spinner over a blank screen.
 *
 * The rule the styles cannot enforce is the one that makes a skeleton work: **it is the silhouette
 * of the real content.** Same block count, same heights, same positions, widths derived from the
 * content rather than hand-picked to fake variety. That belongs to the layout. What belongs here is
 * the timing, and there are three numbers in it:
 *
 * - it appears only after **300ms** of waiting, so a fast response never flashes one (00-rules.md 7.3);
 * - it pulses opacity **0.45 to 1.0** each way over `motion_pulse` 1000ms on `ease_standard`,
 *   infinite reverse;
 * - it hands over to the content with a `motion_state` 220ms crossfade of the block AS ONE OBJECT -
 *   no per-item entrance, no stagger (00-rules.md 8.9).
 *
 * Reduced motion holds the skeleton static at opacity **0.7** and makes the hand-over instant.
 *
 * One documented disagreement between the specs, resolved in favour of the higher authority:
 * `32-master-plan-android.md` 8.9 asks for a static skeleton (D-A5), while `00-rules.md` 3.7 - the
 * law, and precedence 0.1.2 - defines `motion_pulse` for exactly this purpose and says reduced
 * motion is what holds it static. `res/values/motion.xml` and `@style/Widget.Departament.Skeleton`
 * both ship the pulse. This file follows the law, and [hold] is here for the day the owner settles
 * it the other way: it is one call, in one place.
 */
object SkeletonBinder {

    /**
     * Starts the pulse on a skeleton block, or holds it static under reduced motion.
     *
     * ```kotlin
     * SkeletonBinder.pulse(binding.skeletonSubscription)
     * ```
     *
     * Call it on the CONTAINER of the skeleton bars, not on each bar: the block pulses as one
     * object, and eight bars breathing out of phase is exactly the decoration 00-rules.md 8.1 bans.
     * Idempotent - a second call cancels the first animator rather than stacking a second.
     */
    fun pulse(view: View) {
        RunningAnimators.cancel(view)
        // A crossfade left over from a previous swap() runs on the ViewPropertyAnimator, which the
        // animator map does not hold. Stop it too, or it fights this one over the same alpha.
        view.animate().cancel()
        if (view.reducedMotion()) {
            hold(view)
            return
        }
        val animator = ObjectAnimator.ofFloat(view, View.ALPHA, PULSE_LOW, PULSE_HIGH).apply {
            duration = view.durationOf(R.integer.motion_pulse)
            interpolator = view.curve(R.interpolator.ease_standard)
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        RunningAnimators.set(view, animator)
    }

    /**
     * Holds the skeleton static at 0.7 - the reduced-motion state, and the whole treatment if the
     * static reading of D-A5 ever wins.
     */
    fun hold(view: View) {
        RunningAnimators.cancel(view)
        view.animate().cancel()
        view.alpha = STATIC_ALPHA
    }

    /**
     * Shows the skeleton, but only if the wait outlasts the 300ms threshold (00-rules.md 7.3): a
     * response that arrives in 80ms must never flash a placeholder.
     *
     * Pair every call with [swap] or [cancel]; both clear the pending post.
     */
    fun showAfterDelay(view: View) {
        view.removeCallbacks(view.showRunnable())
        view.postDelayed(view.showRunnable(), APPEAR_AFTER_MS)
    }

    /** Drops a pending [showAfterDelay] and stops any pulse, without touching the content. */
    fun cancel(view: View) {
        view.removeCallbacks(view.showRunnable())
        RunningAnimators.cancel(view)
        view.alpha = 1f
        view.visibility = View.GONE
    }

    /**
     * The hand-over: the skeleton block and the real content crossfade over `motion_state` 220ms on
     * `ease_standard`, as one object, with no layout change. Reduced motion swaps instantly.
     *
     * ```kotlin
     * SkeletonBinder.swap(skeleton = binding.skeletonList, content = binding.list)
     * ```
     */
    fun swap(skeleton: View, content: View) {
        skeleton.removeCallbacks(skeleton.showRunnable())
        RunningAnimators.cancel(skeleton)

        val settle = {
            skeleton.alpha = 1f
            skeleton.visibility = View.GONE
            content.alpha = 1f
            content.visibility = View.VISIBLE
        }

        content.motion(snap = settle) {
            content.alpha = 0f
            content.visibility = View.VISIBLE
            val duration = content.durationOf(R.integer.motion_state)
            val curve = content.curve(R.interpolator.ease_standard)
            content.animate().alpha(1f).setDuration(duration).setInterpolator(curve).start()
            skeleton.animate()
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(curve)
                .withEndAction { settle() }
                .start()
        }
    }

    /**
     * One Runnable per view, so [showAfterDelay] and [cancel] can remove the same instance. Held in
     * a weak map rather than on a tag - tag keys must be declared id resources and
     * `res/values/ids.xml` belongs to another wave.
     */
    private fun View.showRunnable(): Runnable = pendingShows.getOrPut(this) {
        Runnable {
            visibility = View.VISIBLE
            pulse(this)
        }
    }

    private val pendingShows = WeakHashMap<View, Runnable>()

    private const val PULSE_LOW = 0.45f
    private const val PULSE_HIGH = 1.0f
    private const val STATIC_ALPHA = 0.7f

    // 00-rules.md 7.3's "appears after 300ms" threshold. It is not a motion duration - nothing
    // moves for these 300ms - so it is not in res/values/motion.xml and does not take a token there.
    private const val APPEAR_AFTER_MS = 300L
}
