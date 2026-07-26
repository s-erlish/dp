# What still needs doing after the screens land

The screen wave owns `MainActivity.kt`, the tab fragments, and the account and billing files. Several
confirmed defects live in exactly those files, so they could not be fixed in parallel — they were
deferred, not dropped. This file is the queue, with the addresses, so nothing is lost when the wave that
reported them is gone.

Ids are from `docs/agents/bugs-android-confirmed.md`. Verify each against the code first: the screen
wave may have fixed some incidentally while rebuilding those files.

## 1. Deferred defects — user-visible, all in the screen wave's files

| # | What the user sees | Where |
|---|---|---|
| **D10** | Taps «Оплатить» on a slow connection, nothing changes, taps again, **is charged twice**. `progress_buy` is bound and hidden in four places and never shown anywhere. | `ui/BuyTariffActivity.kt` |
| **D11** | Rotating the phone with the payment-method sheet open, then picking a method, **crashes the app** — the parked lambda captures the destroyed fragment. | `ui/PaymentMethodSheet.kt`, `ui/AccountFragment.kt` |
| **D09** | After a payment error every tariff card goes neutral while the checkout card still shows a live «Оплатить» for the now-invisible selection, and re-tapping the same tariff does nothing. | `ui/BuyTariffActivity.kt` |
| **D06** | Rapid taps on the connect control issue a second start and push the 20 s deadline out; there is no way to cancel a connect. `R.id.tag_last_click` was declared for this guard and is still unused (**D24**). | `ui/MainActivity.kt`, `res/values/ids.xml` |
| **D07** | Connect with no server selected: a toast about choosing a config file **and** a spinner, then «Не удалось подключиться» 20 s later. Neither says "you have no server". | `ui/MainActivity.kt` |
| **D08** | Cancelling Android's own VPN permission dialog is reported as a connection **failure** 20 s later. A cancelled action is not a failure. | `ui/MainActivity.kt` |
| **D15** | Typing in the servers search empties Главная into the onboarding state and **hides the whole bottom navigation**, because a filtered-to-zero list is indistinguishable from "no servers". Gate on the unfiltered stored count — `MmkvManager.decodeAllServerList()` is the unfiltered truth; `serversCache` must stay the Servers-tab list only. | `ui/MainActivity.kt` (`updateBottomNavVisibility`, `applyHomeListVisibility`) |
| **D03** | Switching tabs quickly strands the highlight under one tab while another tab's content is on screen; for a signed-in user, stranding Аккаунт leaves the app showing it permanently. | `ui/MainActivity.kt` (`showTab`, `selectNav`) |
| **D12** | Signing out leaves the account fragment attached, still collecting and still issuing authenticated calls with a dead session; after a recreate a second instance can be built alongside the restored one. | `ui/MainActivity.kt` (`applyAccountState`) |
| **D13** | Back from a tab **minimises the app instead of returning to Главная — but only on Android 14 and below**, so the same build behaves differently on two phones. Gamepad B minimises on every version, and the manifest declares a leanback launcher. | `ui/MainActivity.kt` (`onKeyDown`), `AndroidManifest.xml` |
| **D16** | The RAM panel can never appear: `PREF_SHOW_MEMORY` has a reader and no writer. The settings spec deliberately dropped it as a user setting, so the decision belongs to whoever owns Главная: either the card earns a product rule or it goes. | `ui/MainActivity.kt`, Главная |
| **D21** | Dead `R.id.sub_update` branch for a menu the screen never inflates. | `ui/MainActivity.kt` |
| **D22** | Dead «Привязать Telegram» banner: doubly unreachable, with two orphan strings. Delete rather than repair — a live entry point already exists in the onboarding card. | `ui/MainActivity.kt`, `res/layout/layout_home_account.xml`, `res/values/strings_nav.xml` |

Second half of **D02**, reported as out-of-group: `applyAccountState` must call
`mainViewModel.reloadServerList()` on every account-state change, or on an explicit logout the
on-screen list outlives the store. And the WebDAV read-then-rewrite around `removeAllServer` in
`delAllConfig` is now redundant — `MmkvManager` no longer clears the shared store — so it can go.

## 2. Work nobody has been given yet

- **Enforce the copy register in code, on both platforms.** `docs/design2026/42-copy-register.md` decides
  one approved Russian wording per concept and maps it to both platforms' resource keys. Nothing has
  applied it yet, so the two clients still word the same concept differently. Includes the plural sets
  and the format-specifier table — a specifier that disagrees with its source crashes at runtime.
- **Act on the release-readiness findings.** `release-android.md` and `release-desktop.md` assess only;
  they change nothing. The R8 keep rules matter most: this app reflects across Gson DTOs, MMKV, a
  Go-generated JNI surface and WorkManager, and a green debug build says nothing about any of them.
- **The unassigned-work list**, once the document sweep produces `UNASSIGNED-WORK.md`.
- **The desktop's three Главная holes**, left by the owner's decision not to add a Серверы tab: there is
  no server search on desktop at all, the per-server actions hide in a right-click menu, and in compact
  mode the list sits below the fold under a 440px-minimum hero.
- **Final audit.** Re-run the state audit's method against the finished screens: for every setting a
  write and a read, for every feature an entry point a user can tap, for every spec rule a consumer.

## 3. Standing constraints

- **One wave per platform at a time.** The build is shared, so a second wave's gate fails on the first
  wave's half-written file and blames the wrong change. Android and the desktop may overlap.
- **Re-record a warning baseline only from a full build** (`--rerun-tasks` / `--no-incremental`). An
  incremental run emits only the recompiled subset's warnings, and a baseline taken from one makes the
  gate accuse whoever builds next.
- **An APK built in this environment cannot be tested.** The native `libv2ray.aar` is not in the
  repository; a type-check stub stands in, and it returns placeholder values. Runtime testing needs a CI
  build, which downloads the real core.
