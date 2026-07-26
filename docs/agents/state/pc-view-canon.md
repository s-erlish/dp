# PC view canon — what is actually reachable at runtime

Scope: every file under `v2rayN/v2rayN.Desktop/Views/`. Determined by reading `MainWindow`'s
construction and tab switching, the compact/wide layout switch, every navigation call, every XAML
instantiation, and a whole-solution grep for each type name.

**The analysis is definitive, not heuristic.** `rg -n 'Activator.CreateInstance|Type.GetType|GetTypes\(\)|Assembly\.'`
over `v2rayN.Desktop/` returns **nothing** — there is no reflection-based view construction anywhere
in the desktop app, so a type with no textual construction site cannot be reached. The csproj has no
`<Compile Remove>` / `<AvaloniaXaml Remove>`, so every file listed here is compiled today.

Verified against branch `claude/app-audit-agents-hyyftk`. I changed no code — this file is the only
thing I wrote.

---

## 0. How anything gets on screen — the four routes

There are exactly four ways a view reaches the user on desktop. Everything below is classified by
which route it takes.

**Route A — tab surface.** `MainWindow` constructs four tab surfaces as fields and keeps all four
alive as permanent children of one `contentHost` panel; switching tabs only changes which one is
visible.

```
MainWindow.axaml.cs:19   private readonly Control _homeView = new HomeView();
MainWindow.axaml.cs:20   private readonly Control _settingsView = new SettingsView();
MainWindow.axaml.cs:21   private readonly AccountView _accountView = new AccountView();
MainWindow.axaml.cs:27   private readonly CompactHomeView _compactHome = new();
MainWindow.axaml.cs:231  foreach (var v in new Control[] { _homeView, _compactHome, _settingsView, _accountView })
MainWindow.axaml.cs:235      contentHost.Children.Add(v);
```

The tab-to-surface resolver is the whole story:

```
MainWindow.axaml.cs:493-497
    private Control ViewFor(AppTab tab) => tab switch
    {
        AppTab.Settings => _settingsView,
        AppTab.Account  => _accountView,
        _ => _compactMode ? _compactHome : _homeView,
    };
```

There are **three** rail buttons and **three** bottom-nav items, matching the owner decision:

```
MainWindow.axaml.cs:176-178   navHome.Click / navSettings.Click / navAccount.Click → ShowTab(...)
BottomNavBar.axaml.cs:49-51   ItemHome.Click / ItemSettings.Click / ItemAccount.Click → Raise(...)
BottomNavBar.axaml.cs:9       enum AppTab   // Home, Settings, Account — no Servers member
```

**Route B — sub-page stack.** Full-screen `UserControl`s pushed onto `subPageHost`
(`MainWindow.axaml:611`) via `PushSubPage`, popped by their own `BackRequested`. Two families:
account sub-pages opened from `MainWindow` (`OpenBuy`/`OpenDevices`/`OpenHistory`/`OpenLogin`), and
settings sub-pages opened from `SettingsView.OpenPage`.

**Route C — modal dialog.** Either an OS window through
`AppManager.Instance.WindowDialog.ShowDialogAsync(vm)` (`Manager/WindowDialog.cs:8`), which resolves
the VM→view through `SimpleViewLocator`, or an in-place `DialogHost.Show(...)`.

**Route D — embedded child.** Instantiated inside another live view's XAML.

**A registration in `SimpleViewLocator` is NOT reachability.** `SimpleViewLocator` is an
`IDataTemplate` (`App.axaml.cs:20-21`); its factory only runs when a matching ViewModel is set as
some `ContentControl.Content`. In the whole desktop app exactly **one** such binding exists:

```
MainWindow.axaml.cs:307
    this.OneWayBind(ViewModel, vm => vm.StatusBarViewModel, v => v.contentStatusBarView.Content)
```

So of the 20 types registered in `SimpleViewLocator`, only those whose VM is *also* passed to
`ShowDialogAsync` from a reachable call site, plus `StatusBarView`, are live. The rest are dead
registrations inherited from the WPF `v2rayN` project.

---

## 1. The three questions that blocked everyone

### `ServersView` vs `ServerListView` vs `CompactServersView`

| | verdict |
|---|---|
| **`ServerListView`** | **LIVE** — the real one. |
| **`ServersView`** | **DEAD** — zero construction sites. |
| **`CompactServersView`** | **DEAD** — zero construction sites. |

