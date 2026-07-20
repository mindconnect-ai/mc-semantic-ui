---
title: Icon
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `icon` — icons anywhere

**`UiIcon`** places an icon wherever a node is accepted: a `UiStack` child, a
tree or list `labelNode`, a table `cellTemplate`. Most components already take
an `icon` string for the common leading-icon case — this node covers *free*
placement, and being a node it has an `id`, so a patch can `REPLACE` or `REMOVE`
it on its own.

An icon carries only a **token**. What that token renders to is decided by a
swappable icon layer — by default an SVG `<use>` into the curated sprite at
`/sui/icons.svg`, sourced from [Lucide](https://lucide.dev) (ISC-licensed).

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=icon"
  title="Live: UiIcon"
  loading="lazy"
  style={{width: '100%', height: '330px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — real tokens from `icons/icons.json`: semantic aliases, then raw sprite
ids, then the status colour helpers and a legacy emoji. The bottom row shows the
`icon` shorthand on an action and on a field.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — set it if a patch needs to swap the icon later. |
| `name` | `String` | The token: a semantic alias (`"delete"`) or a raw sprite id with no alias (`"chevron-right"`). A non-token string (an emoji) is emitted verbatim. |
| `title` | `String` | Accessible label. With it the icon is `role="img"` + `<title>`; without it the icon is `aria-hidden` (decorative). |
| `cssClass` | `String` | Extra CSS class — the status helpers `sui-icon--success`, `--warning`, `--danger` / `--error`, `--muted` live here. |

There is no size field: `.sui-icon` is `1em × 1em` and the stroke is
`currentColor`, so an icon inherits the surrounding text's size and colour.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// The standalone node — free placement, patchable by id:
UiIcon.of("success");                                        // decorative
UiIcon.of("status", "success").labelled("All good")          // id + a11y label
      .withCssClass("sui-icon--success");

// The `icon` shorthand, for the common leading-icon case:
UiAction.primary("save", "Save").icon("save");
UiAction.secondary("edit", "Edit").icon("pencil")            // icon-only button;
        .appearance(UiAction.Appearance.ICON);               //   label → accessible name
UiField.text("q", "Search", null).asEditable().icon("search");
UiLink.of("back", "/x", "Back").icon("back");
UiSectionEntry.of("t", "Settings", body).icon("settings");
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "icon", "id": "status", "name": "success", "title": "All good",
  "cssClass": "sui-icon--success" }

{ "type": "action", "id": "save", "label": "Save", "icon": "save", "style": "PRIMARY" }
```

</TabItem>
</Tabs>

## Tokens

A token is either a **semantic alias** or a **raw sprite id**. Aliases are
declared in `core/mc-semantic-ui-core/icons/icons.json` as `token → lucide-id`, and
that indirection is what makes the library swappable — app JSON that uses an
alias survives a library change unchanged. The full alias set:

| Group | Tokens |
|---|---|
| Editing | `add` · `edit` · `delete` · `remove` · `save` · `copy` · `close` · `check` · `confirm` · `cancel` · `send` · `reset` · `refresh` |
| Navigation | `back` · `forward` · `up` · `down` · `home` · `menu` · `more` · `more-vertical` · `external` · `link` · `expand` · `collapse` · `first` · `last` |
| Status | `success` · `warning` · `error` · `info` · `help` · `loading` · `pending` |
| People & settings | `user` · `users` · `settings` · `search` · `filter` · `sort` · `calendar` · `clock` |
| Files | `file` · `document` · `folder` · `folder-open` · `image` · `download` · `upload` · `print` |
| Markers | `mail` · `bell` · `star` · `favorite` · `bookmark` · `tag` · `flag` |
| Security | `lock` · `unlock` · `key` · `show` · `hide` · `logout` · `login` · `power` |
| Data & tech | `table` · `list` · `grid` · `chart` · `dashboard` · `database` · `server` · `code` · `terminal` · `branch` · `cloud` · `globe` |
| Misc | `money` · `euro` · `percent` · `location` · `phone` · `message` · `chat` · `share` · `flash` · `light` · `dark` |

Raw Lucide ids work too when they are in the sprite. Beyond the ids the aliases
resolve to (`trash-2`, `pencil`, `circle-check`, …), the sprite carries a
`passthrough` block of extra raw ids with no alias: `chevron-up`,
`chevron-down`, `chevron-left`, `chevron-right`, `circle-plus`, `circle-minus`,
`circle-alert`, `arrow-up`, `arrow-down`, `arrow-left`, `arrow-right`,
`smartphone`, `tablet`, `monitor`, `laptop`.

## Notes

**Prefer the alias.** `delete` and `trash-2` render the same glyph today, but
only the first one still means "delete" after the icon library is swapped. Use
raw ids for the decorative cases where no alias fits.

**The library is swappable, and no `UiNode` changes.** Everything routes through
one resolver: `IconRenderer.setSpriteUrl(url)` / `setIconSpriteUrl(url)` points
at a different sprite, and `IconRenderer.setResolver(...)` /
`setIconResolver(...)` replaces the scheme entirely — inline SVG, an icon font,
anything. One call re-skins every icon on the page.

```js
import { setIconSpriteUrl, setIconResolver } from "/sui/renderer.js";
setIconSpriteUrl("/assets/my-icons.svg");
setIconResolver((name, opts) => `<i class="fa fa-${name}"></i>`);
```

**Unknown tokens fail visibly.** A token that matches the sprite-id shape
(`^[a-z][a-z0-9-]*$`) becomes an `<svg><use href="…#token">`; if the sprite has
no such symbol the SVG renders empty — a blank slot, not a fallback glyph. Check
the token against `icons.json` rather than guessing.

**Any other string is a literal glyph.** An emoji or arbitrary text does not
match the token shape and is emitted verbatim in a `<span>`. That is the
migration path: legacy `icon: "📁"` data keeps working while new data uses
`icon: "folder"` — no flag day.

**Changing the default set is a build step.** `icons.svg` is a generated,
committed artifact. Edit the `token → lucide-id` map in
`core/mc-semantic-ui-core/icons/icons.json`, run `npm run build:icons` in
`mc-semantic-ui-core`, and commit the regenerated sprite.

## See also

- **[`action`](./action.md)** — the `icon` shorthand and icon-only buttons.
- **[`toast`](./toast.md)** — level glyphs come from this same set.
- **[Triggers & actions](../triggers.md)** — patching an icon by id.
- **[Triggers cookbook](../triggers-cookbook.md)** — status-flip recipes.
