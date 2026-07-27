# Recon: `impeccable` + `taste-skill` distilled for a native Android implementing agent

**Target repo:** `/home/user/dp` (Android app, `V2rayNG/app/src/main/…`, Kotlin + XML Views, Material 3, package `com.v2ray.ang`, label "departament").
**Sources read (every claim below comes from these files, cited by line):**

| File | Lines | Read |
|---|---|---|
| `/home/user/dp/.claude/skills/impeccable/SKILL.md` | 169 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/android.md` | 40 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/audit.native.md` | 139 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/audit.md` | 135 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/adapt.native.md` | 58 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/craft.md` | 123 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/shape.md` | 165 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/critique.md` | 780 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/polish.md` | 241 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/distill.md` | 111 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/layout.md` | 185 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/typeset.md` | 301 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/colorize.md` | 257 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/interaction-design.md` | 189 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/animate.md` | 203 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/clarify.md` | 288 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/harden.md` | 347 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/onboard.md` | 234 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/product.md` | 60 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/brand.md` | 108 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/quieter.md` | 99 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/optimize.md` | 258 | full |
| `/home/user/dp/.claude/skills/impeccable/reference/ios.md` | 45 | head (contrast only) |
| `/home/user/dp/.claude/skills/taste-skill/SKILL.md` | 1206 | full |

Repo cross-checks (real tokens, cited so the checklist is operational, not hypothetical):
`V2rayNG/app/src/main/res/values/dimens.xml`, `values/styles.xml`, `values/motion.xml`, `values/colors.xml`, `app/src/main/java/com/v2ray/ang/util/MotionUtils.kt`.

---

## 0. Routing: which files are law for THIS project, and which are not

`SKILL.md:20` (Setup step 2): "If the user invoked a sub-command (`craft`, `shape`, `audit`, `polish`, ...), you MUST read the command's reference next: **`reference/<command>.md`, or the native variant from the Commands table** (e.g. `reference/audit.native.md`) **when the project platform is native** (`ios` / `android` / `adaptive`, per the `context.mjs` directive). One file, not both. Non-optional."

`SKILL.md:23` (Setup step 5): "**If PRODUCT.md's `## Platform` is `ios` or `android`**, also read `reference/<platform>.md` (HIG / Material 3 conventions)."

Consequences for `/home/user/dp`:

1. **`audit` → `audit.native.md`, never `audit.md`.** `audit.md:5` states it plainly: "**Web only.** Native platforms (`ios` / `android` / `adaptive`) route to [audit.native.md](audit.native.md) instead; if the project is native, switch to it now."
2. **`adapt` → `adapt.native.md`.**
3. **`android.md` is always additionally loaded** for any UI work here.
4. **The register is Product, not Brand.** `SKILL.md:22`: "If it is app UI, admin, a dashboard, or a tool (design SERVES the product), read `reference/product.md`." The departament Android app is authenticated app UI, so `product.md` governs, plus `android.md` on top. `android.md:5`: "On native, register narrows. Material Design 3 governs structure, navigation, and interaction whatever the register; brand expresses through Material's theming (color roles, type scale, shape, motion)."
5. **The web tooling in `layout.md` / `animate.md` does not apply.** `layout.md:11`: "Native (`ios` / `android` / `adaptive`): structure follows the Layout section of [ios.md](ios.md) / [android.md](android.md) (read it first if Setup hasn't already): platform navigation, insets, and touch targets, never the CSS tooling below." `animate.md:13` says the same for motion: "implementation follows the Motion section of [ios.md](ios.md) / [android.md](android.md) …, system transitions and OS Reduce Motion, never the web tooling below."
6. **`live` and `detect.mjs` are unavailable here.** `SKILL.md:142`: "**`live` and the bundled `detect.mjs` are web-only.** If `setup.platform` is `ios`, `android`, or `adaptive`, don't lead with either; the browser overlay and the HTML rule engine don't apply to native app code." This removes the "mechanical pre-scan" sub-agent that `layout.md:21-27` and `typeset.md:19-25` mandate on web. On Android the assessment sub-agent survives; the detector sub-agent does not.
7. **`taste-skill` is mostly out of scope.** `taste-skill/SKILL.md:903` lists under "OUT OF SCOPE": "Native mobile (use Apple HIG / Material directly)." Line 899 also excludes "Dashboards / dense product UI / admin panels". `taste-skill/SKILL.md:906`: "If the brief is one of the above, **say so explicitly**, point to the right tool, and only apply this skill's marketing-page / about-page / landing-page parts to the surfaces where they apply." See §9 for the narrow set of taste-skill rules that still transfer.

---

## 1. THE ABSOLUTE BANS (verbatim from `SKILL.md:81-92`)

> ### Absolute bans
>
> Match-and-refuse. If you're about to write any of these, rewrite the element with different structure.
>
> - **Side-stripe borders.** `border-left` or `border-right` greater than 1px as a colored accent on cards, list items, callouts, or alerts. Never intentional. Rewrite with full borders, background tints, leading numbers/icons, or nothing.
> - **Gradient text.** `background-clip: text` combined with a gradient background. Decorative, never meaningful. Use a single solid color. Emphasis via weight or size.
> - **Glassmorphism as default.** Blurs and glass cards used decoratively. Rare and purposeful, or nothing.
> - **The hero-metric template.** Big number, small label, supporting stats, gradient accent. SaaS cliché.
> - **Identical card grids.** Same-sized cards with icon + heading + text, repeated endlessly.
> - **Tiny uppercase tracked eyebrow above every section.** The 2023-era kicker (small all-caps text with wide tracking, "ABOUT" "PROCESS" "PRICING" above each heading) is now the saturated AI scaffold; it appears on 55-95% of generations regardless of brief, which is the definition of a tell. One named kicker as a deliberate brand system is voice; an eyebrow on every section is AI grammar. Choose a different cadence.
> - **Numbered section markers as default scaffolding (01 / 02 / 03).** Putting `01 · About / 02 · Process / 03 · Pricing` above every section is the eyebrow trope one tier deeper: reach for it because "landing pages do this" and you're scaffolding by reflex. Numbers earn their place when the section actually IS a sequence (a real 3-step process, an ordered flow, a typed timeline) and the order carries information the reader needs. One deliberate numbered sequence on one page is voice; numbered eyebrows on every section across the site is AI grammar.
> - **Text that overflows its container.** Long heading words plus large clamp scales plus narrow grids cause headline overflow on tablet/mobile. Test the heading copy at every breakpoint; if it overflows, reduce the clamp max or rewrite the copy. The viewport is part of the design.

### 1.1 Android translation of each ban (what to grep for in this repo)

| Ban (SKILL.md) | Android/XML form to refuse | Correct rewrite |
|---|---|---|
| Side-stripe borders | A `<shape>` / `<layer-list>` drawable whose only accent is a left or right band (e.g. an `<item android:left="-4dp">` inset trick, or a 4dp `View` glued to the start of a row); `MaterialCardView` with an asymmetric `strokeWidth` fake | Full 1dp `strokeColor` on the card, or a background tint at 4-8% of accent, or a leading icon tile (`@dimen/tile_size` 40dp, `dimens.xml:31`) |
| Gradient text | `Shader` / `LinearGradient` set on a `TextView` paint, `android:foreground` gradient over text | One solid `?attr/colorOnSurface` or `?attr/colorPrimary`; emphasis via `TextAppearance.App.Title` (700 weight, `styles.xml:74-81`) or size |
| Glassmorphism as default | `RenderEffect.createBlurEffect`, `setRenderEffect`, translucent white overlays on cards, decorative `#33FFFFFF` scrims | `android.md:33`: "**Tonal elevation.** Convey elevation through the standard surface tonal levels (plus shadow where appropriate); no arbitrary drop shadows." Use `?attr/colorSurfaceContainer*` ramp |
| Hero-metric template | A dashboard header of "big number + tiny label + 3 stat chips + gradient" | Real state (connection status, real traffic counters using `TextAppearance.App.Numeric`, `styles.xml:122`), not decorative figures |
| Identical card grids | A `RecyclerView` of same-size `MaterialCardView` items each icon + title + subtitle, repeated for every section of a screen | `layout.md:108`: "Don't default to card grids for everything; spacing and alignment create visual grouping naturally"; `distill.md:55`: "**Remove unnecessary cards**" |
| Tiny uppercase tracked eyebrow | `android:textAllCaps="true"` + `android:letterSpacing≈0.1` 10-11sp label above every section header | Repo law already agrees: `CLAUDE.md` "Section headers are sentence-case bold - NOT tiny ALL-CAPS tracked eyebrows." Use `TextAppearance.App.Title` sentence-case |
| Numbered section markers | "01 · Подключение / 02 · Серверы" style prefixes on settings groups | Drop the number unless the screen is a genuine ordered flow (e.g. a real onboarding step counter) |
| Text overflowing its container | Fixed-width `TextView`s, `singleLine` without `ellipsize`, long Russian strings blowing out a row | `harden.md:64-77` equivalent: give the text `layout_weight`, `android:maxLines` + `android:ellipsize="end"`, never a hard `layout_width` in dp for label text; test at largest font scale |

**Two more bans are inherited from `product.md:44-51`, and they bite harder on Android than the absolute bans do:**

> - Decorative motion that doesn't convey state.
> - Inconsistent component vocabulary across screens. If the "save" button looks different in two places, one is wrong.
> - Display fonts in UI labels, buttons, data.
> - Reinventing standard affordances for flavor (custom scrollbars, weird form controls, non-standard modals).
> - Heavy color or full-saturation accents on inactive states.
> - Modal as first thought. Modals are usually laziness. Exhaust inline / progressive alternatives first.

And `android.md:37-39`:
> - **Material components.** Buttons (filled / tonal / outlined / text), FAB, switches, chips, snackbars, bottom sheets, Material dialogs, navigation bar/rail/drawer. Never port iOS controls or invent equivalents.
> - **One FAB, one primary action.** Never stack FABs or spend one on a secondary task.
> - **Snackbars for transient feedback** (actionable when useful, never a toast for that); dialogs only for decisions that must interrupt.

---

## 2. THE AI SLOP TEST (verbatim from `SKILL.md:94-101`)

> ### The AI slop test
>
> If someone could look at this interface and say "AI made that" without doubt, it's failed. Cross-register failures are the absolute bans above. Register-specific failures live in each reference.
>
> **Category-reflex check.** Run at two altitudes; the second one catches what the first one misses.
>
> - **First-order:** if someone could guess the theme + palette from the category alone, it's the first training-data reflex. Rework the scene sentence and color strategy until the answer isn't obvious from the domain.
> - **Second-order:** if someone could guess the aesthetic family from category-plus-anti-references ("AI workflow tool that's not SaaS-cream → editorial-typographic", "fintech that's not navy-and-gold → terminal-native dark mode"), it's the trap one tier deeper. The first reflex was avoided; the second wasn't. Rework until both answers are not obvious. The brand register's [reflex-reject aesthetic lanes](reference/brand.md) list catches the currently-saturated families.

