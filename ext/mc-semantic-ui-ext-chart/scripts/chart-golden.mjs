// Writes what the TypeScript painter draws for every fixture in
// src/test/resources/chart-golden/*.json next to it, as <name>.svg.
// ChartPainterGoldenTest draws the same fixtures in Java and compares:
// the two painters must produce the same characters.
//
//   npm run build && node scripts/chart-golden.mjs
import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { chartSvg } from "../target/ts-dist/chart/extension.js";

const DIR = join(dirname(fileURLToPath(import.meta.url)), "..", "src", "test", "resources", "chart-golden");
for (const file of readdirSync(DIR).filter(f => f.endsWith(".json")).sort()) {
    const node = JSON.parse(readFileSync(join(DIR, file), "utf8"));
    writeFileSync(join(DIR, file.replace(/\.json$/, ".svg")), chartSvg(node) + "\n");
    console.log("wrote", file.replace(/\.json$/, ".svg"));
}
