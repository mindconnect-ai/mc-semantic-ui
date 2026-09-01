import { defineConfig } from "vite";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
    server: {
        fs: {
            // This demo consumes the client as a `file:` dependency, so npm
            // links it and Vite resolves its files to their real location —
            // outside this folder. The dev server refuses to serve paths
            // outside the project root unless they are allowed here, and the
            // first casualty is the icon sprite: renderers/icon.js resolves it
            // relative to itself, the request goes out as /@fs/…/dist/icons.svg
            // and comes back 403, so every icon silently renders empty.
            //
            // A project that installs the package from the registry needs none
            // of this — node_modules sits inside its own root. This line is the
            // price of developing against the module next door.
            allow: [resolve(here, "..", "..")],
        },
    },
});
