# Verification: `showTab` fade-through race (rapid tab switching shows the wrong tab)

**Claim under test:** A→B then B→C inside the 150 ms fade leaves B on screen while `selectedNavId`
and the nav pill say C, because `withEndAction` does not run on cancel.

**Verdict: CONFIRMED — REAL, and worse than reported.**
The mechanism is exactly as described. Two details in the report are wrong or understated, and both
make the bug *more* severe, not less. Corrected description in §5.

Adversarial posture: I tried five separate ways to refute this (a click debounce, an in-flight
guard, `outgoing.animate().cancel()` covering the case, animations not running on a `GONE` view, and
`View.animate()` returning fresh instances). All five failed. Details in §4.

---

## 1. The code

`/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt:452-494`

```kotlin
452  private fun showTab(tab: Int, previous: Int = tab) {
...
466      val incoming = tabGroup(tab)
467      val outgoing = tabGroup(previous)?.takeIf { previous != tab }
468
469      // Instant swap on the initial paint, a same-tab reselect, or reduced motion.
470      if (incoming == null || outgoing == null || binding.homeRoot.reducedMotion()) {
471          binding.groupHome.isVisible = tab == R.id.nav_home
472          binding.groupServers.isVisible = tab == R.id.nav_servers
473          binding.groupSettings.root.isVisible = tab == R.id.nav_settings
474          binding.groupAccount.isVisible = tab == R.id.nav_account
475          maybeRevealServersTab(tab)
476          return
477      }
...
481      binding.bottomNav.tickHaptic()
482      val dy = 8f * resources.displayMetrics.density
483      outgoing.animate().cancel()
484      outgoing.animate().alpha(0f).setDuration(150).setInterpolator(easeStandard).withEndAction {
485          outgoing.isVisible = false
486          outgoing.alpha = 1f
487          incoming.alpha = 0f
488          incoming.translationY = dy
489          incoming.isVisible = true
490          incoming.animate().alpha(1f).translationY(0f)
491              .setDuration(200).setInterpolator(easeOutQuint).start()
492          maybeRevealServersTab(tab)
493      }.start()
494  }
```

**Citation correction:** the report gives `MainActivity.kt:466-478`. Line 466 is only
`val incoming = tabGroup(tab)`; the animation and the `withEndAction` that carry the defect are at
**`MainActivity.kt:483-493`**. (The original audit entry, `docs/agents/audit-android-ui.md:71-88`,
carries the same stale range.)

The end-action lambda (485-492) closes over `incoming`, `outgoing`, `tab` and `dy` from *its own*
invocation. There is **no** generation counter, no `tab == selectedNavId` recheck, and no reference
to the in-flight transition. That absence is the root cause.

Caller: `MainActivity.kt:352-357`

```kotlin
352  private fun selectNav(navId: Int) {
353      val previous = selectedNavId
354      selectedNavId = navId       // committed immediately
355      updateNavSelection(previous) // nav pill + label weight repaint immediately
356      showTab(navId, previous)     // content swap deferred 150 ms
357  }
```

`selectedNavId` (354) and the nav pill (355, via `updateNavSelection` → `navDot(id)?.visibility` at
`:393`) update **synchronously**, while the content swap is deferred by 150 ms. That is precisely
the split the report describes: chrome says C, content says B.

## 2. The trace (A = Home, B = Servers, C = Settings)

| t | event |
|---|---|
| 0 | Tap Servers. `selectNav(nav_servers)`: `selectedNavId = servers` (`:354`), pill moves to Servers (`:393`). `showTab(servers, home)`: `incoming = groupServers`, `outgoing = groupHome` (`:466-467`). `groupHome.animate().cancel()` (`:483`, no-op — nothing running). `groupHome` alpha 1→0 over 150 ms starts, end-action **E1** attached (`:484-493`). |
| t₂ ∈ (0,150) | Tap Settings. `selectNav(nav_settings)`: `selectedNavId = settings`, pill moves to Settings. `showTab(settings, servers)`: `incoming = groupSettings`, `outgoing = groupServers`. `groupServers.animate().cancel()` (`:483`) — **no-op, `groupServers` has no animation; `groupHome`'s animation is untouched.** `groupServers` alpha 1→0 over 150 ms starts (on a still-`GONE` view), end-action **E2** attached. |
| 150 | **E1** fires: `groupHome.isVisible = false` (`:485`), `groupHome.alpha = 1f` (`:486`), `groupServers.alpha = 0f` (`:487`), `groupServers.translationY = dy` (`:488`), `groupServers.isVisible = true` (`:489`), then `groupServers.animate().alpha(1f).translationY(0f)…start()` (`:490-491`). That `alpha(1f)` call **cancels E2's in-flight alpha animation on the same per-View `ViewPropertyAnimator`**. |
| t₂+150 | E2's animator ends *cancelled*. **E2 never runs.** `groupSettings` is never made `VISIBLE`. |

