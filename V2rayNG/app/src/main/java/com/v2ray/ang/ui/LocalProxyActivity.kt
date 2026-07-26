package com.v2ray.ang.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.Utils
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

/**
 * «Локальный прокси» — только то, что и есть локальный прокси: инбаунды SOCKS5/HTTP на
 * 127.0.0.1, их порт, логин с паролем для доступа из локальной сети и сам доступ по сети
 * (хотспот).
 *
 * Что отсюда ушло и почему — владелец: «там куча настроек которые не связаны никак с
 * локальный прокси, они должны быть в другом месте».
 *
 *  - **Лимит памяти** (`PREF_MEMORY_LIMIT`, `PREF_MEMORY_LIMIT_ENABLED`) убран. Его читал
 *    только этот экран, чтобы нарисовать сам себя: применить его неоткуда, потому что
 *    готовая сборка libv2ray не отдаёт наружу ни одного сеттера памяти — это записано в
 *    `CoreServiceManager.startCoreLoop` рядом с местом, где ограничение применялось бы.
 *    Пять чипов и переключатель «Снять ограничение» ничего не меняли в работе ядра.
 *  - **«Маршрутизация по домену»** (`PREF_ROUTE_ONLY_ENABLED`) переехала в «Дополнительно»
 *    -> «Ядро», где она называется «Только для маршрутизации» и стоит под строкой
 *    «Определение домена в трафике», от которой зависит. Это был второй дом одной и той же
 *    настройки; теперь дом один (`res/xml/pref_settings.xml`).
 *
 * Переключатель, который назывался «Скрыть значок (только прокси)», никогда не прятал
 * значок: он пишет `PREF_ENABLE_LOCAL_PROXY`, а его читает
 * `CoreConfigManager.configureInbounds` — при выключенном значении из конфига ядра убираются
 * инбаунды socks и http. Поэтому строка называется тем, чем она управляет.
 */
class LocalProxyActivity : BaseActivity() {

    private lateinit var switchSocksAuth: MaterialSwitch
    private lateinit var groupSocksDetails: LinearLayout
    private lateinit var etSocksUser: EditText
    private lateinit var etSocksPass: EditText
    private lateinit var etSocksPort: EditText
    private lateinit var btnTogglePass: ImageButton

    private lateinit var switchHotspot: MaterialSwitch
    private lateinit var groupHotspotDetails: LinearLayout
    private lateinit var etHotspotEndpoint: EditText
    private lateinit var etHotspotUser: EditText
    private lateinit var etHotspotPass: EditText
    private lateinit var btnToggleHotspotPass: ImageButton

    private var passwordVisible = false
    private var hotspotPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(
            R.layout.activity_local_proxy,
            showHomeAsUp = true,
            title = getString(R.string.title_local_proxy)
        )

