package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig

/**
 * «РОССИЙСКИЕ ПРИЛОЖЕНИЯ» — a NAMED, REVERSIBLE preset for «Прокси по приложениям» that sends
 * Russian apps AROUND the tunnel.
 *
 * Why it exists: Госуслуги, the banks, Mir Pay, СБП, the operators' self-care apps and the
 * marketplaces all refuse or degrade when they see a foreign exit address — an approval that never
 * arrives, a card that will not add, a self-care app that says the account does not exist. Routing
 * them direct is what makes the VPN usable in Russia rather than something to switch off five times
 * a day.
 *
 * THREE PROPERTIES ARE LOAD-BEARING, AND ALL THREE ARE THE OWNER'S:
 *
 * 1. **It is a preset, not a hidden list.** It has a name, it can be applied and un-applied from a
 *    switch, and every package it contains is visible on the screen it acts on — «Показать
 *    приложения набора» filters the list to exactly these, drawn with their real icons and names,
 *    and each one is still individually toggleable afterwards. A hard-coded list that silently
 *    edited the user's routing would be indistinguishable from a bug.
 * 2. **Un-applying gives back exactly what applying took.** [applyTo] records the packages it
 *    actually ADDED — not the whole preset — in [AppConfig.PREF_RU_BYPASS_PRESET_OWNED], and
 *    [removeFrom] removes only those. A подписка the user had ticked by hand before ever touching
 *    the preset survives un-applying it, because the preset never claimed it.
 * 3. **It never restarts the tunnel by itself.** Every write here goes through the view model's
 *    quiet path, which does NOT raise `SettingsChangeManager.makeRestartService()`. Applying a
 *    routing preset must not drop a live connection under the user — the desktop's equivalent does
 *    exactly that and it is a defect there. The change lands on the next connection, and the screen
 *    says so.
 *
 * ON THE PACKAGE NAMES. Every one below was verified against a store listing, not guessed: RuStore
 * (`rustore.ru/catalog/app/<package>` — the URL slug IS the application id, and RuStore is where
 * most of these live now that they are off Google Play) or Google Play where the app is still
 * listed there. A wrong package is silent — it matches nothing and simply does not route — so
 * guessing one is worse than omitting it. A package that is not installed costs nothing:
 * `CoreVpnService.configurePerAppProxy` skips it.
 */
object RussianAppsPreset {

    /**
     * The preset's contents, grouped by what they are for. Grouping is for the reader — the set is
     * flat at every call site.
     */
    val PACKAGES: List<String> = listOf(
        // Государство
        "ru.rostel",                                // Госуслуги
        "ru.sigma.gisgkh",                          // Госуслуги.Дом

        // Платёжные системы: НСПК, Mir Pay, СБП
        "ru.nspk.mirpay",                           // Mir Pay
        "ru.nspk.sbpay",                            // СБПэй
        "ru.nspk.mir.loyalty",                      // Привет! — акции СБП и «Мир»

        // Банки
        "ru.sberbankmobile",                        // СберБанк Онлайн
        "com.idamob.tinkoff.android",               // Т-Банк
        "ru.alfabank.mobile.android",               // Альфа-Банк
        "ru.vtb24.mobilebanking.android",           // ВТБ Онлайн
        "ru.ozon.fintech.finance",                  // Ozon Банк
        "ru.gazprombank.android.mobilebank.app",    // Газпромбанк
        "ru.gazprompay.android",                    // Gazprom Pay
        "logo.com.mbanking",                        // ПСБ
        "ru.mkb.mobile",                            // МКБ Онлайн
        "ru.rosbank.android.beta",                  // РОСБАНК Онлайн
        "ru.raiffeisennews",                        // Райффайзен Онлайн Банк Россия
        "com.openbank",                             // БМ-Банк (Открытие)
        "ru.sovcomcard.halva.v1",                   // Халва — Совкомбанк
        "ru.alfabank.oavdo.amc",                    // Альфа-Бизнес
        "ru.vtb.smb",                               // Бизнес Платформа ВТБ
        "ru.psbank.invest",                         // ПСБ Инвестиции

        // Маркетплейсы и доставка
        "ru.ozon.app.android",                      // OZON
        "com.wildberries.ru",                       // Wildberries
        "com.octopod.russianpost.client.android",   // Почта России

        // VK
        "com.vkontakte.android",                    // ВКонтакте
        "com.vk.mail",                              // VK Почта

        // Яндекс
        "ru.yandex.taxi",                           // Яндекс Go
        "ru.yandex.yandexmaps",                     // Яндекс Карты и Навигатор
        "ru.yandex.music",                          // Яндекс Музыка
        "ru.yandex.searchplugin",                   // Яндекс Старт
        "com.yandex.browser",                       // Яндекс Браузер

        // Операторы связи
        "ru.mts.mymts",                             // Мой МТС
        "ru.megafon.mlk",                           // МегаФон
        "ru.beeline.services",                      // билайн
        "ru.tele2.mytele2",                         // t2 (Теле2)
        "ru.yota.android",                          // Yota
        "ru.sber.telecom",                          // СберМобайл
        "ru.tinkoff.mvno",                          // Т-Мобайл
    )

