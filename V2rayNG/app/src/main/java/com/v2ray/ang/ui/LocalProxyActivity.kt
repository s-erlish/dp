package com.v2ray.ang.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.Utils
import kotlin.random.Random

/**
 * «Локальный прокси» — экран настроек локального SOCKS5/HTTP прокси:
 * лимит памяти ядра, SOCKS5-авторизация (логин/пароль) и переключатели локального прокси.
 */
class LocalProxyActivity : BaseActivity() {

    private val memoryValues = intArrayOf(40, 60, 80, 100, 150)

    private lateinit var toggleMemory: MaterialButtonToggleGroup
    private lateinit var memoryButtons: Map<Int, MaterialButton>
    private lateinit var switchMemoryUnlimited: MaterialSwitch

    private lateinit var switchSocksAuth: MaterialSwitch
    private lateinit var groupSocksDetails: LinearLayout
    private lateinit var etSocksUser: EditText
    private lateinit var etSocksPass: EditText
    private lateinit var etSocksPort: EditText
    private lateinit var btnTogglePass: ImageButton

    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(
            R.layout.activity_local_proxy,
            showHomeAsUp = true,
            title = getString(R.string.title_local_proxy)
        )

        bindMemorySection()
        bindSocksSection()
        bindLocalProxySection()
    }

    // region ПАМЯТЬ
    private fun bindMemorySection() {
        toggleMemory = findViewById(R.id.toggle_memory)
        switchMemoryUnlimited = findViewById(R.id.switch_memory_unlimited)
        memoryButtons = mapOf(
            40 to findViewById(R.id.btn_mem_40),
            60 to findViewById(R.id.btn_mem_60),
            80 to findViewById(R.id.btn_mem_80),
            100 to findViewById(R.id.btn_mem_100),
            150 to findViewById(R.id.btn_mem_150),
        )

        val currentLimit = SettingsManager.getMemoryLimit()
        val selected = memoryButtons.keys.firstOrNull { it == currentLimit } ?: 100
        memoryButtons[selected]?.let { toggleMemory.check(it.id) }

        toggleMemory.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = memoryButtons.entries.firstOrNull { it.value.id == checkedId }?.key ?: return@addOnButtonCheckedListener
            MmkvManager.encodeSettings(AppConfig.PREF_MEMORY_LIMIT, value)
        }

        val limitEnabled = SettingsManager.isMemoryLimitEnabled()
        switchMemoryUnlimited.isChecked = !limitEnabled
        updateMemoryButtonsEnabled(limitEnabled)

        findViewById<View>(R.id.row_memory_unlimited).setOnClickListener {
            switchMemoryUnlimited.toggle()
        }
        switchMemoryUnlimited.setOnCheckedChangeListener { _, isChecked ->
            // Переключатель «Снять ограничение» инвертирует флаг включения лимита.
            MmkvManager.encodeSettings(AppConfig.PREF_MEMORY_LIMIT_ENABLED, !isChecked)
            updateMemoryButtonsEnabled(!isChecked)
        }
    }

    private fun updateMemoryButtonsEnabled(enabled: Boolean) {
        memoryButtons.values.forEach { it.isEnabled = enabled }
    }
    // endregion

    // region SOCKS5-АВТОРИЗАЦИЯ
    private fun bindSocksSection() {
        switchSocksAuth = findViewById(R.id.switch_socks_auth)
        groupSocksDetails = findViewById(R.id.group_socks_details)
        etSocksUser = findViewById(R.id.et_socks_user)
        etSocksPass = findViewById(R.id.et_socks_pass)
        etSocksPort = findViewById(R.id.et_socks_port)
        btnTogglePass = findViewById(R.id.btn_toggle_pass)

        val user = SettingsManager.getSocksUsername()
        val pass = SettingsManager.getSocksPassword()
        etSocksUser.setText(user ?: "")
        etSocksPass.setText(pass ?: "")
        etSocksPort.setText(
            MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT) ?: AppConfig.PORT_SOCKS
        )

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
        etSocksPort.doAfterTextChanged {
            MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_PORT, it?.toString()?.trim() ?: "")
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
        // «Блокировать UDP» инвертирует PREF_SOCKS_ENABLE_UDP (true = UDP разрешён).
        bindSwitchRow(
            R.id.row_block_udp,
            R.id.switch_block_udp,
            !MmkvManager.decodeSettingsBool(AppConfig.PREF_SOCKS_ENABLE_UDP, true)
        ) { checked ->
            MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_ENABLE_UDP, !checked)
        }

        bindSwitchRow(
            R.id.row_http_auth,
            R.id.switch_http_auth,
            MmkvManager.decodeSettingsBool(AppConfig.PREF_APPEND_HTTP_PROXY, false)
        ) { checked ->
            MmkvManager.encodeSettings(AppConfig.PREF_APPEND_HTTP_PROXY, checked)
        }

        bindSwitchRow(
            R.id.row_hide_icon,
            R.id.switch_hide_icon,
            MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
        ) { checked ->
            MmkvManager.encodeSettings(AppConfig.PREF_ENABLE_LOCAL_PROXY, checked)
        }

        bindSwitchRow(
            R.id.row_hotspot,
            R.id.switch_hotspot,
            MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING, false)
        ) { checked ->
            MmkvManager.encodeSettings(AppConfig.PREF_PROXY_SHARING, checked)
        }

        bindSwitchRow(
            R.id.row_route_domain,
            R.id.switch_route_domain,
            MmkvManager.decodeSettingsBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false)
        ) { checked ->
            MmkvManager.encodeSettings(AppConfig.PREF_ROUTE_ONLY_ENABLED, checked)
        }
    }

    private fun bindSwitchRow(rowId: Int, switchId: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val row = findViewById<View>(rowId)
        val sw = findViewById<MaterialSwitch>(switchId)
        sw.isChecked = initial
        row.setOnClickListener { sw.toggle() }
        sw.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
    }
    // endregion
}