`ServerListView` is embedded directly in both Home layouts:

```
HomeView.axaml:35          <views:ServerListView x:Name="ServerList" Grid.Row="1" />
CompactHomeView.axaml:91   <views:ServerListView x:Name="ServerList" Margin="0,4,0,0" />
```

`ServersView` is a 12-line wrapper whose only content is `<local:ServerListView />`
(`ServersView.axaml:11`). A whole-solution grep for `\bServersView\b` returns **three** hits: its own
`.axaml:2` (`x:Class`), its own `.axaml.cs:7` (`public partial class`), and its own `.axaml.cs:9`
(ctor). Nobody constructs it. It is the abandoned host for the «Серверы» tab the owner rejected.

`CompactServersView` is the same story with a header bolted on: grep for `\bCompactServersView\b`
gives its own `.axaml:2`, its own `.axaml.cs:8` and `:10`, plus three prose comments
(`L.Servers.cs:5`, `L.Servers.cs:14`, `CompactServersView.axaml:20`). No construction site.

Neither appears in `contentHost.Children` (`MainWindow.axaml.cs:231`), in `ViewFor`
(`MainWindow.axaml.cs:493-497`), or in any XAML: the only `<views:*>` / `<local:*>` tags in the whole
project are `JsonEditor`, `ServerListView`, `SubscriptionMetaView`, `OnboardingView`, `BottomNavBar`,
`AccountSyncView`, `HomeAccountChip`, `ConnectHeroView`, and four local converters.

### `HomeView` vs `CompactHomeView`

Both **LIVE**, mutually exclusive by layout mode, selected at `MainWindow.axaml.cs:496`. Breakpoint
is 760px wide with 24px hysteresis (`MainWindow.axaml.cs:30-33`); the app **starts compact**
(`_compactMode = true`, default window 372×630). `railHost.IsVisible = !compact` /
`bottomNav.IsVisible = compact` (`MainWindow.axaml.cs:750-751`). Only the current layout's Home holds
the shared `HomeViewModel`; the other is unbound to free its rows (`MainWindow.axaml.cs:517-529`).
They are **not** duplicates to be merged — they are the wide and compact trees of one screen, sharing
`ConnectHeroView`, `HomeAccountChip`, `ServerListView` and `HomeHeroPresenter`.

### `OptionSettingWindow` / `SubSettingWindow` vs `SettingsView`

`SettingsView` is **LIVE** (`MainWindow.axaml.cs:20`, `ViewFor` line 494) and owns eight sub-pages
(`SettingsView.axaml.cs:43-50`).

`OptionSettingWindow` and `SubSettingWindow` are **DEAD**. They are only opened by
`MainWindowViewModel.OptionSettingAsync` (`ServiceLib/ViewModels/MainWindowViewModel.cs:762`) and
`SubSettingAsync` (line 744), which run only from `OptionSettingCmd` / `SubSettingCmd`. Those two
commands are bound in exactly one place in the entire solution, and it is the **WPF** project, not
this one:

```
v2rayN/Views/MainWindow.xaml.cs:58   this.BindCommand(ViewModel, vm => vm.SubSettingCmd,    v => v.menuSubSetting)
v2rayN/Views/MainWindow.xaml.cs:65   this.BindCommand(ViewModel, vm => vm.OptionSettingCmd, v => v.menuOptionSetting)
```

Grep for `SubSettingCmd|OptionSettingCmd|SubSettingAsync|OptionSettingAsync` across
`v2rayN.Desktop/` returns **zero** hits. The Avalonia shell has no menu bar and never binds them.

---

## 2. Full inventory — every file under `Views/`

Legend: **LIVE** reachable in normal use · **WIDE-ONLY** / **COMPACT-ONLY** live in one layout ·
**DIALOG** modal, reachable · **DIALOG (vestigial)** modal, reachable only for a profile shape the
desktop UI cannot create · **DEAD** no reachable construction site · **INFRA** not a view.

