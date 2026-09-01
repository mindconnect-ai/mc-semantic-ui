# @mindconnect-ai/mc-semantic-ui-core

The client half of [mc-semantic-ui](https://github.com/mindconnect-ai/mc-semantic-ui):
a typed JSON UI vocabulary (`UiNode`) and the renderer that turns it into DOM.

Plain ESM, no runtime dependencies, no build tool required — it runs from a
`<script type="module">` as happily as it does through Vite or webpack. The
same tree also renders server-side from the Java library, and the markup is
identical either way.

> Not the [Semantic UI](https://semantic-ui.com) CSS framework, and not related
> to it. This is a server-driven UI toolkit that happens to share a word; the
> `mc-` prefix is what keeps the two apart.

## Install

```bash
npm install @mindconnect-ai/mc-semantic-ui-core
```

## Use

```ts
import { SuiRenderer, installDefaultHandlers } from "@mindconnect-ai/mc-semantic-ui-core";
import { SuiEventBus } from "@mindconnect-ai/mc-semantic-ui-core/eventbus";
import "@mindconnect-ai/mc-semantic-ui-core/sui.css";

const host = document.getElementById("app")!;
const renderer = installDefaultHandlers(new SuiRenderer(host));

// The bus owns fetching, morphing and the enhancers (menu state, tab
// overflow, menu-button popovers) — it is what makes the page a live SPA.
const bus = new SuiEventBus(renderer, host);
bus.navigate("/products");
```

Rendering a tree you already hold, without the bus:

```ts
import { createDefaultRenderer } from "@mindconnect-ai/mc-semantic-ui-core";
import type { UiNode } from "@mindconnect-ai/mc-semantic-ui-core/model";

const tree: UiNode = await fetch("/api/screen").then(r => r.json());
document.querySelector("#app")!.innerHTML = createDefaultRenderer().render(tree);
```

### Entry points

| Import | What it gives you |
| --- | --- |
| `@mindconnect-ai/mc-semantic-ui-core` | `SuiRenderer`, `createDefaultRenderer`, `renderIcon`, the enhancers |
| `…/eventbus` | `SuiEventBus` — the SPA driver |
| `…/model` | The `UiNode` union and every node type |
| `…/bff` | `bffFetch`, `redirectToLogin` for a session-cookie backend |
| `…/i18n` | Message lookup used by the renderers |
| `…/sui.css`, `…/sui-dark.css`, `…/sui-sbb.css` | Stylesheets |
| `…/icons.svg` | The curated icon sprite |

### Themes

`sui.css` is the base and is always required. `sui-dark.css` and `sui-sbb.css`
layer on top of it — import the base first.

### Icons

Icons are tokens (`"delete"`), resolved at render time into an SVG `<use>` into
the sprite. The default resolver finds the sprite next to the compiled module
(`new URL("../icons.svg", import.meta.url)`), which works unchanged whether the
package is served from `node_modules`, from a Spring app at `/sui/`, or from a
CDN.

Vite needs nothing for this: a production build emits the sprite as a hashed
asset and rewrites the reference. One case does need a line of config — when the
package is **linked** rather than installed (a monorepo, or a `file:`
dependency), the dev server resolves the sprite to its real location outside
your project and refuses to serve it, so every icon comes back empty with a 403
in the network tab. Allow the path:

```ts
// vite.config.ts
export default defineConfig({
  server: { fs: { allow: ["/path/to/the/repo"] } },
});
```

If a bundler rewrites the URL into something wrong anyway, point it at the
sprite yourself:

```ts
import { setIconSpriteUrl } from "@mindconnect-ai/mc-semantic-ui-core";
import spriteUrl from "@mindconnect-ai/mc-semantic-ui-core/icons.svg?url";

setIconSpriteUrl(spriteUrl);
```

That `?url` import needs `"types": ["vite/client"]` in your tsconfig, or
TypeScript reports the module as missing while the build itself succeeds.

`setIconResolver` replaces the mechanism entirely, for a different sprite,
inline SVG, or an icon font.

## Versioning

This package shares one version with the Java artifacts on Maven Central, cut
from the same commit. `0.2.0` of the npm package and `0.2.0` of
`ai.mindconnect:mc-semantic-ui-core` are the same client — which matters,
because SSR and SPA markup must match.

Pre-1.0: breaking changes may land in a minor.

## License

Apache-2.0
