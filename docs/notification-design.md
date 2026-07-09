# Connected-State Foreground Notification — Upgrade Design

Design-only document. No code is modified here. Target app: v2rayNG / Xray fork
"departament VPN", package `com.v2ray.ang`, sources under `/home/user/dp/V2rayNG`.

Goal: replace the current plain ongoing notification with an upgraded persistent
(foreground) notification for the **connected** state that shows:

- the connected **server name + country flag**,
- the connection **uptime** (live elapsed timer),
- live **↓ / ↑ speed**,
- an inline **ON/OFF quick-toggle** (connect / disconnect) button.

---

## 1. What exists today (studied)

Files read:
`app/src/main/java/com/v2ray/ang/handler/NotificationManager.kt`,
`core/CoreServiceManager.kt`, `service/CoreVpnService.kt`,
`service/CoreProxyOnlyService.kt`, `contracts/ServiceControl.kt`,
`util/NotificationHelper.kt`, `util/MessageUtil.kt`,
`dto/entities/ProfileItem.kt`, `AndroidManifest.xml`, `AppConfig.kt`.

Current behaviour of `NotificationManager` (a Kotlin `object`, single notification,
`NOTIFICATION_ID = 1`):

- `showNotification(currentConfig: ProfileItem?)` builds a
  `NotificationCompat.Builder` with:
  - `setSmallIcon(R.drawable.ic_stat_name)`,
  - `setContentTitle(currentConfig?.remarks)` — the server name, no flag,
  - `setPriority(PRIORITY_MIN)`, `setOngoing(true)`, `setShowWhen(false)`,
    `setOnlyAlertOnce(true)`,
  - content `PendingIntent` → `MainActivity`,
  - **two** actions: **Stop** and **Restart**, each a `getBroadcast` PendingIntent
    to `AppConfig.BROADCAST_ACTION_SERVICE` with extra `"key"` =
    `MSG_STATE_STOP (4)` / `MSG_STATE_RESTART (5)`, package-scoped to
    `AppConfig.ANG_PACKAGE`.
  - Then `service.startForeground(NOTIFICATION_ID, ...)`.
- `startSpeedNotification()` runs a coroutine every `QUERY_INTERVAL_MS = 3000ms`,
  calls `CoreServiceManager.queryAllOutboundTrafficStats()`, splits proxy vs direct,
  and calls `updateNotification(text, proxyTraffic, directTraffic)` which:
  - swaps the small icon by traffic (`ic_stat_name` / `ic_stat_proxy` /
    `ic_stat_direct`) above `NOTIFICATION_ICON_THRESHOLD`,
  - sets a two-line `BigTextStyle` (`proxy • up↑ down↓`, `direct • up↑ down↓`).
  - It also pushes live speed to the UI via
    `MessageUtil.sendMsg2UI(MSG_STATE_SPEED_UPDATE, longArrayOf(down, up))`.
- `createNotificationChannel()` creates channel `AppConfig.RAY_NG_CHANNEL_ID`
  ("v2rayNG Background Service") — it sets `IMPORTANCE_HIGH` then immediately
  overwrites `chan.importance = IMPORTANCE_NONE`. **`IMPORTANCE_NONE` suppresses the
  channel in the shade** (it survives only as the minimal FGS entry). See §4.
- The stop/restart actions are received in
  `CoreServiceManager.ReceiveMessageHandler.onReceive()` (registered on
  `BROADCAST_ACTION_SERVICE` in `doStartCoreLoop`, together with `SCREEN_ON/OFF`
  which start/stop the speed loop). `MSG_STATE_STOP` → `serviceControl.stopService()`;
  `MSG_STATE_RESTART` → stop, sleep 500ms, `startVService`.

Connection start point: `CoreServiceManager.doStartCoreLoop()` sets
`currentConfig = config`, calls `NotificationManager.showNotification(currentConfig)`,
then `coreController.startLoop(...)`, then `startSpeedNotification()`. This is the
natural place to capture `connectStartMillis`.

