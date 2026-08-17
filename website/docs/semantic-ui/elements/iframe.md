---
title: IFrame
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `iframe` — an embedded browsing context

**`UiIFrame`** folds a whole foreign page into a semantic-ui screen — a
Swagger UI, a Grafana dashboard, a legacy admin page. The framework renders
the `<iframe>` and its sizing; what happens inside is the embedded page's
business.

Two height modes, same convention as [`scrollpane`](./scrollpane.md):

- **`height` set** — the frame caps at that CSS length in normal flow.
- **`height` absent** — the frame fills the leftover space of a flex-column
  parent (the "embedded app takes the whole content pane" layout).

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` and the patch target. |
| `src` | `String` | The embedded page's URL. Same-origin or absolute. |
| `height` | `String` | CSS length capping the frame's height. Absent = fill the flex parent. |
| `sandbox` | `String` | Optional `sandbox` attribute value (`"allow-scripts allow-same-origin"`). Absent = no sandbox — fine for same-origin embeds; set it for third-party content. |
| `title` | `String` | Becomes the iframe's `title` attribute — set it, it is how screen readers name the region. |
| `cssClass` | `String` | Extra CSS class added next to `sui-iframe`. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// Swagger UI as a full-height admin page
UiIFrame.of("api-explorer", "/swagger-ui/index.html")
    .title("REST API explorer");

// a capped third-party embed
UiIFrame.of("status", "https://status.example.com")
    .height("480px")
    .sandbox("allow-scripts allow-same-origin")
    .title("Service status");
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "iframe", "id": "api-explorer",
  "src": "/swagger-ui/index.html", "title": "REST API explorer" }
```

</TabItem>
</Tabs>
