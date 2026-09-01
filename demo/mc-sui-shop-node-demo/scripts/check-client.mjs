// Verifies this demo can still find the semantic-ui client the way it uses it
// at runtime: through ordinary package resolution, not a file copy.
//
//   node scripts/check-client.mjs   (or: npm run check-client)
//
// The demo depends on the core module by relative path, so a directory move
// elsewhere in the repo, a broken "exports" map, or a core that was never
// compiled all break it — silently, because Express would just serve 404s and
// the page would come up blank. The Maven build runs this in the `test` phase;
// the demo has no unit tests, and this is the closest thing it has to one.

import { existsSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";

const PKG = "@mindconnect-ai/mc-semantic-ui-core";
const require = createRequire(import.meta.url);

let pkgJson;
try {
  pkgJson = require.resolve(`${PKG}/package.json`);
} catch {
  console.error(
    `Cannot resolve ${PKG}.\n` +
    "Run `npm install` here; it links the core module from ../../core/.");
  process.exit(1);
}

const dist = join(dirname(pkgJson), "dist");

// The files index.html actually asks for, plus the sprite the icon renderer
// resolves relative to itself — the one that has to sit beside renderers/.
const REQUIRED = ["renderer.js", "eventbus.js", "sui.css", "icons.svg"];
const missing = REQUIRED.filter(f => !existsSync(join(dist, f)));

if (!existsSync(dist) || missing.length) {
  console.error(
    `Client incomplete at ${dist}\n` +
    (missing.length ? `Missing: ${missing.join(", ")}\n` : "") +
    "Build the core module once:\n" +
    "  cd ../../core/mc-semantic-ui-core && npm install && npm run build");
  process.exit(1);
}

console.log(`ok — ${PKG} resolves, client complete at ${dist}`);
