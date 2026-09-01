// Pure Node.js / Express demo for semantic-ui.
//
// The server returns UiPage trees as plain JSON — the exact shape a Spring Boot
// controller would return — and, for a browser navigation, renders that same
// tree to finished HTML first (see ./document.ts). So the page arrives readable
// with no JavaScript, and the bus then takes it over.
//
// One process serves both. In development Vite runs as Express middleware, so
// the API and the client share a port and the client still gets hot reload; with
// --prod the pre-built bundle in dist/ is served as static files instead.

import express from "express";
import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { renderDocument, type PageMeta } from "./document.js";
import {
    deleteProduct, detailMeta, findProduct, listMeta, productDetailPage, productListPage,
} from "./pages.js";
import type { UiPage } from "@mindconnect-ai/mc-semantic-ui-core/model";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "..");
const prod = process.argv.includes("--prod");

const app = express();
app.use(express.json());

// One URL per page, serving both the page and its data.
const api = express.Router();

// GET /products — the list, optionally filtered by ?q.
api.get("/products", (req, res) => {
    const q = typeof req.query.q === "string" ? req.query.q : undefined;
    res.json(productListPage(q));
});

// GET /products/:id — the detail, as a dialog over the list.
api.get("/products/:id", (req, res) => {
    const product = findProduct(req.params.id);
    if (!product) {
        res.status(404).json(productListPage());
        return;
    }
    res.json(productDetailPage(product));
});

// DELETE /products/:id — removes a product, then returns the refreshed list.
api.delete("/products/:id", (req, res) => {
    deleteProduct(req.params.id);
    res.json(productListPage());
});

/**
 * Content negotiation decides who answers, so a page and its data can share one
 * URL — the Node counterpart of the Spring `OncePerRequestFilter` that forwards
 * browser navigations to the SPA shell.
 *
 * A browser navigating to /products (address bar, reload, a shared link) sends
 * `Accept: text/html`: skip the API and let the client shell answer. The event
 * bus then fetches that very same URL, with the `Accept: application/json` it
 * sets on every request, and gets the UiPage.
 *
 * The consequence is the point of it: the client needs no table mapping page
 * URLs to endpoints. There is only one URL, and what comes back depends on who
 * is asking.
 */
app.use((req, res, next) => {
    const wantsHtml = (req.headers.accept ?? "").includes("text/html");
    if (req.method === "GET" && wantsHtml) return next();
    api(req, res, next);
});

// ── Server-side rendering ───────────────────────────────────────────────────
//
// What a browser navigation gets. The same UiPage the API would have returned,
// drawn to HTML here, so the content is in the response rather than one fetch
// away. `shell()` supplies index.html — through Vite in development so its
// client and the module graph are injected, straight from the build otherwise.

/** Resolves a page URL to the tree and the metadata that describe it. */
function pageFor(path: string, query: string | undefined): { page: UiPage; meta: PageMeta } | null {
    if (path === "/" || path === "/products") {
        return { page: productListPage(query), meta: listMeta(query) };
    }
    const match = /^\/products\/([^/]+)$/.exec(path);
    if (match) {
        const product = findProduct(match[1]);
        if (!product) return null;
        return { page: productDetailPage(product), meta: detailMeta(product) };
    }
    return null;
}

let shell: (url: string) => Promise<string>;

if (prod) {
    const dist = resolve(root, "dist");
    if (!existsSync(dist)) {
        console.error(`No build found at ${dist}\nRun \`npm run build\` first.`);
        process.exit(1);
    }
    const built = readFileSync(resolve(dist, "index.html"), "utf8");
    shell = async () => built;
    // Hashed assets; index.html is never served from here, it goes through the
    // renderer below.
    app.use(express.static(dist, { index: false }));
} else {
    // Imported lazily: vite is a devDependency and must not be required to
    // start the production server.
    const { createServer } = await import("vite");
    const vite = await createServer({
        root,
        // "custom", not "spa": this app renders its own HTML, and Vite's SPA
        // fallback would otherwise answer first with the untouched template.
        appType: "custom",
        server: { middlewareMode: true },
    });
    app.use(vite.middlewares);
    shell = url => vite.transformIndexHtml(url, readFileSync(resolve(root, "index.html"), "utf8"));
}

app.get("*", async (req, res, next) => {
    try {
        const q = typeof req.query.q === "string" ? req.query.q : undefined;
        const found = pageFor(req.path, q);
        if (!found) {
            // A real 404, not a 200 carrying an apology: a search engine that
            // indexes soft-404s keeps them.
            res.status(404).type("html")
                .send(renderDocument(await shell(req.originalUrl), productListPage(), {
                    title: "Not found — semantic-ui shop",
                    description: "No such product.",
                    canonical: "/products",
                }));
            return;
        }
        res.type("html").send(renderDocument(await shell(req.originalUrl), found.page, found.meta));
    } catch (err) {
        next(err);
    }
});

const port = Number(process.env.PORT ?? 3000);
const server = app.listen(port, () => {
    console.log(`mc-sui-shop-node-demo (${prod ? "production" : "dev"}) running at http://localhost:${port}/`);
});

// 3000 is a popular port. Saying so is more use than a listen stack trace.
server.on("error", (err: NodeJS.ErrnoException) => {
    if (err.code !== "EADDRINUSE") throw err;
    console.error(
        `Port ${port} is already in use — something else is listening there.\n` +
        `Run the demo on a different port:\n` +
        `  PORT=${port + 10} ${prod ? "npm start" : "npm run dev"}\n` +
        `To see what holds it:  lsof -nP -iTCP:${port} -sTCP:LISTEN`);
    process.exit(1);
});
