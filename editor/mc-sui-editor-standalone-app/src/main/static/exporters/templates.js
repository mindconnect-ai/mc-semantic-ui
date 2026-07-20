/*
 * File templates for the exported projects.
 *
 * The exported app is a plain, hand-written Semantic UI app — the exact shape
 * the docs teach (index.html shell + app.js wiring SuiRenderer + SuiEventBus).
 * No custom runtime/player: what you download is what you'd write by hand.
 *
 *   • Static build  — app.js embeds your pages as UiNode trees and mounts them;
 *                     navigation between them is backend-free.
 *   • Server builds  — one clean path per page returns UiPage JSON; app.js boots
 *                     with bus.start(path); a filter forwards browser
 *                     navigations to the shell (the standard SPA setup).
 */

// ── Shared shell ────────────────────────────────────────────────────────────

export const indexHtml = (title) => `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title}</title>
  <link rel="stylesheet" href="./sui/sui.css">
  <link rel="stylesheet" href="./sui/sui-dark.css">
</head>
<body>
  <main id="app"></main>
  <script type="module" src="./app.js"></script>
</body>
</html>
`;

// The entry script for the SERVER builds — identical to the docs' "Build an
// app (SPA)" example: create the renderer, attach the bus, boot from the URL.
export const serverAppJs = (entryPath) => `import { SuiRenderer, installDefaultHandlers } from "./sui/renderer.js";
import { SuiEventBus } from "./sui/eventbus.js";

const host = document.getElementById("app");

// The renderer paints UiNode trees; the event bus wires clicks, form submits,
// navigation and browser history.
const renderer = installDefaultHandlers(new SuiRenderer(host));
const bus = new SuiEventBus(renderer, host);

// Boot from the address bar so deep links survive a reload; "/" opens the entry
// page. Each screen is a clean path the backend serves as UiPage JSON.
const ENTRY = ${JSON.stringify(entryPath)};
const path = window.location.pathname + window.location.search;
bus.start(path === "/" ? ENTRY : path);
`;

// ── Node.js / Express ────────────────────────────────────────────────────────

export const NODE_SERVER = `// Express backend for a Semantic UI app.
//
// One clean path per page (e.g. /contact-list) serves the page's UiPage JSON.
// Browser navigations (Accept: text/html) get the SPA shell so deep links and
// reloads work; the event bus's JSON fetches (Accept: application/json) get the
// page data. This is the standard Semantic UI SPA setup — no /api prefix, no
// client-side URL rewriting. Page trees are bundled under ./pages; back them
// with a database to keep building.
import express from "express";
import { dirname, resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import { readFile } from "node:fs/promises";

const here = dirname(fileURLToPath(import.meta.url));
const pub = resolve(here, "public");
const pagesDir = resolve(here, "pages");
const app = express();

const wantsHtml = (req) => (req.headers.accept || "").includes("text/html");

// One clean path per page. HTML → shell; JSON → the page.
app.get("/:slug([a-z0-9-]+)", async (req, res, next) => {
  if (wantsHtml(req)) return res.sendFile(join(pub, "index.html"));
  try {
    res.type("application/json").send(await readFile(join(pagesDir, req.params.slug + ".json"), "utf8"));
  } catch {
    next();
  }
});

// Static assets (/sui/*, /app.js, /sui/sui.css) and the shell at "/".
app.use(express.static(pub));

const port = process.env.PORT ?? 8080;
app.listen(port, () => console.log(\`App running at http://localhost:\${port}/\`));
`;

export const NODE_PKG = `{
  "name": "sui-app",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "description": "A Semantic UI app exported from the visual editor — one clean path per page.",
  "scripts": {
    "start": "node server.js"
  },
  "dependencies": {
    "express": "^4.19.2"
  }
}
`;

export const NODE_README = `# Semantic UI App (Node.js / Express)

A plain Semantic UI SPA, exported from the visual editor. \`public/\` holds the
static shell (index.html + app.js + sui/), and the Express server serves **one
clean path per page** as UiPage JSON.

## Run

    npm install
    npm start

then open http://localhost:8080

## How it works

- \`GET /contact-list\` with \`Accept: text/html\` → the SPA shell (index.html)
- \`GET /contact-list\` with \`Accept: application/json\` → that page's UiPage JSON

The event bus (in app.js) fetches with \`Accept: application/json\`; browsers send
\`text/html\`, so deep links and reloads land on the shell and boot the app.

## Layout

- server.js  — Express: clean page paths + static shell
- public/    — index.html, app.js, sui/ (renderer + event bus + themes)
- pages/     — one \`<slug>.json\` per page (a UiPage). Edit or replace with a DB.
`;