Manifest: `CoreVpnService` / `CoreProxyOnlyService` are
`android:foregroundServiceType="specialUse"` (subtype `vpn` / `proxy`);
`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` are
declared. `POST_NOTIFICATIONS` is already requested at runtime in `MainActivity`
(`checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS)`).

There is **no** existing country-flag helper in `Utils.kt`; the flag string is
produced by the separate *server-flags* design and consumed here as a ready string.

---

## 2. Notification layout — standard template vs custom RemoteViews

### Recommendation: keep the **standard template** (do not switch to RemoteViews)

The standard `NotificationCompat` template already gives us, for free and with full
OEM/theme/dark-mode compatibility:

- title line → **server name + flag**,
- the built-in **chronometer** (uptime) in the timestamp slot,
- content/BigText line → **↓/↑ speed**,
- inline **action buttons**.

Custom `RemoteViews` (`DecoratedCustomViewStyle`) would let us pin the flag as a real
`ImageView` drawable and fully control layout, but it costs us: manual dark/light
theming, per-OEM text-color bugs, larger maintenance surface, and it does **not**
automatically get the template chronometer (you must embed a `<Chronometer>` and drive
it via `RemoteViews.setChronometer(id, base, format, started)`). Given the required
fields map cleanly onto the standard template, RemoteViews is not worth the risk.
Keep it as a **Phase-2 option** only if a guaranteed graphical flag is mandated.

### Proposed standard-template layout

```
[small icon]  🇳🇱 Amsterdam-01                      ⏱ 01:23:45   <- setUsesChronometer
              ↓ 3.4 MB/s   ↑ 512 KB/s                            <- contentText / BigText
              [ ⏻ Disconnect ]   [ ↻ Restart ]                   <- actions
```

- **Title** (`setContentTitle`): `"$flag $remarks"` — flag string + server remarks.
- **Uptime**: `setWhen(connectStartMillis)` + `setUsesChronometer(true)` +
  `setShowWhen(true)`. The system renders a live MM:SS / H:MM:SS stopwatch in the
  timestamp slot and updates it itself — **no per-second push, no battery cost**
  (this is the recommended approach). Today the code does the opposite
  (`setShowWhen(false)` and no chronometer), so this is the key change.
  Optionally `setChronometerCountDown(false)` (default) to be explicit.
- **Speed**: keep the existing 3s coroutine (`startSpeedNotification` /
  `updateSpeedNotificationOnce`) but simplify the rendered string to a single
  compact line `"↓ {down}  ↑ {up}"` using the proxy figures already computed
  (`proxyDownlink/…`, `toSpeedString()`); optionally keep the 2-line BigText for the
  expanded view. Speed is the only field that needs periodic `notify()`.
- **Colorize** (optional): `setColorized(true)` + `setColor(brandColor)` reads as a
  premium "connected" state and is allowed for foreground-service ongoing
  notifications.

### Rendering the country flag reliably across OEMs

Two mechanisms, in order of preference:

1. **Emoji flag in text (recommended default).** The flag is two Unicode Regional
   Indicator symbols (e.g. `U+1F1F3 U+1F1F1` → 🇳🇱), produced by the server-flags
   design from the server's ISO-3166 country code. Zero assets, scales with system
   font, respects dark mode. **Caveat:** a minority of OEM/older fonts render regional
   indicators as bare letters ("NL") instead of a flag glyph. Mitigation: the
   server-flags helper must provide a **text fallback** — if no country is known, emit
   a neutral globe `🌐` or the ISO code in brackets, and never emit a broken glyph. The
   notification layer just consumes `flag: String` and concatenates; it does not decide
   the fallback.
2. **Small drawable flag (Phase-2, only with custom RemoteViews).** Bundle a compact
   flag sprite set (or generate the emoji into a `Bitmap` via `Paint.drawText` and set
   it as an `ImageView`/`setLargeIcon`). This guarantees a graphical flag even on fonts
   that lack regional indicators, at the cost of the RemoteViews trade-offs in §2.
   A lightweight middle path: render the emoji flag to a `Bitmap` once per connection
   and pass it to `setLargeIcon(bitmap)` while keeping the standard template — gives a
   guaranteed visual flag on the large-icon slot without full custom RemoteViews.

