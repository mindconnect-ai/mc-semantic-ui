---
title: Themes
---

# Themes

A theme changes how every node looks without touching the tree. The core ships
seven, all in `mc-semantic-ui-core` and served next to the runtime under
`/sui/`:

| Theme | Stylesheet | What it is |
| --- | --- | --- |
| Default | `sui.css` | The framework's own look — always loaded |
| Dark | `sui-dark.css` | The default, dark |
| Compact | `sui-compact.css` | The default, tighter: the spacing scale one step down |
| Clody | `sui-clody.css` | Warm neutrals, one plane, hairlines instead of shadows |
| Gipiti | `sui-gipiti.css` | Achromatic, two planes, near-black pill buttons |
| Sorbet | `sui-sorbet.css` | Pastels on a tinted ground, soft plum shadows |
| Amethyst | `sui-amethyst.css` | Violet on a dark ground |
| SBB | `sui-sbb.css` | A standalone sheet that *replaces* `sui.css` |

Every theme but SBB is an **overlay**: it loads after `sui.css` and only acts
while its class, `sui-theme-<name>`, sits on `<html>`. Load all the sheets you
offer once, and switching is a class swap — no reload, no second request. SBB
is its own design system and replaces the base, so it is picked when the page
is built, not switched at runtime.

## Letting the user choose

Which theme is on is a decision about the browser, not the account: it
changes no data and the server has no opinion on it. So it lives on the
client, in `localStorage` under `sui-theme`.

```html
<head>
  <link rel="stylesheet" href="/sui/sui.css">
  <link rel="stylesheet" href="/sui/sui-dark.css">
  <link rel="stylesheet" href="/sui/sui-amethyst.css">
  <!-- Blocking, in <head>: puts the remembered theme on before the first paint. -->
  <script src="/sui/theme-boot.js" data-default-theme="amethyst"></script>
</head>
<body>
  <div id="app"></div>
  <script type="module">
    import { installThemeSwitch, SUI_THEMES } from "/sui/renderer.js";

    // A palette button in the header, ahead of the user widget.
    installThemeSwitch({
      defaultTheme: "amethyst",
      themes: SUI_THEMES.filter(t => ["default", "dark", "amethyst"].includes(t.id)),
    });
  </script>
</body>
```

- **`theme-boot.js`** runs before anything is drawn, so a reload does not flash
  the default first. It takes `?theme=<name>` from the address (and remembers
  it, so a link can hand someone a look), then the remembered choice, then
  `data-default-theme`.
- **`installThemeSwitch(options)`** keeps a picker in the `UiHeader`. It is the
  same markup a `UiMenuButton` renders, so it inherits the popover and its
  positioning; it survives navigations and patches that redraw the header.
  Options: `themes` (what it offers, in order), `defaultTheme`, `storageKey`.
  Give it the same default as `data-default-theme`, or the picker ticks a theme
  the page is not wearing.
- **`applyTheme(name)`** and **`currentTheme()`** are the same state for your own
  control — a settings page, a keyboard shortcut.

## Server-rendered pages

A page rendered as HTML by `UiPageHtmlMessageConverter` picks its sheets from
the request attribute `UiPageHtmlMessageConverter.THEME_ATTRIBUTE`: `light`
(the default), `sbb`, or any overlay name. An overlay links `sui.css` plus its
own sheet and puts the class on `<html>`.

## Writing a theme

An overlay sets the framework's tokens (`--sui-color-*`, `--sui-radius-*`,
`--sui-space-*`, `--sui-shadow-*`, `--sui-header-*`) under
`:root.sui-theme-<name>` and restyles as little as it can beyond that. Two
things are worth copying from the shipped ones:

- **Name no application class.** An app's own stylesheet should speak in the
  same tokens, so a theme only has to set them.
- **A floating menu keeps its ground.** A theme that makes the sidebar
  transparent — so it reads as one plane with the shell — has to give it back a
  background once it floats as a drawer (`.sui-menu--overlay`, or
  `.sui-menu--responsive` below 768px). Otherwise the page shows through the
  navigation. Each shipped overlay shows the rule.