// ── Spring Boot ──────────────────────────────────────────────────────────────

export const SPRING_POM = `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.4</version>
        <relativePath/>
    </parent>

    <groupId>ai.mindconnect.ui</groupId>
    <artifactId>sui-app</artifactId>
    <version>1.0.0</version>
    <name>Semantic UI App</name>
    <description>A Semantic UI app exported from the visual editor — one clean path per page.</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
`;

export const SPRING_APP = `package ai.mindconnect.ui.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A Semantic UI app exported from the visual editor. Serves the static shell
 * (src/main/resources/static) and one clean path per page as UiPage JSON
 * (see PagesController). Keep building on it like any Spring Boot app.
 */
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
`;

export const SPRING_PAGES = `package ai.mindconnect.ui.app;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * One clean path per page: {@code GET /<slug>} returns that page's UiPage JSON.
 *
 * <p>The event bus fetches these with {@code Accept: application/json}; browser
 * navigations (Accept: text/html) are forwarded to the SPA shell first by
 * {@link SpaForwardingFilter}, so this controller only ever answers the bus.
 *
 * <p>Page trees are bundled as classpath resources under {@code /pages},
 * generated from the project you built in the visual editor. Swap this for real
 * data access (DB, service, …) to keep developing.
 */
@RestController
public class PagesController {

    @GetMapping(path = "/{slug:[a-z0-9-]+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> page(@PathVariable String slug) throws IOException {
        var res = new ClassPathResource("pages/" + slug + ".json");
        if (!res.exists()) return ResponseEntity.notFound().build();
        try (var in = res.getInputStream()) {
            return ResponseEntity.ok(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
`;

export const SPRING_FILTER = `package ai.mindconnect.ui.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SPA forwarding: a browser navigating to a page path (e.g. {@code /contact-list})
 * must get the shell so the app can boot and fetch the page; the event bus
 * fetching the same path must get the JSON. The two are told apart by the
 * {@code Accept} header — browsers send {@code text/html}, the bus sends
 * {@code application/json}. HTML navigations are forwarded to {@code /index.html};
 * everything else (JSON fetches, static assets) flows through untouched.
 */
@Component
@Order(0)
public class SpaForwardingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        if (isBrowserNavigation(req)) {
            req.getRequestDispatcher("/index.html").forward(req, res);
            return;
        }
        chain.doFilter(req, res);
    }

    private boolean isBrowserNavigation(HttpServletRequest req) {
        if (!"GET".equals(req.getMethod())) return false;
        String path = req.getRequestURI();
        // Static assets carry a dot (app.js, sui/...css) or live under /sui/ —
        // never forward those; let the resource handler serve them.
        if (path.startsWith("/sui/") || path.contains(".")) return false;
        String accept = req.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains("text/html");
    }
}
`;

export const SPRING_PROPS = `server.port=8080
`;

export const SPRING_README = `# Semantic UI App (Spring Boot)

A plain Semantic UI SPA, exported from the visual editor. It serves the static
shell (src/main/resources/static) and **one clean path per page** as UiPage JSON.

## Run

    mvn spring-boot:run

then open http://localhost:8080

## How it works

- \`GET /contact-list\` with \`Accept: text/html\` → the SPA shell (SpaForwardingFilter)
- \`GET /contact-list\` with \`Accept: application/json\` → that page's UiPage JSON (PagesController)

The event bus (in app.js) fetches with \`Accept: application/json\`, so deep links
and reloads land on the shell and boot the app — one bookmarkable path per screen.

## Layout

- src/main/resources/static/ — index.html, app.js, sui/ (renderer + event bus)
- src/main/resources/pages/  — one \`<slug>.json\` per page (a UiPage). Edit or DB-back.
- PagesController            — serves a page per clean path
- SpaForwardingFilter        — forwards browser navigations to the shell
`;

// ── Static ─────────────────────────────────────────────────────────────────────

export const STATIC_README = `# Semantic UI App (static)

A plain Semantic UI app, exported from the visual editor. No backend: \`app.js\`
holds your pages as UiNode trees and mounts them with the renderer + event bus,
and navigation between pages happens in the browser.

## Run

Serve this folder from any static file server (module scripts need http://, not
file://):

    npx serve .
    # or
    python3 -m http.server 8080

## Layout

- index.html — the shell (loads app.js + sui/sui.css)
- app.js     — your pages as UiNode trees + the renderer/bus wiring
- sui/       — the compiled Semantic UI renderer + event bus + themes
`;
