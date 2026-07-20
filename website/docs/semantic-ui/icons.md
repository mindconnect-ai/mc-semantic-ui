---
title: Icon library
sidebar_position: 8
---

# The icon library

Icons are referenced by **token**, never by file: a node carries
`icon: "delete"`, and a resolver turns that into markup. Because everything
routes through that one resolver, the entire icon set is swappable without
touching a single `UiNode`.

For the `icon` node itself — fields, sizes, status colours, live preview — see
**[`icon`](./elements/icon.md)**.

## Tokens: semantic aliases vs raw ids

The default sprite ships two kinds of name:

- **Semantic aliases** — `delete`, `edit`, `add`, `back`, `download`, `show`,
  `folder`, `document`, `info`, `warning`, `error`, `success`, `grid`. These
  describe *intent*, so they survive a change of icon library.
- **Raw sprite ids** — whatever the underlying set calls its glyphs.

Prefer the aliases. They are the reason a re-skin is a config change rather than
a find-and-replace across your application code.

The full list lives in
[`core/mc-semantic-ui-core/icons/icons.json`](https://github.com/mindconnect-ai/mc-semantic-ui/blob/main/core/mc-semantic-ui-core/icons/icons.json).
An unknown token renders nothing visible — a missing icon is a typo, and it
fails where you can see it.

## Pointing at a different sprite

```java
// SSR
IconRenderer.setSpriteUrl("/assets/my-icons.svg");
```

```js
// SPA
import { setIconSpriteUrl } from "/sui/renderer.js";
setIconSpriteUrl("/assets/my-icons.svg");
```

## Replacing the scheme entirely

Inline SVG, an icon font, an image CDN — the resolver decides:

```js
import { setIconResolver } from "/sui/renderer.js";
setIconResolver((name, opts) =>
    `<i class="fa fa-${name}"${opts.title ? ` title="${opts.title}"` : ""}></i>`);
```

```java
IconRenderer.setResolver((name, cssClass, title, id) -> "<i class=\"fa fa-" + name + "\"></i>");
```

Every renderer routes through the active resolver, so one call re-skins every
icon on the page — including the ones inside built-in nodes such as menu items
and table row actions.

## Changing the default sprite

The sprite is a **generated, committed** artifact; the Maven build only copies
it. To change the set, edit the `token → icon-id` map in
`core/mc-semantic-ui-core/icons/icons.json` and regenerate:

```bash
cd core/mc-semantic-ui-core
npm run build:icons      # rewrites src/main/resources/icons.svg
```

Commit the regenerated `icons.svg`. To move to a different icon library
altogether, install that library's static-SVG package, re-point the ids in
`icons.json` and regenerate — the semantic aliases, and therefore all your
application JSON, stay exactly the same.

## Styling

`.sui-icon` is `1em × 1em`, `fill: none`, `stroke: currentColor`. Colour an icon
by setting `color` on it or on an ancestor. The sprite's status helpers —
`sui-icon--success`, `--warning`, `--danger`, `--muted` — are ready-made
`cssClass` values.

## See also

- **[`icon`](./elements/icon.md)** — the node reference.
- **[`action`](./elements/action.md)** — `icon` plus `appearance: ICON` gives an
  icon-only button.
- **[Icon gallery ↗](pathname:///widget-demo/)** — every token in the shipped
  sprite, searchable.
