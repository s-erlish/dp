package com.v2ray.ang.ui.component

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import com.v2ray.ang.R
import com.v2ray.ang.util.reducedMotion

/**
 * ЭКРАН ПРОГРУЗКИ — README §3. Один слой, два потока, четыре шага.
 *
 * Полноэкранный слой вешается в `android.R.id.content` окна-хозяина, поэтому он закрывает и нижнюю
 * навигацию, и всё остальное, ничего не зная о том, какая вкладка под ним. Снимается он оттуда же —
 * **удаляется из иерархии, а не прячется**: спрятанный слой продолжает жить, держать битмапы дуги и
 * ловить фокус.
 *
 * ЧТО ЭТО НЕ ТАКОЕ. Это не экран входа и не замена `LoginActivity` (PORT-DELTA П-13): слой ничего не
 * решает и ничего не запрашивает — он **показывает** состояние, которое ему сообщает поток. Тайминги
 * README (1200 / 3000 / 4600 мс) — потолки анимации, а не расписание: шаги двигает ответ сервера.
 *
 * ТРИ ВЕЩИ, КОТОРЫЕ ЗДЕСЬ СДЕЛАНЫ НАРОЧНО:
 *
 * 1. **Одна дуга** (грабля №7). Дуга — это картинка `ic_flow_arc` с trimPathEnd 0.199 и ОДИН
 *    бесконечный [ObjectAnimator] на `rotation`, а не цепочка сегментов и не перезапуск по таймеру.
 *    Аниматор останавливается на четвёртом шаге и при снятии слоя, а не «когда-нибудь».
 * 2. **Уход — «Проявление»**: прозрачность в 0 плюс отдаление до 1.06. Никакого размытия
 *    (грабля №10) и никакого сдвига.
 * 3. **Снятие и старт сборки Главной — один кадр** (грабля №6): [removeAndHandOff] делает и то, и
 *    другое подряд, синхронно. Контракт — [HomeHandoff].
 */
