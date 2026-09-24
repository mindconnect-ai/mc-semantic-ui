/**
 * actionsOverflow: the button bar of a list's or table's header and the
 * footer of a form or a detail hand themselves to the shared overflow
 * behaviour with MENU, and stay plain with WRAP — the same mark the tabs
 * and a header's extras carry, so wireOverflow needs no new knowledge.
 * Against the compiled output; markup only, the behaviour has its own tests.
 */
import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");
const { createDefaultRenderer } = await import(`${DIST}/renderer.js`);

const MARK = ` data-sui-overflow="menu" data-sui-overflow-items=":not(.sui-link)"`;
const back = { type: "action", id: "back", label: "Back", onClick: { url: "/", method: "GET" } };
const render = (node) => createDefaultRenderer().render(node);

describe("actionsOverflow", () => {
    test("MENU marks the bar of each host, WRAP marks nothing", () => {
        const hosts = [
            ["list", { type: "list", id: "l", title: "Inbox", items: [], actions: [back] }, "sui-actions"],
            ["table", { type: "table", id: "t", title: "Orders", columns: [], rows: [], actions: [back] }, "sui-actions"],
            ["form", { type: "form", id: "f", title: "Edit", fields: [], actions: [back] }, "sui-form-footer"],
            ["detail", { type: "detail", id: "d", title: "Order", fields: [], actions: [back] }, "sui-form-footer"],
        ];
        for (const [name, node, bar] of hosts) {
            const menu = render({ ...node, actionsOverflow: "MENU" });
            assert.ok(menu.includes(`<div class="${bar}"${MARK}>`), `${name}: ${menu}`);
            const wrap = render(node);
            assert.ok(!wrap.includes("data-sui-overflow"), `${name}: ${wrap}`);
            assert.ok(wrap.includes(`<div class="${bar}">`), `${name}: ${wrap}`);
        }
    });

    test("a table without buttons has no bar to mark", () => {
        const html = render({ type: "table", id: "t", title: "Orders", columns: [], rows: [], actionsOverflow: "MENU" });
        assert.ok(!html.includes("sui-actions"), html);
        assert.ok(html.includes("sui-table-header"), html);
    });
});