**Resulting state:** `groupHome` GONE, `groupServers` **VISIBLE** (fading to alpha 1),
`groupSettings` GONE, `selectedNavId == R.id.nav_settings`, pill under Settings.
The user tapped Settings and is looking at Servers. **Claim confirmed.**

## 3. Platform-contract proof

The claim rests on two AOSP behaviours. Both verified against
`platform/frameworks/base/core/java/android/view/ViewPropertyAnimator.java` (`refs/heads/main`):

1. **`withEndAction` javadoc, verbatim:** *"Specifies an action to take place when the next
   animation ends. The action is only run if the animation ends normally; if the ViewPropertyAnimator
   is canceled during that animation, the runnable will not run."*
   Implementation, `AnimatorEventListener.onAnimationCancel`:
   ```java
   if (mListener != null) { mListener.onAnimationCancel(animation); }
   if (mAnimatorOnEndMap != null) { mAnimatorOnEndMap.remove(animation); }
   ```
   The end runnable is removed from the map on cancel, so the subsequent `onAnimationEnd` finds
   nothing to run. The report's "documented not to run on cancel" is literally correct.

2. **A new animation on a property cancels the running one.** `animatePropertyBy` opens with
   *"First, cancel any existing animations on this property"*, scans `mAnimatorMap`, and calls
   `animatorToCancel.cancel()`. The in-code comment is decisive:
   *"Note that it's safe to break out here because every new animation on a property will cancel a
   previous animation on that property, so there can only ever be one such animation running."*
   E2's animator drives only `alpha`, so its property mask empties and the whole animator is
   cancelled — not merely re-targeted.

I could not read the platform sources locally (`/opt/android-sdk` ships `platforms/*/android.jar`
stubs only — `javap -p` on `android.view.View` exposes `public ViewPropertyAnimator animate()` and
no fields). The per-View singleton is nonetheless established: the `ViewPropertyAnimator` class
javadoc states *"Calls to View.animate() will return a reference to the appropriate
ViewPropertyAnimator object for that View"*, and the invariant asserted by the comment above
("there can only ever be one such animation running" per property) is only true if every
`view.animate()` call resolves to the same instance. The app's own code depends on it too:
`outgoing.animate().cancel()` (`:483`) followed by `outgoing.animate().alpha(0f)` (`:484`) is
meaningless unless both calls return the same object — and the file repeats that idiom nine more
times (`:1691-1692`, `:1693-1694`, `:1697/1700`, `:1762-1763`, `:1764-1765`, `:1766-1767`,
`:1807-1808`, `:1836`, `:1842`, `:1872`).

## 4. Refutation attempts (all failed)

- **Is there a click debounce?** No. `MainActivity.kt:339-344` wires four bare
  `setOnClickListener { selectNav(...) }` with no throttle. Repo-wide grep for
  `SystemClock.elapsedRealtime|lastClick|onSingleClick|throttle|debounce` across `**/*.kt` returns
  only `MainActivity.kt:1585-1586` (a VPN-restart timeout) and `auth/AccountCache.kt:13,36,52` (TTL
  cache) — nothing click-related.
- **Is there an in-flight/animating guard?** No. Grep for
  `isAnimating|tabSwitching|transitionInFlight|animInProgress` across `**/*.kt` returns zero hits.
  `selectNav` (`:352`) has no re-entrancy guard and no `if (navId == selectedNavId) return`.
- **Does `outgoing.animate().cancel()` (`:483`) cover it?** No — and this is the subtle part. It
  cancels the animator of the *newly* outgoing view, never the one actually in flight. It *does*
  correctly serialise the case where the same view is outgoing twice (A→B→A→B: the third call finds
  `groupHome` still animating, cancels it, and its stale E1 is correctly discarded). That partial
  correctness is presumably why the guard reads as sufficient. The gap is exactly the case where the
  second switch has a *different* outgoing view.
- **Do animations run on a `GONE` view?** Yes. `ViewPropertyAnimator.start()` creates and starts a
  `ValueAnimator` driven by the thread's `AnimationHandler`/`Choreographer`; it is not gated on view
  visibility. E2's animator is live and in `mAnimatorMap` when E1 fires at t=150, which is what makes
  it cancellable. (It is also guaranteed still running: E2 started at t₂>0 and ends at t₂+150>150.)
- **Could `View.animate()` return fresh instances, so both animations coexist and E2 still fires?**
  That is the only escape, and it is closed by §3. It is also worth noting this is the *only*
  hypothesis under which the code self-heals — if both animators ran, E2 would fire at t₂+150 and set
  `groupServers.isVisible = false` / `groupSettings.isVisible = true`, producing correct final state.
  The defect hinges entirely on the cancel, and the cancel is real.

