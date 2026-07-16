# Design: Country flags + collapsible server groups

**App:** "departament VPN" (v2rayNG / Xray fork), Kotlin.
**Scope:** design only — no code changes. Two features:
- **(A)** A left-side country-flag slot on each server row (Happ-style).
- **(B)** Collapsible / expandable subscription groups so a list can be hidden/shown.

Real files touched by the eventual implementation:
- `app/src/main/res/layout/item_recycler_main.xml`
- `app/src/main/java/com/v2ray/ang/ui/MainRecyclerAdapter.kt`
- `app/src/main/java/com/v2ray/ang/ui/GroupServerFragment.kt`
- `app/src/main/java/com/v2ray/ang/ui/GroupPagerAdapter.kt`
- `app/src/main/java/com/v2ray/ang/dto/entities/ProfileItem.kt` (read-only; no schema change needed)
- New helper: `app/src/main/java/com/v2ray/ang/util/FlagUtil.kt`

---

## 1. Where the country comes from

### Reality check on Remnawave

Remnawave **does not ship a structured country/ISO field to clients.** A Host's only human label is its **Remark** ("the name of the host that will be displayed in the dashboard… if the Host uses only Finnish nodes, you might name it 'Finland'"), and country identification is a *manual naming convention*, not a metadata structure — confirmed by the Hosts docs.[^rw-hosts] The remark string is what ends up in each generated share-link's `#fragment`, which the app parses into `ProfileItem.remarks`. Remnawave's own frontend renders country flags via the `country-flag-emoji-polyfill` dependency and its templates embed emoji flags directly in group names (e.g. `🇫🇮`),[^rw-front][^rw-mihomo] i.e. the flag lives **inside the remark text**, not in a side-channel field.

Consequence: there is **no reliable server-side country field to read.** We must derive the flag from what we already have. The existing layout even hints at this — `item_recycler_main.xml` line 54 has `tools:text="🇳🇱 Netherlands • Amsterdam"`, so remarks are already expected to carry a flag.

### Three candidate sources, compared

| # | Source | Coverage | Cost | Reliability |
|---|--------|----------|------|-------------|
| **a** | **Emoji flag already in `ProfileItem.remarks`** (Remnawave/most panels prepend `🇳🇱`) | High for Remnawave/Marzban subs | ~zero | Exact when present |
| **b** | **2-letter ISO code parsed from remarks** (`"NL - …"`, `"[DE]"`, `"US·LA"`, or the country *name* → code) | Medium | Low | Good, needs a name/code table |
| **c** | **GeoIP lookup of `server`/IP** using the shipped `geoip.dat` | Universal in theory | **High** | Poor in practice — see below |

**Why (c) is a last resort, not a first choice.** `geoip.dat` is not a MaxMind MMDB the app can query per-IP; in this codebase it is the Loyalsoldier/Xray routing blob (`AppConfig.GEOIP_DAT`, downloaded on demand in `UserAssetActivity`/`UserAssetViewModel`, consumed by the Xray core for routing — not present in app assets and not indexed by ISO country). It only carries a few tags actually used for routing (`geoip:private`, `geoip:cn`), so it **cannot map an arbitrary IP to an arbitrary country** without shipping a real GeoIP DB and a decoder, plus a DNS resolve for hostname-based servers (blocking network on a bind is unacceptable). So (c) is expensive and low-payoff.

### Recommendation — layered resolver (a → b → globe)

Resolve lazily in this order, stopping at the first hit:

1. **(a)** Scan `remarks` for an existing **regional-indicator flag emoji** (a pair of code points in `U+1F1E6..U+1F1FF`). If found, reuse it verbatim — zero ambiguity, already localized by the panel.
2. **(b)** Else parse a **2-letter ISO-3166-1 alpha-2 code** from `remarks` using a small set of patterns (`^[^A-Za-z]*([A-Z]{2})\b`, bracketed `[DE]`, or a compact **country-name → code** lookup for the common ~60 names). Convert the code to a flag emoji at runtime (see §3).
3. **(c) — deferred / optional flag** behind a setting, off by default. Only attempt GeoIP if a real MMDB is bundled later; otherwise skip.
4. **Fallback:** a neutral **globe** (`🌐` or a vector `ic_flag_globe`).

This gets essentially full coverage for Remnawave subscriptions from (a) alone, (b) mops up plain-text remarks, and we never pay the GeoIP cost.

---

## 2. Rendering: emoji vs. bundled flag images

