// Assembles dist/ — the folder that IS the npm package, and the one the Maven
// build folds into the JAR under META-INF/resources/sui-ext/.
//
// Same arrangement as the core module: one script decides what an extension
// ships, so the JAR and the tarball cannot drift apart.
//
// The compiled extension has no runtime import of anything, on purpose. That
// is what lets the same file load from a CDN, from under a path prefix, and
// straight off a Spring app — with no bundler, no import map, and nothing for
// the browser to resolve.

import { cpSync, existsSync, mkdirSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..");

const TS_DIST = join(ROOT, "target", "ts-dist");
const OUT = join(ROOT, "dist");
// The stylesheets already sit at the path the JAR serves them from.
const CSS_DIR = join(ROOT, "src", "main", "resources", "META-INF", "resources", "sui-ext");

if (!existsSync(TS_DIST)) {
  console.error(
    `Compiled TypeScript not found at ${TS_DIST}\n` +
    "Run `npm run build` (tsc, then this script) rather than this script alone.");
  process.exit(1);
}

rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });
cpSync(TS_DIST, OUT, { recursive: true });

// Flattened? No — the folder mirrors what the JAR serves at
// META-INF/resources/sui-ext/, so the same relative layout holds whether the
// file came from a JAR, from npm, or from a CDN. The exports map hides the
// nesting from consumers, who just import `<pkg>/<name>.css`.
if (existsSync(CSS_DIR)) {
  cpSync(CSS_DIR, OUT, { recursive: true });
}

console.log(`Assembled npm package contents in ${OUT}`);
