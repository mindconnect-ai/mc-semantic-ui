---
title: Overflow behaviour
sidebar_position: 9
---

# Overflow: one row, or wrap

Some rows must not grow: a tab bar, a header's nav links, a toolbar. When the
entries stop fitting, there are only two honest answers — **wrap onto another
line**, or **keep one row and hide the surplus behind a `⋯` menu**.

semantic-ui implements the second as **one shared behaviour** rather than once
per component. Any container can opt in, including one you build yourself.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=header-responsive"
  title="Live: overflow behaviour"
  loading="lazy"
  style={{width: '100%', height: '260px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — two headers, identical extras. Narrow the window: the first wraps, the
second collapses into `⋯`.*

## Opting in

The nodes that have a row to protect expose it as a field:

| Node | Field | Values |
|---|---|---|
| [`section`](./elements/section.md) | `tabOverflow` | `WRAP` (default) · `MENU` |
| [`header`](./elements/header.md) | `extrasOverflow` | `WRAP` (default) · `MENU` |

```java
UiSection.of("tabs", null).tabOverflow(UiSection.TabOverflow.MENU);
UiHeader.of("Acme").extrasOverflow(UiHeader.ExtrasOverflow.MENU);
```

**`WRAP` is the default on purpose.** It needs no JavaScript, so it is also what
server-rendered pages do — and a wrapped row is never unusable, just taller.

## Using it on your own container

The behaviour is driven by data attributes, not by node type, so it works on
anything you render:

```html
<div class="my-toolbar" data-sui-overflow="menu">
  <button>Bold</button>
  <button>Italic</button>
  …
</div>
```

| Attribute | Meaning |
|---|---|
| `data-sui-overflow="menu"` | Opt in. Any other value is left alone. |
| `data-sui-overflow-items="<selector>"` | Which children may move. Default: all element children. A tab bar sets `.sui-tab` so its own `⋯` control is never eaten. |
| `data-sui-overflow-active="<class>"` | When a moved child carries this class, the `⋯` button gets it too — so a hidden-but-selected entry stays visible. Default `active`. |

The event bus wires this on every mount and on DOM changes, so there is nothing
to call. If you render outside the bus, call it yourself:

```js
import { wireOverflow } from "/sui/renderer.js";
wireOverflow();          // idempotent; safe after every re-render
```

## How it decides

On first run and on every resize the behaviour pulls everything back into the
row, measures, then moves children from the end into the dropdown until it fits.

:::note One CSS rule you can't break
The container must be **start-aligned**. Overflow past the *start* edge is not
scrollable, so `scrollWidth` would equal `clientWidth` and the measurement would
always conclude "it fits" — the menu would stay empty forever. `justify-content:
flex-end` on an overflow container is therefore a silent bug; push the row to
the right with the *parent's* layout instead.

This cost an hour when the header was built, which is why it is written down.
:::

## See also

- **[`section`](./elements/section.md)** — tab bars.
- **[`header`](./elements/header.md)** — the nav-links case, plus what else a
  header drops on a narrow screen.
- **[Mobile & responsive](./responsive.md)** — the other responsive mechanics.
