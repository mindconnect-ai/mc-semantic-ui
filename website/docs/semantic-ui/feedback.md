---
title: Loading & busy state
sidebar_position: 7
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Loading &amp; busy state

Most of the busy feedback in a semantic-ui app is automatic. This page covers
the behaviour that spans every node type; for the nodes themselves see
**[`spinner`](./elements/spinner.md)** and
**[`progress`](./elements/progress.md)**.

## Automatic inline loading

Any control that dispatches a trigger — a button, an action rendered as a link,
a form submit — gets an `is-loading` class while its request is in flight. The
CSS paints a small spinner on it and blocks a second click; the class is removed
when the request settles. Nothing to configure:

```java
// Clicking this shows a spinner on the button until the POST resolves.
UiAction.primary("save", "Save").icon("save")
        .onClick(UiTrigger.api("POST", "/products"));
```

A **link** can dispatch too: give [`link`](./elements/link.md) an `onClick` and
it fires a fetch/patch — and gets the same inline loading — instead of
navigating. `href` stays as the no-JS fallback:

```java
UiLink.of("more", "/items?page=2", "Load more")
      .onClick(UiTrigger.api("GET", "/items?page=2"));
```

## The loading policy

Inline feedback and the global loading indicator share one policy:

```ts
bus.setLoadingPolicy("manual");   // suppresses both the global bar and inline spinners
```

`"auto"` (the default) shows it on every dispatch. A predicate
`(ctx) => boolean` decides per trigger — useful for a chat or streaming surface
that manages its own affordances.

## When the server owns the state

The automatic behaviour covers *this control's own click is in flight*. When the
button must **stay** busy across a re-render, or look busy without a click, put
it in the model with `loading`:

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiAction.primary("save", "Saving…").loading(true);   // rendered busy + disabled
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "action", "id": "save", "label": "Saving…", "style": "PRIMARY", "loading": true }
```

</TabItem>
</Tabs>

The typical flow: the click returns a patch replacing the button with
`loading: true`, and a later patch replaces it again with the finished button.

## See also

- **[`spinner`](./elements/spinner.md)** — the standalone busy indicator.
- **[`progress`](./elements/progress.md)** — determinate and indeterminate bars
  and rings, and how to drive them from the server.
- **[Triggers & actions](./triggers.md)** — what a dispatch actually does.
