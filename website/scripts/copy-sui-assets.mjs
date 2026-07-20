// Copies the compiled browser bundles into the Docusaurus static/ folder so the
// site serves them at <baseUrl>/sui/** and <baseUrl>/sui-ext/**.
//
// Two reasons this exists:
//   1. the live <SuiIsland> demos on the docs pages import the renderer at
//      runtime from <baseUrl>/sui/renderer.js;
//   2. the same files are the public CDN bundle documented in cdn-assets.md.
//
// Sources are build outputs, so this runs as a prestart/prebuild hook and the
// destinations are git-ignored. If the modules haven't been built yet we warn
// and carry on — the docs still build, the live demos just won't render.

import { cpSync, existsSync, mkdirSync, rmSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, "../..");

const CORE = resolve(repo, "core/mc-semantic-ui-core");
const EXT = resolve(repo, "ext/mc-semantic-ui-ext-diagram");
const EXT_CHART = resolve(repo, "ext/mc-semantic-ui-ext-chart");
const STATIC = resolve(here, "../static");

let missing = [];

function copyDir(from, to, label) {
    if (!existsSync(from)) { missing.push(label); return; }
    rmSync(to, { recursive: true, force: true });
    mkdirSync(to, { recursive: true });
    cpSync(from, to, { recursive: true });
}

/** Like copyDir, but adds to an existing directory instead of replacing it. */
function mergeDir(from, to, label) {
    if (!existsSync(from)) { missing.push(label); return; }
    mkdirSync(to, { recursive: true });
    cpSync(from, to, { recursive: true });
}

// ── core: compiled TS bundle + the stylesheets ──────────────────────────────
copyDir(resolve(CORE, "target/ts-dist"), resolve(STATIC, "sui"),
        "core/mc-semantic-ui-core/target/ts-dist (run: mvn -pl core/mc-semantic-ui-core install)");

for (const css of ["sui.css", "sui-dark.css", "sui-sbb.css"]) {
    const src = resolve(CORE, "src/main/resources", css);
    if (existsSync(src)) cpSync(src, resolve(STATIC, "sui", css));
}
// The icon sprite is served next to the bundle (renderIcon resolves /sui/icons.svg).
const sprite = resolve(CORE, "src/main/resources/icons.svg");
if (existsSync(sprite)) cpSync(sprite, resolve(STATIC, "sui", "icons.svg"));

// ── diagram extension: its own bundle under /sui-ext ────────────────────────
copyDir(resolve(EXT, "target/ts-dist"), resolve(STATIC, "sui-ext"),
        "ext/mc-semantic-ui-ext-diagram/target/ts-dist (run: mvn -pl ext/mc-semantic-ui-ext-diagram install)");
// …plus its stylesheet, which is a plain resource rather than TS output — so
// it does not live in target/ts-dist and has to be staged separately. Without
// it a diagram renders as unstyled SVG, and the docs' <link> would 404 (the
// dev server answers unknown paths with the SPA shell, so it looks like a 200).
mergeDir(resolve(EXT, "src/main/resources/META-INF/resources/sui-ext"),
        resolve(STATIC, "sui-ext"),
        "ext/mc-semantic-ui-ext-diagram/src/main/resources/META-INF/resources/sui-ext");

// ── chart extension: same shape — compiled TS plus its stylesheet ───────────
mergeDir(resolve(EXT_CHART, "target/ts-dist"), resolve(STATIC, "sui-ext"),
        "ext/mc-semantic-ui-ext-chart/target/ts-dist (run: mvn -pl ext/mc-semantic-ui-ext-chart install)");
mergeDir(resolve(EXT_CHART, "src/main/resources/META-INF/resources/sui-ext"),
        resolve(STATIC, "sui-ext"),
        "ext/mc-semantic-ui-ext-chart/src/main/resources/META-INF/resources/sui-ext");

// ── the two backend-free apps, folded into the site ─────────────────────────
// These back the /widget-demo and /editor links the docs point at, and the
// <iframe> embeds on the editor page.
copyDir(resolve(repo, "demo/mc-sui-widget-demo/target/dist"),
        resolve(STATIC, "widget-demo"),
        "demo/mc-sui-widget-demo/target/dist (run: mvn -pl demo/mc-sui-widget-demo package)");

copyDir(resolve(repo, "editor/mc-sui-editor-standalone-app/target/dist"),
        resolve(STATIC, "editor"),
        "editor/mc-sui-editor-standalone-app/target/dist (run: mvn -pl editor/mc-sui-editor-standalone-app package)");

if (missing.length) {
    console.warn("[copy-sui-assets] not built yet, skipping:\n  - " + missing.join("\n  - "));
} else {
    console.log("[copy-sui-assets] staged /sui and /sui-ext into website/static/");
}
