# departament VPN — repo guidance

## Design standard (MANDATORY for all UI/design work)

Any change to UI — layouts, styles, themes, drawables, spacing, typography,
colors, components, tabs, buttons, screens, empty/error/loading states — MUST
follow the vendored design skills in `.claude/skills/`:

- **`ui-ux-pro-max`** — pro UI/UX craft (hierarchy, layout, components).
- **`impeccable`** — remove "AI slop"; obey its Absolute Bans and the AI-slop test.
- Supporting: `ui-ux-design-system`, `ui-ux-styling`, `ui-ux-design`,
  `ui-ux-brand`, `taste-skill`.

Read the relevant SKILL.md before designing, and apply it. Non-negotiable rules
distilled for this app (Android, Material3, "Incy" = pure dark + ONE bright blue
accent, brand font Space Grotesk, Russian UI, sentence-case):

- ONE spacing scale (`@dimen/space_4/8/12/16/24`), ONE 16dp screen gutter, ONE
  accent (blue; red only for destructive), consistent radii
  (`@dimen/radius_chip 12` / `radius_card 20` / `radius_tile 12`), consistent
  40dp tiles / 22dp glyphs, `@dimen/row_min_height 56` (≥48dp touch targets).
- Type scale via `TextAppearance.App.{Headline,Title,Body,Subtitle,Caption,Chip}`.
- Section headers are sentence-case bold — NOT tiny ALL-CAPS tracked eyebrows.
- No nested cards. No decorative gradients/glows. No emoji as UI chrome. No
  off-scale spacing. Body text contrast ≥4.5:1.
- Every state designed (pressed = subtle scale, selected, disabled, empty,
  loading, error), copy in the interface's voice, active verbs.

Honor every design request the owner has made across the project (Incy dark +
blue, tightened profile, tariff badge, ₽ currency, seamless sub-screen toolbar,
unified server icon, no ripple glow on nav, buy/link-Telegram CTAs, sentence-case
Russian copy, etc.).
