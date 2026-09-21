/**
 * The browser painter and the server painter draw the same calendar.
 *
 * calendar.json rendered in each of the three views must equal
 * calendar.expected-<view>.html; CalendarPainterParityTest holds the Java
 * painter to the same three files. Runs against the compiled output in
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
const FIXTURES = path.resolve(HERE, "../resources/calendar");

const tidy = (html) => html.replace(/>\s+</g, "><").trim();

describe("calendar renderer", () => {
    let renderer, ext, fixture;

    before(async () => {
        const core = await import(`${CORE_DIST}/renderer.js`);
        ext = await import(`${DIST}/calendar/extension.js`);
        renderer = core.createDefaultRenderer();
        ext.install(renderer);
        fixture = JSON.parse(readFileSync(path.join(FIXTURES, "calendar.json"), "utf8"));
    });

    for (const view of ["MONTH", "WEEK", "DAY"]) {
        test(`draws the ${view} view as calendar.expected-${view.toLowerCase()}.html`, () => {
            const expected = readFileSync(path.join(FIXTURES, `calendar.expected-${view.toLowerCase()}.html`), "utf8");
            assert.equal(tidy(renderer.render({ ...fixture, view })), tidy(expected));
        });
    }

    test("a month grid covers whole weeks, honouring the week start", () => {
        const sept = ext.renderCalendar({ type: "calendar", id: "c", date: "2026-09-01" });
        assert.match(sept, /data-date="2026-08-31"/);   // Monday before the 1st
        assert.match(sept, /data-date="2026-10-04"/);   // Sunday after the 30th
        assert.doesNotMatch(sept, /data-date="2026-08-30"/);
        const sunday = ext.renderCalendar({ type: "calendar", id: "c", date: "2026-09-01", weekStart: "SUNDAY" });
        assert.match(sunday, /data-date="2026-08-30"/);
        assert.match(sunday, /<span class="sui-calendar-weekday" role="columnheader">Sun<\/span><span class="sui-calendar-weekday" role="columnheader">Mon/);
    });

    test("the header buttons work without a server: every one says where it goes", () => {
        const html = ext.renderCalendar({ type: "calendar", id: "c", view: "WEEK", date: "2026-09-21" });
        assert.match(html, /data-nav-date="2026-09-14" data-nav-view="WEEK" aria-label="Previous"/);
        assert.match(html, /data-nav-date="2026-09-21" data-nav-view="DAY">Day</);
        assert.doesNotMatch(html, /data-trigger/);
        assert.doesNotMatch(html, />Today</);   // no `today` in the model, nothing to go to
    });

    test("navigation steps by day, week or month and keeps the view", () => {
        const html = ext.renderCalendar({ type: "calendar", id: "c", view: "MONTH", date: "2026-01-31", onNavigate: { url: "/c?d={date}&v={view}" } });
        assert.match(html, /\/c\?d=2026-02-28&v=MONTH/);   // clamped to February
        assert.match(html, /\/c\?d=2025-12-31&v=MONTH/);
        assert.match(html, /\/c\?d=2026-01-31&v=WEEK/);
    });

    test("labels come from the model when they are complete", () => {
        const html = ext.renderCalendar({ type: "calendar", id: "c", view: "DAY", date: "2026-09-21",
            labels: { weekdaysLong: ["Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"], months: ["x"] } });
        assert.match(html, /Montag, 21 September 2026/);   // months list incomplete → English
    });

    test("the select trigger fills date and hour", () => {
        assert.deepEqual(ext.selectTrigger({ url: "/p?d={date}&h={hour}", method: "POST" }, "2026-09-21", 9),
            { url: "/p?d=2026-09-21&h=9", method: "POST" });
        assert.equal(ext.selectTrigger({ url: "/p?d={date}&h={hour}" }, "2026-09-21", null).url, "/p?d=2026-09-21&h=");
    });
});