class FlowOverlay private constructor(
    private val root: View,
    private val kind: Kind,
) {

    /** Какой из двух потоков рисуется: тексты и глиф в кольце берутся отсюда. */
    enum class Kind { TELEGRAM, CLIPBOARD }

    private val ringBox: View = root.findViewById(R.id.flow_ring_box)
    private val arc: ImageView = root.findViewById(R.id.flow_arc)
    private val ringDone: View = root.findViewById(R.id.flow_ring_done)
    private val sonar: View = root.findViewById(R.id.flow_sonar)
    private val glyph: ImageView = root.findViewById(R.id.flow_glyph)
    private val check: ImageView = root.findViewById(R.id.flow_check)
    private val title: TextView = root.findViewById(R.id.flow_title)
    private val note: TextView = root.findViewById(R.id.flow_note)
    private val bar: View = root.findViewById(R.id.flow_bar)
    private val barFill: View = root.findViewById(R.id.flow_bar_fill)
    private val toast: LinearLayout = root.findViewById(R.id.flow_toast)
    private val toastLabel: TextView = root.findViewById(R.id.flow_toast_label)

    private var arcSpin: ObjectAnimator? = null
    private var beat: AnimatorSet? = null
    private var barAnimator: ValueAnimator? = null
    private var back: OnBackPressedCallback? = null

    /** Последний показанный шаг: полоса прогресса догоняет его после первой раскладки. */
    private var step = -1
    private var removed = false

    init {
        if (kind == Kind.TELEGRAM) {
            glyph.setImageResource(R.drawable.ic_telegram_24dp)
            glyph.isVisible = true
        }
        startArc()
        // Ширина полосы известна только после раскладки; до неё заливка остаётся нулевой, а не
        // прыгает от полной ширины к 18 %.
        bar.post { if (!removed && step >= 0) applyProgress(step, animate = false) }
    }

    // ------------------------------------------------------------------ шаги

    /**
     * Показать шаг 0..3. Тексты — `values/strings_flow.xml`, дословно из прототипа; проценты
     * 18 / 54 / 86 / 100 переезжают 900 мс ease-out-quart.
     *
     * Шаг 3 — финал: дуга останавливается, кольцо замыкается акцентом, копия контура уходит сонаром
     * до 1.6x за 600 мс, в центре появляется галочка, снизу поднимается тост.
     */
    @MainThread
    fun step(index: Int) {
        if (removed || index == step) return
        step = index.coerceIn(0, LAST_STEP)
        title.setText(titleFor(step))
        note.setText(noteFor(step))
        applyProgress(step, animate = true)
        if (step == LAST_STEP) playFinale()
    }

    /**
     * Финал показан — подержать его и уйти. [onRemoved] выполняется в том же кадре, в котором слой
     * снят с иерархии; звать оттуда [HomeHandoff.assemble] и больше ничего.
     */
    @MainThread
    fun finish(onRemoved: () -> Unit) {
        if (removed) return
        step(LAST_STEP)
        dismiss(holdMs = FINISH_HOLD_MS, onRemoved = onRemoved)
    }

    /**
     * Уйти без финала: поток сорвался (сервер ответил отказом, пользователь передумал). Слой
     * снимается тем же движением, но без задержки и без сборки Главной.
     */
    @MainThread
    fun cancel() {
        if (removed) return
        dismiss(holdMs = 0L, onRemoved = {})
    }

    // ------------------------------------------------------------------ движение

    private fun startArc() {
        if (root.reducedMotion()) return
        arcSpin = ObjectAnimator.ofFloat(arc, View.ROTATION, 0f, 360f).apply {
            duration = root.resources.getInteger(R.integer.motion_spin).toLong()
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun stopArc() {
        arcSpin?.cancel()
        arcSpin = null
    }

    /** Полоса прогресса: ширина заливки как доля дорожки. Дорожка знает свою ширину после раскладки. */
    private fun applyProgress(index: Int, animate: Boolean) {
        val track = bar.width
        if (track <= 0) return
        val target = (track * PERCENT[index] / 100f).toInt()
        barAnimator?.cancel()
        val from = barFill.layoutParams.width.coerceAtLeast(0)
        if (!animate || root.reducedMotion() || from == target) {
            setFillWidth(target)
            return
        }
        barAnimator = ValueAnimator.ofInt(from, target).apply {
            duration = root.resources.getInteger(R.integer.motion_flow_bar).toLong()
            interpolator = root.curve(R.interpolator.ease_out_quart)
            addUpdateListener { setFillWidth(it.animatedValue as Int) }
            start()
        }
    }

    private fun setFillWidth(width: Int) {
        barFill.layoutParams = barFill.layoutParams.apply { this.width = width }
    }

    /**
     * Шаг 3. Одна [AnimatorSet] со стартовыми задержками вместо цепочки `postDelayed`: цепочка
     * переживает уход экрана и стреляет в отсутствующее вью, а набор отменяется одним вызовом.
     */
    private fun playFinale() {
        stopArc()
        toastLabel.setText(if (kind == Kind.TELEGRAM) R.string.flow_tg_toast else R.string.flow_clip_toast)

        if (root.reducedMotion()) {
            arc.alpha = 0f
            ringDone.alpha = 1f
            check.alpha = 1f
            toast.alpha = 1f
            toast.translationY = 0f
            return
        }

        val state = root.durationOf(R.integer.motion_state)
        val sonarMs = root.durationOf(R.integer.motion_emphasis)
        val pop = root.durationOf(R.integer.motion_reveal)
        val toastMs = root.durationOf(R.integer.motion_popup)
        val settle = root.curve(R.interpolator.ease_out_quint)

        check.scaleX = CHECK_FROM
        check.scaleY = CHECK_FROM
        toast.translationY = TOAST_RISE_DP * root.resources.displayMetrics.density
        sonar.alpha = 1f

        beat?.cancel()
        beat = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(arc, View.ALPHA, 0f).setDuration(state),
                ObjectAnimator.ofFloat(ringDone, View.ALPHA, 1f).setDuration(state),
                ObjectAnimator.ofFloat(sonar, View.SCALE_X, SONAR_TO).apply {
                    duration = sonarMs
                    interpolator = settle
                },
                ObjectAnimator.ofFloat(sonar, View.SCALE_Y, SONAR_TO).apply {
                    duration = sonarMs
                    interpolator = settle
                },
                ObjectAnimator.ofFloat(sonar, View.ALPHA, 0f).apply {
                    duration = sonarMs
                    interpolator = settle
                },
                ObjectAnimator.ofFloat(check, View.ALPHA, 1f).apply {
                    duration = pop
                    interpolator = settle
                },
                ObjectAnimator.ofFloat(check, View.SCALE_X, 1f).apply {
                    duration = pop
                    interpolator = settle
                },
                ObjectAnimator.ofFloat(check, View.SCALE_Y, 1f).apply {
                    duration = pop
                    interpolator = settle
                },
                ObjectAnimator.ofFloat(toast, View.ALPHA, 1f).apply {
                    duration = toastMs
                    interpolator = settle
                },
                ObjectAnimator.ofFloat(toast, View.TRANSLATION_Y, 0f).apply {
                    duration = toastMs
                    interpolator = settle
                },
            )
            // Аппаратный слой только на время боя и снимается по его окончании — иначе кольцо
            // остаётся в отдельном буфере на весь экран.
            doOnEnd { ringBox.setLayerType(View.LAYER_TYPE_NONE, null) }
            ringBox.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            start()
        }
    }

    /**
     * Уход «Проявление» (§3): прозрачность в 0 плюс отдаление до 1.06, 520 мс на кривой смены
     * экрана. Размытия нет — оно лагает (грабля №10).
     */
    private fun dismiss(holdMs: Long, onRemoved: () -> Unit) {
        stopArc()
        barAnimator?.cancel()
        if (root.reducedMotion()) {
            root.postDelayed({ removeAndHandOff(onRemoved) }, holdMs)
            return
        }
        root.animate().cancel()
        root.animate()
            .alpha(0f)
            .scaleX(EXIT_SCALE)
            .scaleY(EXIT_SCALE)
            .setStartDelay(holdMs)
            .setDuration(root.durationOf(R.integer.motion_flow_exit))
            .setInterpolator(root.curve(R.interpolator.ease_screen))
            .withEndAction { removeAndHandOff(onRemoved) }
            .start()
    }

    /**
     * ГРАБЛЯ №6. Снятие слоя и запуск сборки Главной — подряд, синхронно, в одном кадре. Между этими
     * двумя строками не должно появиться ни `post`, ни `animate`, ни какого-либо ожидания.
     */
    private fun removeAndHandOff(onRemoved: () -> Unit) {
        if (removed) return
        removed = true
        beat?.cancel()
        back?.remove()
        back = null
        val content = root.parent as? ViewGroup
        content?.removeView(root)
        if (content != null) {
            for (index in 0 until content.childCount) {
                content.getChildAt(index).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            }
        }
        onRemoved()
    }

    // ------------------------------------------------------------------ тексты

    @StringRes
    private fun titleFor(index: Int): Int = when (kind) {
        Kind.TELEGRAM -> TG_TITLES[index]
        Kind.CLIPBOARD -> CLIP_TITLES[index]
    }

    @StringRes
    private fun noteFor(index: Int): Int = when (kind) {
        Kind.TELEGRAM -> TG_NOTES[index]
        Kind.CLIPBOARD -> CLIP_NOTES[index]
    }

    companion object {

        /**
         * Вешает слой поверх всего окна и показывает нулевой шаг.
         *
         * @param onCancel что делать по кнопке «назад». Если null, «назад» слой глотает: поток,
         * который нельзя бросить, не должен делать вид, что его бросили.
         */
        @MainThread
        fun show(activity: Activity, kind: Kind, onCancel: (() -> Unit)? = null): FlowOverlay {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val view = LayoutInflater.from(activity)
                .inflate(R.layout.layout_flow_overlay, content, false)
            content.addView(view)
            // Экран под слоем закрыт для глаза — он должен быть закрыт и для TalkBack, иначе
            // палец находит кнопки, которых зритель не видит.
            for (index in 0 until content.childCount) {
                val child = content.getChildAt(index)
                if (child !== view) {
                    child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
            }
            val overlay = FlowOverlay(view, kind)
            if (activity is ComponentActivity) {
                overlay.back = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        onCancel?.invoke()
                    }
                }.also { activity.onBackPressedDispatcher.addCallback(it) }
            }
            overlay.step(0)
            return overlay
        }

        private const val LAST_STEP = 3

        /** §3: 18 / 54 / 86 / 100 %. */
        private val PERCENT = intArrayOf(18, 54, 86, 100)

        private val TG_TITLES = intArrayOf(
            R.string.flow_tg_0_title,
            R.string.flow_tg_1_title,
            R.string.flow_tg_2_title,
            R.string.flow_tg_3_title,
        )
        private val TG_NOTES = intArrayOf(
            R.string.flow_tg_0_note,
            R.string.flow_tg_1_note,
            R.string.flow_tg_2_note,
            R.string.flow_tg_3_note,
        )
        private val CLIP_TITLES = intArrayOf(
            R.string.flow_clip_0_title,
            R.string.flow_clip_1_title,
            R.string.flow_clip_2_title,
            R.string.flow_clip_3_title,
        )
        private val CLIP_NOTES = intArrayOf(
            R.string.flow_clip_0_note,
            R.string.flow_clip_1_note,
            R.string.flow_clip_2_note,
            R.string.flow_clip_3_note,
        )

        /** §3: финал держится, пока читается тост, и только потом слой начинает таять. */
        private const val FINISH_HOLD_MS = 1400L

        private const val EXIT_SCALE = 1.06f
        private const val SONAR_TO = 1.6f
        private const val CHECK_FROM = 0.9f
        private const val TOAST_RISE_DP = 8f
    }
}