| Option | Size | Perf | Reliability | Verdict |
|--------|------|------|-------------|---------|
| **Emoji in a `TextView`** | 0 KB | Excellent (text draw) | Renders on all modern Android; older/AOSP-emoji devices may show letter-box `NL`. Mitigable with the existing AndroidX **emoji2** (already a Material/AppCompat transitive dep) | **Recommended** |
| Bundled per-ISO **vector/SVG** set (~250 flags) | ~200–500 KB of `VectorDrawable`s, or a sprite | Good, but 250 drawables bloat resources & build | Pixel-consistent everywhere | Overkill for v1 |
| Downloaded flag PNGs | 0 in APK | Network + cache complexity, offline gaps | — | Rejected (VPN app, offline-first) |

**Recommendation: emoji flag in a `TextView`.** It is the lightest reliable option, matches how Happ and the Remnawave frontend already do it, and needs no asset pipeline. Keep the bundled-vector path as a documented future upgrade if the letter-box fallback proves visible on target devices; if adopted, ship a single 9-patch/sprite or on-demand vectors keyed by ISO code rather than 250 loose files.

### Where the flag view goes in `item_recycler_main.xml`

The row already has a 4dp accent bar `@id/layout_indicator` (lines 30–36) as the first child of `info_container`. Insert the flag **immediately after the indicator, before the vertical text column** (the `LinearLayout weight=1` at line 38). A `TextView` sized ~24sp with a fixed width keeps rows aligned:

```xml
<!-- after @id/layout_indicator, before the weighted text column -->
<TextView
    android:id="@+id/tv_flag"
    android:layout_width="32dp"
    android:layout_height="wrap_content"
    android:layout_gravity="center_vertical"
    android:gravity="center"
    android:layout_marginStart="2dp"
    android:layout_marginEnd="4dp"
    android:textSize="22sp"
    android:includeFontPadding="false"
    tools:text="🇳🇱" />
```

Because it is a plain view in the existing `ViewBinding` layout, it surfaces automatically as `itemMainBinding.tvFlag` — no adapter plumbing beyond setting text. (If the bundled-vector option is later chosen, swap this for an `ImageView` of the same box and set `setImageResource` instead.)

### How `MainRecyclerAdapter` sets it

In `onBindViewHolder` (around lines 60–63, next to `tvName`), add one line. Since remarks frequently already begin with the flag, strip it from `tvName` to avoid a double flag:

```kotlin
val flag = FlagUtil.resolveFlag(profile)            // emoji String, never blank
holder.itemMainBinding.tvFlag.text = flag
// optional de-dup: if remarks starts with the same emoji, drop it from the title
holder.itemMainBinding.tvName.text = FlagUtil.stripLeadingFlag(profile.remarks)
```

No layout-manager or diffing changes are required; the flag is bound per-row like every other field.

---

## 3. Helper: `FlagUtil` (ProfileItem → flag)

New file `app/src/main/java/com/v2ray/ang/util/FlagUtil.kt`, pure/stateless (mirrors the existing `Utils.kt` style). Public surface:

```kotlin
object FlagUtil {
    private const val GLOBE = "🌐"

    /** Layered resolve: (a) emoji already in remarks → (b) ISO code / name → (c) globe. */
    fun resolveFlag(profile: ProfileItem): String

    /** Extracts a leading regional-indicator flag emoji from text, or null. */
    fun extractFlagEmoji(text: String): String?

    /** ISO-3166-1 alpha-2 → flag emoji via regional indicators (offset 0x1F1A5). */
    fun codeToFlag(cc: String): String?      // "NL" -> "🇳🇱"

    /** Parse a 2-letter code or a known country name out of a remark. */
    fun parseCountryCode(remarks: String): String?

    /** Remove a leading flag emoji (+ separators) so the title isn't doubled. */
    fun stripLeadingFlag(remarks: String): String
}
```

`codeToFlag` uses the standard regional-indicator trick — map each ASCII letter to `0x1F1E6 + (c - 'A')` and concatenate the two code points.[^emoji-kotlin][^emoji-iso] Keep `parseCountryCode`'s name table small (top ~60 countries) and case-insensitive. All functions are allocation-cheap and safe to call on the bind thread. Unit-testable in isolation (`FlagUtilTest`).

---

## 4. Collapsible / expandable groups (feature B)

### Current structure

Top-level UI is a **tab per subscription**: `activity_main.xml` hosts a `ViewPager2` driven by `GroupPagerAdapter`, which creates one `GroupServerFragment` per `GroupMapItem` (`{id, remarks}`). Each fragment owns a single `RecyclerView` + `MainRecyclerAdapter` and a `layout_meta_bar` header (title, traffic, ping/refresh). So "a group" already maps 1:1 to a tab; there is no in-list sectioning today.

### Two designs considered

- **Option 1 — merge everything into one list with sticky section headers** (subscription header rows interleaved with server rows in a single `MainRecyclerAdapter`, header taps collapse the section). This is the true "one scrolling list, collapse any section" model, but it **fights the existing tab architecture**: it means retiring `ViewPager2`/`GroupPagerAdapter`, adding a header view-type + payload model to the adapter, and reworking drag-reorder (which currently assumes a flat server list). High blast radius.

