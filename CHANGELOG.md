# Changelog

What changed, in the words of someone who has to decide whether to upgrade.

This is not the commit log — [the releases page][releases] has that, generated.
An entry here earns its place by telling a reader of the *library* what is
different for them: a new node type, a behaviour that changed, a bug whose
symptom they may have been living with.

The format is [Keep a Changelog][keepachangelog]; this project follows
[semantic versioning][semver], with the caveat that it is pre-1.0 and breaking
changes may land in a minor.

**Adding an entry:** put it under `## [Unreleased]`, in the section that fits.
The release workflow renames that heading to the version being cut and opens a
fresh empty one, so nothing has to be moved by hand at release time.

[releases]: https://github.com/mindconnect-ai/mc-semantic-ui/releases
[keepachangelog]: https://keepachangelog.com/en/1.1.0/
[semver]: https://semver.org/spec/v2.0.0.html

## [Unreleased]

### Added

- **The JavaFX renderer draws the whole vocabulary.** `scrollpane` and `icon`
  join the core renderer; `app-shell`, `header` and `iframe` arrive in
  `mc-semantic-ui-javafx-shell`, kept separate because `iframe` is a `WebView`
  and `javafx-web` carries a WebKit build per platform.
- **Icons on the desktop**, rebuilt from the same `icons.svg` the browser
  loads — all 2037 Lucide symbols, so a token means the same glyph in every
  renderer. Every model type that carries an icon token now shows it.
- **`markdown` and `json-viewer` on the desktop**, as
  `mc-semantic-ui-javafx-markdown` and `mc-semantic-ui-javafx-json`. Markdown
  is walked into real controls rather than an embedded browser.

### Changed

- **`app-shell` and `header` are part of `mc-semantic-ui-javafx`.** They are a
  screen's frame, so the renderer draws them without anything being installed.
  What was `mc-semantic-ui-javafx-shell` is now
  `mc-semantic-ui-javafx-iframe` and holds `iframe` alone — the one node type
  that needs `javafx-web`, and the only reason a separate artifact was ever
  worth it. `SuiFxShell.install` becomes `SuiFxIFrame.install`. Nothing was
  published under the old name.
- **`SuiFxEventBus` applies a `UiPage`** — which is what made
  `UiTrigger.go(href)` work on the desktop at all.
- **`STREAM` reads Server-Sent Events** on JavaFX, so an agent's patches paint
  as they arrive. A page's `activeStreams` reconnect on their own.
- **`mc-sui-javafx-browser`** — a browser for `UiNode` servers: type a url and
  whatever comes back is painted. Useful for checking that a screen the SPA
  drives comes up the same on the desktop.
- **`suiI18n`**, a message catalog for the runtime's own chrome strings, with
  English and German built in and the locale following `<html lang>`.

### Fixed

- **Relative urls resolve** against the page they arrived on. A server writes
  `/admin/tools` and `/img/logo.svg`; on the desktop every such link was dead.
- **The JavaFX palette resolves when the overlay is not the scene root.** It
  was declared on `.root`, which JavaFX stamps on the scene root and nowhere
  else — so an embedded overlay saw none of it and every colour in the
  stylesheet fell back to its own name.
- **Row-action and pager triggers substitute their placeholders.** `{id}` and
  `{page}` went out in the url as written, so those buttons requested paths
  that do not exist.
- **An empty string counts as absent**, as it does in the TS renderers. A
  server sending `"title": ""` drew a blank strip the browser never showed.
- **A section whose entries are all unnamed stacks** instead of becoming tabs —
  the shape a chat page sends, where tabs hid two thirds of the screen.
- Tab panels paint in the surface colour; a wide table scrolls sideways rather
  than squeezing its row actions off-screen; `headerExtra` is drawn, without
  which a page offered no way to filter; and the detail grid keeps its term
  column, which had collapsed so far that every field name read `...`.

## [0.1.3] - 2026-08-17

### Added

- The entire Lucide set ships in the icon sprite.
- `display` state (`hidden` / `blank`) on every `UiNode`, folded into the css
  class so every renderer inherits it without per-template handling.
- `ai` and `bot` icon tokens; `ai` is now a brain, and `tools` a wrench.

### Fixed

- The sprite build no longer fuses attribute names, which had been producing a
  sprite that rendered nothing.

## [0.1.2] - 2026-08-17

### Added

- `UiIFrame` — an embedded browsing context.

## [0.1.1] - 2026-08-17

### Added

- `UiScrollPane` — a scrolling viewport that can stick to the newest content.
- `PASSWORD` field type, with an eye toggle.
- Leading header icon on lists and tables.
- `cssClass` and a wide variant on dialogs.
- Enter submits a form's primary action.

### Changed

- Tabbed sections enclose their panel, so the bar and the panel read as one
  card.

### Fixed

- The icon inset survives on compact header-extra inputs.
- Assorted mobile header fixes.

## [0.1.0] - 2026-07-21

First public release: the `UiNode` vocabulary, the dual renderer (SSR
Handlebars + SPA TypeScript), the visual editor, and the JSON, markdown,
diagram and chart extensions.
