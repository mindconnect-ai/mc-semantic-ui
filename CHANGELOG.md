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

- **The TypeScript model names the fields Java actually sends.** Nine node
  interfaces were missing what every `UiNode` carries — `title`, `display`, and
  on `UiIcon` the six event triggers — plus `cssClass` on `UiAction` and
  `UiField`, and `style` / `appearance` / `loading` on `UiMenuItem`, which in
  Java inherits them from `UiAction`. A server sent them, the declarations said
  they did not exist, and a consumer naming one got a compile error for a field
  that was right there in the JSON.

  Purely additive and all optional, so nothing that compiled before stops
  compiling. `id` deliberately stays optional where it already was — making
  these extend `UiNodeBase` would have tightened it and broken callers that
  build a text or a row without one.

  One divergence is left and is recorded in the mirror test rather than
  papered over: Java models `UiPage` as a `UiNode` subtype with the
  discriminator `"page"`, TypeScript as a plain envelope with none. Which of
  the two is right is a question about the wire format.

### Fixed

- **The app shell scrolls its content again, not the window.** `.sui-shell`
  said `min-height: 100dvh`, which fills the window but lets the shell grow
  past it as soon as the content is taller. Then the *window* scrolled and
  carried the header and the menu off the top — while the shell's own
  `overflow: hidden` on the body and `overflow: auto` on the content pane sat
  waiting for a height to push against. They only engage once something bounds
  the shell, so every page that did not cap it for itself scrolled the wrong
  element, and the ones that behaved did so because they had capped it
  themselves.

  Measured on a shell with forty rows in a 285px window: the shell was 2058px
  tall, the document scrolled, and the header ended at −1772px. It is now 285px,
  the document does not scroll, the content pane does, and the header stays at
  0. `.sui-shell--fit`, the embedded variant, keeps its freedom to be taller
  than the window.

- **A floating menu looks like it floats.** `.sui-shell-body > .sui-menu`
  flattens the menu into the shell — no radius, no shadow — which is right
  while it pushes the content aside. It also outranked the elevation that
  `.sui-menu--responsive` sets for the mobile drawer: two classes against one,
  and a media query adds no specificity, so the shadow was reset before it ever
  landed and the drawer read as pasted onto the page rather than lying over it.
  Both the overlay and the responsive drawer now state their ground and their
  elevation where it wins by order rather than by weight.


## [0.2.0] - 2026-08-29

### Added

- **The two patch operations that add and remove an element now animate.**
  `APPEND` fades its new elements in with a small lift; `REMOVE` fades its
  target out and collapses the space it held before dropping it, so a row
  leaving a list no longer blinks out from under the ones below it. The
  renderer sets `.sui-enter` and `.sui-leave`, and `sui.css` decides what they
  look like — restyle either without touching the renderer, or turn the whole
  thing off with `renderer.animatePatches = false`.

  `REPLACE` and `MERGE` deliberately do **not** animate: they are the
  streaming path, and a chat turn replaces the same node once per token.

- **Disclosures open and close instead of snapping.** Activity summaries,
  collapsible sections and tree nodes animate their height, via
  `::details-content` and `interpolate-size`. Where a browser has neither, the
  rule is ignored and they snap exactly as before — no feature query needed.
  Menu-button popovers are deliberately left out: their content is positioned,
  and clipping it to an animated height would break it.

- **Dialogs leave the way they arrive.** A dialog dropped in with a small
  fall; now it falls back out instead of vanishing between frames. Closing by
  backdrop click or × goes through the same path as a close the server
  ordered — the event bus now removes the host through the renderer's new
  `removeAnimated()` rather than deleting it outright — so the two look the
  same.

- **Navigation cross-fades where the browser supports it.** Mounting a page
  and filling a slot — an app shell's content area — go through the View
  Transition API, so a screen change is a fade rather than a jump. Nothing
  else does: `APPEND` and `REMOVE` have their own animation, and a `REPLACE`
  that is not a slot is very often a component redrawing itself once per
  streaming token. Turn it off with `renderer.viewTransitions = false`;
  browsers without the API are unaffected. Name a region with
  `view-transition-name` to animate it as its own pair — that is how a header
  stays put while the content under it changes.

- **Motion is themable.** Durations and easings are custom properties now
  (`--sui-duration-fast|base|slow`, `--sui-ease-standard|decelerate`) and every
  transition in `sui.css` is timed by them, so a theme can set a tempo the way
  it sets a palette. Looping indicators keep their own timing on purpose — a
  spinner that stopped would read as "finished".