| File | Verdict | Proof |
|---|---|---|
| `AboutPage` | LIVE (sub-page) | `SettingsView.axaml.cs:48` `WireRow(RowAbout, () => OpenPage(new AboutPage()));` |
| `AccountSyncView` | LIVE (overlay) | `MainWindow.axaml:604` `<views:AccountSyncView x:Name="accountSyncView" … />` |
| `AccountView` | LIVE (tab) | `MainWindow.axaml.cs:21`; selected at `:495` `AppTab.Account => _accountView` |
| `AddGroupServerWindow` | DIALOG (vestigial) | `ProfilesViewModel.cs:537` `ShowDialogAsync(addGroupServerViewModel)`, reached from `ServerListView.axaml.cs:784` `_ = profiles.EditServerAsync();` — but only for `IsGroupType()` profiles, and the two commands that create them (`GenGroupAllServerCmd`/`GenGroupRegionServerCmd`, `ProfilesViewModel.cs:72-73`) are bound only in the dead `ProfilesView` |
| `AddServer2Window` | DIALOG | `ProfilesViewModel.cs:532`, same `EditServerAsync` route, for `EConfigType.Custom`. Genuinely creatable: pasting a full Xray JSON stores a Custom node (`Handler/Fmt/XrayJsonTemplateFmt.cs:7`), and subscriptions can carry one (`ConfigHandler.cs:2088`) |
| `AddServerWindow` | DIALOG | `ProfilesViewModel.cs:542` — the default branch of `EditServerAsync`, i.e. the right-click «Изменить» on any ordinary server row (`ServerListView.axaml:169`) |
| `BackupAndRestoreView` | **DEAD** | Only `SimpleViewLocator.cs:18`. `BackupPage.axaml.cs:13` reuses the *ViewModel* (`private readonly BackupAndRestoreViewModel _vm = new();`) and draws its own UI; the view is never built |
| `BackupPage` | LIVE (sub-page) | `SettingsView.axaml.cs:49` |
| `BottomNavBar` | LIVE (compact chrome) | `MainWindow.axaml:592`; `MainWindow.axaml.cs:751` `bottomNav.IsVisible = compact;` |
| `BuyView` | LIVE (sub-page) | `MainWindow.axaml.cs:1220` `var view = new BuyView();` |
| `CheckUpdateView` | **DEAD** | Only `SimpleViewLocator.cs:19`. `CheckUpdateViewModel` is never constructed outside `DesignData.cs:36` |
| `ClashConnectionsView` | **DEAD** | Only `SimpleViewLocator.cs:20`; VM only in `DesignData.cs:34` |
| `ClashProxiesView` | **DEAD** | Only `SimpleViewLocator.cs:21`; VM only in `DesignData.cs:32` |
| `CompactHomeView` | COMPACT-ONLY | `MainWindow.axaml.cs:27`; `:496` `_ => _compactMode ? _compactHome : _homeView` |
| `CompactServersView` | **DEAD** | No construction site anywhere; see §1 |
| `ConnectHeroView` | LIVE (embedded) | `HomeView.axaml` + `CompactHomeView.axaml`, bound via `HomeHeroPresenter.Bind` at `HomeView.axaml.cs:62` and `CompactHomeView.axaml.cs:100` |
| `DevicesView` | LIVE (sub-page) | `MainWindow.axaml.cs:1234` |
| `DnsSubView` | LIVE (sub-page) | `SettingsView.axaml.cs:44` |
| `FullConfigTemplateWindow` | **DEAD** | Opened only by `MainWindowViewModel.FullConfigTemplateAsync` (`:800`) via `FullConfigTemplateCmd`, bound only at `v2rayN/Views/MainWindow.xaml.cs:68` (WPF) |
| `GeoFilesPage` | LIVE (sub-page) | `SettingsView.axaml.cs:47` |
| `GlobalHotkeySettingWindow` | **DEAD** | Opened only by `MainWindowViewModel.cs:214` inside `GlobalHotkeySettingCmd`, bound only at `v2rayN/Views/MainWindow.xaml.cs:69` (WPF) |
| `HomeAccountChip` | LIVE (embedded) | `HomeView.axaml:26`ff and `CompactHomeView.axaml`; one definition shared by both layouts |
| `HomeHeroPresenter.cs` | INFRA (live) | Not a view — the hero↔VM wiring helper; `HomeHeroPresenter.cs:42`, called from both Home layouts |
| `HomeView` | WIDE-ONLY | `MainWindow.axaml.cs:19`; `:496` |
| `ISubPage.cs` | INFRA (live) | Interface implemented by every settings sub-page; `MainWindow` uses it to wire `BackRequested` |
| `JsonEditor` | LIVE (embedded) | `AddServerWindow.axaml` (×3, live host). Also in the dead `FullConfigTemplateWindow.axaml` (×4) |
| `LoginView` | LIVE (sub-page) | `MainWindow.axaml.cs:1267` `var view = new LoginView { DataContext = _accountVm };` |
| `MainWindow` | LIVE (shell) | `App.axaml.cs:47` `var mainWindow = (MainWindow)viewLocator.Build(mainWindowViewModel);` |
| `MessageBoxDialog` | DIALOG | `Common/UI.cs:14` `var box = new MessageBoxDialog(caption, msg);` — the app's confirm/alert |
| `MsgView` | **DEAD** | Only `SimpleViewLocator.cs:26`. See the bug in §4 — this one is not harmless |
| `OnboardingView` | LIVE (overlay) | `MainWindow.axaml:596` `<views:OnboardingView x:Name="onboardingView" />` |
| `OptionSettingWindow` | **DEAD** | See §1 |
| `PaymentHistoryView` | LIVE (sub-page) | `MainWindow.axaml.cs:1247` |
| `PerAppProxyPage` | LIVE (sub-page) | `SettingsView.axaml.cs:43` |
| `PingSettingsPage` | LIVE (sub-page) | `SettingsView.axaml.cs:45` |
| `ProfilesSelectWindow` | DIALOG (vestigial) | `AddGroupServerViewModel.cs:116` via `AddGroupServerWindow.axaml.cs:38` (`menuAddChildServer`). Its other two callers — `RoutingRuleDetailsViewModel.cs:105` and `SubEditViewModel.cs:85` — are both dead |
| `ProfilesView` | **DEAD** | Only `SimpleViewLocator.cs:29`. `ProfilesViewModel` is used heavily (`HomeViewModel.cs:129` `Profiles = main.ProfilesViewModel;`) but is never set as any `ContentControl.Content` — the only such binding in the app is `StatusBarViewModel` at `MainWindow.axaml.cs:307` |
| `ProviderSettingsPage` | **DEAD** | Grep for `ProviderSettingsPage` yields only its own `.axaml:2`, its own `.axaml.cs:14` and `:23`, and two comments in `L.Settings.cs`. Not in `SettingsView`'s eight `WireRow`/`OpenPage` calls (`SettingsView.axaml.cs:43-50`) |
| `QrcodeView` | DIALOG | `ServerListView.axaml.cs:116` `await DialogHost.Show(new QrcodeView(url));` — the row «Поделиться · QR-код» |
| `RoutingRuleDetailsWindow` | **DEAD** | Opened only by `RoutingRuleSettingViewModel.cs:141`, i.e. from inside the dead `RoutingRuleSettingWindow` |
| `RoutingRuleSettingWindow` | **DEAD** | Opened only by `RoutingSettingViewModel.RoutingAdvancedEditAsync` (`:120-141`). The live replacement deliberately does not call it: `RoutingSubView.axaml.cs:13-14` — «Редактирование отдельных правил (что открывало ещё одно окно) здесь намеренно не показываем». `RoutingSubView` calls only `RoutingAdvancedSetDefault`, `DomainStrategy` and `RoutingAdvancedImportRulesCmd` |
| `RoutingSubView` | LIVE (sub-page) | `SettingsView.axaml.cs:46` |
| `ServerListView` | LIVE (embedded) | `HomeView.axaml:35`, `CompactHomeView.axaml:91` |
| `ServersView` | **DEAD** | No construction site anywhere; see §1 |
| `SettingsView` | LIVE (tab) | `MainWindow.axaml.cs:20`; `:494` |
| `StatusBarView` | LIVE (hidden, 0×0) | `MainWindow.axaml.cs:307` `OneWayBind(… vm.StatusBarViewModel … contentStatusBarView.Content)`; the host is `Width="0" Height="0" Opacity="0"` (`MainWindow.axaml:643-653`). It is kept **on purpose** — it carries the tray icon, clipboard and sudo-password interaction handlers. **Do not delete.** |
| `SubEditWindow` | **DEAD** | Two callers only: `SubSettingViewModel.cs:78` (from the dead `SubSettingWindow`, `SubSettingWindow.axaml.cs:70`) and `ProfilesViewModel.EditSubAsync` (`:946`), reachable only through `EditSubCmd`/`AddSubCmd`, bound only in the dead `ProfilesView` (`ProfilesView.axaml.cs:46-47`) |
| `SubSettingWindow` | **DEAD** | See §1 |
| `SubscriptionMetaView` | LIVE (embedded) | `ServerListView.axaml` — it is each subscription group's header inside the live list |
| `SudoPasswordInputView` | DIALOG | `StatusBarView.axaml.cs:202` `var dialog = new SudoPasswordInputView();`, registered as `ViewModel.PasswordInputInteraction` handler at `:55`. Live because `StatusBarView` is in the visual tree even at 0×0 (Linux TUN elevation) |
| `ThemeSettingView` | **DEAD** | Only `SimpleViewLocator.cs:36`. Theme/language now live as rows in `SettingsView` (`SegThemeDark/SegThemeLight`, `RowLanguage`) |
| `UrlSchemesPage` | LIVE (sub-page) | `SettingsView.axaml.cs:50` |