## 5. Corrected description — two ways the report understates it

### 5a. It does NOT recover "until the next tab switch" — the stale group leaks permanently

The report ends with *"until the next tab switch."* That is wrong. `showTab` only ever hides
`tabGroup(previous)` (`:467`, `:485`). After the race, `previous` is C (`groupSettings`), which is
already `GONE`. **Nothing in the animated path ever hides the stale-visible B.**

Continuing the trace — user now taps Account (D):
`showTab(nav_account, nav_settings)` → `outgoing = groupSettings` (already GONE, alpha 1), which
fades a non-visible view for 150 ms, then reveals `groupAccount`. `groupServers` is still `VISIBLE`.
**Two tab groups are now composited simultaneously**, and it compounds with every further switch.

The four groups are siblings in one `FrameLayout` (`activity_main.xml:36-508`) in declaration order
`group_home` (`:42`) → `group_servers` (`:456`) → `group_settings` (`:493`) → `group_account`
(`:503`), so later-declared tabs draw on top. Every group is transparent — `group_home`
`android:background="@android:color/transparent"` (`:45`), `group_servers` a `LinearLayout` with no
background, `group_settings` = `layout_settings_content.xml` root with
`android:background="@android:color/transparent"` (`layout_settings_content.xml:7`), `group_account`
a bare `FrameLayout` (`:503-506`) — so the stale content visibly bleeds through rather than being
masked.

**Worse:** if the stale group sits *higher* in z-order than the newly selected tab, the wrong tab
draws on top and the app is stuck showing it indefinitely. Concrete case for a signed-in user
(`nav_account` is only visible when logged in, `MainActivity.kt:2395`): Home → Account → Settings
within 150 ms strands `group_account` (topmost, `:503`) visible; every subsequent switch to Home,
Servers or Settings renders *underneath* Account, so the user sees the Account tab forever while the
pill points elsewhere.

The only recovery is the instant-swap branch at `:470-477`, which is the sole place all four
visibilities are re-asserted. It is reached on a same-tab reselect (`previous == tab` → `outgoing`
null via the `takeIf` at `:467`), under reduced motion, or on an unknown nav id. So tapping the
*already-selected* pill silently repairs the app — not a discoverable recovery.

### 5b. It needs only two tabs, not three

The report frames this as A→B→C. The two-tab variant **A→B→A** — the ordinary "wrong tab, tap back"
correction, and by far the likeliest real trigger — hits the identical failure:

`showTab(A, previous=B)` sets `outgoing = groupB` (`:467`); `groupB.animate().cancel()` (`:483`) is a
no-op because `groupB` has no animation; at t=150 E1 runs `groupB.animate().alpha(1f)` (`:490`) and
cancels the E2 that would have revealed A. Final state: **B on screen, A `GONE`, `selectedNavId == A`.**
The user taps back to the tab they came from and stays on the tab they were leaving.

### 5c. Non-user-initiated triggers

`selectNav` is also called programmatically, each of which can land inside another transition's
150 ms window:

- `MainActivity.kt:275-276` — Back press from any non-Home tab returns to Home.
- `MainActivity.kt:1027` — the signed-in header chip selects the Account tab.
- `MainActivity.kt:1097` — `if (!loggedIn && selectedNavId == R.id.nav_account) selectNav(R.id.nav_home)`,
  fired from the logged-in-state repaint. This one is genuinely asynchronous with respect to a tab
  animation and needs no fast tapping at all.
- `MainActivity.kt:2411-2412` — `locateSelectedServer()` forces the Servers tab.
- `MainActivity.kt:269-271` — saved-state restore on activity recreate.

Note also that `showTab` commits the `AccountFragment` transaction and latches
`accountFragmentAdded` at `:454-459`, *before* the animation. So a tab-switch to Account that loses
the race still attaches the fragment — the latch is not corrupted, but the fragment is created for a
tab that never appears.

## 6. Fix

The end-action closes over a snapshot of a transition that may already be obsolete. Either:

- **(a) Guard the end action with the current selection** — cheapest, ~1 line: wrap the body of
  `:485-492` so it no-ops (or falls through to the instant swap) when `tab != selectedNavId`, and
  make the guarded path assert all four visibilities the way `:471-474` does. This also fixes the
  leak in §5a because the recovery path re-asserts every group.
- **(b) Serialise properly** — hold the in-flight `outgoing` view (or a monotonically increasing
  transition id) in a field; on entry to the animated branch, cancel the previous transition's
  animator *and* apply its end state (`prevOutgoing.isVisible = false; prevOutgoing.alpha = 1f`)
  before starting the new one. This is the structurally correct fix and eliminates the
  two-groups-visible state entirely.

Either way, the four-visibility reset currently unique to `:471-474` should become the single
authority on which group is visible, invoked at the end of every transition rather than only on the
instant path.
