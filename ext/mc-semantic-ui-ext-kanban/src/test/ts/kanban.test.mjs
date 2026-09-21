/**
 * The browser painter and the server templates draw the same board.
 *
 * board.expected.html is the agreed markup for board.json; this test holds the
 * TypeScript renderer to it and KanbanSsrParityTest holds the Handlebars
 * templates to the same file. Whitespace between tags is ignored, because the
 * templates end in a newline. Runs against the compiled output in
 * target/ts-dist and the core's, so both must be built first.
 */
import { test, describe, before } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { readFileSync } from "node:fs";
import path from "node:path";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.resolve(HERE, "../../../target/ts-dist");
const CORE_DIST = path.resolve(HERE, "../../../../../core/mc-semantic-ui-core/target/ts-dist");
const FIXTURES = path.resolve(HERE, "../resources/kanban");

const tidy = (html) => html.replace(/>\s+</g, "><").trim();

describe("kanban renderer", () => {
    let renderer, ext;

    before(async () => {
        const core = await import(`${CORE_DIST}/renderer.js`);
        ext = await import(`${DIST}/kanban/extension.js`);
        renderer = core.createDefaultRenderer();
        ext.install(renderer);
    });

    test("draws board.json as board.expected.html", () => {
        const board = JSON.parse(readFileSync(path.join(FIXTURES, "board.json"), "utf8"));
        const expected = readFileSync(path.join(FIXTURES, "board.expected.html"), "utf8");
        assert.equal(tidy(renderer.render(board)), tidy(expected));
    });

    test("a locked card is not draggable, a plain one is", () => {
        const html = ext.renderKanbanCard({ type: "kanban-card", id: "a", title: "A", locked: true });
        assert.doesNotMatch(html, /draggable/);
        assert.match(ext.renderKanbanCard({ type: "kanban-card", id: "b", title: "B" }), /draggable="true"/);
    });

    test("only colour syntax reaches the style attribute", () => {
        const bad = ext.renderKanbanCard({ type: "kanban-card", id: "a", title: "A", color: "red;background:url(https://ev.il)" });
        assert.doesNotMatch(bad, /style=|ev\.il/);
        assert.match(ext.renderKanbanCard({ type: "kanban-card", id: "b", title: "B", color: "rgb(1, 2, 3)" }),
            /style="--sui-kanban-accent:rgb\(1, 2, 3\)"/);
    });

    test("the move trigger fills every placeholder and leaves the rest alone", () => {
        const t = ext.moveTrigger(
            { url: "/b/move?card={card}&from={from}&to={to}&index={index}", method: "POST", behavior: "APPLY_RESPONSE" },
            { card: "c 1", from: "todo", to: "done", index: 2 });
        assert.deepEqual(t, { url: "/b/move?card=c%201&from=todo&to=done&index=2", method: "POST", behavior: "APPLY_RESPONSE" });
    });
});