**Counts:** 52 files — 30 live (incl. 2 layout-scoped Home trees and 2 infra files), 6 live dialogs
(2 of them vestigial), **16 dead**.

---

## 3. Deletion recommendation

I deleted nothing. Ranked by safety.

### Tier 1 — delete outright, zero cost, no other file to touch

These have no reference anywhere outside their own two files, so removing the `.axaml` + `.axaml.cs`
pair is self-contained.

- `ServersView.axaml` / `.axaml.cs`
- `CompactServersView.axaml` / `.axaml.cs`
- `ProviderSettingsPage.axaml` / `.axaml.cs`

For `ServersView` and `CompactServersView` this is more than tidying: they are the physical remains
of the rejected «Серверы» tab, and leaving them in the tree is exactly how a future agent
re-discovers and re-adds it.

**Before deleting `CompactServersView`, harvest it.** The owner decision makes desktop server search
Главная's problem, and `CompactServersView.axaml:85-108` is the already-designed, already-localised
answer — a search field bound to `Profiles.ServerFilter` with a live `TextChanged` filter in
`CompactServersView.axaml.cs:21-40`, including the note that the VM only refreshes on empty input so
the view must call `RefreshServers()` on every keystroke. Its header also carries the
refresh / ping / add-menu cluster (`CompactServersView.axaml:61-82`). Move that markup and that
handler into `HomeView`/`CompactHomeView` **first**, then delete. Two localisation keys are used
*only* by this file — `Servers_Title` and `Servers_SearchPlaceholder` (`L.Servers.cs:17,26`) — and
they should move with the markup, not be dropped.

