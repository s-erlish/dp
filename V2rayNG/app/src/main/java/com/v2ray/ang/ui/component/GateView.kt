package com.v2ray.ang.ui.component

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.auth.AuthManager
import com.v2ray.ang.auth.AuthManager.LoginState
import com.v2ray.ang.auth.BackendConfig
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.ui.LoginActivity
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.reducedMotion
import com.v2ray.ang.viewmodel.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * НАЧАЛЬНЫЙ ЭКРАН — README §2, и оба потока, которые с него начинаются (§3).
 *
 * Это корень `layout_home_empty.xml`, то есть тот же блок `@id/gate`, который `HomeFragment`
 * показывает вместо карточки подписки. Класс здесь появился ровно за тем, чтобы вся механика
 * начального экрана — вступление, буфер обмена, раскрытие «Других способов», два потока прогрузки —
 * жила в одном файле рядом со своей разметкой, а не расползалась по чужому фрагменту.
 *
 * ЧЕТЫРЕ ФОРМЫ ГЕЙТА СОХРАНЕНЫ (PORT-DELTA П-01). `HomeFragment.paintGate` продолжает писать в
 * `tv_gate_title`, `tv_gate_caption`, `btn_gate_primary` и `btn_gate_secondary`; [bindOnboarding]
 * только включает онбординговый обвес в форме «вход» и выключает его во всех остальных.
 *
 * БУФЕР ОБМЕНА ЧИТАЕТСЯ ДВА РАЗА И БОЛЬШЕ НИКОГДА: при выходе окна на передний план и по нажатию на
 * «Добавить из буфера обмена». Никакого таймера, никакого фонового опроса, никакой настройки
 * «разрешить чтение буфера» и никакого предупреждения перед чтением: экран живёт, пока подписок нет,
 * то есть примерно раз за установку, и карточка обязана появляться сама. Сначала — дешёвая проверка
 * типа (`hasMimeType(MIMETYPE_TEXT_PLAIN)`), содержимое читается только если тип подошёл.
 *
 * ВХОД ЧЕРЕЗ TELEGRAM ИДЁТ ОТСЮДА, а не из `LoginActivity`: в дизайне у этого пути нет экрана входа,
 * есть слой прогрузки. Под слоем работает тот же `AuthManager.beginTelegramLogin()`, который питает
 * `AuthViewModel`, — это второй потребитель одного и того же состояния, а не вторая реализация
 * входа. `LoginActivity` со всем, что у неё есть (почта, OTP, 2FA, Google), остаётся в проекте и
 * открывается строкой «Войти через сайт»: она перестала быть путём по умолчанию, а не исчезла.
 */
class GateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /**
     * То немногое, что начальный экран не может сделать сам: оболочка владеет лаунчером
     * авторизации, меню добавления и списком серверов.
     */
    interface Host {
        /** Открыть `LoginActivity` через лаунчер оболочки, который применит изменения на возврате. */
        fun openAuth(intent: Intent)

        /** «Добавить по QR-коду» — сканер живёт в оболочке. */
        fun addByQr(anchor: View)

        /** Подписка добавлена: перечитать список серверов. */
        fun onSubscriptionAdded()

        /** Вход состоялся: подтянуть подписку аккаунта. */
        fun refreshSubscriptions()
    }

    private lateinit var shield: FrameLayout
    private lateinit var ringInner: View
    private lateinit var title: TextView
    private lateinit var caption: TextView
    private lateinit var cardSlot: FrameLayout
    private lateinit var cardCta: TextView
    private lateinit var buttonSlot: FrameLayout
    private lateinit var clipboardButton: MaterialButton
    private lateinit var primary: MaterialButton
    private lateinit var secondary: MaterialButton
    private lateinit var moreButton: LinearLayout
    private lateinit var moreCaret: ImageView
    private lateinit var moreList: LinearLayout

    private var host: Host? = null
    private var onboarding = false
    private var entered = false
    private var moreOpen = false

    /** Ссылка, найденная в буфере. null — буфер пуст, и это состояние, а не ошибка. */
    private var clipLink: String? = null

    private var flow: FlowOverlay? = null
    private var flowJob: Job? = null
    private var telegramOpened = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        shield = findViewById(R.id.gate_shield)
        ringInner = findViewById(R.id.gate_ring_inner)
        title = findViewById(R.id.tv_gate_title)
        caption = findViewById(R.id.tv_gate_caption)
        cardSlot = findViewById(R.id.gate_clip_card_slot)
        cardCta = findViewById(R.id.btn_gate_clip_add)
        buttonSlot = findViewById(R.id.gate_clip_button_slot)
        clipboardButton = findViewById(R.id.btn_gate_clipboard)
        primary = findViewById(R.id.btn_gate_primary)
        secondary = findViewById(R.id.btn_gate_secondary)
        moreButton = findViewById(R.id.btn_gate_more)
        moreCaret = findViewById(R.id.gate_more_caret)
        moreList = findViewById(R.id.gate_more_list)

        // Пунктирная обводка внутреннего кольца рисуется PathEffect'ом, а его аппаратный слой
        // на части устройств игнорирует. Один программный слой на одну картинку 88dp — дешевле,
        // чем кольцо, которое где-то сплошное, а где-то пунктирное.
        ringInner.setLayerType(LAYER_TYPE_SOFTWARE, null)

        cardCta.pressFeedback(R.anim.press_button)
        clipboardButton.pressFeedback(R.anim.press_button)
        primary.pressFeedback(R.anim.press_button)
        moreButton.pressFeedback(R.anim.press_button)
        findViewById<View>(R.id.row_gate_qr).pressFeedback(R.anim.press_row)
        findViewById<View>(R.id.row_gate_site).pressFeedback(R.anim.press_row)

        cardCta.onSingleClick(Haptic.PRESS) { clipLink?.let { link -> startClipboardFlow(link) } }
        clipboardButton.onSingleClick { onClipboardButton() }
        moreButton.onSingleClick { toggleMore() }
        findViewById<View>(R.id.row_gate_qr).onSingleClick { host?.addByQr(it) }
        findViewById<View>(R.id.row_gate_site).onSingleClick { openSite() }
    }

    // ------------------------------------------------------------------ форма

    /**
     * Включает начальный экран (§2) или возвращает блок к обычному виду гейта.
     *
     * Зовётся из `HomeFragment.paintGate` последней строкой — после того, как форма гейта уже
     * написала заголовок, подпись и действия, — поэтому здесь и стоит окончательная видимость.
     */
    fun bindOnboarding(active: Boolean, host: Host?) {
        this.host = host
        val changed = onboarding != active
        onboarding = active
        shield.isVisible = active
        moreButton.isVisible = active

        if (!active) {
            if (!changed) return
            // Остальные формы гейта: заголовок, подпись и две обычные кнопки. Глиф Telegram
            // снимается — его ставит только форма входа. Поток, если он идёт, НЕ трогаем: он
            // живёт на окне, а не в этом блоке, и смена формы гейта под непрозрачным слоем — это
            // ровно то, ради чего слой и поднят.
            primary.icon = null
            cardSlot.isVisible = false
            buttonSlot.isVisible = false
            setMore(open = false, animate = false)
            return
        }

        // Ставится на КАЖДОЙ отрисовке, а не только на переходе: `paintGate` перед этим вызовом
        // написал в те же вьюхи формулировки формы «вход», и последнее слово должно остаться за
        // начальным экраном.
        secondary.isVisible = false
        title.setText(R.string.onb_title)
        primary.setText(R.string.onb_login_telegram)
        primary.icon = ContextCompat.getDrawable(context, R.drawable.ic_telegram_24dp)
        primary.onSingleClick(Haptic.PRESS) { startTelegramFlow() }

        // Буфер ЧИТАЕТСЯ только при первом включении экрана; дальше отрисовка лишь повторяет уже
        // известное состояние. Перечитывают его два события: выход окна на передний план и
        // нажатие на кнопку.
        if (changed) refreshClipboard(animate = false) else applyClipState(animate = false)
        playEntrance()
    }

    /**
     * Выход окна на передний план — единственный момент, когда буфер читается сам. Он же ловит
     * возвращение из Telegram: если поток ждёт подтверждения, значит пользователь вернулся, и шаг
     * можно двигать дальше.
     */
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus || !onboarding) return
        val running = flow
        if (running != null) {
            if (telegramOpened) running.step(1)
        } else {
            refreshClipboard(animate = entered)
        }
    }

    // ------------------------------------------------------------------ буфер обмена

    private fun onClipboardButton() {
        // §2.5: нажатие перечитывает буфер. Нашлась ссылка — кнопка сворачивается и на её месте
        // разворачивается карточка, добавить остаётся одним нажатием. Не нашлась — экран остаётся
        // как был, без сообщения об ошибке: пустой буфер это состояние, а не отказ.
        refreshClipboard(animate = true)
    }

    /** Единственное место, где содержимое буфера действительно читается. */
    private fun refreshClipboard(animate: Boolean) {
        clipLink = readSubscriptionLink()
        applyClipState(animate)
    }

    /** Отрисовка уже известного состояния буфера — без чтения. */
    private fun applyClipState(animate: Boolean) {
        val link = clipLink
        caption.setText(if (link != null) R.string.onb_sub_found else R.string.onb_sub_empty)
        caption.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        setSlot(cardSlot, expand = link != null, animate = animate)
        setSlot(buttonSlot, expand = link == null, animate = animate)
    }

    /**
     * Дешёвая проверка сначала, содержимое потом: если в буфере лежит картинка или ничего, тип не
     * совпадёт и до текста дело не дойдёт. Возвращается только то, что похоже на ссылку подписки —
     * разбирать её всё равно будет импорт.
     */
    private fun readSubscriptionLink(): String? {
        val manager = ContextCompat.getSystemService(context, ClipboardManager::class.java) ?: return null
        val description = manager.primaryClipDescription ?: return null
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return null
        val text = try {
            manager.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Clipboard could not be read", e)
            return null
        }
        if (text.length < MIN_LINK_LENGTH) return null
        return text.takeIf { candidate -> LINK_PREFIXES.any { candidate.startsWith(it, true) } }
    }

    // ------------------------------------------------------------------ «Другие способы»

    private fun toggleMore() {
        setMore(open = !moreOpen, animate = true)
    }

    private fun setMore(open: Boolean, animate: Boolean) {
        moreOpen = open
        setSlot(moreList, expand = open, animate = animate)
        val target = if (open) 180f else 0f
        if (!animate || reducedMotion()) {
            moreCaret.rotation = target
            return
        }
        moreCaret.animate().rotation(target)
            .setDuration(durationOf(R.integer.motion_caret))
            .setInterpolator(curve(R.interpolator.ease_out_quart))
            .start()
    }

    // ------------------------------------------------------------------ движение

    /**
     * §2 «Появление экрана»: щит 620 мс из 0.7x, заголовок 460 мс с задержкой 80 мс, подзаголовок
     * 140 мс, кнопки 200 / 260 / 320 мс.
     *
     * Один [AnimatorSet] со стартовыми задержками, а не цепочка `postDelayed`: цепочка переживает
     * уход экрана и стреляет в отсутствующее вью. Аппаратные слои держатся только на время боя —
     * это лечение грабли №1, текст на возврате не дёргается.
     */
    private fun playEntrance() {
        if (entered) return
        entered = true
        val lifted = listOf<View>(title, caption, cardSlot, buttonSlot, primary, moreButton)
        if (reducedMotion()) {
            shield.alpha = 1f
            lifted.forEach { it.alpha = 1f; it.translationY = 0f }
            return
        }

        val rise = LIFT_DP * resources.displayMetrics.density
        val appear = durationOf(R.integer.motion_appear)
        val settle = curve(R.interpolator.ease_out_quint)
        val animators = mutableListOf<android.animation.Animator>()

        shield.alpha = 0f
        shield.scaleX = BLOOM_FROM
        shield.scaleY = BLOOM_FROM
        val bloom = durationOf(R.integer.motion_ob_bloom)
        animators += ObjectAnimator.ofFloat(shield, View.ALPHA, 1f).apply {
            duration = bloom
            interpolator = settle
        }
        animators += ObjectAnimator.ofFloat(shield, View.SCALE_X, 1f).apply {
            duration = bloom
            interpolator = settle
        }
        animators += ObjectAnimator.ofFloat(shield, View.SCALE_Y, 1f).apply {
            duration = bloom
            interpolator = settle
        }

        listOf(
            title to DELAY_TITLE,
            caption to DELAY_CAPTION,
            cardSlot to DELAY_FIRST_ACTION,
            buttonSlot to DELAY_FIRST_ACTION,
            primary to DELAY_SECOND_ACTION,
            moreButton to DELAY_THIRD_ACTION,
        ).forEach { (view, delay) ->
            view.alpha = 0f
            view.translationY = rise
            animators += ObjectAnimator.ofFloat(view, View.ALPHA, 1f).apply {
                duration = appear
                startDelay = delay
                interpolator = settle
            }
            animators += ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f).apply {
                duration = appear
                startDelay = delay
                interpolator = settle
            }
        }

        lifted.forEach { it.setLayerType(LAYER_TYPE_HARDWARE, null) }
        AnimatorSet().apply {
            playTogether(animators)
            doOnEnd { lifted.forEach { it.setLayerType(LAYER_TYPE_NONE, null) } }
            start()
        }
    }

    /**
     * §2 «Переключение состояний буфера»: блоки не подменяются, они едут высотой 340 мс на
     * ease-out-quart и прозрачностью 240 мс. Высота берётся измерением, а не из спецификации: при
     * крупном шрифте карточка выше 190dp, и жёсткое число обрезало бы ей подпись.
     */
    private fun setSlot(slot: View, expand: Boolean, animate: Boolean) {
        // Повторная отрисовка того же состояния не должна перезапускать анимацию: слот уже там,
        // где надо, и второй проход дал бы моргание на каждой перерисовке экрана.
        if (slot.isVisible == expand && slot.alpha == (if (expand) 1f else 0f)) return
        RunningAnimators.cancel(slot)
        slot.animate().cancel()
        if (!animate || reducedMotion()) {
            slot.isVisible = expand
            slot.alpha = if (expand) 1f else 0f
            setSlotHeight(slot, if (expand) LayoutParams.WRAP_CONTENT else 0)
            return
        }

        val from = if (slot.isVisible) slot.height else 0
        val target = if (expand) measureSlot(slot) else 0
        if (expand) {
            slot.visibility = View.VISIBLE
            setSlotHeight(slot, from)
        }
        slot.animate().alpha(if (expand) 1f else 0f)
            .setDuration(durationOf(R.integer.motion_ob_fade))
            .setInterpolator(curve(R.interpolator.ease_out_quart))
            .start()
        val height = ValueAnimator.ofInt(from, target).apply {
            duration = durationOf(R.integer.motion_expand)
            interpolator = curve(R.interpolator.ease_out_quart)
            addUpdateListener { setSlotHeight(slot, it.animatedValue as Int) }
            doOnEnd {
                if (expand) setSlotHeight(slot, LayoutParams.WRAP_CONTENT) else slot.isVisible = false
            }
        }
        RunningAnimators.set(slot, height)
    }

    private fun setSlotHeight(slot: View, height: Int) {
        slot.layoutParams = slot.layoutParams.apply { this.height = height }
    }

    private fun measureSlot(slot: View): Int {
        val width = width - paddingStart - paddingEnd
        if (width <= 0) return slot.height
        slot.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        return slot.measuredHeight
    }

    // ------------------------------------------------------------------ поток «из буфера»

    /**
     * §3, набор «Из буфера»: читаем буфер → нашли подписку → проверяем сервера → готово.
     * Шаги двигает работа, а не таймер; задержки между ними — нижний порог, чтобы шаг успели
     * прочитать, а не расписание.
     */
    private fun startClipboardFlow(link: String) {
        val activity = activityOrNull() ?: return
        if (flow != null) return
        holdHome(activity)
        val overlay = FlowOverlay.show(activity, FlowOverlay.Kind.CLIPBOARD)
        flow = overlay
        flowJob = activity.lifecycleScope.launch {
            delay(STEP_DWELL_MS)
            overlay.step(1)
            val added = withContext(Dispatchers.IO) {
                try {
                    val result = AngConfigManager.importBatchConfig(link, "", true)
                    result.count > 0 || result.countSub > 0
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Clipboard import failed", e)
                    false
                }
            }
            if (!added) {
                failFlow(R.string.notice_add_failed)
                return@launch
            }
            overlay.step(2)
            host?.onSubscriptionAdded()
            delay(STEP_DWELL_MS)
            handOff(overlay)
        }
    }

    // ------------------------------------------------------------------ поток «Telegram»

    /**
     * §3, набор «Telegram». Слой показывает состояния того же `AuthManager`, которым живёт
     * `AuthViewModel`: открываем Telegram → проверяем вход (когда пользователь вернулся) →
     * добавляем подписку → готово.
     */
    private fun startTelegramFlow() {
        val activity = activityOrNull() ?: return
        if (flow != null) return
        if (!BackendConfig.isConfigured()) {
            // Бэкенда нет — входить некуда; экран входа скажет это своими словами.
            openSite()
            return
        }
        holdHome(activity)
        telegramOpened = false
        val overlay = FlowOverlay.show(activity, FlowOverlay.Kind.TELEGRAM) { abortFlow() }
        flow = overlay
        flowJob = activity.lifecycleScope.launch {
            AuthManager().beginTelegramLogin().collect { state ->
                when (state) {
                    is LoginState.AwaitingTelegram -> openTelegram(state.deepLink)
                    is LoginState.Polling -> openTelegram(state.deepLink)
                    is LoginState.Success -> {
                        overlay.step(2)
                        host?.refreshSubscriptions()
                        delay(STEP_DWELL_MS)
                        handOff(overlay)
                    }

                    is LoginState.Error -> failFlow(
                        AuthViewModel.messageFor(state.error, awaitingTelegram = telegramOpened)
                    )

                    is LoginState.Idle, is LoginState.SiteLoading -> Unit
                }
            }
        }
    }

    /** Ссылка открывается один раз на токен: повторный `Polling` не должен снова прыгать в Telegram. */
    private fun openTelegram(deepLink: String) {
        if (telegramOpened || deepLink.isBlank()) return
        telegramOpened = true
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Telegram не установлен: тот же t.me открывается в браузере и завершает то же
            // подтверждение, поэтому опрос продолжается, а не падает.
            LogUtil.w(AppConfig.TAG, "Telegram is not installed, falling back to the browser", e)
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (second: ActivityNotFoundException) {
                LogUtil.w(AppConfig.TAG, "No browser to open the Telegram link", second)
                failFlow(R.string.auth_err_telegram_missing)
            }
        }
    }

    // ------------------------------------------------------------------ финал и срыв

    /**
     * Первая половина стыка: Главная ставится в предсборочное состояние ПОД уже поднятым слоем,
     * пока её никто не видит. Иначе она соберётся сама, за секунду с небольшим, и слой уйдёт с
     * готового экрана — это и есть грабля №6.
     *
     * Оболочка знает то, чего не знает вкладка: её флаг переживает пересоздание вьюх фрагмента
     * (поворот экрана посреди потока), поэтому, когда хозяин — `MainActivity`, держим через неё, а
     * [HomeHandoff] остаётся путём для любого другого хозяина.
     */
    private fun holdHome(activity: ComponentActivity) {
        val shell = activity as? MainActivity
        if (shell != null) shell.holdHomeEntrance() else HomeHandoff.prime()
    }

    /**
     * ГРАБЛЯ №6, вторая половина. Последний шаг, тост — и снятие слоя тем же кадром, что и запуск
     * сборки Главной: [FlowOverlay.finish] удаляет слой из иерархии и синхронно, следующей строкой,
     * зовёт этот колбэк. Между ними нет ни ожидания, ни поста, ни второго кадра.
     */
    private fun handOff(overlay: FlowOverlay) {
        val shell = activityOrNull() as? MainActivity
        overlay.finish {
            // Слой уже снят строкой выше — оболочке остаётся снять флаг и проиграть таблицу §3.
            if (shell != null) shell.revealHome { } else HomeHandoff.assemble()
        }
        flow = null
        telegramOpened = false
    }

    /** Пользователь нажал «назад» на слое прогрузки: поток брошен, экран возвращается как был. */
    private fun abortFlow() {
        flowJob?.cancel()
        flowJob = null
        flow?.cancel()
        flow = null
        telegramOpened = false
        releaseHome()
    }

    /**
     * Отпустить Главную, если поток кончился ничем. [holdHome] оставляет её в предсборочном
     * состоянии — с нулевой прозрачностью строки аккаунта, кнопки и списка, — и без этой строки
     * сорвавшийся поток оставил бы под собой пустой экран.
     */
    private fun releaseHome() {
        val shell = activityOrNull() as? MainActivity
        if (shell != null) shell.revealHome { } else HomeHandoff.assemble()
    }

    /**
     * Отказ. Слой уходит, а причина остаётся на экране подписью — тем же способом, каким гейт уже
     * показывает причину неудачной загрузки серверов. Ни тоста, ни диалога.
     */
    private fun failFlow(@StringRes message: Int) {
        flow?.cancel()
        flow = null
        flowJob = null
        telegramOpened = false
        releaseHome()
        caption.setText(message)
        caption.setTextColor(caption.themeColor(R.attr.colorDestructiveText))
    }

    // ------------------------------------------------------------------ мелочи

    private fun openSite() {
        val intent = Intent(context, LoginActivity::class.java)
            .putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_SITE)
        host?.openAuth(intent) ?: context.startActivity(intent)
    }

    private fun activityOrNull(): ComponentActivity? {
        var probe: Context? = context
        while (probe is android.content.ContextWrapper) {
            if (probe is ComponentActivity) return probe
            probe = probe.baseContext
        }
        return null
    }

    private companion object {
        /** §2: подъём при появлении — 26dp, как в прототипе. */
        const val LIFT_DP = 26f
        const val BLOOM_FROM = 0.7f

        const val DELAY_TITLE = 80L
        const val DELAY_CAPTION = 140L
        const val DELAY_FIRST_ACTION = 200L
        const val DELAY_SECOND_ACTION = 260L
        const val DELAY_THIRD_ACTION = 320L

        /** Нижний порог показа шага: меньше — и текст не успевают прочитать. */
        const val STEP_DWELL_MS = 900L

        const val MIN_LINK_LENGTH = 12
        val LINK_PREFIXES = listOf(
            "http://", "https://", "vmess://", "vless://", "trojan://", "ss://",
            "ssr://", "socks://", "hysteria://", "hysteria2://", "hy2://", "wireguard://", "wg://",
        )
    }
}