**Decision:** ship emoji-in-title (option 1) first; keep `setLargeIcon` emoji-bitmap
as a cheap reliability upgrade if field reports show OEMs mis-rendering the glyph.

---

## 3. The single ON/OFF toggle action

Because this notification only exists while the service is in the **connected**
(foreground) state, the inline toggle is functionally an **OFF / Disconnect** control:
tapping it turns the VPN off and the notification is then removed. There is no
"connect" state to toggle *from* inside this same notification (when disconnected there
is no ongoing notification). So:

- Replace today's two actions (**Stop** + **Restart**) with **one prominent primary
  action**: the ON→OFF toggle.
  - Icon: a power/toggle glyph (add e.g. `ic_power_24dp`; today the code reuses
    `ic_delete_24dp`, which reads wrong for a toggle).
  - Label: `"Disconnect"` (state = currently ON). A localized string, e.g.
    `R.string.notification_action_toggle_disconnect` = "Disconnect".
  - Reuse the **existing plumbing** verbatim: a `PendingIntent.getBroadcast` to
    `AppConfig.BROADCAST_ACTION_SERVICE`, package = `ANG_PACKAGE`,
    extra `"key" = AppConfig.MSG_STATE_STOP`, flags
    `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`, request code
    `NOTIFICATION_PENDING_INTENT_STOP_V2RAY`. This is already handled by
    `ReceiveMessageHandler` → `serviceControl.stopService()`. **No receiver change
    needed.**
- Keep **Restart** as an optional **secondary** action (unchanged
  `MSG_STATE_RESTART`) for users who want to re-dial without leaving the shade.

### True cross-state ON/OFF (design note)

If the product wants a single toggle that is present and flips label in **both**
connected and disconnected states, that cannot live on the FGS notification (which is
destroyed on disconnect). Two options, out of scope for this notification but worth
recording:

- Use the existing **Quick Settings tile** (`service/QSTileService.kt`) as the
  always-available on/off toggle — it already calls
  `CoreServiceManager.startVServiceFromToggle` / `stopVService`.
- Or add a *separate* low-priority, non-FGS "controller" notification on its own
  channel that persists across states with a `MSG_STATE_START` vs `MSG_STATE_STOP`
  toggle. `MSG_STATE_START` is already a defined constant; the receiver's
  `MSG_STATE_START` branch is currently a no-op and would need to call
  `startVService`. This is a larger change and is **not** required for the connected
  notification upgrade.

---

## 4. Android 13+ permission, FGS type, channel importance

- **POST_NOTIFICATIONS (Android 13 / API 33).** Already declared and already requested
  in `MainActivity`. If denied, the FGS still runs but the notification is not shown;
  behaviour is unchanged by this design. No new work beyond ensuring the request is
  triggered before/at first connect (it is).
- **Foreground-service type.** Keep `specialUse` (subtypes `vpn` / `proxy`) as
  declared — no change. This design does not add a new FGS type.
- **Channel importance — the one real fix.** The current channel is created with
  `IMPORTANCE_NONE`, which blocks it from the notification shade and can leave the
  ongoing notification effectively hidden. Change to **`IMPORTANCE_LOW`**:
  - stays **pinned** in the shade (visible, so the flag/uptime/speed/toggle are
    actually seen),
  - **low-noise**: no sound, no heads-up, no vibration,
  - keep `lockscreenVisibility = VISIBILITY_PRIVATE`.
  - Note: changing importance on an existing channel is ignored by the system after
    first creation, so either bump the channel id (e.g. `RAY_NG_CHANNEL_ID` → a new
    versioned id) or document that users who upgrade must clear data / re-grant. A new
    channel id is the clean path.
- **Ongoing / non-dismissable.** Keep `setOngoing(true)`. On Android 13 users can
  dismiss FGS notifications by default; on Android 14 `setOngoing(true)` again keeps
  most FGS notifications sticky. Add
  `setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)` so it
  appears instantly (no 10s grace delay) — appropriate for a user-initiated VPN
  connect.
