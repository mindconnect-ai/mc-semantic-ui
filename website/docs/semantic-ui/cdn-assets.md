---
title: CDN assets for HTML clients
sidebar_position: 8
---

# CDN assets for plain-HTML clients

Clients that are **just HTML** — no Spring Boot app serving `/sui/*` from the
core JAR, no build step, no npm — can load the browser runtime straight from
this docs site. The bundle is the exact output of `mc-semantic-ui-core`'s
TypeScript build: `renderer.js`, `eventbus.js`, `model.js` (+ `.d.ts`), the
per-node renderers, and the stylesheets (`sui.css`, `sui-dark.css`,
`sui-sbb.css`).

## Two URL shapes

**Latest** — follows `main`, replaced on every docs deploy:

```
https://mindconnect-ai.github.io/mc-docs/sui/renderer.js
https://mindconnect-ai.github.io/mc-docs/sui/eventbus.js
https://mindconnect-ai.github.io/mc-docs/sui/sui.css
```

**Pinned** — frozen forever by a `sui-v<version>` git tag on `mc-docs`,
served immutably by jsDelivr's CDN:

```
https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/renderer.js
https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/eventbus.js
https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/sui.css
```

Use **pinned** for anything you ship: the files behind a tag never change, so
a deployed client can't be broken by a later docs deploy. Use **latest** for
experiments and demos that should track the current state of `main`.

## Minimal client

The same [UI island](./ui-island.md) pattern, with CDN URLs instead of a
backend-served `/sui/*`:

```html
<!doctype html>
<link rel="stylesheet"
      href="https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/sui.css">

<div id="app"></div>

<script type="module">
  import { SuiRenderer, installDefaultHandlers }
    from "https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/renderer.js";
  import { SuiEventBus }
    from "https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/eventbus.js";

  const host = document.getElementById("app");
  const renderer = installDefaultHandlers(new SuiRenderer(host));
  const bus = new SuiEventBus(renderer, host);

  // The tree can come from anywhere — an inline literal, a fetch() to your
  // own backend, a static JSON file next to this HTML…
  renderer.mount({
    type: "detail", id: "hello", title: "Hello from the CDN",
    fields: [{ type: "field", id: "f1", label: "Runtime", fieldType: "TEXT",
               value: "loaded from jsDelivr, no build step" }]
  });
</script>
```

Relative imports inside the bundle (`renderer.js` → `./renderers/*.js`,
`eventbus.js` → `./model.js`) resolve against the CDN URL automatically —
loading the two entry modules is enough.

:::note Backend calls are up to you
The runtime is origin-agnostic, but any `UiTrigger` URL (`/api/…`) is fetched
against **your page's origin**. If your backend lives elsewhere, use absolute
trigger URLs and enable CORS there — the CDN only hosts the static runtime.
:::

## Cutting a release

Releases are cut from the private monorepo — run the **docs** workflow
manually (*Actions → docs → Run workflow*) and fill in `sui_release`, e.g.
`0.1.0`. The workflow deploys the site as usual and then tags the resulting
`gh-pages` commit as `sui-v0.1.0` on `mc-docs`. Later deploys replace the
Pages tree, but the tagged commit stays reachable — that's what keeps the
jsDelivr URLs immutable.