### 2.1 The Android slop test (verbatim, `android.md:7-9`)

> ## The Android slop test
>
> Would a fluent Android user trust this app, or trip on off-spec components? The most common tell is an iOS app wearing Android's skin: a bottom-only navigation copied from iPhone, a back arrow that ignores the system Back gesture, Cupertino-shaped switches and dialogs. Material 3 is the rulebook; follow its components and theme the brand through it.

### 2.2 The product slop test (verbatim, `product.md:5-9`)

> ## The product slop test
>
> Not "would someone say AI made this." Familiarity is often a feature here. The test is: would a user fluent in the category's best tools (Linear, Figma, Notion, Raycast, Stripe come to mind) sit down and trust this interface, or pause at every subtly-off component?
>
> Product UI's failure mode isn't flatness, it's strangeness without purpose: over-decorated buttons, mismatched form controls, gratuitous motion, display fonts where labels should be, invented affordances for standard tasks. The bar is earned familiarity. The tool should disappear into the task.

**How the three compose for this repo.** For a settings row, a server list item, or the Account tab, the *governing* test is the Android slop test plus the product slop test (would a fluent Android user trust it; would a Linear/Raycast user pause). The generic AI slop test still gates any surface with marketing intent (the buy-tariff screen, onboarding hero, the "get a plan" CTA), because those are the only places in the app where design is doing brand work. The absolute bans apply everywhere, unconditionally: `SKILL.md:96` "Cross-register failures are the absolute bans above."

**Category-reflex check applied to a VPN client.** First-order reflex for "VPN app" is: pure black background, neon green/cyan "connected" glow, a big pulsing shield, monospace, terminal chrome. The repo has already avoided that by committing to Incy dark + one blue accent sampled from the logo (`colors.xml:3-5`, `brand_blue #1E5FC7`). Second-order reflex is "VPN that isn't neon-hacker → glassy iOS-style translucency with a big circular power button". Refuse both. `android.md:33` (tonal elevation, no arbitrary shadows) and the glassmorphism ban are exactly the guardrails.

---

## 3. THE AUDIT PROCEDURE (native) - `audit.native.md`

`audit.native.md:1`: "Run systematic **technical** quality checks on a native app (`ios` / `android` / `adaptive`) and generate a comprehensive report. Don't fix issues; document them for other commands to address."
`audit.native.md:3`: "This is a code-level audit, not a design critique. Audit from source (SwiftUI / UIKit / Compose / React Native / Flutter); no browser tooling or `detect.mjs` applies. Score against the platform reference(s)."

### 3.1 The five dimensions, 0-4 each, 20 total

**Dimension 1 - Accessibility (VoiceOver / TalkBack)** (`audit.native.md:9-19`). Check for:
- Missing labels: interactive elements without accessibility labels, traits/roles, or state announcements.
- Reading and focus order: illogical traversal, unreachable controls, focus lost on navigation.
- Text scaling: "fixed point sizes defeating Dynamic Type (iOS) or px instead of sp (Android); layouts that clip or overlap at large sizes".
- Touch targets: "below 44 pt (iOS) / 48 dp (Android), or crammed without spacing".
- Reduce Motion ignored: parallax and large slides with no crossfade alternative.
- Contrast: text failing contrast in either appearance, light or dark.

Scoring (`audit.native.md:19`): "0=Screen reader unusable, 1=Major gaps (unlabeled controls, no scaling), 2=Partial (labels exist, order or scaling breaks), 3=Good (minor gaps), 4=Excellent (labeled, ordered, scales cleanly, Reduce Motion honored)".

*Android greps:* `android:contentDescription` missing on any `ImageButton`/`ImageView` that is clickable; `android:importantForAccessibility`; hardcoded `dp` on `android:textSize` (must be `sp`); `android:focusable`; `announceForAccessibility` on async state changes; `MotionUtils.animationsEnabled` (`util/MotionUtils.kt:26`) / `View.reducedMotion()` (`MotionUtils.kt:50`) not consulted before an animator.

**Dimension 2 - Performance** (`audit.native.md:21-31`). Check for: slow startup (heavy work on launch before first frame); unvirtualized lists ("long content without FlatList / LazyColumn / List recycling"); main-thread jank ("synchronous work in scroll or gesture paths, dropped frames on 60/120 Hz"); wasted rendering; image handling ("full-size images decoded for thumbnails, no caching"); app weight.
Scoring: "0=Janky everywhere, 1=Major problems (unvirtualized lists, slow launch), 2=Partial, 3=Good (minor improvements possible), 4=Excellent (fast launch, smooth scroll, lean)".

*Android greps:* work in `Activity.onCreate` before `setContentView`/first frame; `RecyclerView` adapters doing IO in `onBindViewHolder`; `notifyDataSetChanged()` where `DiffUtil` belongs; `runBlocking` on main; bitmap decode without `inSampleSize`/Coil-Glide.

**Dimension 3 - Appearance & Theming** (`audit.native.md:33-41`). Check for:
- "**Hard-coded colors**: raw hex instead of semantic system colors (iOS) / Material color roles (Android) / design tokens".
- "**Broken dark appearance**: missing dark variants, poor contrast in dark, quick inverts".
- "**Dynamic Color** (Android 12+): no static fallback scheme, or ignored where it fits".
- "**Off-platform materials**: hand-rolled blur/glassmorphism instead of system materials or tonal elevation".

Scoring: "0=Hard-coded everything, 1=Minimal tokens, 2=Partial (tokens exist, inconsistently used), 3=Good (minor hard-coded values), 4=Excellent (semantic throughout, both appearances first-class)".

*Android greps:* `#` literals in layout XML; `@color/` raw refs where `?attr/colorSurface*`/`?attr/colorOnSurfaceVariant` belongs; a `values/` color with no `values-night/` counterpart.

**Dimension 4 - Platform Conformance (CRITICAL)** (`audit.native.md:43-53`). "Score against the loaded platform reference(s), including their slop tests. Check for":
- "**Broken system gestures**: edge-swipe back disabled (iOS), predictive Back hijacked (Android)".
- "**Inset violations**: content under the notch, Dynamic Island, home indicator, status bar, or keyboard".
- "**Off-platform navigation**: custom global nav, overloaded tab bars, iOS patterns on Android or vice versa".
- "**Web-shaped controls**: HTML-style buttons, custom toggles, hover-dependent affordances".
- "**Icon drift**: mixed icon sets instead of SF Symbols / Material Symbols".
- "**AI tells**: the shared absolute bans still apply (AI palette, gradient text, hero metrics)".

Scoring: "0=Web port (nothing native), 1=Heavy violations (3-4 kinds), 2=Some (1-2 noticeable), 3=Mostly conformant (subtle issues), 4=Fully native (a fluent user trusts every screen)".

*Android greps:* `onBackPressed` overrides that swallow back; missing `OnBackPressedCallback` / `android:enableOnBackInvokedCallback`; `WindowCompat.setDecorFitsSystemWindows` + `ViewCompat.setOnApplyWindowInsetsListener` absent; `adjustResize`/IME insets; custom drawn switches instead of `MaterialSwitch`; a mix of vector sets instead of one Material Symbols family.

**Dimension 5 - Adaptivity** (`audit.native.md:55-64`). Check for: stretched phone layouts on tablet ("instead of using size classes / window size classes"); orientation breakage ("landscape clipping, ignored, or locked without reason"); keyboard/IME handling ("inputs hidden behind the keyboard, no inset adjustment"); multitasking (Android multi-window breaking layout); foldables ("hinge-unaware layouts on posture change (Android)").
Scoring: "0=One screen size only, 1=Major breakage (landscape or tablet broken), 2=Partial, 3=Good (minor edge cases), 4=Excellent (adapts across sizes, orientations, and windowing)".

### 3.2 The report skeleton (mandatory shape, `audit.native.md:66-124`)

1. **Audit Health Score table** - one row per dimension with "Key Finding", then Total `??/20`.
   Rating bands (`audit.native.md:79`): "18-20 Excellent (minor polish), 14-17 Good (address weak dimensions), 10-13 Acceptable (significant work needed), 6-9 Poor (major overhaul), 0-5 Critical (fundamental issues)".
2. **Platform Conformance Verdict** - `audit.native.md:82`: "**Start here.** Pass/fail: does this read as a native app or a ported website? List specific violations. Be brutally honest."
3. **Executive Summary** - score, issue counts by severity, top 3-5 critical issues, recommended next steps.
4. **Detailed Findings by Severity.** Severity ladder (`audit.native.md:93-96`): "**P0 Blocking**: Prevents task completion. Fix immediately"; "**P1 Major**: Significant difficulty or platform-guideline violation. Fix before release"; "**P2 Minor**: Annoyance, workaround exists. Fix in next pass"; "**P3 Polish**: Nice-to-fix, no real user impact. Fix if time permits".
   Every issue documents: `[P?] Issue name` · **Location** (screen, file, line) · **Category** (Accessibility / Performance / Theming / Conformance / Adaptivity) · **Impact** · **Guideline** (the HIG / Material rule it violates) · **Recommendation** · **Suggested command**.
5. **Patterns & Systemic Issues** - `audit.native.md:109-111` wants recurring-root-cause statements, e.g. "Hard-coded colors appear in 15+ screens, should use semantic colors", "Touch targets consistently below 44 pt throughout the tab bar and list rows".
6. **Positive Findings** - "Note what's working well: good practices to maintain and replicate."
7. **Recommended Actions** - commands in priority order, P0 first. `audit.native.md:124`: "End with `/impeccable polish` as the final step if any fixes were recommended."

