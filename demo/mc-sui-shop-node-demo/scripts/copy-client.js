// Copies the semantic-ui client assets (compiled TS renderer + CSS) from the
// core module into this demo's public/sui/ folder, so the browser can import
// them at /sui/renderer.js, /sui/eventbus.js and load /sui/sui.css.
//
// The renderer is plain ESM with no runtime dependencies (Idiomorph is loaded
// lazily from a CDN by the renderer itself), so a straight file copy is enough.
import {cpSync, mkdirSync, existsSync} from "node:fs";
import {dirname, resolve} from "node:path";
import {fileURLToPath} from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const core = resolve(here, "../../../core/mc-semantic-ui-core/src/main");
const tsDist = resolve(core, "../../target/ts-dist");
const resources = resolve(core, "resources");
const out = resolve(here, "../public/sui");

if (!existsSync(tsDist)) {
  console.error(
    "Compiled client not found at " + tsDist + "\n" +
    "Build the core module's TypeScript first:\n" +
    "  cd ../../core/mc-semantic-ui-core && npm install && npm run build\n" +
    "(or run a Maven build of mc-semantic-ui-core, which compiles the TS).");
  process.exit(1);
}

mkdirSync(out, {recursive: true});

// Compiled renderer / eventbus / model + per-type renderers.
cpSync(tsDist, out, {recursive: true});

// Stylesheets live in resources, not in the TS output.
for (const css of ["sui.css", "sui-dark.css", "sui-sbb.css"]) {
  const src = resolve(resources, css);
  if (existsSync(src)) cpSync(src, resolve(out, css));
}

console.log("Copied semantic-ui client assets to public/sui/");