### Tier 2 — delete, but `Common/SimpleViewLocator.cs` must be edited in the same change

Each of these is named in `SimpleViewLocator`'s ctor, so the file will not compile once the type is
gone. Delete the view **and** its `RegisterViewFactory<…>()` line together. (`DesignData.cs` names
only the *ViewModels*, which live in `ServiceLib` and stay.)

| View | Locator line to remove |
|---|---|
| `OptionSettingWindow` | `SimpleViewLocator.cs:27` |
| `SubSettingWindow` | `SimpleViewLocator.cs:35` |
| `SubEditWindow` | `SimpleViewLocator.cs:34` |
| `RoutingRuleSettingWindow` | `SimpleViewLocator.cs:31` |
| `RoutingRuleDetailsWindow` | `SimpleViewLocator.cs:30` |
| `FullConfigTemplateWindow` | `SimpleViewLocator.cs:23` |
| `GlobalHotkeySettingWindow` | `SimpleViewLocator.cs:24` |
| `ProfilesView` | `SimpleViewLocator.cs:29` |
| `ClashProxiesView` | `SimpleViewLocator.cs:21` |
| `ClashConnectionsView` | `SimpleViewLocator.cs:20` |
| `CheckUpdateView` | `SimpleViewLocator.cs:19` |
| `BackupAndRestoreView` | `SimpleViewLocator.cs:18` |
| `ThemeSettingView` | `SimpleViewLocator.cs:36` |

Two extra edits in this tier: deleting `FullConfigTemplateWindow` also removes the
`<Compile Update="Views\FullConfigTemplateWindow.axaml.cs">` item in
`v2rayN.Desktop.csproj`; deleting `MsgView` (below) is the only Tier-2 item with behaviour attached.

### Tier 3 — do not delete yet, decide the feature first

- **`MsgView`** (`SimpleViewLocator.cs:26`) — dead as a view, but its ViewModel is the sole consumer
  of a live event channel. Deleting it silently is fine; deleting it *without* fixing §4 leaves a
  real bug in place. Read §4 first.