**The NEVER list (`audit.native.md:134-139`, verbatim):**
> - Report issues without explaining impact (why does this matter?)
> - Provide generic recommendations (be specific and actionable)
> - Skip positive findings (celebrate what works)
> - Forget to prioritize (everything can't be P0)
> - Report false positives without verification

Also `audit.native.md:132`: "**IMPORTANT**: Be thorough but actionable. Too many P3 issues creates noise. Focus on what actually matters."

### 3.3 What `audit.md` adds that `audit.native.md` does not repeat

`audit.md` is the web file and is explicitly not to be used here (`audit.md:5`), but its Dimension 5 is the origin of the anti-pattern gate and is worth keeping as the mental model: `audit.md:55-59` "**5. Anti-Patterns (CRITICAL)** … Check against ALL the **DON'T** guidelines from the parent impeccable skill … **Score 0-4**: 0=AI slop gallery (5+ tells), 1=Heavy AI aesthetic (3-4 tells), 2=Some tells (1-2 noticeable), 3=Mostly clean (subtle issues only), 4=No AI tells (distinctive, intentional design)". On native, that gate lives inside Dimension 4's "**AI tells**: the shared absolute bans still apply" (`audit.native.md:51`).

### 3.4 The design-critique procedure (`critique.md`), condensed for native

`audit` is technical. `critique` is the design review, and it has hard invariants (`critique.md:5-14`):
- Assessment A (design review) and Assessment B (detector/browser evidence) are both required.
- Both must run as isolated sub-agents when a Task tool exists; running inline is "a degraded run".
- `critique.md:9`: "If you degrade for any reason, the report's first line MUST be a banner: `⚠️ DEGRADED: single-context (<reason>)`."
- `critique.md:10`: "Assessment A must finish before detector findings enter the parent synthesis context."

**On Android, Assessment B's detector half cannot run** (`SKILL.md:142`, detector is web-only). The honest native adaptation: Assessment A (design review from source + emulator screenshots) stays mandatory; Assessment B becomes "device/emulator evidence" (screenshots at default and largest font scale, TalkBack pass, dark theme pass), and the provenance banner still declares what actually ran.

Assessment A must evaluate (`critique.md:46-51`): AI slop verdict; holistic design (hierarchy, IA, emotional fit, discoverability, composition, typography, color, accessibility, states, copy, edge cases); cognitive load; emotional journey (peak-end rule, emotional valleys, reassurance at high-stakes moments); Nielsen's 10 heuristics scored 0-4.

**Design Health Score** = Nielsen 10 × 4 = 40 (`critique.md:98-114`). `critique.md:114`: "Be honest with scores. A 4 means genuinely excellent. Most real interfaces score 20-32." Bands (`critique.md:578-584`): 36-40 Excellent; 28-35 Good; 20-27 Acceptable; 12-19 Poor; 0-11 Critical.

**Cognitive Load Checklist, 8 items** (`critique.md:299-306`), each a yes/no, count failures:
Single focus · Chunking (≤4 items per group) · Grouping (proximity/borders/shared background) · Visual hierarchy · One thing at a time · Minimal choices (≤4 visible options per decision point) · Working memory (no cross-screen memorization) · Progressive disclosure.
Scoring (`critique.md:308`): "0–1 failures = low cognitive load (good). 2–3 = moderate (address soon). 4+ = high cognitive load (critical fix needed)."
Working-memory rule (`critique.md:314-327`): "**Humans can hold ≤4 items in working memory at once**"; practical caps include "Navigation menus: ≤5 top-level items", "Form sections: ≤4 fields visible per group before a visual break", "Action buttons: 1 primary, 1–2 secondary, group the rest in a menu", "Pricing tiers: ≤3 options (more causes analysis paralysis)".

**Personas** (`critique.md:603-758`). Pick 2-3. The selection table (`critique.md:751-758`) maps "Onboarding flow → Jordan, Casey", "Dashboard / admin → Alex, Sam", "Form-heavy / wizard → Jordan, Sam, Casey". For this app the three that bite: **Sam** (accessibility-dependent: "Can the entire primary flow be completed keyboard-only?", "Missing or invisible focus indicators", "Meaning conveyed by color alone"), **Casey** (distracted mobile: "Are primary actions in the thumb zone (bottom half of screen)?", "Is state preserved if the user leaves and returns?", "Are touch targets at least 44×44pt?"), **Riley** (stress tester: "What happens at the edges (0 items, 1000 items, very long text)?", "Workflows that lose user data on refresh or navigation").

**Report tone rules (`critique.md:163-169`, verbatim):**
> - Be direct. Vague feedback wastes everyone's time.
> - Be specific. "The submit button," not "some elements."
> - Say what's wrong AND why it matters to users.
> - Give concrete suggestions. Cut "consider exploring..." entirely.
> - Prioritize ruthlessly. If everything is important, nothing is.
> - Don't soften criticism. Developers need honest feedback to ship great design.

---

## 4. CRAFT RULES: LAYOUT

### 4.1 Platform-first (this overrides the CSS advice in `layout.md`)

`android.md:11-16`:
> ## Layout & structure
>
> - **Material navigation, matched to size.** Navigation bar (bottom, 3–5 destinations) on compact width; navigation rail or drawer on expanded width. Never ship a phone bottom-bar untouched on a tablet.
> - **System Back always works.** Honor the predictive Back gesture and Back button; never trap the user or hijack the gesture.
> - **Edge-to-edge with window insets.** Apply the status bar, navigation bar, display cutout, and IME insets so content never hides behind system bars or the keyboard.
> - **Top app bar for screen context**; pair with a FAB when the screen has a single primary action.

`android.md:18-20`:
> ## Touch targets
>
> - **48×48 dp minimum** for every touch target, with at least 8 dp between them.

Note the conflict with the generic guidance: `layout.md:137` and `polish.md:197` say "44×44px minimum". **On Android, 48dp wins** (`android.md:20`, `audit.native.md:17`). The repo already encodes it: `@dimen/row_min_height` = 56dp (`dimens.xml:33`).

### 4.2 Spacing, rhythm, hierarchy (register-neutral, from `layout.md`)

- `layout.md:78`: "Use a consistent spacing scale … What matters is that values come from a defined set, not arbitrary numbers." `layout.md:79`: "Prefer a 4pt base scale (4, 8, 12, 16, 24, 32, 48, 64, 96px) over 8pt; 8pt is too coarse and you'll frequently need 12px between 8 and 16." The repo's `space_4/8/12/16/24/32` (`dimens.xml:14-19`) is exactly this 4pt scale. **Any dp literal in a layout that is not one of those six values is drift.**
- `layout.md:47` / `SKILL.md:47`: "Vary spacing for rhythm." `layout.md:86-88`: "**Tight grouping** for related elements (8-12px between siblings)"; "**Generous separation** between distinct sections (48-96px)"; "**Varied spacing** within sections (not every row needs the same gap)". On a phone the section number scales down; the principle (group tight, separate generously) does not.
- Squint test (`layout.md:46`, `layout.md:156`): "Apply the squint test: blur your (metaphorical) eyes. Can you still identify the most important element, second most important, and clear groupings?"
- Hierarchy dimensions table (`layout.md:117-123`): Size strong at "3:1 ratio or more", weak at "<2:1"; Weight strong "Bold vs Regular", weak "Medium vs Regular"; Space strong "Surrounded by white space".
- `layout.md:114`: "Use the fewest dimensions needed for clear hierarchy. Space alone can be enough … Add color or size contrast only when simpler means aren't sufficient." `layout.md:115`: "The best hierarchy combines 2–3 dimensions at once."
- Elevation (`layout.md:130-131`): "Build a consistent shadow scale (sm → md → lg → xl); shadows should be subtle. Use elevation to reinforce hierarchy, not as decoration." On Android this is `android.md:33` tonal elevation, not `android:elevation` literals.
- Optical adjustment (`layout.md:135-136`): "If an icon looks visually off-center despite being geometrically centered, nudge it. But only if you're confident it actually looks wrong. Don't adjust speculatively." "Geometrically centered glyphs often look off-center (play icons need to shift right, arrows shift toward their direction)."

### 4.3 The layout NEVER list (`layout.md:146-152`, verbatim)

> - Use arbitrary spacing values outside your scale
> - Make all spacing equal (variety creates hierarchy)
> - Wrap everything in cards (not everything needs a container)
> - Nest cards inside cards (use spacing and dividers for hierarchy within)
> - Use identical card grids everywhere (icon + heading + text, repeated)
> - Default to the hero metric layout (big number, small label, stats, gradient) as a template. If showing real user data, a prominent metric can work, but it should display actual data, not decorative numbers.

Plus `SKILL.md:48`: "Cards are the lazy answer. Use them only when they're truly the best affordance. Nested cards are always wrong."

### 4.4 Verification (`layout.md:154-163`)

Squint test · Rhythm · Hierarchy ("Is the most important content obvious within 2 seconds?") · Breathing room · Consistency · Responsiveness.
`layout.md:163`: "Answer each item above by citing the file, selector, or value that satisfies it; never a bare yes."

### 4.5 Adaptivity (`adapt.native.md`)

- `adapt.native.md:15`: "**Restructure, don't stretch.** A scaled-up phone UI on a tablet is the failure mode. Use size classes (iOS) / window size classes (Android) to switch structure."
- `adapt.native.md:16`: "**Navigation changes shape**: … Android navigation bar becomes a rail or drawer on expanded width."
- `adapt.native.md:18`: "**Multitasking is a size, not an edge case**: iPad Split View and Android multi-window can hand you a phone-width window on a tablet."
- `adapt.native.md:21-23`: "Landscape restructures (side-by-side panes, repositioned controls); never clip or letterbox. Lock orientation only when the task truly demands it." "Foldables (Android): react to posture and hinge via window size classes; test folded, unfolded, and tabletop."
- `adapt.native.md:47-49`: "Drive structure from **size classes / window size classes**, never from device-model checks." "Respect safe areas and window insets in every new configuration (notch, hinge, status bar, keyboard)." "Test on simulators for breadth, then real hardware for truth."
- NEVER (`adapt.native.md:53-58`): ship a stretched phone layout on a tablet; port one platform's controls or navigation onto the other; hide core functionality on smaller devices; **lock orientation to dodge a layout bug**; trust simulators alone.

---

## 5. CRAFT RULES: TYPOGRAPHY

### 5.1 Platform rules (`android.md:22-27`, verbatim)

> ## Typography
>
> - **Material type scale.** Display, Headline, Title, Body, Label roles (large/medium/small each). Map text to roles; never hand-pick sizes per screen.
> - **Roboto is the system face**; theme a brand face in through the type scale, keeping body, labels, and controls legible and consistent.
> - **sp units, never fixed px**, so type follows the system font-size setting.

This is satisfied in-repo by `TextAppearance.App.*` (`styles.xml:56-129`), which parents onto the Material 3 roles: `Display` → `TextAppearance.Material3.HeadlineMedium` (34sp/700, `styles.xml:56-63`), `Headline` → `HeadlineSmall` (24sp/700, `:65-72`), `Title` → `TitleMedium` (16sp/700, `:74-81`), `Title.Medium` (16sp/500, `:83-86`), `Body` → `BodyMedium` (14sp, `:88-93`), `Subtitle` → `BodyMedium` 13sp on `colorOnSurfaceVariant` (`:95-100`), `Caption` → `BodySmall` 12sp (`:102-107`), `Chip` → `LabelSmall` 11sp/500 (`:109-115`), `Numeric` with `"tnum" on, "lnum" on` (`:117-129`).

**Rule for the implementing agent: never write `android:textSize` in a layout. Always `android:textAppearance="@style/TextAppearance.App.X"`.** Any literal `textSize` is a Dimension-3 theming finding.

### 5.2 Product-register typography (`product.md:11-16`, verbatim)

> - **One family is often right.** Product UIs don't need display/body pairing. A well-tuned sans carries headings, buttons, labels, body, data.
> - **Fixed rem scale, not fluid.** Clamp-sized headings don't serve product UI. Users view at consistent DPI, and a fluid h1 that shrinks in a sidebar looks worse, not better.
> - **Tighter scale ratio.** 1.125–1.2 between steps is typical. More type elements here than on brand surfaces; exaggerated contrast creates noise.
> - **Line length still applies for prose** (65–75ch). Data and compact UI can run denser.

Note the repo ramp 11/12/13/14/16/24/34sp: 12→13→14 is a 1.08 ratio, tighter than `product.md`'s 1.125-1.2 floor. `typeset.md:44` names exactly this failure: "Are font sizes too close together? (14px, 15px, 16px = muddy hierarchy)". This is a real, defensible finding to raise: `Body` 14sp vs `Subtitle` 13sp vs `Caption` 12sp lean on color (`colorOnSurface` vs `colorOnSurfaceVariant`) more than size. `typeset.md:89` gives the sanctioned fix: "**Combine dimensions**: Size + weight + color + space for strong hierarchy. Don't rely on size alone." The repo does combine color + weight, so it is not automatically a defect - but do not add a fourth near-neighbour size.

### 5.3 Generic typography rules that survive translation to Android

- `SKILL.md:39`: "Cap body line length at 65–75ch." (On a phone the container does this; the rule bites on tablets and on long Russian descriptive paragraphs.)
- `SKILL.md:40`: "Don't pair fonts that are similar but not identical (two geometric sans-serifs, two humanist sans-serifs). Pair on a contrast axis (serif + sans, geometric + humanist) or use one family in multiple weights." The repo does exactly the sanctioned move: Space Grotesk for headings/labels/numbers, system face for body (`styles.xml:88-107` carry no `fontFamily`).
- `SKILL.md:42`: "Display heading letter-spacing floor: ≥ -0.04em." Repo Display is `-0.02` (`styles.xml:61`), Headline `-0.01` (`:70`). Compliant.
- `typeset.md:96`: "Adjust line-height per context: tighter for headings (1.1-1.2), looser for body (1.5-1.7)."
- `typeset.md:97` + `typeset.md:182`: light-on-dark compensation. Verbatim (`typeset.md:182`): "**Non-obvious**: Light text on dark backgrounds needs compensation on three axes, not just one. Bump line-height by 0.05–0.1, add a touch of letter-spacing (0.01–0.02em), and optionally step the body weight up one notch (regular → medium). The perceived weight drops across all three; fix all three." **This is directly load-bearing for an Incy pure-dark app.** The repo already adds `letterSpacing 0.01` on Body (`styles.xml:92`) and `0.02` on Caption (`:105`); the line-height axis (`android:lineSpacingMultiplier`) is the one to check.
- `typeset.md:102`: "Use `tabular-nums` for data tables and numbers that should align." Repo: `TextAppearance.App.Numeric` (`styles.xml:117-129`). Any live-updating counter (traffic, ping, balance) that does not use it is a finding.
- `typeset.md:104`: "Use semantic token names (`--text-body`, `--text-heading`), not value names (`--font-16`)." Repo naming complies.
- `typeset.md:110`: "Don't use more than 3-4 weights (Regular, Medium, Semibold, Bold is plenty)." Repo uses 500 and 700 only.
- `typeset.md:284`: "**ALL-CAPS tracking**: capitals sit too close at default spacing. Add 5–12% letter-spacing … to short all-caps labels". *Only relevant if all-caps survives at all; per the eyebrow ban and repo law, it mostly should not.*

### 5.4 The typography NEVER list (`typeset.md:113-121`, verbatim) and its Android caveats

> - Use more than 2-3 font families
> - Pick sizes arbitrarily; commit to a scale
> - Set body text below 16px
> - Use decorative/display fonts for body text
> - Disable browser zoom (`user-scalable=no`)
> - Use `px` for font sizes; use `rem` to respect user settings
> - Default to Inter/Roboto/Open Sans when personality matters
> - Pair fonts that are similar but not identical (two geometric sans-serifs)

Android translation, with the two entries that do **not** transfer literally:
- "Set body text below 16px" is a **web** rule (16px = 1rem browser default). On Android the equivalent is the Material Body role in **sp**; 14sp Body is `BodyMedium`, which is standard Material and correct. Do not "fix" 14sp to 16sp. The transferable half is: never go below 12sp for anything a user must read, and never below `Caption`.
- "Use `rem`" → **use `sp`** (`android.md:26`, `audit.native.md:15`). `dp` for text is the violation to hunt.
- "Disable browser zoom" → the Android analogue is any layout that clips or overlaps at 130%/200% system font scale (`audit.native.md:15`: "layouts that clip or overlap at large sizes"). Test at max font scale before shipping.
- "Default to Inter/Roboto … when personality matters" is satisfied by Space Grotesk; see the conflict note in §10.

---

## 6. CRAFT RULES: COLOR

### 6.1 Platform rules (`android.md:28-33`, verbatim)

> ## Color & theming
>
> - **Material color roles** (primary, on-primary, surface, surface-variant, secondary-container, outline, error). Role tokens resolve light/dark and contrast variants automatically; raw hex breaks there.
> - **Dynamic Color (Material You)** where it fits: derive the scheme from the user's wallpaper on Android 12+, with a static fallback.
> - **Dark theme is a first-class scheme.** Design and test it; never a quick invert.
> - **Tonal elevation.** Convey elevation through the standard surface tonal levels (plus shadow where appropriate); no arbitrary drop shadows.

Practical rule: every color in a layout is `?attr/color*`. Raw `@color/` or `#RRGGBB` in a layout file is a finding unless it is a deliberately fixed brand asset (logo tint).

*Note on Dynamic Color:* `android.md:31` says "where it fits", not "always". A single-accent brand identity (Incy blue) is a legitimate reason to ship a static scheme; the audit requirement is that the static fallback exists and is coherent, not that wallpaper theming is enabled.

### 6.2 Product-register color (`product.md:18-24`, verbatim)

> Product defaults to Restrained. A single surface can earn Committed (a dashboard where one category color carries a report, an onboarding flow with a drenched welcome screen), but Restrained is the floor.
>
> - State-rich semantic vocabulary: hover, focus, active, disabled, selected, loading, error, warning, success, info. Standardize these.
> - Accent color used for primary actions, current selection, and state indicators only, not decoration.
> - A second neutral layer for sidebars, toolbars, and panels (slightly cooler or warmer than the content surface).

`colorize.md:11` restates it: "Product: semantic-first and almost always Restrained. Accent color is reserved for primary action, current selection, and state indicators. Not decoration. Every color has a consistent meaning across every screen."

The four color strategies (`SKILL.md:75-79`): **Restrained** ("tinted neutrals + one accent ≤10%. Product default"), **Committed** ("one saturated color carries 30–60% of the surface"), **Full palette**, **Drenched**. For this app the answer is Restrained everywhere, with at most one Committed surface (a buy/upgrade screen) if it earns it.

### 6.3 Contrast (non-negotiable)

`SKILL.md:34`, verbatim:
> - **Verify contrast.** Body text must hit ≥4.5:1 against its background; large text (≥18px or bold ≥14px) needs ≥3:1. Placeholder text needs the same 4.5:1, not the muted-gray default. The most common failure: muted gray body text on a tinted near-white. If the contrast is even close, bump the body color toward the ink end of the ramp; light gray "for elegance" is the single biggest reason AI designs feel hard to read.

`SKILL.md:35`: "Gray text on a colored background looks washed out. Use a darker shade of the background's own hue, or a transparency of the text color."

WCAG table (`colorize.md:207-212`): Body text AA 4.5:1 / AAA 7:1; Large text (18px+ or 14px bold) AA 3:1; **UI components, icons AA 3:1**; decorations none.

Dangerous combinations (`colorize.md:216-222`): "Light gray text on white (the #1 accessibility fail)"; red on green; blue on red; yellow on white; "Thin light text on images (unpredictable contrast)".

**Android application:** `colorOnSurfaceVariant` is the app's muted ink and is used by `Subtitle` (`styles.xml:98`) and `Caption` (`styles.xml:105`). Verify it against every surface tone in the `surfaceContainer*` ramp (`colors.xml:123` comment names that ramp) in **both** light and night values. A 13sp Subtitle is not "large text"; it needs the full 4.5:1.

### 6.4 Dark mode is a design, not an invert

`colorize.md:234-245`. Verbatim table (`colorize.md:238-243`):

| Light Mode | Dark Mode |
|---|---|
| Shadows for depth | Lighter surfaces for depth (no shadows) |
| Dark text on light | Light text on dark (reduce font weight) |
| Vibrant accents | Desaturate accents slightly |
| White backgrounds | Either pure black or a deep surface that fits the brand (a brand-tinted near-black at oklch 12-18% works too) |

`colorize.md:245`: "In dark mode, depth comes from surface lightness, not shadow. Build a 3-step surface scale where higher elevations are lighter … Reduce body text weight slightly (e.g. 350 instead of 400) because light text on dark reads as heavier than dark text on light."

This is the same instruction as `android.md:33` (tonal elevation) arriving from the color side. For an Incy pure-dark app: elevation = surface tone step, never `android:elevation` + a black shadow.

### 6.5 The color NEVER list (`colorize.md:123-130`, verbatim)

> - Use every color in the rainbow (choose 2-4 colors beyond neutrals)
> - Apply color randomly without semantic meaning
> - Put gray text on colored backgrounds. It looks washed out; use a darker shade of the background color or transparency instead
> - Violate WCAG contrast requirements
> - Use color as the only indicator (accessibility issue)
> - Make everything colorful (defeats the purpose)
> - Default to purple-blue gradients (AI slop aesthetic)

Plus `colorize.md:90`, restating the absolute ban: "**NEVER**: `border-left` or `border-right` greater than 1px as a colored accent stripe."
Plus `colorize.md:253`: "**Alpha Is A Design Smell.** Heavy use of transparency (rgba, hsla) usually means an incomplete palette. Alpha creates unpredictable contrast, performance overhead, and inconsistency. Define explicit overlay colors for each context instead. Exception: focus rings and interactive states." *Android note:* `colors.xml:38` `icon_tile_blue #334C8DFF` is an alpha value used as a tile fill; that is a deliberate surface-tint pattern (`colorize.md:88`: "**Surface tints**: A 4-8% background wash of the accent color instead of a stripe"), so it is sanctioned - but it should exist as one named token, not be re-derived ad hoc per layout.

The 60-30-10 rule as the skill actually means it (`colorize.md:193-201`): "This rule is about **visual weight**, not pixel count: 60% Neutral backgrounds, white space, base surfaces; 30% Secondary colors: text, borders, inactive states; 10% Accent: CTAs, highlights, focus states. The common mistake: using the accent color everywhere because it's 'the brand color.' Accent colors work *because* they're rare."

---

## 7. CRAFT RULES: INTERACTION

### 7.1 The Eight Interactive States (`interaction-design.md:5-16`, verbatim table)

| State | When | Visual Treatment |
|-------|------|------------------|
| **Default** | At rest | Base styling |
| **Hover** | Pointer over (not touch) | Subtle lift, color shift |
| **Focus** | Keyboard/programmatic focus | Visible ring (see below) |
| **Active** | Being pressed | Pressed in, darker |
| **Disabled** | Not interactive | Reduced opacity, no pointer |
| **Loading** | Processing | Spinner, skeleton |
| **Error** | Invalid state | Red border, icon, message |
| **Success** | Completed | Green check, confirmation |

`interaction-design.md:18`: "**The common miss**: Designing hover without focus, or vice versa. They're different. Keyboard users never see hover states."

**Android mapping.** Hover is real on Android (mouse, trackpad, Chromebook, DeX) but is never the primary path; `audit.native.md:49` bans "hover-dependent affordances". Focus is real and load-bearing on Android TV, hardware keyboards, and TalkBack linear navigation. So the Android state set is: **default · pressed · focused · selected · disabled · loading · error · success**, expressed through `android:state_pressed` / `state_focused` / `state_selected` / `state_enabled` in a `selector`, plus a `ColorStateList` rather than one-off colors. `product.md:32`: "Every interactive component has: default, hover, focus, active, disabled, loading, error. Don't ship with half of these."

Repo law already fixes the pressed treatment: `CLAUDE.md` "Every state designed (pressed = subtle scale, selected, disabled, empty, loading, error)". Press-in duration token exists: `motion_press_in` 90ms (`motion.xml:12`), press-out 160ms (`motion.xml:15`).

### 7.2 Focus rings (`interaction-design.md:20-42`)

`interaction-design.md:22`: "**Never `outline: none` without replacement.** It's an accessibility violation."
Focus ring design (`interaction-design.md:37-41`): "High contrast (3:1 minimum against adjacent colors)"; "2-3px thick"; "Offset from element (not inside it)"; "Consistent across all interactive elements".
*Android:* do not delete the default focus highlight from a custom background drawable without adding a `state_focused` layer that meets 3:1.

### 7.3 Forms (`interaction-design.md:43-46`, `clarify.md`)

- `interaction-design.md:45`: "**Placeholders aren't labels.** They disappear on input. Always use visible `<label>` elements. **Validate on blur**, not on every keystroke (exception: password strength). Place errors **below** fields with `aria-describedby` connecting them."
- *Android:* `TextInputLayout` with `android:hint` on the layout (floating label survives input) and `app:helperText` / `app:error`; never a bare `EditText` whose `hint` is the only label. `app:errorEnabled="true"` so the error slot does not shift layout when it appears.
- `taste-skill/SKILL.md:232`: "No placeholder-as-label. Ever." and `:231`: "Label ABOVE input. Helper text optional but present in markup. Error text BELOW input."

### 7.4 Loading & optimistic updates (`interaction-design.md:47-49`)

`interaction-design.md:49`: "**Optimistic updates**: Show success immediately, rollback on failure. Use for low-stakes actions (likes, follows), not payments or destructive actions. **Skeleton screens > spinners**: they preview content shape and feel faster than generic spinners."
`product.md:34`: "Skeleton states for loading, not spinners in the middle of content."
*For this app:* connect/disconnect and subscription purchase are **not** low-stakes; never optimistically claim "Подключено" before the tunnel is up.

### 7.5 Destructive actions: undo beats confirm (`interaction-design.md:153-155`)

`interaction-design.md:155`: "**Undo is better than confirmation dialogs.** Users click through confirmations mindlessly. Remove from UI immediately, show undo toast, actually delete after toast expires. Use confirmation only for truly irreversible actions (account deletion), high-cost actions, or batch operations."
*Android:* that "undo toast" is a **Snackbar with an action**, per `android.md:39` ("Snackbars for transient feedback (actionable when useful, never a toast for that)"). Deleting a server config should Snackbar-with-undo, not `AlertDialog`.

### 7.6 Overlays and clipping (`interaction-design.md:87-89`, `SKILL.md:65`)

`SKILL.md:65`: "Dropdowns rendered with `position: absolute` inside an `overflow: hidden` or `overflow: auto` container will be clipped."
*Android analogue, and it is a real bug class here:* a `PopupWindow` / `ListPopupWindow` / anchored menu inside a clipping parent, or a `RecyclerView` item whose overflow menu is clipped by `android:clipChildren="true"` / `clipToPadding`. Use `MaterialAlertDialog`, `BottomSheetDialogFragment`, or a `PopupMenu` anchored above the clipping boundary. And per `product.md:51`: "Modal as first thought. Modals are usually laziness. Exhaust inline / progressive alternatives first."

### 7.7 Gesture discoverability (`interaction-design.md:177-185`)

`interaction-design.md:179`: "Swipe-to-delete and similar gestures are invisible. Hint at their existence": "**Partially reveal**: Show delete button peeking from edge"; "**Onboarding**: Coach marks on first use"; "**Alternative**: Always provide a visible fallback (menu with 'Delete')". `interaction-design.md:185`: "Don't rely on gestures as the only way to perform actions."

### 7.8 The interaction avoid-list (`interaction-design.md:189`, verbatim)

> **Avoid**: Removing focus indicators without alternatives. Using placeholder text as labels. Touch targets <44x44px. Generic error messages. Custom controls without ARIA/keyboard support.

(On Android read "<44x44px" as "<48x48dp" per `android.md:20`, and "ARIA" as contentDescription + state announcements.)

---

## 8. CRAFT RULES: MOTION

### 8.1 Platform rule (`android.md:40`, verbatim)

> - **Material motion patterns.** Container transform, shared-axis, fade-through, with standard easing and durations; honor the system Remove animations setting with a crossfade or instant cut.

`animate.md:13`: on native, "system transitions and OS Reduce Motion, never the web tooling below."

**The system Remove-animations setting is this app's `prefers-reduced-motion`.** The repo already has the plumbing: `MotionUtils.animationsEnabled(context)` reads `Settings.Global.ANIMATOR_DURATION_SCALE` (`util/MotionUtils.kt:26-29`), plus `Context.animationsEnabled()` (`:38`) and `View.reducedMotion()` (`:50`). `motion.xml:5-8` documents the contract: "Honor reduced motion at the call site: when Settings.Global.ANIMATOR_DURATION_SCALE == 0 the system collapses these to 0 and views jump straight to the end state (see MotionUtils)."
**Any hand-rolled `ValueAnimator` / `ObjectAnimator` / `ViewPropertyAnimator` that does not consult this is an accessibility finding** (`audit.native.md:16`: "**Reduce Motion ignored**: parallax and large slides with no crossfade alternative").

### 8.2 Product-register motion (`product.md:38-42`, verbatim)

> - 150–250 ms on most transitions. Users are in flow; don't make them wait for choreography.
> - Motion conveys state, not decoration. State change, feedback, loading, reveal: nothing else.
> - No orchestrated page-load sequences. Product loads into a task; users don't want to watch it load.

`animate.md:11` restates: "Product: 150–250 ms on most transitions. Motion conveys state: feedback, reveal, loading, transitions between views. No page-load choreography; users are in a task and won't wait for it."

### 8.3 Duration ladder (`animate.md:103-110`, verbatim table) and the repo's tokens

| Duration | Use Case | Examples |
|----------|----------|----------|
| **100–150ms** | Instant feedback | Button press, toggle, color change |
| **200–300ms** | State changes | Menu open, tooltip, hover state |
| **300–500ms** | Layout changes | Accordion, modal, drawer |
| **500–800ms** | Entrance animations | Page load, hero reveal |

`animate.md:103`: "**Duration: the 100/300/500 rule.** Timing matters more than easing for 'feels right'."
`animate.md:124`: "**Exit animations are faster than entrances.** Use ~75% of enter duration."

Repo mapping (`values/motion.xml`), and it lines up cleanly:
- `motion_press_in` 90ms (`:12`) - "Finger-down press: quick acknowledgement (< 100ms feels instant)" → the 100-150ms feedback band, at the fast edge.
- `motion_press_out` 160ms (`:15`) - release/settle.
- `motion_state` 220ms (`:17`) - selection, enable/disable, color/elevation → the 200-300ms band and inside `product.md`'s 150-250ms.
- `motion_reveal` 300ms (`:19`) - show/hide, expand, sheet entrance.
- `motion_stagger` 40ms (`:22`) - "Cap the staggered count so the total never exceeds ~400ms."
- `motion_emphasis` 600ms (`:25`) - "reserve for the single primary action, never chrome."

`motion.xml:3-4` states the same law the skill does: "One tempo for the whole app … Ease-out only, no bounce. Exit is always faster than enter". **Hardcoded animation durations in Kotlin are drift; use `resources.getInteger(R.integer.motion_*)`.**

### 8.4 Easing (`animate.md:112-122`, verbatim)

```css
/* Recommended: natural deceleration */
--ease-out-quart: cubic-bezier(0.25, 1, 0.5, 1);    /* Smooth */
--ease-out-quint: cubic-bezier(0.22, 1, 0.36, 1);   /* Slightly snappier */
--ease-out-expo: cubic-bezier(0.16, 1, 0.3, 1);     /* Confident, decisive */

/* AVOID: feel dated and tacky */
/* bounce: cubic-bezier(0.34, 1.56, 0.64, 1); */
/* elastic: cubic-bezier(0.68, -0.6, 0.32, 1.6); */
```

`SKILL.md:56`: "Ease out with exponential curves (ease-out-quart / quint / expo). No bounce, no elastic."
*Android:* `PathInterpolator(0.16f, 1f, 0.3f, 1f)` (ease-out-expo) or Material's `MotionUtils`/`Easing` emphasized-decelerate. **Banned: `BounceInterpolator`, `OvershootInterpolator`, `AnticipateOvershootInterpolator`.**

### 8.5 Stagger (`SKILL.md:59`, `animate.md:56`)

`SKILL.md:59`: "Staggering the items within one list is legitimate. The tell is the uniform reflex (one identical entrance applied to every section), not motion itself; each reveal should fit what it reveals. Suppressing the reflex is never a reason to ship a page with no motion at all."
`animate.md:56`: "**List rhythm**: Sibling stagger is legitimate for cards-in-a-grid or list-items-appearing. Whole-section fade-on-scroll is not a list and is not legitimate. Cap total stagger time: 10 items at 50ms each = 500ms total. For more items, reduce per-item delay or cap the staggered count."
Repo: `motion_stagger` 40ms with the ~400ms total cap (`motion.xml:20-22`) → **cap the staggered item count at 10.**

### 8.6 Reveal must enhance an already-visible default (`SKILL.md:60`)

> Reveal animations must enhance an already-visible default. Don't gate content visibility on a class-triggered transition; transitions pause on hidden tabs and headless renderers, so the reveal never fires and the section ships blank.

*Android:* never start a view at `alpha=0` / `visibility=INVISIBLE` and rely on an animator to bring it in. If the animator is cancelled (config change, reduced motion, fragment detach, `onPause` before the post), the content is permanently invisible. Set the end state first, animate from a copied start state, and always set the end state in `onAnimationCancel`/`onAnimationEnd`.

### 8.7 The motion NEVER list (`animate.md:183-190`, verbatim)

> - Use bounce or elastic easing curves; they feel dated and draw attention to the animation itself
> - Animate layout properties casually (`width`, `height`, `top`, `left`, margins) when transform, FLIP, or grid-based techniques would work
> - Use durations over 500ms for feedback (it feels laggy)
> - Animate without purpose (every animation needs a reason)
> - Ignore `prefers-reduced-motion` (this is an accessibility violation)
> - Animate everything (animation fatigue makes interfaces feel exhausting)
> - Block interaction during animations unless intentional

`SKILL.md:55`: "Don't animate CSS layout properties unless truly needed." *Android:* animate `translationX/Y`, `alpha`, `scaleX/Y`, `rotation` (all composited); avoid animating `layoutParams`, `padding`, or `height` per-frame. `TransitionManager.beginDelayedTransition` with `AutoTransition`/`ChangeBounds` is the sanctioned FLIP equivalent for a real layout change.

`SKILL.md:61`: "Premium motion materials are not just transform/opacity. Blur, backdrop-filter, clip-path, mask, and shadow/glow are part of the palette when they materially improve the effect and stay smooth." *Android:* `RenderEffect` blur is API 31+ and expensive; per the glassmorphism ban it must be purposeful, not decorative, and needs a pre-31 fallback.

Perceived performance (`animate.md:164`): "The 80ms threshold: anything under ~80ms feels instant because our brains buffer sensory input for that long to synchronize perception. Target this for micro-interactions." Repo `motion_press_in` = 90ms is just over that line and is a deliberate choice, not an error.

---

## 9. `taste-skill`: what transfers to Android and what does not

`taste-skill/SKILL.md:8-9` scopes itself: "Landing pages, portfolios, and redesigns. Not dashboards, not data tables, not multi-step product UI." `:903` explicitly excludes "Native mobile (use Apple HIG / Material directly)."

**Do NOT apply to this repo:** Sections 1 (the three dials), 2 (design-system map), 3.A/3.B/3.E (React/Next/Tailwind stack), 4.1's font-pool lists, 4.3 (anti-center bias), 4.7 (hero/nav/bento/eyebrow/zigzag layout discipline), 5 (GSAP skeletons), 10 (pattern vocabulary), 12 (block library). All of it is web-landing-page material.

**Rules that DO transfer, because they are medium-independent craft or copy rules:**

| Rule | Source | Android form |
|---|---|---|
| **Em-dash ban, absolute** | `taste-skill:685-701`. Verbatim (`:687`): "**Em-dash (`—`) is COMPLETELY banned.** … There is no 'limited use' allowance, no 'natural language frequency' allowance, no 'in body copy is fine' allowance. None." `:693` extends it to en-dash used as a separator; `:695-697` permit only the regular hyphen and the minus sign. | Grep every `res/values*/strings*.xml` for `—` and `–`. This repo has 25 strings files (`strings.xml`, `strings_account.xml`, `strings_auth.xml`, `strings_buy.xml`, …). |
| **Copy self-audit before ship** | `taste-skill:321-326`: re-read every visible string; flag anything "Grammatically broken", with "unclear referents", that "Sounds like AI hallucination", or that "Reads like an LLM trying to sound thoughtful". "If unsure whether a string makes sense, replace it with a plain functional sentence. AI-generated cute copy is worse than boring copy." | Applies to Russian UI copy verbatim. Pairs with `clarify.md`'s voice rules. |
| **Fake-precise numbers flagged** | `taste-skill:327-330`; `:618` "NO fake-perfect numbers." | Ping values, traffic figures, device counts, tariff prices must be real data or explicitly mock. |
| **No generic placeholder identities** | `taste-skill:616-620` ("Jane Doe" effect, "Acme", filler verbs "Elevate / Seamless / Unleash / Next-Gen / Revolutionize") | Server names, account previews, empty-state examples. |
| **Color Consistency Lock** | `taste-skill:190`: "Once an accent color is chosen for a page, it is used on the WHOLE page… Pick one accent, lock it, audit every component before shipping." | One accent (Incy blue) across every screen; red only destructive. Matches `/home/user/dp/CLAUDE.md`. |
| **Shape Consistency Lock** | `taste-skill:217`: "Pick ONE corner-radius scale… Mixed systems are allowed only when there is a documented rule (e.g. 'buttons are full-pill, cards are 16px, inputs are 8px') and that rule is followed everywhere." | The repo HAS that documented rule: `radius_chip` 12dp, `radius_card` 20dp, `radius_tile` 12dp, `radius_pill` 100dp, `radius_sheet` 24dp (`dimens.xml:22-28`). Any other corner value is drift. |
| **Page Theme Lock** | `taste-skill:341-348`: "The page has ONE theme. Sections do not invert." | No light-surface screen inside a dark app; no per-screen theme overrides except the documented `ThemeOverlay.Mono` (`colors.xml:28`). |
| **Button contrast check** | `taste-skill:225`: mandatory a11y check that button text is readable against button background, "WCAG AA min (4.5:1 for body, 3:1 for large text 18px+)" | Every filled/tonal/outlined `MaterialButton` and every CTA. |
| **CTA wrap ban** | `taste-skill:226`: "Button text MUST fit on one line… (3 words max for primary CTAs, ideally 1-2)" | Russian labels are longer than English; check at 130% font scale. |
| **No duplicate CTA intent** | `taste-skill:227`: one label per intent across the whole surface | "Купить" vs "Оформить" vs "Продлить" must not all mean the same action. |
| **Form contrast check** | `taste-skill:228`: inputs, placeholders, focus rings, helper text, error text all pass AA | `TextInputLayout` hint/helper/error colors in dark theme. |
| **Full interactive-state cycles** | `taste-skill:220-224`: "LLMs default to 'static successful state only.' Always implement full cycles: Loading (skeletal loaders matching the final layout's shape, avoid generic circular spinners) · Empty States · Error States · Tactile Feedback (on `:active`… `scale-[0.98]`)" | Matches `CLAUDE.md`'s "pressed = subtle scale". |
| **Z-index restraint** | `taste-skill:548`: "NEVER spam arbitrary `z-50`… Document the z-index scale in a project constants file." `SKILL.md:51`: "Build a semantic z-index scale (dropdown → sticky → modal-backdrop → modal → toast → tooltip). Never arbitrary values like 999 or 9999." | Android: `translationZ`/`elevation` ordering and `ViewGroup` child order; no magic elevation numbers. |
| **Reduced motion mandatory** | `taste-skill:526-529` | Already covered by `MotionUtils`. |
| **No pure black** | `taste-skill:585`: "**No pure `#000000` and no pure `#ffffff`** - use off-black (zinc-950, near-black warm gray) and off-white. Pure values kill depth." `:601`: "**NO pure black (`#000000`).**" | ⚠️ **Direct tension with the repo's stated "Incy = pure dark".** See §10.4. |
| **No neon / outer glows** | `taste-skill:600`: "**NO neon / outer glows** by default. Use inner borders or subtle tinted shadows." | Matches `CLAUDE.md`'s "No decorative gradients/glows" and "no ripple glow on nav". |
| **Emoji discouraged** | `taste-skill:148`: "Discouraged by default in code, markup, and visible text. Replace symbols with icon-library glyphs." | Matches `CLAUDE.md`'s "No emoji as UI chrome". |
| **One icon family** | `taste-skill:144`: "One family per project."; `:145` "Standardize `strokeWidth` globally" | `audit.native.md:50` says the same as "Icon drift: mixed icon sets instead of … Material Symbols". Repo law: "unified server icon". |

---

## 10. Conflicts between sources, and how to resolve them (do not "fix" these)

An implementing agent reading these skills cold will try to change four things that are already correct. Each resolution is grounded in the skills themselves.

### 10.1 Space Grotesk is on the reflex-reject font list, and that does not apply here

`brand.md:32` lists "Space Grotesk" and "Space Mono" among training-data default fonts to ban. The repo's brand face is Space Grotesk (`styles.xml:57`, `:66`, `:75`, `:110`, `:121`).

**Resolution, three independent reasons:**
1. `brand.md:1-3` scopes the whole file to the **brand register** ("brand sites, landing pages, marketing surfaces… The deliverable is the design itself"). This app is product register (`SKILL.md:22`, `product.md:3`).
2. `brand.md:42`, verbatim: "The reflex-reject lists apply to **new design choices**. When the existing brand has already committed to a font or a lane as part of its identity, identity-preservation wins; variants on an existing surface don't second-guess what's already shipping."
3. `SKILL.md:24` (Setup step 6) makes identity preservation the general rule: "**Skip this step only if step 3 found committed brand colors in existing tokens; in that case identity-preservation wins.**"

Additionally `android.md:25` sanctions exactly what the repo did: "**Roboto is the system face**; theme a brand face in through the type scale". Space Grotesk on headings/labels/numerics + system face on body (`styles.xml:88-107`) is that pattern, and it also satisfies `SKILL.md:40` (pair on a contrast axis or use one family in multiple weights) since Space Grotesk is geometric and the system face is neo-grotesque.

### 10.2 The blue accent is not a "reflex blue"

`colorize.md:168`: "The hue you pick is a brand decision and should not come from a default. Do not reach for blue (hue 250) or warm orange (hue 60) by reflex."
`colors.xml:3-4` documents provenance: "Brand: departament VPN blue (sampled from logo)" `#1E5FC7`. Sampled from an existing logo is the definition of not-a-reflex, and `SKILL.md:24` gives identity preservation priority. Do not re-hue the app.

### 10.3 44 vs 48, px/rem vs sp

`layout.md:137`, `polish.md:197`, `critique.md:737`, `interaction-design.md:189` all say 44×44px (a web/iOS number). `android.md:20` and `audit.native.md:17` say **48×48 dp with ≥8dp between targets**. On Android, 48dp wins; treat 44 as a floor that is already exceeded.
Same shape for units: `typeset.md:119` "Use `rem`" → `android.md:26` "**sp units, never fixed px**".
Same shape for body size: `typeset.md:117` "Set body text below 16px" is a browser-root-size rule and does not mean 16sp on Android; the Material Body roles govern (`android.md:24`).

### 10.4 "Pure dark" vs "no pure black"

`/home/user/dp/CLAUDE.md` defines Incy as "pure dark + ONE bright blue accent". `taste-skill:585` and `:601` ban pure `#000000`. `colorize.md:243` is the tiebreaker and permits both: dark mode backgrounds are "Either pure black or a deep surface that fits the brand (a brand-tinted near-black at oklch 12-18% works too)".

**Operational reading:** "pure dark" as an aesthetic (no washed-out grey-blue chrome, no light surfaces) is compatible with the skills. But `colorize.md:245` still requires "a 3-step surface scale where higher elevations are lighter", and `android.md:33` requires tonal elevation. So: an OLED-black `colorSurface` is allowed; a **flat** palette where surface, surfaceContainer, surfaceContainerHigh are all `#000000` is not, because it destroys the elevation channel. `colors.xml:123` shows the repo already ships a "Surface container ramp … enables layering & glass depth", so the ramp exists - verify the night values keep real separation.

### 10.5 Eyebrows: the skills and the repo agree

`SKILL.md:90` bans the tiny uppercase tracked eyebrow; `taste-skill:253-257` caps eyebrows at 1 per 3 sections with a mechanical count; `/home/user/dp/CLAUDE.md` says "Section headers are sentence-case bold - NOT tiny ALL-CAPS tracked eyebrows." No conflict. `TextAppearance.App.Chip` (11sp, `letterSpacing 0.04`, `styles.xml:109-115`) is for **chips and badges**, which is a legitimate use; it must not be repurposed as a section eyebrow.

---

## 11. THE OPERATIONAL CHECKLIST

This is the deliverable: one ordered procedure an implementing agent follows for any UI change in `/home/user/dp`.

### Phase 0 - Load (mandatory, before touching a file)

- [ ] Confirm platform = `android`. Load `reference/android.md` (`SKILL.md:23`) **and** `reference/product.md` (`SKILL.md:22`, app UI = product register).
- [ ] Load the sub-command reference, **native variant where one exists**: `audit` → `audit.native.md`; `adapt` → `adapt.native.md`. One file, not both (`SKILL.md:20`).
- [ ] Read at least one existing project file before designing (`SKILL.md:21`): `values/styles.xml`, `values/dimens.xml`, `values/motion.xml`, `values/colors.xml`, `values/themes.xml`. "Don't reinvent the wheel; use what's there when it works."
- [ ] Do **not** run `detect.mjs`, `live`, `live-server.mjs`, or any browser overlay: web-only (`SKILL.md:142`).
- [ ] Do **not** run the palette script: committed brand colors exist (`SKILL.md:24`, `colors.xml:3-5`).
- [ ] Read `/home/user/dp/CLAUDE.md` design law and honor every prior owner request listed there.

### Phase 1 - Shape (before code, for any new surface)

- [ ] `craft.md:44`: "Run /impeccable shape… Shape is **required** for craft." `craft.md:46`: "Present the shape output and stop. Wait for the user to confirm, override, or course-correct before writing code."
- [ ] Brief must pin: primary user action; **Key States** list (`shape.md:145`: "default, empty, loading, error, success, edge cases"); interaction model; content requirements including every label, empty-state message, and error message; color strategy (Restrained for product); scope/fidelity.
- [ ] `shape.md:28`: "**Assert-then-confirm, not menu-with-escape.**" When the answer is obvious from PRODUCT/CLAUDE.md, name it and ask for confirmation; do not present a 4-option menu.
- [ ] Skip the visual-direction-probe step only with the required one-line announcement (`craft.md:73`, `shape.md:80`).

### Phase 2 - Structure (platform conformance first)

- [ ] Navigation is Material and size-matched: bottom navigation bar 3-5 destinations on compact; rail/drawer on expanded (`android.md:13`). No untouched phone bottom-bar on tablet.
- [ ] System Back works everywhere; predictive Back honored; no gesture hijack, no traps (`android.md:14`).
- [ ] Edge-to-edge with status bar, navigation bar, display cutout **and IME** insets applied (`android.md:15`).
- [ ] Top app bar carries screen context; at most one FAB, for the single primary action (`android.md:16`, `:38`).
- [ ] No iOS transplants: no Cupertino switches/dialogs/pickers, no back-chevron-only navigation, no action sheets in place of bottom sheets (`android.md:9`, `adapt.native.md:29-37`).
- [ ] No web-shaped controls, no hover-dependent affordance (`audit.native.md:49`).
- [ ] Snackbar for transient feedback (actionable where useful); dialog only for a decision that must interrupt (`android.md:39`). Prefer undo-Snackbar over confirm-dialog for reversible destruction (`interaction-design.md:155`).
- [ ] Structure driven by window size classes, never device-model checks (`adapt.native.md:47`). Landscape restructures rather than clips; orientation is not locked to dodge a bug (`adapt.native.md:21`, `:57`).

### Phase 3 - Layout & spacing

- [ ] Every spacing value comes from `@dimen/space_4|8|12|16|24|32` (`dimens.xml:14-19`). Zero off-scale dp literals (`layout.md:147`).
- [ ] One 16dp screen gutter (`@dimen/screen_gutter`, `dimens.xml:34`).
- [ ] Spacing varies for rhythm: tight within a group, generous between groups (`layout.md:86-88`). Not every gap is 16.
- [ ] Squint test passes: primary, secondary, and groupings identifiable blurred (`layout.md:46`).
- [ ] Hierarchy uses 2-3 dimensions (size + weight + space), not size alone (`layout.md:115`, `typeset.md:89`).
- [ ] No nested cards, anywhere (`SKILL.md:48`, `layout.md:150`, `distill.md:54`).
- [ ] No card used where spacing and a divider would group just as well (`layout.md:108`, `distill.md:55`).
- [ ] No repeated identical icon+title+subtitle card grid as the whole screen (`SKILL.md:89`).
- [ ] Corner radii only from `radius_chip 12` / `radius_card 20` / `radius_tile 12` / `radius_pill 100` / `radius_sheet 24` (`dimens.xml:22-28`; Shape Consistency Lock, `taste-skill:217`).
- [ ] Tiles 40dp with 22dp glyphs (`@dimen/tile_size`, `@dimen/tile_glyph`, `dimens.xml:31-32`); rows ≥ `@dimen/row_min_height` 56dp (`dimens.xml:33`).
- [ ] Every touch target ≥48×48dp with ≥8dp separation (`android.md:20`). Expand the hit rect where the glyph is smaller.
- [ ] Elevation via Material tonal surfaces, not arbitrary shadow (`android.md:33`, `colorize.md:245`).

### Phase 4 - Typography

- [ ] Zero `android:textSize` literals; every `TextView` uses `@style/TextAppearance.App.{Display|Headline|Title|Title.Medium|Body|Subtitle|Caption|Chip|Numeric}` (`styles.xml:56-129`).
- [ ] All text sizes are `sp`, never `dp`/`px` (`android.md:26`, `audit.native.md:15`).
- [ ] Section headers are sentence-case bold `TextAppearance.App.Title`; no `textAllCaps` eyebrow above sections (`SKILL.md:90`, `/home/user/dp/CLAUDE.md`).
- [ ] Live-updating numerals use `TextAppearance.App.Numeric` so digits do not jitter (`styles.xml:117-129`, `typeset.md:102`).
- [ ] No display font in buttons, labels, or data rows beyond the defined roles (`product.md:48`).
- [ ] Light-on-dark compensation checked on all three axes: line-height, letter-spacing, weight (`typeset.md:182`).
- [ ] No new size introduced adjacent to an existing one (no 15sp between 14 and 16) (`typeset.md:44`).
- [ ] Layout survives 130% and 200% system font scale without clipping or overlap (`audit.native.md:15`).
- [ ] Long strings: `maxLines` + `ellipsize`, `layout_weight` instead of fixed widths, tested with the longest Russian label (`SKILL.md:92`, `harden.md:88-99`).

### Phase 5 - Color & theme

- [ ] Every color is `?attr/color*` (Material role). No `#RRGGBB` in layouts; `@color/` only for genuinely fixed brand assets (`android.md:30`, `audit.native.md:35`).
- [ ] One accent (Incy blue) for primary action, current selection, and state indicators only. Not decoration (`product.md:22`, `colorize.md:11`).
- [ ] Red reserved for destructive/error; success/warning/info have single fixed meanings across all screens (`colorize.md:119-121`).
- [ ] Body/subtitle/caption text ≥4.5:1 against **every** surface tone it lands on, in light and night (`SKILL.md:34`, `colorize.md:207`). Icons and UI components ≥3:1.
- [ ] No gray text on a colored fill; use a darker shade of that fill's hue or an alpha of the text color (`SKILL.md:35`, `colorize.md:126`).
- [ ] Color is never the only signal for state (`colorize.md:128`, `critique.md:686`).
- [ ] Dark is designed, not inverted; the surface ramp keeps real tonal separation for elevation (`android.md:32`, `colorize.md:234-245`).
- [ ] No decorative gradients, no gradient text, no glow, no glassmorphism-by-default (`SKILL.md:86-87`, `taste-skill:600-604`).
- [ ] No 4dp+ colored side stripe on any row, card, or callout (`SKILL.md:85`). Use a full 1dp stroke, a 4-8% accent surface tint (`colorize.md:88`), or a leading icon tile.

### Phase 6 - States (every one, every component)

- [ ] default · pressed · focused · selected · disabled · loading · error · success, via `selector` + `ColorStateList`, not per-instance colors (`interaction-design.md:5-16`, `product.md:32`).
- [ ] Pressed = subtle scale at `motion_press_in` 90ms / `motion_press_out` 160ms (`motion.xml:12`, `:15`; `/home/user/dp/CLAUDE.md`).
- [ ] Focus visible and ≥3:1 against adjacent color; never removed without replacement (`interaction-design.md:22`, `:38`).
- [ ] Disabled is clearly non-interactive and not a full-saturation accent (`product.md:50`).
- [ ] Loading is a skeleton shaped like the final content, not a centered spinner over content (`product.md:34`, `interaction-design.md:49`, `taste-skill:221`).
- [ ] Empty state teaches: what appears here, why it matters, one clear action (`onboard.md:170-183`, `product.md:35`, `clarify.md:92-99`).
- [ ] Error state names the problem in plain language and offers recovery, with a retry affordance for network failures (`clarify.md:200-212`, `harden.md:141-155`).
- [ ] Success confirms what happened and what is next (`clarify.md:101-109`).
- [ ] Double-submit prevented (button disabled while in flight) (`harden.md:203-206`).

### Phase 7 - Motion

- [ ] All durations come from `@integer/motion_*` (`motion.xml:12-25`); no hardcoded ms in Kotlin.
- [ ] Most transitions land in 150-250ms (`product.md:39`); feedback under 150ms; nothing over 500ms except the single `motion_emphasis` hero moment.
- [ ] Exit is faster than enter (`animate.md:124`, `motion.xml:5`).
- [ ] Ease-out only. No `BounceInterpolator`, no `OvershootInterpolator`, no elastic (`SKILL.md:56`, `animate.md:119-121`).
- [ ] Every animator consults `View.reducedMotion()` / `MotionUtils.animationsEnabled()` and collapses to an instant cut or crossfade (`MotionUtils.kt:26,38,50`; `android.md:40`; `animate.md:188`).
- [ ] No content starts invisible waiting on an animator; the visible state is the default (`SKILL.md:60`).
- [ ] List stagger ≤10 items at 40ms (`motion.xml:20-22`, `animate.md:56`).
- [ ] No page-load choreography; the screen loads into the task (`product.md:42`).
- [ ] Animate `translationX/Y`, `alpha`, `scale`, `rotation`. Layout changes go through `TransitionManager`, not per-frame `layoutParams` (`animate.md:185`, `optimize.md:113`).
- [ ] Every animation can be justified in one sentence: hierarchy, storytelling, feedback, or state transition (`taste-skill:360`, `animate.md:187`).

### Phase 8 - Copy (Russian, sentence case, active verbs)

- [ ] Buttons are verb + object, never "OK" / "Готово" as a catch-all (`clarify.md:186-193`).
- [ ] Errors answer: what happened, why, how to fix (`clarify.md:202`). Templates at `clarify.md:206-212`.
- [ ] Never blame the user (`clarify.md:214-216`); never use humor in an error (`clarify.md:234`).
- [ ] One term per concept across the whole app; build and enforce the glossary (`clarify.md:257-268`).
- [ ] No redundant copy: if the heading says it, the body does not repeat it (`clarify.md:270-272`, `distill.md:78`).
- [ ] Loading copy is specific ("Сохраняем черновик…"), not "Загрузка…" (`clarify.md:274-276`).
- [ ] Confirmation dialogs name the action and its consequence; buttons say the action, not "Да"/"Нет" (`clarify.md:278-280`).
- [ ] Zero em-dashes and zero en-dash separators in any `strings*.xml` (`taste-skill:685-701`).
- [ ] Copy self-audit run over every visible string before declaring done (`taste-skill:321-326`).
- [ ] No emoji as UI chrome (`taste-skill:148`, `/home/user/dp/CLAUDE.md`).

### Phase 9 - Harden (`harden.md`)

- [ ] Extreme inputs: 100+ char names, empty, single char, emoji, RTL, CJK, huge numbers, 1000+ list items, zero items (`harden.md:7-14`, `:336-345`).
- [ ] Every network failure path has a message and a retry (`harden.md:141-147`).
- [ ] Each API status handled distinctly: 400 validation, 401 re-auth, 403 permission, 404 not-found, 429 rate limit, 500 generic + support (`harden.md:166-172`).
- [ ] State survives process death, rotation, and app switch (`critique.md:715`, `:740`).
- [ ] Never block the whole interface because one component errored (`harden.md:331`).

### Phase 10 - Verify, then polish

- [ ] `layout.md:163` discipline: answer every verification item "by citing the file, selector, or value that satisfies it; never a bare yes."
- [ ] Run the native audit (§3) and record the 5 dimension scores plus the Platform Conformance Verdict.
- [ ] Run the polish checklist (`polish.md:184-205`), which requires: aligned to the design system with drift named by **root cause** (`polish.md:13`: missing token / one-off implementation / conceptual misalignment); IA and flow shape matching neighbouring features; all interactive states; contrast AA; focus visible; no console errors; reduced motion respected; code clean.
- [ ] `polish.md:47`: "Polish is the last step, not the first. Don't polish work that's not functionally complete."
- [ ] `polish.md:5`: "Detector and automated QA output are defect evidence only. A clean script result is never proof that the design is strong."
- [ ] `craft.md:109`: write an honest critique against the brief and the DON'Ts, patch material defects, re-inspect. "**Don't invent defects to demonstrate iteration.** A confident 'first pass clean, shipping' beats a fake fix."
- [ ] Exit bar (`craft.md:111`): "defensible in a high-end studio review."

### Phase 11 - Cleanup (`polish.md:234-242`)

- [ ] Replace any custom implementation that duplicates an existing shared component or style.
- [ ] Delete orphaned drawables/styles/layouts made obsolete by the change.
- [ ] Consolidate: any new value introduced should become a token in `dimens.xml` / `motion.xml` / `colors.xml`, or be removed.

---

## 12. The distill pass (`distill.md`), because this app has settings screens

`distill.md:26`: "**CRITICAL**: Simplicity is not about removing features. It's about removing obstacles between users and their goals. Every element should justify its existence."
`distill.md:37`: "Simplification is hard. It requires saying no to good ideas to make room for great execution. Be ruthless."

Applicable moves (`distill.md:43-79`):
- IA: "Clear hierarchy: ONE primary action, few secondary actions, everything else tertiary or hidden"; "Progressive disclosure: Hide complexity behind clear entry points"; "Remove redundancy: If it's said elsewhere, don't repeat it here".
- Visual: "Reduce color palette: Use 1-2 colors plus neutrals, not 5-7"; "Limit typography: One font family, 3-4 sizes maximum, 2-3 weights"; "Remove decorations: Eliminate borders, shadows, backgrounds that don't serve hierarchy or function"; "Flatten structure… never nest cards inside cards"; "Consistent spacing: Use one spacing scale, remove arbitrary gaps".
- Interaction: "Reduce choices"; "Smart defaults"; "Remove steps"; "Clear CTAs: ONE obvious next step, not five competing actions".
- Content: "Shorter copy: Cut every sentence in half, then do it again"; "Active voice"; "Remove jargon".

The distill NEVER list (`distill.md:86-92`), verbatim:
> - Remove necessary functionality (simplicity ≠ feature-less)
> - Sacrifice accessibility for simplicity (clear labels and ARIA still required)
> - Make things so simple they're unclear (mystery ≠ minimalism)
> - Remove information users need to make decisions
> - Eliminate hierarchy completely (some things should stand out)
> - Oversimplify complex domains (match complexity to actual task complexity)

Pair with `critique.md:308`'s cognitive-load scoring: a settings hub failing 4+ of the 8 checklist items is a critical fix, and the fix is progressive disclosure, not smaller type.

---

## 13. Quick token inventory (verified, for citing in future reports)

| Token | Value | File:line |
|---|---|---|
| `space_4 / 8 / 12 / 16 / 24 / 32` | 4/8/12/16/24/32dp | `values/dimens.xml:14-19` |
| `radius_chip` | 12dp | `values/dimens.xml:22` |
| `radius_card` | 20dp | `values/dimens.xml:23` |
| `radius_tile` | 12dp | `values/dimens.xml:24` |
| `radius_pill` | 100dp | `values/dimens.xml:26` |
| `radius_sheet` | 24dp | `values/dimens.xml:28` |
| `tile_size` / `tile_glyph` | 40dp / 22dp | `values/dimens.xml:31-32` |
| `row_min_height` | 56dp | `values/dimens.xml:33` |
| `screen_gutter` | 16dp | `values/dimens.xml:34` |
| `TextAppearance.App.Display` | 34sp / 700 / -0.02 | `values/styles.xml:56-63` |
| `TextAppearance.App.Headline` | 24sp / 700 / -0.01 | `values/styles.xml:65-72` |
| `TextAppearance.App.Title` | 16sp / 700 / 0.0 | `values/styles.xml:74-81` |
| `TextAppearance.App.Title.Medium` | 16sp / 500 | `values/styles.xml:83-86` |
| `TextAppearance.App.Body` | 14sp / 0.01 / onSurface | `values/styles.xml:88-93` |
| `TextAppearance.App.Subtitle` | 13sp / 0.01 / onSurfaceVariant | `values/styles.xml:95-100` |
| `TextAppearance.App.Caption` | 12sp / 0.02 / onSurfaceVariant | `values/styles.xml:102-107` |
| `TextAppearance.App.Chip` | 11sp / 500 / 0.04 | `values/styles.xml:109-115` |
| `TextAppearance.App.Numeric` | tnum + lnum on | `values/styles.xml:117-129` |
| `motion_press_in` | 90ms | `values/motion.xml:12` |
| `motion_press_out` | 160ms | `values/motion.xml:15` |
| `motion_state` | 220ms | `values/motion.xml:17` |
| `motion_reveal` | 300ms | `values/motion.xml:19` |
| `motion_stagger` | 40ms | `values/motion.xml:22` |
| `motion_emphasis` | 600ms | `values/motion.xml:25` |
| `brand_blue` / `brand_blue_dark` | `#1E5FC7` / `#17469A` | `values/colors.xml:4-5` |
| reduced-motion helpers | `animationsEnabled()`, `View.reducedMotion()` | `util/MotionUtils.kt:26, 38, 50` |

---

## 14. One-paragraph summary for an implementing agent

For any UI change in `/home/user/dp`: the platform reference is `android.md` (Material 3 is the rulebook, brand themes through it), the register reference is `product.md` (earned familiarity, Restrained color, 150-250ms motion, no modal-first, no display fonts in labels), and `audit` routes to `audit.native.md`, never `audit.md`. The eight Absolute Bans in `SKILL.md:85-92` apply unconditionally and translate to XML as: no colored side-stripe drawables, no shader gradients on text, no decorative blur, no fake hero metrics, no endless identical card grids, no ALL-CAPS tracked eyebrows, no `01 /02 /03` section numbering, no text that clips at large font scales. The three slop tests stack: would a fluent Android user trust every screen; would a Linear/Raycast user pause at a subtly-off component; could anyone guess the palette from the category. All spacing, radii, type, motion, and color come from the tokens already in `values/` and nowhere else, every touch target is 48dp, every text size is `sp` via a `TextAppearance.App.*` role, every color is a `?attr/color*` role at ≥4.5:1 for body text in both themes, every interactive element ships all eight states, and every animator asks `MotionUtils` before it runs. `taste-skill` is out of scope for native UI except its medium-independent rules: the absolute em-dash ban, the copy self-audit, one accent locked across the app, one radius system, contrast checks on every CTA and form, and full loading/empty/error state cycles.