        bindSocksSection()
        bindLocalProxySection()
    }

    // region SOCKS5-АВТОРИЗАЦИЯ
    private fun bindSocksSection() {
        switchSocksAuth = findViewById(R.id.switch_socks_auth)
        groupSocksDetails = findViewById(R.id.group_socks_details)
        etSocksUser = findViewById(R.id.et_socks_user)
        etSocksPass = findViewById(R.id.et_socks_pass)
        btnTogglePass = findViewById(R.id.btn_toggle_pass)

        val user = SettingsManager.getSocksUsername()
        val pass = SettingsManager.getSocksPassword()
        etSocksUser.setText(user ?: "")
        etSocksPass.setText(pass ?: "")

        val authOn = !user.isNullOrEmpty() && !pass.isNullOrEmpty()
        switchSocksAuth.isChecked = authOn
        groupSocksDetails.visibility = if (authOn) View.VISIBLE else View.GONE

        findViewById<View>(R.id.row_socks_auth).setOnClickListener { switchSocksAuth.toggle() }
        switchSocksAuth.setOnCheckedChangeListener { _, isChecked ->
            groupSocksDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                if (etSocksUser.text.isNullOrBlank() || etSocksPass.text.isNullOrBlank()) {
                    generateAndFillCreds()
                }
            } else {
                // Отключение авторизации: очищаем сохранённые креды (ядро перейдёт на noauth).
                MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_USERNAME, "")
                MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_PASSWORD, "")
            }
        }

        etSocksUser.doAfterTextChanged {
            MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_USERNAME, it?.toString()?.trim() ?: "")
        }
        etSocksPass.doAfterTextChanged {
            MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_PASSWORD, it?.toString() ?: "")
        }

        btnTogglePass.setOnClickListener { togglePasswordVisibility() }
        findViewById<ImageButton>(R.id.btn_copy_user).setOnClickListener {
            Utils.setClipboard(this, etSocksUser.text.toString())
            toastSuccess(R.string.lp_copied)
        }
        findViewById<ImageButton>(R.id.btn_copy_pass).setOnClickListener {
            Utils.setClipboard(this, etSocksPass.text.toString())
            toastSuccess(R.string.lp_copied)
        }
        findViewById<MaterialButton>(R.id.btn_reset_creds).setOnClickListener {
            generateAndFillCreds()
            toastSuccess(R.string.lp_creds_reset)
        }
    }

    private fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
        etSocksPass.inputType = if (passwordVisible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        etSocksPass.setSelection(etSocksPass.text?.length ?: 0)
        btnTogglePass.setImageResource(
            if (passwordVisible) R.drawable.ic_lp_eye_off else R.drawable.ic_lp_eye
        )
        btnTogglePass.contentDescription =
            getString(if (passwordVisible) R.string.lp_hide_password else R.string.lp_show_password)
    }

    private fun generateAndFillCreds() {
        // Логин: dep_ + 6 hex; пароль: 12 hex. Изменение полей сохраняется через doAfterTextChanged.
        etSocksUser.setText("dep_" + randomHex(6))
        etSocksPass.setText(randomHex(12))
    }

    private fun randomHex(length: Int): String {
        val chars = "0123456789abcdef"
        return buildString(length) {
            repeat(length) { append(chars[Random.nextInt(chars.length)]) }
        }
    }
    // endregion

    // region ЛОКАЛЬНЫЙ ПРОКСИ
    private fun bindLocalProxySection() {
        // Порт петлевого прокси. Поле жило внутри group_socks_details и показывалось только
        // при включённой SOCKS5-авторизации, то есть при значении по умолчанию порт локального
        // прокси нельзя было ни увидеть, ни поменять. Читает SettingsManager.getSocksPort().
        etSocksPort = findViewById(R.id.et_socks_port)
        etSocksPort.setText(
            MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT) ?: AppConfig.PORT_SOCKS
        )
        etSocksPort.doAfterTextChanged {
            MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_PORT, it?.toString()?.trim() ?: "")
        }

        // Мастер-строка группы: читает CoreConfigManager.configureInbounds — при выключенном
        // значении инбаунды socks и http вырезаются из конфига ядра.
        bindSwitchRow(
            R.id.row_local_proxy,
            R.id.switch_local_proxy,
            MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
        ) { checked ->
            MmkvManager.encodeSettings(AppConfig.PREF_ENABLE_LOCAL_PROXY, checked)
        }

        // Прямой смысл, без двойного отрицания: включено = UDP идёт через прокси
        // (inbound.settings.udp в CoreConfigManager). Хранимое поле то же самое.
        bindSwitchRow(
            R.id.row_udp,
            R.id.switch_udp,
            MmkvManager.decodeSettingsBool(AppConfig.PREF_SOCKS_ENABLE_UDP, true)
        ) { checked ->
            MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_ENABLE_UDP, checked)
        }

        // Читает CoreVpnService: Builder.setHttpProxy() на Android Q и выше, то есть система
        // сообщает приложениям HTTP-прокси туннеля. Авторизации тут никогда не было.
        bindSwitchRow(
            R.id.row_http_proxy,
            R.id.switch_http_proxy,
            MmkvManager.decodeSettingsBool(AppConfig.PREF_APPEND_HTTP_PROXY, false)
        ) { checked ->
            MmkvManager.encodeSettings(AppConfig.PREF_APPEND_HTTP_PROXY, checked)
        }

        bindHotspotSection()
    }

    private fun bindSwitchRow(rowId: Int, switchId: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val row = findViewById<View>(rowId)
        val sw = findViewById<MaterialSwitch>(switchId)
        sw.isChecked = initial
        row.setOnClickListener { sw.toggle() }
        sw.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
    }
    // endregion

    // region ДОСТУП ЧЕРЕЗ ХОТСПОТ (ручной SOCKS5-прокси в LAN)
    private fun bindHotspotSection() {
        switchHotspot = findViewById(R.id.switch_hotspot)
        groupHotspotDetails = findViewById(R.id.group_hotspot_details)
        etHotspotEndpoint = findViewById(R.id.et_hotspot_endpoint)
        etHotspotUser = findViewById(R.id.et_hotspot_user)
        etHotspotPass = findViewById(R.id.et_hotspot_pass)
        btnToggleHotspotPass = findViewById(R.id.btn_toggle_hotspot_pass)

        val enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING, false)
        switchHotspot.isChecked = enabled
        groupHotspotDetails.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            // Ядро включит LAN-инбаунд только с авторизацией: гарантируем наличие кред.
            SettingsManager.ensureSocksShareCredentials()
        }
        refreshHotspotEndpoint()

        findViewById<View>(R.id.row_hotspot).setOnClickListener { switchHotspot.toggle() }
        switchHotspot.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_PROXY_SHARING, isChecked)
            groupHotspotDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                // Включение с пустыми кредами — сгенерировать, сохранить и показать пароль.
                SettingsManager.ensureSocksShareCredentials()
                refreshHotspotEndpoint()
                setHotspotPasswordVisible(true)
            }
        }

        btnToggleHotspotPass.setOnClickListener { setHotspotPasswordVisible(!hotspotPasswordVisible) }
        findViewById<ImageButton>(R.id.btn_copy_hotspot_endpoint).setOnClickListener {
            Utils.setClipboard(this, etHotspotEndpoint.text.toString())
            toastSuccess(R.string.lp_copied)
        }
        findViewById<ImageButton>(R.id.btn_copy_hotspot_user).setOnClickListener {
            Utils.setClipboard(this, etHotspotUser.text.toString())
            toastSuccess(R.string.lp_copied)
        }
        findViewById<ImageButton>(R.id.btn_copy_hotspot_pass).setOnClickListener {
            Utils.setClipboard(this, etHotspotPass.text.toString())
            toastSuccess(R.string.lp_copied)
        }
    }

    private fun refreshHotspotEndpoint() {
        val ip = resolveLanIpv4()
        val port = SettingsManager.getSocksSharePort()
        etHotspotEndpoint.setText(
            if (ip != null) "SOCKS5  $ip:$port" else getString(R.string.lp_hotspot_no_ip)
        )
        etHotspotUser.setText(SettingsManager.getSocksUsername() ?: "")
        etHotspotPass.setText(SettingsManager.getSocksPassword() ?: "")
        // Пароль повторно маскируется при каждом обновлении полей.
        setHotspotPasswordVisible(hotspotPasswordVisible)
    }

    private fun setHotspotPasswordVisible(visible: Boolean) {
        hotspotPasswordVisible = visible
        etHotspotPass.inputType = if (visible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        btnToggleHotspotPass.setImageResource(
            if (visible) R.drawable.ic_lp_eye_off else R.drawable.ic_lp_eye
        )
        btnToggleHotspotPass.contentDescription =
            getString(if (visible) R.string.lp_hide_password else R.string.lp_show_password)
    }

    /**
     * Resolve the phone's own Wi-Fi/hotspot IPv4 address by enumerating up, non-loopback
     * interfaces (read-only, needs no extra permission). Prefers wlan/ap/swlan; falls back to
     * any site-local IPv4. Returns null when there is no usable LAN address.
     */
    private fun resolveLanIpv4(): String? {
        return try {
            val candidates = mutableListOf<Pair<String, String>>() // ifaceName to ip
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                val name = nif.name?.lowercase().orEmpty()
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val host = addr.hostAddress ?: continue
                        candidates.add(name to host)
                    }
                }
            }
            candidates.firstOrNull {
                it.first.startsWith("wlan") || it.first.startsWith("ap") || it.first.startsWith("swlan")
            }?.second ?: candidates.firstOrNull()?.second
        } catch (e: Exception) {
            null
        }
    }
    // endregion
}
