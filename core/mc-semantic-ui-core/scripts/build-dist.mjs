// Assembles dist/ — the folder that IS the npm package.
//
//   node scripts/build-dist.mjs   (or: npm run build, which runs tsc first)
//
// Two channels read this folder and neither owns it: the Maven build copies it
// into the JAR under META-INF/resources/sui/ (see pom.xml), and npm publishes
// it through the "exports" map in package.json. Deciding here what "the client"
// is — compiled TypeScript at the root, stylesheets and icon sprite next to it
// — is what keeps a Spring app and a Vite app on the same renderer, which they
// must be: SSR and SPA markup have to match.
//
// The layout is load-bearing in one place: renderers/icon.js resolves the
// sprite as `new URL("../icons.svg", import.meta.url)`, so icons.svg must sit
// exactly one level above renderers/. Move it and icons silently stop
// resolving in every consumer that does not call setIconSpriteUrl().

import { cpSync, existsSync, mkdirSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..");

const TS_DIST = join(ROOT, "target", "ts-dist");
const RESOURCES = join(ROOT, "src", "main", "resources");
const OUT = join(ROOT, "dist");

// Stylesheets and the sprite live under src/main/resources because Maven owns
// that tree; they are part of the client all the same.
const ASSETS = ["sui.css", "sui-dark.css", "sui-sbb.css", "icons.svg"];

if (!existsSync(TS_DIST)) {
  console.error(
    `Compiled TypeScript not found at ${TS_DIST}\n` +
    "Run `npm run build` (tsc, then this script) rather than this script alone.");
  process.exit(1);
}

// A stale file in dist/ would be published even after its source was deleted,
// so the folder is rebuilt from nothing every time.
rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });

cpSync(TS_DIST, OUT, { recursive: true });

const missing = [];
for (const asset of ASSETS) {
  const src = join(RESOURCES, asset);
  if (existsSync(src)) cpSync(src, join(OUT, asset));
  else missing.push(asset);
}
if (missing.length) {
  console.error(
    `Missing from ${RESOURCES}: ${missing.join(", ")}\n` +
    "The sprite is generated and committed — run `npm run build:icons` if it is icons.svg.");
  process.exit(1);
}

console.log(`Assembled npm package contents in ${OUT}`);
