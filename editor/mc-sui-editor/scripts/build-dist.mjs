// Assembles dist/ — the folder that IS the npm package, and the one the Maven
// build folds into the JAR under META-INF/resources/sui-editor/.
//
// Same arrangement as the core and the extensions: one script decides what
// ships, so the JAR and the tarball cannot drift apart.
//
// editor.html is deliberately left out. It is the shell a Spring app serves,
// and it carries an import map pointing at /sui/ — absolute paths that mean
// something only there. An npm consumer brings their own page, and a bundler
// resolves the package name without any map.

import { cpSync, existsSync, mkdirSync, readdirSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..");

const TS_DIST = join(ROOT, "target", "ts-dist");
const STATIC = join(ROOT, "src", "main", "resources", "static");
const OUT = join(ROOT, "dist");

if (!existsSync(TS_DIST)) {
  console.error(
    `Compiled TypeScript not found at ${TS_DIST}\n` +
    "Run `npm run build` (tsc, then this script) rather than this script alone.");
  process.exit(1);
}

rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });
cpSync(TS_DIST, OUT, { recursive: true });

for (const file of readdirSync(STATIC)) {
  if (file.endsWith(".css")) cpSync(join(STATIC, file), join(OUT, file));
}

console.log(`Assembled npm package contents in ${OUT}`);