- **`MERGE` patch operation** — change the fields you name and leave the rest
  alone, instead of resending a whole node to flip one flag:

  ```java
  UiPatch.Operation.hide("filters")                 // and show("filters")
  UiPatch.Operation.merge("save", Map.of("enabled", false, "label", "Saving…"))
  ```

  An explicit `null` clears a field rather than being ignored, so a state can be
  turned off as well as on. The merge is shallow: a named field is replaced
  whole.
- **A hybrid page carries its own model**, in a `<script type="application/json">`
  at the end of the body, so a `MERGE` works on a page the client never
  rendered. Only pages served with a SPA bootstrap carry it.
- **[Patches & visibility](https://mindconnect-ai.github.io/mc-semantic-ui/docs/semantic-ui/patches)**
  in the documentation — the five operations, what `MERGE` is for and is not,
  the two ways to hide something, and where a merge finds the rest of the node
  on each of the three delivery modes.
- **`mc-sui-merge-demo`** — a page to poke at for the above. Hide and show a
  panel, toggle a button's own label and style, and read what each click
  actually put on the wire beside the `REPLACE` that would have done the same
  thing. Served as a hybrid page, so it exercises the embedded model too. An
  application rather than a library: it is in the repository, not on Maven
  Central.

- **Spacing and type are on a scale.** `--sui-space-*` (a 4px grid with one
  half-step) and `--sui-text-*` (six steps), and the rules that set the
  layout's rhythm — page, list rows, table cells, fields, buttons, menu,
  tabs, dialog — now use them. The sheet had 25 distinct spacing values, 16
  of them off any grid, and 12 font sizes from 9px to 24px; a theme that
  wanted to be denser had to override component by component, and a new
  component had nothing to pick from.

  Snapping to the grid moved values by at most 2px each, which makes rows
  about 5px shorter than before. Measured across three pages, no element
  changed width or horizontal position by more than 4px.

  This is a first pass over the declarations that form the rhythm, not the
  whole sheet: 15 of 127 spacing declarations are on the scale so far. The
  rest are one-off insets and fixed control sizes, which mostly should stay
  literal — but some are simply not migrated yet.

### Changed

- **`prefers-reduced-motion` is honoured everywhere, from one place.** The two
  partial blocks are replaced by a single one that flattens the duration
  variables, so it covers every transition in the sheet — including ones added
  later. The renderer also skips the patch animations outright under reduced
  motion, and in a hidden tab, where the browser would not run them anyway.

### Fixed

- **Animation no longer breaks the renderer outside a browser.** Deciding
  whether to animate is the one thing here that has to ask about its
  environment, and the question was asked in a way that assumed the answer: the
  gate read `window.matchMedia` without checking there was a `window`, so a
  patch applied from a Node backend died with a `ReferenceError` instead of
  applying. It now asks for a real browser positively, and anything else
  simply does not animate.

- **A `MERGE` on a table row destroyed the row.** The table's patch handling
  knew `REPLACE`, `APPEND` and `REMOVE`, so a merge fell through to the generic
  path — which renders a row on its own, and a row outside a table is a
  key/value list by design. The `<tr>` was swapped for a `<dl>` and the cells
  went with it, while the table's own model kept the old row and reverted the
  merge on the next patch. A merge inside a table now goes through that model,
  the way a replace does.
- **A class name that merely contained a visibility marker was mangled.**
  `display` is the source of truth for visibility, so the marker it folds into
  `cssClass` is stripped before being derived again — but by substring, not by
  whole class. `sui-hidden-x` came out as `-x` and the node lost its styling,
  on the server and in the browser both.
- **JavaFX refused a merge naming a field the target does not have.** The SPA
  ignores it; JavaFX went through Jackson, which fails an unknown property by
  default, and dropped the whole merge. One patch has to mean the same thing on
  both renderers, so JavaFX ignores it too — and says so.
- **A standalone action was inert on a hybrid page.** An action that is not
  already inside a `UiForm` renders JS-free as a `<form>` wrapping a
  `<button type="submit">`, with the trigger on the form — where a browser
  without JS needs it. But the click lands on the button, and the bus looked
  for a trigger only there: it found none and returned, having already
  cancelled the native submit. So the button did nothing at all, and said
  nothing about it. It now reads the trigger off that wrapper. Only that
  wrapper — a button inside a clickable row still keeps its own behaviour.
- **`display` works client-side on the web.** `cls()` read only `cssClass`,
  while the server folds `display` into it — so setting or clearing `display`
  from the client changed a field no client-side renderer looked at.

- **JavaFX: a patch inside a tab or a scroll pane no longer collapses the
  page.** Deleting a row, or anything else that patched a node sitting in a
  JavaFX *control* rather than a pane, swapped the node inside the control's
  private skin container and left the control itself still pointing at the
  node that had been taken out. The replacement got none of what the control
  does for its content — a `ScrollPane` stretches only the node its `content`
  property names — so the panel shrank to its bare minimum and every label in
  it turned into an ellipsis. Patches now find the slot a node actually
  occupies: a tab's panel, a scroll pane's content, a collapsible's body, or a
  pane's child list.
- **JavaFX: `APPEND` and `CLEAR` reach through a `scrollpane`.** Appending to
  one means appending to what it scrolls, which is how a chat adds a message
  to its transcript; before, the operation was silently dropped.
- **JavaFX: a dialog looks like the app it came out of.** A dialog is its own
  scene, and a scene starts from the bare JavaFX theme; it inherited only what
  the owner had put on its *scene*, which for an app that styles its root — the
  documented way, and what `SuiFxOverlay` itself does — is nothing. Tabs and
  buttons in a dialog came up unstyled. The palette is now installed outright,
  and the owner's root stylesheets are adopted as well.
- **JavaFX: `DOWNLOAD` saves a file you can find.** It wrote the bytes to a
  randomly named temp file and logged the path, which from the clicking end is
  indistinguishable from nothing happening. The name now comes from
  `Content-Disposition`, or the url if the server said nothing — the same
  two-step the browser renderer makes — and the default handler asks where to
  put the file instead of logging. `setDownloadHandler` still overrides it.
- **JavaFX: markdown tables are tables.** Tables are a GitHub extension rather
  than CommonMark, and the parser was built without it — so the desktop was the
  one renderer of the three that showed a table as the row of pipes it is
  written as. `markdown` now parses GFM tables and paints them as a grid, with
  header cells, per-column alignment and inline markup inside a cell.
- **JavaFX: a table is as tall as its rows.** A `TableView`'s preferred height
  is a flat 400px whatever it holds, so a table of one attached file came up as
  a row of data over a lawn of empty grid.
- **JavaFX: a row action's label is not clipped.** The width of the row-action
  column was a fixed guess, and "Remove" came out as "R…". The column now grows
  to what its buttons actually measure, and a button is never narrower than its
  own label.

- **Pages stop disagreeing with each other.** A section's own `<h2>` had no
  rule at all, so it fell through to the browser's default — 22.5px at weight
  700 on screens whose every other title is 17px at 600, which is why the
  vector-store and file pages looked like a different app. Table rows had a
  different horizontal padding from list rows (16px against 20px), so a page
  built on a table sat at a different rhythm from one built on a list. Both
  are on the shared step now, and every container's title comes from the same
  one.

- **A table's row name weighs the same as a list's.** A link in a table cell
  is the row's name, exactly as `.sui-list-item-label` is in a list, and it
  was rendering a weight lighter — which is why workflow names looked thinner
  than agent names for no reason anyone could point at. Column headings also
  stop wrapping: one row of labels rather than two of ragged fragments.

- **The column heading stops being a band.** A filled strip in `surface-alt`
  with square corners inside a rounded card cut the card's own radius off at
  the top, which is what made it read as something stuck on rather than part
  of the table. It is the card's surface now, separated by the same hairline
  as every row, with the outer cells carrying the corners — and it is the
  same size as the cells under it, where it used to be two steps smaller. A
  label printed smaller than the thing it labels reads as a footnote.

- **Field-group titles are sentence case.** Uppercase at 12px in bold was the
  loudest quiet thing in the sheet, and capitals cost the word shapes that
  make a small label readable at a glance.

- **An empty menu head no longer reserves 40px.** An app shell whose menu has
  no title, and whose collapse toggle lives in the page header instead, still
  rendered the head — empty, and still full height. With the menu's own
  padding that was 52px of nothing between the header and the first nav item,
  on every screen. It is 8px now.

- **The content area is flush, and the sidebar is 200px.** The content used
  to sit in 20px of its own padding, so the page ended in a band of nothing
  on all four sides and the card floated in the frame instead of being it.
  The sidebar reserved 240px for labels nobody has — with "Vector Stores" as
  the longest, the text ended 17px short of its own edge, and the content's
  padding added more: 37px of nothing between the navigation and what it
  navigates to.

- **List rows line up.** A row's height follows its description, and the
  actions were centred in it — so the buttons sat 11, 21 or 31px below the
  title depending on how long that description happened to be, next to a
  badge that was top-aligned and therefore disagreed with them. They are
  aligned to the start now, which puts the action group's centre exactly on
  the title's centre in every row: measured across a ten-row list, the spread
  goes from 20px to 0. The row also gained a 24px gutter, because the
  description used to run to within 2px of the first button — not an overlap,
  but close enough to read as one — and the actions no longer shrink when the
  text is long.

## [0.1.4] - 2026-08-29

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

- **`SuiFxEventBus` applies a `UiPage`** — which is what made
  `UiTrigger.go(href)` work on the desktop at all.
- **`STREAM` reads Server-Sent Events** on JavaFX, so an agent's patches paint
  as they arrive. A page's `activeStreams` reconnect on their own.
- **`mc-sui-javafx-browser`** — a browser for `UiNode` servers: type a url and
  whatever comes back is painted. Useful for checking that a screen the SPA
  drives comes up the same on the desktop. An application rather than a
  library: it is in the repository, not on Maven Central.
- **`suiI18n`**, a message catalog for the runtime's own chrome strings, with
  English and German built in and the locale following `<html lang>`.

### Changed

- **`app-shell` and `header` are part of `mc-semantic-ui-javafx`.** They are a
  screen's frame, so the renderer draws them without anything being installed.
  What was `mc-semantic-ui-javafx-shell` is now
  `mc-semantic-ui-javafx-iframe` and holds `iframe` alone — the one node type
  that needs `javafx-web`, and the only reason a separate artifact was ever
  worth it. `SuiFxShell.install` becomes `SuiFxIFrame.install`. Nothing was
  published under the old name.

### Fixed

- **The runtime's own strings are English.** A few German ones had survived in
  the client's chrome; they are translated, and `suiI18n` is how a host
  puts a language back.
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
- **A JavaFX repaint no longer throws away what the user was doing.** Focus, a
  text control's caret and selection, scroll offsets, the open tab and expanded
  panes now survive a patch. The browser gets this from its morphing library;
  the desktop rebuilds and swaps, so typing in a field while a stream patched
  the page above it used to cost the cursor mid-word.
- **`display` works on JavaFX.** The state has been on `UiNode` since v0.1.3,
  folded into a style class that this renderer never read — a `HIDDEN` node
  stayed on screen. `HIDDEN` now takes the node out of the layout and `BLANK`
  leaves its space behind, matching `display:none` and `visibility:hidden`.
- **Table cell templates are applied on JavaFX.** A column can paint its cells
  as nodes rather than text, with `{dataKey}` placeholders filled per row.
  Without it a column meant to be a link showed the raw value and there was no
  way in.
- **A second dialog under the same id came up empty on JavaFX.** A server
  swaps one dialog for another by removing it and appending the replacement,
  both in one patch. The operations were being reordered — the dialog ones
  applied inline, the rest collected and run afterwards — so the remove landed
  after the append had already claimed the id, and deleted the content it had
  just built. Patch operations run in sequence now, because a patch means what
  it means in order.
- **SVG line art is drawn on JavaFX.** A brand logo written as SVG used to be
  skipped: JavaFX has no SVG support, and the libraries that add it bring a
  rendering engine. But the icon sprite was already being rebuilt as shapes,
  and a logo is very often the same kind of drawing — so that machinery now
  handles any document of the sort, groups and viewBoxes and stroke widths
  included. A logo in `currentColor` takes the brand's own colour, so it lights
  up on the dark band without a second asset. Gradients, text and masks are
  still out of reach and come back as nothing rather than as something wrong.
- **A JavaFX dialog scrolls instead of growing off the screen.** A window sizes
  itself to its content, and a long form grew past the bottom with the Close
  button somewhere below the taskbar. The body scrolls now and the window stops
  at four fifths of the screen's visual bounds.
- **A field's trailing action is drawn on JavaFX.** A path field's "Browse…"
  button was simply absent, leaving the path to be typed from memory.
- **Paging worked off by one on JavaFX.** `page` is one-based, as the SPA has
  always read it, and both the list and the table pager assumed zero. On a
  16-item list of pages of 10 the first page was labelled "2 / 2", Previous was
  enabled there, and pressing it asked the server for page 0.
- **The JavaFX header is the dark band the web draws.** It was painted in the
  surface colour with dark text — the same header in name only. The
  `--sui-header-*` tokens now exist on the desktop too, so brand, navigation
  and the user widget read against the band, and a host can relight it the same
  way. An SVG brand logo is skipped with a note rather than silently never
  appearing: JavaFX draws raster formats only.

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