- **Option 2 — collapse *within* each tab (recommended).** Keep tabs. Add a **single collapsible section** inside `GroupServerFragment`: the existing `layout_meta_bar` header becomes a tap target with a chevron that shows/hides that tab's `RecyclerView`. This is the lightest change that satisfies "the list can be hidden/shown," fits the current per-subscription tab exactly, and touches no adapter internals.

For multiple sections in one view (e.g. the "all servers" pseudo-tab, `subId == ""`), Option 2 generalizes cleanly by grouping that flat list under one header; if per-subscription sub-headers there are later wanted, that single tab alone can adopt Option 1's header-row approach without disturbing the others.

### Recommended design (Option 2)

1. **Header as toggle.** In `fragment_group_server.xml`, wrap the `RecyclerView` in the existing meta-bar block and add a chevron `ImageView` (`ic_expand_more` / `ic_expand_less`) to `layout_meta_bar`. In `GroupServerFragment.setupMetaBar()` (line 120), set `meta.root.setOnClickListener { toggleCollapsed() }`.
2. **Collapse action.** `toggleCollapsed()` flips `binding.recyclerView.visibility` (GONE/VISIBLE), rotates the chevron, and persists state. Use `View.GONE` (cheap, no measure) rather than an animator for v1; an optional `TransitionManager.beginDelayedTransition` gives a slide.
3. **Persisted state.** Store a per-subscription boolean via the existing `MmkvManager` (new key `PREF_GROUP_COLLAPSED + subId`), read in `onResume()/bindMetaBar()` so a collapsed group stays collapsed across app restarts and tab switches. Default = expanded.
4. **No adapter change.** `MainRecyclerAdapter`, drag-reorder, and `GroupPagerAdapter` are untouched — collapsing is a pure visibility toggle on the fragment's own RecyclerView.

Trade-off: Option 2 doesn't give a single unified scroll of all groups, but it is far lighter, keeps drag-reorder intact, and directly delivers the "hide/show a list" requirement per group.

---

## 5. Implementation plan (referencing real files)

**Feature A — flags**
1. Add `TextView @id/tv_flag` to `item_recycler_main.xml` (between `layout_indicator` and the weighted text column, §2).
2. Add `app/src/main/java/com/v2ray/ang/util/FlagUtil.kt` (§3). No change to `ProfileItem.kt` — resolution is derived at bind time, so the persisted DTO/JSON stays stable.
3. In `MainRecyclerAdapter.onBindViewHolder` set `tvFlag.text = FlagUtil.resolveFlag(profile)` and optionally `tvName.text = FlagUtil.stripLeadingFlag(profile.remarks)` (§2).
4. (Optional) enable AndroidX `emoji2` fallback if letter-box flags appear on target devices; (future) swap `tv_flag` to an `ImageView` + bundled ISO vectors if pixel-consistency is required.
5. Add `FlagUtilTest` covering emoji-in-remark, `"NL - …"`, `"[DE]"`, country-name, and globe fallback.

**Feature B — collapsible groups**
6. Add a chevron `ImageView` to `layout_meta_bar` and make `meta.root` clickable in `fragment_group_server.xml` + `GroupServerFragment.setupMetaBar()`.
7. Implement `toggleCollapsed()` in `GroupServerFragment` (visibility toggle + chevron rotation).
8. Add `PREF_GROUP_COLLAPSED` handling in `MmkvManager`; persist per-`subId`; restore in `bindMetaBar()`/`onResume()`.
9. Leave `MainRecyclerAdapter` and `GroupPagerAdapter` unchanged.

**Sequencing:** A and B are independent and can ship separately; A is the smaller, higher-visibility change and should land first.

---

[^rw-hosts]: Remnawave Documentation — Hosts (Remark is the only host label; country identification is a manual naming convention, no structured country field). https://docs.rw/learn-en/hosts/
[^rw-front]: Remnawave frontend dependencies include `country-flag-emoji-polyfill` (flags rendered as emoji, not a metadata field). https://github.com/remnawave/frontend/blob/main/package.json
[^rw-mihomo]: Remnawave Mihomo template guide — proxy-group names embed emoji flags directly (e.g. `🇫🇮`). https://docs.rw/guides/templates/mihomo/
[^emoji-kotlin]: "Kotlin way of converting country codes to emoji flags" (regional-indicator offset technique). https://gist.github.com/bhurling/c955c778f7a0765aaffd9214b12b3963
[^emoji-iso]: Emoji country flags and their ISO-3166 codes (regional indicator symbols `U+1F1E6..U+1F1FF`). https://apps.timwhitlock.info/emoji/tables/iso3166
