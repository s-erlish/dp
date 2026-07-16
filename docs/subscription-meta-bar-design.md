# Subscription Meta Bar — Design

A header bar rendered above each subscription's server list (one per group tab), showing the
subscription **title**, a **ping** button, a **refresh/update** button, a **traffic-used progress
bar** (used vs total, with correct unlimited/infinite handling), and the **expiry/reset** date.
Everything is interdependent: refresh re-fetches the sub → updates traffic + expiry → repaints the
bar; ping tests that subscription's servers; the bar always reflects the latest
`subscription-userinfo` response header.

Target: `com.v2ray.ang` (v2rayNG/Xray fork, Kotlin, XML Material3 views).

---

## 0. Background — the `subscription-userinfo` header

Remnawave / 3x-ui / Marzban / Happ / Hiddify and most panels return traffic + expiry metadata in a
single HTTP **response header** on the subscription URL:

```
subscription-userinfo: upload=4520000000; download=210000000000; total=536870912000; expire=1749954800
```

- **Format**: `key=value` pairs separated by `;` (whitespace after `;` is optional). All four keys
  are optional — a panel may send only `expire`, or omit the header entirely. Values are decimal
  integers.
  ([v2raytun docs](https://docs.v2raytun.com/overview/supported-headers),
  [Hiddify URL-Scheme wiki](https://github.com/hiddify/hiddify-app/wiki/URL-Scheme))
- **`upload`, `download`**: bytes consumed (upload + download counted separately).
- **`total`**: total quota in **bytes**.
- **`expire`**: **Unix epoch seconds** at which the subscription expires (also used by many panels as
  the reset/renewal date for monthly-reset plans).

**Conventions clients follow** (the docs leave these implicit; these are the de-facto rules used by
Hiddify, v2rayN, Nekobox, Streisand, v2raytun when rendering the header):

| Case | Meaning | Bar behaviour |
|------|---------|---------------|
| `total` missing or `total == 0` | **Unlimited / infinite** quota | Show used only, no bar (or an `∞` indicator) |
| `total > 0` | Finite quota | Progress bar = `used / total`, clamped to `[0,1]` |
| `expire` missing or `expire == 0` | No expiry | Hide expiry line |
| `expire > 0` | Expiry/reset date | Show date; if `expire < now`, show **Expired** state |
| header absent entirely | Panel sends no metadata | Hide the whole traffic/expiry region; keep title + buttons |

- **Used traffic** = `upload + download` (matches Hiddify / v2rayN display). Some panels only fill
  `download`; summing is safe because the unused field is `0`.
- **Base64**: the raw header is used as-is. Unlike `profile-title`, no `base64:` prefix variant is
  standardised for `subscription-userinfo`, so we parse the raw string only.

---

## 1. Current state in this fork (the gap)

- **`dto/entities/SubscriptionItem.kt`** — stores `remarks`, `url`, `enabled`, `lastUpdated`,
  `autoUpdate`, `updateInterval`, filters, `userAgent`, etc. **No** traffic/expiry fields.
- **`util/HttpUtil.getUrlContentWithUserAgent(UrlContentRequest): String`** — returns **only the
  response body string**; the OkHttp `Response` (and therefore its headers) is discarded inside a
  `.use { }`. This is the root gap: `subscription-userinfo` is never read.
- **`handler/AngConfigManager.updateConfigViaSub(SubscriptionCache)`** (lines ~527–606) — fetches
  the body via `getUrlContentWithUserAgent`, parses configs, and on success sets
  `it.subscription.lastUpdated` and calls `MmkvManager.encodeSubscription(guid, subscription)`. This
  is the single choke point where the header must be captured and persisted.
- **`extension/_Ext.kt`** — already provides `fun Long.toTrafficString()` (B/KB/MB/GB/TB/PB, 1 dp).
  Reuse it; do **not** add a new formatter.
- **`viewmodel/SubscriptionsViewModel`** holds the tab list; the **per-tab** server screen is
  `MainViewModel` (scoped by `subscriptionId`), which already owns ping + update flows.
- **UI**: `ui/GroupServerFragment` + `res/layout/fragment_group_server.xml` (a `FrameLayout` →
  `SwipeRefreshLayout` → `RecyclerView`). No header region exists yet.

**Conclusion**: the fork stores none of the four fields and throws away response headers. We add the
fields, capture the header at one point, and render a new bar.

---

## 2. Data model

### 2.1 New fields on `SubscriptionItem`

`dto/entities/SubscriptionItem.kt` (append; all defaulted so existing persisted JSON deserialises
unchanged — `JsonUtil.fromJsonSafe` tolerates missing keys):

```kotlin
data class SubscriptionItem(
    // ... existing fields unchanged ...

    // --- subscription-userinfo metadata (bytes / epoch-seconds) ---
    var uploadUsed: Long = 0,       // bytes, from header `upload`
    var downloadUsed: Long = 0,     // bytes, from header `download`
    var totalTraffic: Long = 0,     // bytes, from header `total`; 0 == unlimited
    var expire: Long = 0,           // epoch SECONDS, from header `expire`; 0 == no expiry
    var userInfoUpdated: Long = 0,  // epoch millis, when metadata was last captured (for "as of" / staleness)
)
```

Derived helpers (put as extension/computed props on `SubscriptionItem`, e.g. in `_Ext.kt` or a small
`SubscriptionItemExt.kt`; keep the data class a plain POJO for JSON):

```kotlin
val SubscriptionItem.usedTraffic: Long get() = uploadUsed + downloadUsed
val SubscriptionItem.isUnlimited: Boolean get() = totalTraffic <= 0L
val SubscriptionItem.hasExpiry: Boolean get() = expire > 0L
val SubscriptionItem.isExpired: Boolean get() = expire in 1 until (System.currentTimeMillis() / 1000)
/** 0f..1f, only meaningful when !isUnlimited */
val SubscriptionItem.trafficFraction: Float
    get() = if (isUnlimited) 0f else (usedTraffic.toFloat() / totalTraffic).coerceIn(0f, 1f)
/** true when the header carried at least one meaningful field */
val SubscriptionItem.hasUserInfo: Boolean get() = usedTraffic > 0 || totalTraffic > 0 || expire > 0
```

### 2.2 Parsing the header

New pure parser (unit-testable, no Android deps) in `util/SubscriptionUserInfo.kt`:

```kotlin
data class SubscriptionUserInfo(
    val upload: Long = 0, val download: Long = 0,
    val total: Long = 0, val expire: Long = 0,
) {
    companion object {
        /** Parse "upload=..; download=..; total=..; expire=.." (any subset). Returns null if nothing usable. */
        fun parse(raw: String?): SubscriptionUserInfo? {
            if (raw.isNullOrBlank()) return null
            val map = raw.split(';')
                .mapNotNull { part ->
                    val i = part.indexOf('='); if (i <= 0) return@mapNotNull null
                    val k = part.substring(0, i).trim().lowercase()
                    val v = part.substring(i + 1).trim().toLongOrNull() ?: return@mapNotNull null
                    k to v
                }.toMap()
            if (map.isEmpty()) return null
            return SubscriptionUserInfo(
                upload = map["upload"] ?: 0, download = map["download"] ?: 0,
                total = map["total"] ?: 0, expire = map["expire"] ?: 0,
            )
        }
    }
}
```

### 2.3 Capturing the header during a sub update

Two-part change so headers stop being discarded:

1. **`util/HttpUtil`** — add a variant that returns body **and** the header (leave the existing
   `getUrlContentWithUserAgent` untouched for other callers, or make it delegate):

   ```kotlin
   data class UrlContentResult(val body: String, val headers: Map<String, String>)

   @Throws(IOException::class)
   fun getUrlContentWithUserAgentEx(request: UrlContentRequest): UrlContentResult {
       // identical redirect/proxy logic to getUrlContentWithUserAgent, but on the successful
       // branch also read: response.header("subscription-userinfo")
       // return UrlContentResult(body, mapOf("subscription-userinfo" to (userInfo ?: "")))
   }
   ```

   (Header names are case-insensitive in OkHttp's `Response.header()`.)

2. **`handler/AngConfigManager.updateConfigViaSub`** — call the `Ex` variant, and after a successful
   parse (the existing `count > 0` block, where `lastUpdated` is already set) merge the header into
   the persisted item **before** the single `encodeSubscription` call:

   ```kotlin
   SubscriptionUserInfo.parse(result.headers["subscription-userinfo"])?.let { info ->
       it.subscription.uploadUsed = info.upload
       it.subscription.downloadUsed = info.download
       it.subscription.totalTraffic = info.total
       it.subscription.expire = info.expire
       it.subscription.userInfoUpdated = System.currentTimeMillis()
   }
   it.subscription.lastUpdated = System.currentTimeMillis()
   MmkvManager.encodeSubscription(it.guid, it.subscription)
   ```

   Absent header → fields keep their prior values (do not zero them on a header-less refresh). This
   covers both the manual path (`MainViewModel.updateConfigViaSubAll`) and the auto path
   (`SubscriptionUpdater.UpdateTask` → `updateConfigViaSub`) with one edit.

### 2.4 Formatting

- Bytes → `Long.toTrafficString()` (existing). Display: `"$used / $total"`, e.g. `"214.6 GB / 500.0 GB"`.
- Unlimited: `"$used / ∞"` (`used.toTrafficString() + " / ∞"`), no numeric bar.
- Expiry: format `expire * 1000` with a `SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())`;
  optionally append days-left (`(expire - now/1000) / 86400`). Show `R.string.sub_expired` when
  `isExpired`.

---

## 3. UI spec

### 3.1 New layout — `res/layout/layout_subscription_meta_bar.xml`

A card mounted above the list. Vertical `LinearLayout` (a `MaterialCardView` or a
`bg_server_card` background, matching `item_recycler_main.xml`). Two rows:

**Row 1 — title + actions** (horizontal, `center_vertical`):
- `tv_sub_title` — `TextView`, `weight=1`, `maxLines=1`, `ellipsize=end`,
  `textColor="?attr/colorOnSurface"`, bold ~15sp. Bound to `subscription.remarks`.
- `progress_action` — small indeterminate `ProgressBar` (24dp), `visibility=gone`, shown while
  refreshing or pinging.
- `btn_ping` — borderless `ImageView`/`MaterialButton` icon (`?attr/selectableItemBackgroundBorderless`,
  8dp pad, 20dp icon, `ic_speedometer`/existing ping icon), `contentDescription` = ping.
- `btn_refresh` — same style, `ic_refresh` (reuse `ic_refresh` / `ic_more` set), `contentDescription`
  = update subscription.

**Row 2 — traffic + expiry** (`id=layout_traffic`, vertical, `marginTop=8dp`):
- Horizontal line: `tv_traffic` (`weight=1`, start; e.g. `214.6 GB / 500.0 GB`, `colorOnSurfaceVariant`,
  11–12sp) + `tv_expiry` (end; e.g. `Expires 2026-08-15 · 37d`).
- `progress_traffic` — a horizontal determinate `LinearProgressIndicator`
  (`com.google.android.material.progressindicator.LinearProgressIndicator`), `max=1000`,
  `trackThickness=6dp`, `marginTop=4dp`. Tint via `colorPing` (green) normally; switch
  `indicatorColor` to `colorPingRed` when `trafficFraction >= 0.9`.

Add strings to `res/values/strings.xml`: `sub_traffic_used` (`%1$s / %2$s`), `sub_traffic_unlimited`
(`%1$s / ∞`), `sub_expires` (`Expires %1$s`), `sub_expired` (`Expired`), `sub_reset` (`Resets %1$s`),
plus `contentDescription` strings for the two buttons (reuse existing test/update strings where they
exist, e.g. `R.string.title_service_ping`, `R.string.title_update_subscription`).

### 3.2 Mount point — `fragment_group_server.xml`

Wrap the existing content in a vertical container and `<include>` the bar on top:

```xml
<LinearLayout ... orientation="vertical">
    <include
        android:id="@+id/layout_meta_bar"
        layout="@layout/layout_subscription_meta_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/refresh_layout"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">
        <androidx.recyclerview.widget.RecyclerView ... />
    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>
</LinearLayout>
```

The bar is a sibling of the `RecyclerView` (not a list header), so it stays pinned while the list
scrolls. `FragmentGroupServerBinding` will expose `binding.layoutMetaBar.*` via the `<include>` id.

The **"all servers" pseudo-tab** (`subId == ""`, no single subscription) has no `subscription-userinfo`:
hide the whole bar (`layout_meta_bar.visibility = GONE`) when `subId.isEmpty()`.

### 3.3 States

| State | Condition | Rendering |
|-------|-----------|-----------|
| **Hidden** | `subId.isEmpty()` (all-servers tab) | Whole bar `GONE` |
| **No metadata** | `!hasUserInfo` | Title + buttons only; `layout_traffic` `GONE` |
| **Normal (finite)** | `total > 0`, `used < total`, not expired | `used / total`, determinate bar green |
| **Near limit** | `trafficFraction >= 0.9` | Bar + `tv_traffic` tinted `colorPingRed` |
| **Unlimited** | `isUnlimited` | `tv_traffic = "used / ∞"`; `progress_traffic` `GONE` |
| **Expiry / reset** | `hasExpiry` | `tv_expiry` shows date (+ days-left); else `GONE` |
| **Expired** | `isExpired` | `tv_expiry = "Expired"` in `colorPingRed`; title unaffected |
| **Updating** | refresh in flight | `progress_action` `VISIBLE`, `btn_refresh` disabled |
| **Pinging** | ping in flight | `progress_action` `VISIBLE`, `btn_ping` disabled |
| **Error** | refresh returned `failureCount>0` | Toast (existing) + bar keeps last-known values; optional stale hint from `userInfoUpdated` |

---

## 4. Interactions wiring

All flows already exist on `MainViewModel`, scoped to the fragment's `subscriptionId`
(set in `GroupServerFragment.onResume` via `mainViewModel.subscriptionIdChanged(subId)`). No new
plumbing beyond a repaint signal.

- **Bind / repaint** — add `private fun bindMetaBar()` in `GroupServerFragment`, called from
  `onViewCreated`, `onResume`, and after refresh/ping completes. It reads
  `MmkvManager.decodeSubscription(subId)` and applies §3.3.

- **Refresh button** → reuse the existing manual-update path. Because the fragment is already scoped,
  call `mainViewModel.updateConfigViaSubAll()` (which, when `subscriptionId` is non-empty, updates
  **only this sub** — see `MainViewModel` lines 159–166) off the main thread, mirroring
  `MainActivity.importConfigViaSub()`:
  ```kotlin
  btn_refresh -> lifecycleScope.launch(Dispatchers.IO) {
      showActionProgress()
      val r = mainViewModel.updateConfigViaSubAll()   // captures header via §2.3, persists
      withContext(Main) {
          if (r.configCount > 0) mainViewModel.reloadServerList()
          bindMetaBar()                                // re-reads persisted traffic/expiry
          hideActionProgress()
      }
  }
  ```
  This makes refresh **interdependent**: it re-fetches → header persisted → `bindMetaBar` repaints
  traffic + expiry. (Optionally expose a thin `MainViewModel.refreshCurrentSub()` wrapper so the
  fragment doesn't inline the coroutine.)

- **Ping button** → reuse existing per-subscription ping. Call `mainViewModel.testAllTcping()`
  (fast, socket-level) or `mainViewModel.testAllRealPing()` (real delay via test service); both
  already operate on `serversCache` for the current `subscriptionId` and push results through
  `updateListAction`, which the adapter renders per-row. Toggle `progress_action` around it. Ping
  does not touch the traffic bar; they're independent actions on the same bar.

- **Auto-update** already flows through `SubscriptionUpdater.UpdateTask → updateConfigViaSub`, so the
  §2.3 edit means background refreshes also keep traffic/expiry fresh; next time the tab is shown
  `bindMetaBar` reflects it.

---

## 5. Implementation plan (small commits, real files)

1. **Data fields** — add 5 fields to `dto/entities/SubscriptionItem.kt` + derived extensions
   (`isUnlimited`, `usedTraffic`, `trafficFraction`, `isExpired`, `hasUserInfo`). Defaulted, so no
   migration. *(pure model, compiles alone)*
2. **Header parser** — add `util/SubscriptionUserInfo.kt` with `parse()` + a small unit test
   (`app/src/test/...`) covering full header, subset, blank, garbage.
3. **HTTP header capture** — add `UrlContentResult` + `getUrlContentWithUserAgentEx` to
   `util/HttpUtil.kt` (reads `response.header("subscription-userinfo")`). Keep the old method.
4. **Persist on update** — edit `handler/AngConfigManager.updateConfigViaSub` to use the `Ex` variant
   and merge parsed metadata into the `SubscriptionItem` before the existing
   `MmkvManager.encodeSubscription`. Covers manual + auto paths. *(traffic now stored; no UI yet)*
5. **Bar layout + strings** — add `res/layout/layout_subscription_meta_bar.xml`, string resources,
   and any missing icons (reuse `ic_refresh`, ping icon). `<include>` it in
   `fragment_group_server.xml` and re-weight the `SwipeRefreshLayout`.
6. **Bind + states** — implement `bindMetaBar()` in `ui/GroupServerFragment.kt`; wire visibility per
   §3.3; hide on `subId.isEmpty()`.
7. **Actions** — wire `btn_refresh` (reuse `updateConfigViaSubAll` + `reloadServerList` + repaint)
   and `btn_ping` (`testAllTcping`/`testAllRealPing`), with `progress_action` toggling and button
   disabling.
8. **Polish** — near-limit red tint, expiry days-left / expired state, `contentDescription`s,
   light/dark check against `?attr/colorOnSurface*` and `colorPing`/`colorPingRed`.

**Key files touched:** `SubscriptionItem.kt`, new `util/SubscriptionUserInfo.kt`, `util/HttpUtil.kt`,
`handler/AngConfigManager.kt`, new `res/layout/layout_subscription_meta_bar.xml`,
`res/layout/fragment_group_server.xml`, `res/values/strings.xml`, `ui/GroupServerFragment.kt`
(optionally a `refreshCurrentSub()` wrapper in `viewmodel/MainViewModel.kt`). Reused as-is:
`extension/_Ext.kt` (`toTrafficString`), `MainViewModel` ping/update, `SubscriptionUpdater`.

---

## Sources

- [v2raytun — Supported headers (`subscription-userinfo` format & examples)](https://docs.v2raytun.com/overview/supported-headers)
- [Hiddify App — URL Scheme wiki (subscription metadata handling)](https://github.com/hiddify/hiddify-app/wiki/URL-Scheme)
- [V2Fly — Subscription Manager](https://www.v2fly.org/en_US/v5/config/service/subscription.html)