    private val packageSet: Set<String> = PACKAGES.toSet()

    /** MMKV stores string sets as MutableSet, so every write below hands it a fresh mutable copy. */
    private fun Set<String>.forStore(): MutableSet<String> = HashSet(this)

    fun contains(packageName: String): Boolean = packageSet.contains(packageName)

    /** True when the preset is currently applied to the bypass selection. */
    fun isApplied(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_RU_BYPASS_PRESET_ON, false)

    /**
     * Adds every preset package that is not already selected, and remembers exactly which ones it
     * added so [removeFrom] can hand them back and touch nothing else.
     *
     * @param current the selection as it stands
     * @return the packages to ADD (may be empty when everything was already selected)
     */
    fun applyTo(current: Set<String>): Set<String> {
        val added = packageSet - current
        MmkvManager.encodeSettings(AppConfig.PREF_RU_BYPASS_PRESET_OWNED, added.forStore())
        MmkvManager.encodeSettings(AppConfig.PREF_RU_BYPASS_PRESET_ON, true)
        return added
    }

    /**
     * The packages to REMOVE when the preset is switched off: only what [applyTo] added.
     *
     * A package the user had chosen before applying the preset is not in the owned set, so it stays
     * — un-applying a preset must never take away a decision the user made themselves.
     */
    fun removeFrom(): Set<String> {
        val owned = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_RU_BYPASS_PRESET_OWNED)
            ?.toSet()
            .orEmpty()
        MmkvManager.encodeSettings(AppConfig.PREF_RU_BYPASS_PRESET_OWNED, HashSet<String>())
        MmkvManager.encodeSettings(AppConfig.PREF_RU_BYPASS_PRESET_ON, false)
        return owned
    }

    /**
     * DEFAULT-ON, ONCE, AND ONLY ON A CONFIGURATION NOBODY HAS TOUCHED.
     *
     * The owner asked for this to be on out of the box. "Out of the box" is the whole of the
     * condition: it seeds only when per-app proxy is off AND the selection is empty — an untouched
     * install. Anyone who has already chosen a mode or ticked an app has made a decision, and
     * flipping their routing to «Кроме выбранных» behind their back would destroy it. They get the
     * switch on the screen instead, off, waiting to be pressed.
     *
     * Runs at most once either way, so a user who turns the preset off does not find it back on at
     * the next launch.
     */
    fun seedOnFirstRun() {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_RU_BYPASS_PRESET_SEEDED, false)) return
        MmkvManager.encodeSettings(AppConfig.PREF_RU_BYPASS_PRESET_SEEDED, true)

        val perApp = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)
        val selection = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
        if (perApp || !selection.isNullOrEmpty()) return

        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, packageSet.forStore())
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, true)
        // «Кроме выбранных»: the selection is what goes AROUND the tunnel, which is the whole point.
        MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, true)
        MmkvManager.encodeSettings(AppConfig.PREF_RU_BYPASS_PRESET_OWNED, packageSet.forStore())
        MmkvManager.encodeSettings(AppConfig.PREF_RU_BYPASS_PRESET_ON, true)
    }
}