- **Priority.** Raise `PRIORITY_MIN` → `PRIORITY_LOW` to match the channel and ensure
  the actions/flag render (MIN can collapse actions on some launchers).

---

## 5. Implementation plan (referencing real files/functions)

1. **`core/CoreServiceManager.kt`**
   - Add a field `connectStartMillis: Long` (or expose via a getter). Set it in
     `doStartCoreLoop()` right before/at `NotificationManager.showNotification(...)`,
     and pass it in (e.g. `showNotification(currentConfig, connectStartMillis)`).
   - Optionally add `fun getRunningFlag(): String` that consumes the server-flags
     design output for `currentConfig` (or pass the flag string through
     `showNotification`).
   - Receiver (`ReceiveMessageHandler`) needs **no change** — `MSG_STATE_STOP` already
     drives the toggle; keep `MSG_STATE_RESTART` for the optional secondary action.

2. **`handler/NotificationManager.kt`** (main changes)
   - `showNotification(currentConfig, connectStartMillis, flag)`:
     - title = `"$flag ${currentConfig?.remarks}"`,
     - `setWhen(connectStartMillis).setUsesChronometer(true).setShowWhen(true)`,
     - replace the two `addAction(...)` calls: one **Disconnect** toggle
       (`MSG_STATE_STOP`, new `ic_power_24dp`, `notification_action_toggle_disconnect`)
       + optional **Restart** (unchanged),
     - `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)`,
       `setPriority(PRIORITY_LOW)`; optional `setColorized(true)/setColor(...)`,
       optional `setLargeIcon(flagBitmap)`.
   - `createNotificationChannel()`: use a **new versioned channel id**, importance
     `IMPORTANCE_LOW`, keep `VISIBILITY_PRIVATE`, drop the `IMPORTANCE_NONE` override.
   - `updateSpeedNotificationOnce()` / `updateNotification()`: keep the 3s loop and
     traffic-based small-icon swap; render speed as compact `"↓ … ↑ …"`. Do **not**
     touch `setWhen`/chronometer here (system drives uptime). Preserve `setOnlyAlertOnce`.
   - Keep `NOTIFICATION_ID`, PendingIntent request codes, and
     `BROADCAST_ACTION_SERVICE` wiring as-is.

3. **Resources**
   - `res/values/strings.xml`: add `notification_action_toggle_disconnect`
     ("Disconnect"); keep `title_service_restart`.
   - `res/drawable/`: add `ic_power_24dp` (toggle icon).

4. **`AppConfig.kt`**: add the new versioned channel id constant (leave
   `RAY_NG_CHANNEL_ID` for back-compat/removal).

5. **Manifest**: no change (FGS types, `POST_NOTIFICATIONS` already present).

6. **Flag source dependency**: consumes the *server-flags* design's
   `country → emoji-flag (+text fallback)` helper; this doc treats `flag: String` as an
   input only.

**Battery note:** uptime is free (system chronometer); only the pre-existing 3s speed
loop calls `notify()`. The `SCREEN_ON/OFF` handling in `CoreServiceManager` already
pauses the speed loop when the screen is off, so nothing changes on that front.

---

## Sources

- [NotificationCompat.Builder (androidx) — setUsesChronometer / setWhen](https://androidx.de/androidx/core/app/NotificationCompat.Builder.html)
- [Beyond the Basics: Android Notifications (chronometer / colorized ongoing)](https://medium.com/design-bootcamp/beyond-the-basics-unlocking-the-full-potential-of-android-notifications-9d4f3704c405)
- [Foreground services overview — Android Developers](https://developer.android.com/guide/components/foreground-services)
- [How to Dismiss Running App Notifications in Android 13 (setOngoing behaviour)](https://www.geeksforgeeks.org/android/how-to-dismiss-running-app-notifications-in-android-13/)
- [Persisting FGS notifications on newer Android versions — Zebra Developer Portal](https://developer.zebra.com/blog/persisting-fgs-notifications-newer-android-versions)
