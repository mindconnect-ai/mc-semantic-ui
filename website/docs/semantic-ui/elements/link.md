---
title: Link
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `link` — plain navigation

**`UiLink`** is an anchor: text (optionally with a leading icon) that takes the
user somewhere. It is the quiet counterpart to [`action`](./action.md) — no
button chrome, no `style`, no `confirm`. Where an action *does something*, a
link *goes somewhere*.

Reach for `UiLink` for back/next navigation, "View history", a footnote to
external documentation, or the `links` row under a [`detail`](./detail.md) or
[`form`](./form.md). Reach for a `UiAction` with `appearance: LINK` instead when
the thing is really a command that merely wants to look understated.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=link"
  title="Live: UiLink"
  loading="lazy"
  style={{width: '100%', height: '200px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — plain links, links with a leading icon, an external link that opens in a
new tab, and "Load history", which carries an `onClick` trigger and patches the
line below instead of navigating.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id and DOM `id`. The `of(...)` factories set it to `rel` for backwards compatibility. |
| `rel` | `String` | Link relation / role — e.g. `back`, `next`, `ref`. Historically doubled as the id slot. |
| `href` | `String` | Target URL. Falls back to `#` when absent. Also the no-JS / SSR target when `onClick` is set. |
| `label` | `String` | The visible link text. |
| `icon` | `String` | Icon token rendered before the label. See [icons](./icon.md). |
| `external` | `boolean` | Defaults to `false`. `true` renders `target="_blank" rel="noopener noreferrer"` and always navigates natively. |
| `onClick` | `UiTrigger` | Optional click behaviour dispatched through the event bus instead of navigating. Ignored when `external` is `true`. |
| `title` | `String` | Inherited from `UiNode`. Not rendered by the link renderer. |
| `cssClass` | `String` | Extra CSS class added next to `sui-link`. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// Plain navigation — the id is set to the rel:
UiLink.of("back", "/products", "Back to products");

// With a leading icon:
UiLink.of("ref", "/products/42/manual.pdf", "Download the manual").icon("download");

// External — opens in a new tab, onClick would be ignored:
UiLink.external("ref", "https://example.com", "Vendor website");

// A link that fires a trigger through the bus; href stays as the no-JS target:
UiLink.of("ref", "/products/42/history", "Load history")
      .onClick(UiTrigger.api("GET", "/products/42/history"));

// Where links usually live:
UiDetail.of("product-detail", "Product")
        .link(UiLink.of("ref", "/products/42/history", "View history"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "link", "id": "back", "rel": "back",
  "href": "/products", "label": "Back to products" }

{ "type": "link", "id": "manual", "rel": "ref", "icon": "download",
  "href": "/products/42/manual.pdf", "label": "Download the manual" }

{ "type": "link", "id": "site", "rel": "ref", "external": true,
  "href": "https://example.com", "label": "Vendor website" }

{ "type": "link", "id": "history", "rel": "ref",
  "href": "/products/42/history", "label": "Load history",
  "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET",
               "url": "/products/42/history" } }
```

</TabItem>
</Tabs>

## Notes

**Three renderings, one node.** Without `onClick` the anchor gets a `data-href`
hint and the bus routes it through the SPA. With `onClick` it gets a
`data-trigger` and dispatches a fetch/patch, with inline loading painted on the
link itself while the request is in flight — `href` remains as the plain-HTML
fallback for SSR and no-JS. With `external: true` neither hint is emitted, so
the native click goes through and `target="_blank"` actually works.

**`external` wins over `onClick`.** An external link always navigates natively;
attaching a trigger to it has no effect. If you need a behaviour, drop
`external` and open the tab yourself with `UiTrigger.openInTab(url)`.

**`rel` is not HTML's `rel`.** It is the node's own relation slot — `back`,
`next`, `ref` — and the `of(...)` factories copy it into `id` so that patch
targets and the editor's id-based selection keep working with legacy trees. Give
two links on the same page distinct `rel` values, or set the `id` explicitly.

**Links come in rows, not alone.** `UiDetail` and `UiForm` both carry a `links`
list rendered in the footer next to the actions; that is where most links
belong. A loose link inside a [`stack`](./stack.md) is fine for navigation
chrome, but a run of them usually wants to be a [`menu`](./menu.md).

## See also

- **[`action`](./action.md)** — clickable things that *do* something.
- **[`detail`](./detail.md)** — the `links` footer row.
- **[`icon`](./icon.md)** — the token set available for `icon`.
- **[Triggers cookbook](../triggers-cookbook.md)** — what an `onClick` can do.