- **`CheckUpdateView`** — dead, but it is the app's only «проверить обновления» UI. `AboutPage`
  (`AboutPage.axaml.cs`) shows version + runtime and links to the site/bot, with no update check.
  If update-checking is meant to exist on desktop, this is the surface to rehost, not to delete.
- **`AddGroupServerWindow` / `ProfilesSelectWindow`** — classified vestigial, not dead. Both are
  reachable through `EditServerAsync` for a group-type profile; the desktop just has no way to
  *create* one. Leave them until someone decides whether policy-group/proxy-chain servers are a
  desktop feature. Deleting `AddGroupServerWindow` would also orphan `ProfilesSelectWindow`.

### Do not delete

`StatusBarView` — invisible but load-bearing (tray icon, clipboard interactions, sudo password,
TUN elevation banner). `HomeView` and `CompactHomeView` — both live, one per layout.
`ProfilesView.axaml.cs` is dead **but `ProfilesViewModel` is the engine's server list** and stays.

---

## 4. Findings the audits should act on

**A. Desktop drops every snack/notice message on the floor.** `MainWindow` routes all user feedback
into a subscriber that does not exist at runtime:

```
MainWindow.axaml.cs:330-333  AppEvents.SendSnackMsgRequested … .Subscribe(async c => await DelegateSnackMsg(c))
MainWindow.axaml.cs:1841     private Task DelegateSnackMsg(string content)
MainWindow.axaml.cs:1843         NoticeManager.Instance.SendMessage(content);
NoticeManager.cs:22              AppEvents.SendMsgViewRequested.Publish(content);
```

The **only** subscriber to `SendMsgViewRequested` in the entire solution is `MsgViewModel`'s ctor
(`ServiceLib/ViewModels/MsgViewModel.cs:33`), and `MsgViewModel` is never constructed on desktop
outside `DesignData.cs:26`. The comment at `MainWindow.axaml.cs:1836-1839` explicitly claims this
change fixed «добавляю подписку — ничего не происходит, без объяснений» by routing feedback to the
inline message panel — but that panel does not exist here. The bug it describes is still live, and
`SubscriptionImportLogHandler` (`MainWindowViewModel.cs:733`), every `NoticeManager.Enqueue`, and
every `AppEvents.SendSnackMsgRequested.Publish` in `AccountViewModel`/`DevicesViewModel` (~20 call
sites) all vanish the same way. Whoever owns Главная or the shell needs to give this a real surface;
this is not a dead-code cleanup, it is a silent-failure bug.

**B. The three problems the rejected «Серверы» tab was meant to solve are confirmed, and Главная owns
them now.**
- *No server search on desktop.* `ServerListView.axaml` has no search field; the only one ever
  written lives in the dead `CompactServersView.axaml:85-108`. `Servers_SearchPlaceholder` exists in
  `L.Servers.cs:26` and is currently referenced by nothing reachable.
- *Seven per-server actions hide in a right-click menu.* `ServerListView.axaml:164-179` — make
  default, test latency, edit, duplicate, share QR, share link, delete. This `ContextMenu` is the
  app's **only** route to edit/delete/share/QR: `AddServerWindow`, `AddServer2Window`, `QrcodeView`
  and `RemoveServerAsync` are all reached exclusively from `ServerListView.axaml.cs:780-818`. Any
  redesign that touches that menu must keep every one of the seven reachable.
- *The list sits below the fold in compact.* `CompactHomeView.axaml:89-91` places `ServerListView`
  as item (5) of a `StackPanel` inside the page scroll, under the hero.

**C. `Geo.Nav.Servers` is declared and unused, as the owner decision requires.**
`Assets/GlobalResources.axaml:454` defines it; the only `Geo.Nav.*` references are `.Home`,
`.Settings`, `.Account` in `MainWindow.axaml:465,477,489` and `BottomNavBar.axaml:137,146,156`. No
`Nav_Servers` string exists in `L.Shell.cs`. Nothing to do — recorded so nobody "fixes" it.

**D. Two settings sub-pages carry known dead rows.** Noted by a previous agent at
`ServerListView.axaml.cs:770-778`: `PingSettingsPage.axaml`'s `RowHttp`/`RowIcmp` are unimplementable
and `ResolvePingAction()` silently falls back to Realping. That belongs to the settings owner, not to
this canon.
