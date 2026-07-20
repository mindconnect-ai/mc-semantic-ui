/*
 * Fetches the running app's own files so an exporter can repackage them.
 *
 * The app can't list its own directory over HTTP, so the build emits
 * data/manifest.json — a flat list of every file under the site root. We fetch
 * the manifest, then fetch each file as bytes. The result is the complete,
 * byte-for-byte set of runtime files (index.html, shell + core + editor JS,
 * CSS, schema/defaults JSON), ready to drop into any package layout.
 */

/**
 * @returns {Promise<Array<{name: string, data: Uint8Array}>>} runtime files,
 *   each {@code name} relative to the site root (e.g. "sui/renderer.js").
 */
export async function fetchAppFiles() {
    const manifest = await fetch("./data/manifest.json").then(r => {
        if (!r.ok) throw new Error("data/manifest.json missing — run the Maven build to generate it");
        return r.json();
    });

    // Include the manifest itself so the exported app can re-export in turn.
    const paths = manifest.includes("data/manifest.json")
        ? manifest
        : [...manifest, "data/manifest.json"];

    return Promise.all(paths.map(async (path) => {
        const buf = await fetch("./" + path).then(r => {
            if (!r.ok) throw new Error(`asset fetch failed: ${path} (${r.status})`);
            return r.arrayBuffer();
        });
        return { name: path, data: new Uint8Array(buf) };
    }));
}
