---
title: Progress
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `progress` — bars and rings

**`UiProgress`** shows how far along a task is, as a horizontal `BAR` (the
default) or a circular `CIRCLE` ring. Set `value` for determinate progress;
leave it out and the node renders an indeterminate animation for "work is
happening, duration unknown".

`status` tints the fill, so the same node doubles as the *result* state once the
task finishes — a bar that ends green, or red.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=progress"
  title="Live: UiProgress"
  loading="lazy"
  style={{width: '100%', height: '210px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — determinate bars with status colours, an indeterminate bar, three
rings. "Advance +20%" drives the top bar and the first ring with a `REPLACE`
patch, exactly as a server would.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — the DOM id and the target of the `REPLACE` patch that advances it. |
| `value` | `Double` | Current progress. **`null` renders an indeterminate loop.** |
| `max` | `Double` | Upper bound for `value`. Defaults to `100` when absent. |
| `variant` | `BAR` · `CIRCLE` | Shape. Defaults to `BAR`. |
| `status` | `NORMAL` · `SUCCESS` · `WARNING` · `ERROR` | Colour intent of the fill. Defaults to `NORMAL`. |
| `showValue` | `Boolean` | Whether to render the trailing `NN%` readout. Defaults to `true`; forced off while indeterminate. |
| `title` | `String` | Accessible label on the `role="progressbar"` element. |
| `cssClass` | `String` | Extra CSS class on the element. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiProgress.of(60);                                        // a 60% bar
UiProgress.of(30, 120);                                   // 30 of 120 → 25%
UiProgress.of(100).status(UiProgress.Status.SUCCESS);     // green, done
UiProgress.of(80).status(UiProgress.Status.WARNING).showValue(false);
UiProgress.of(45).variant(UiProgress.Variant.CIRCLE);     // a ring
UiProgress.indeterminate();                               // duration unknown
UiProgress.indeterminate().variant(UiProgress.Variant.CIRCLE);

// Advancing it is an ordinary patch on the node's id:
UiPatch.of().patch(UiPatch.Operation.replace("upload", UiProgress.of(80)));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "progress", "id": "upload", "value": 60, "status": "NORMAL" }

{ "type": "progress", "id": "ring", "value": 45, "variant": "CIRCLE" }

{ "type": "progress", "id": "busy" }
```

Pushing an update:

```json
{ "patches": [
  { "op": "REPLACE", "targetId": "upload",
    "node": { "type": "progress", "id": "upload", "value": 80 } }
] }
```

</TabItem>
</Tabs>

## Notes

**Omitting `value` is the switch.** There is no `indeterminate` flag — a `null`
`value` *is* indeterminate, and `UiProgress.indeterminate()` is just a
constructor that leaves it unset. The renderer then drops the `NN%` readout and
swaps `aria-valuenow` for `aria-busy="true"`.

**Advance it with a `REPLACE` patch, keeping the id.** Progress is server state:
each update is an ordinary patch on the same node id, from a polling trigger, a
streamed response, or a handler. Nothing is animated for you between values —
the CSS transitions the fill width, so coarse steps still look smooth.

**`status` turns the bar into an outcome.** `SUCCESS` at 100 reads as "done",
`ERROR` as "failed at 45%". That saves swapping the node for a text line when
the task settles.

**`percent` is computed, not carried.** The readout is `round(value / max * 100)`
clamped to 0–100, so a `value` of 30 against `max` 120 shows 25%. `max`
defaults to 100, which is why `UiProgress.of(60)` already means 60%.

**For an unknown *and* uninteresting duration, use a spinner instead.** An
indeterminate bar claims a full-width slot; [`spinner`](./spinner.md) is the
lighter signal, and a control's own busy state is lighter still.

## See also

- **[`spinner`](./spinner.md)** — busy without a quantity.
- **[`action`](./action.md)** — `loading`, the busy-control state.
- **[Triggers & actions](../triggers.md)** — patch operations in full.
- **[Triggers cookbook](../triggers-cookbook.md)** — polling and streaming recipes.
