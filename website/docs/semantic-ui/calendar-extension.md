---
title: Calendar extension
sidebar_position: 5
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# The calendar extension

**`mc-semantic-ui-ext-calendar` brings a calendar**: a month, a week or a day
of events, with the buttons to move between them — and, like the
[chart extension](./chart-extension.md), both halves of it: the node types
(`UiCalendar`, `UiCalendarEvent`) *and* the painters, one in Java for the
server and one in TypeScript for the browser, held to the same bytes.

## What you get

| | |
|---|---|
| Node types | `calendar` (`UiCalendar`), `calendar-event` (`UiCalendarEvent`) |
| Views | `MONTH`, `WEEK`, `DAY` |
| Moving around | previous / next / today and the view switch re-render the calendar from its own model — no server needed; with an `onNavigate` they fire it too, `{date}` and `{view}` filled in, so the server can send the new period's events |
| Picking | a click on a day or an hour fires `onSelect` with `{date}` and `{hour}` — through the browser bundle |
| Events | all-day (may span days) or timed (sit in their hour); an accent `color`; `onClick` like any node |
| Words | English, or `labels` from the model — `labels(Locale)` takes the names from `java.time` |
| Works without JavaScript | **yes** for looking and moving around, when rendered server-side; picking a day needs the bundle |

## Install

:::tip With the asset registry
A Spring Boot host with the core's asset registry needs none of the browser
wiring below: the jar declares its files, and `installAll(renderer, bus)` from
`/sui/assets.js` installs it. See [the asset registry](./extension-assets.md).
:::

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java / server-side">

```xml
<dependency>
  <groupId>ai.mindconnect</groupId>
  <artifactId>mc-semantic-ui-ext-calendar</artifactId>
  <version>0.4.2</version>
</dependency>
```

Nothing else to configure. The module ships `templates/sui/calendar.hbs` plus
the Handlebars helper it needs, contributed through `SuiHelperContributor` —
found as a Spring bean or via `ServiceLoader`.

```java
@GetMapping("/cal")
public UiPage calendar(@RequestParam(defaultValue = "MONTH") UiCalendar.View view,
                       @RequestParam Optional<LocalDate> date, Locale locale) {
    LocalDate anchor = date.orElse(LocalDate.now());
    var cal = UiCalendar.of("cal", view, anchor)
            .labels(locale)
            .hours(7, 19)
            .onNavigate(UiTrigger.go("/cal?date={date}&view={view}"))
            .onSelect(UiTrigger.api("POST", "/cal/pick?date={date}&hour={hour}"));
    for (var e : events.between(anchor.minusMonths(1), anchor.plusMonths(1))) {
        cal.event(UiCalendarEvent.timed(e.id(), e.title(), e.start(), e.end())
                .onClick(UiTrigger.go("/events/" + e.id())));
    }
    return UiPage.of("/cal", cal);
}
```

`of()` sets `today` from the server's clock. It travels in the model on
purpose: neither painter consults a clock or a locale, which is what keeps the
server's bytes and the browser's identical.

</TabItem>
<TabItem value="browser" label="Browser">

```html
<link rel="stylesheet" href="/sui/sui.css">
<link rel="stylesheet" href="/sui-ext/calendar/calendar.css">

<script type="module">
  import { createDefaultRenderer } from "/sui/renderer.js";
  import { SuiEventBus } from "/sui/eventbus.js";
  import { install as installCalendar } from "/sui-ext/calendar/extension.js";

  const root = document.getElementById("app");
  const renderer = createDefaultRenderer().attach(root);
  const bus = new SuiEventBus(renderer, root);
  installCalendar(renderer, { bus });   // registers "calendar"; picks go through the bus

  bus.start("/cal");
</script>
```

`installCalendar(renderer, { bus })` is scoped to that renderer. The
navigation buttons are ordinary `data-trigger` links the bus handles by
itself; the bus is for `onSelect`, which the `<sui-calendar>` element fires
when a day or an hour is clicked. Without a bus the element emits a
`sui-calendar-select` event (`detail: { date, hour }`) instead.

</TabItem>
</Tabs>

## How the calendar talks to the server

Two triggers, two sets of placeholders filled into their URL:

| Trigger | Fired by | Placeholders |
|---|---|---|
| `onNavigate` | previous, next, today, and the Day / Week / Month buttons | `{date}` — the day to show, `yyyy-MM-dd`; `{view}` — `DAY`, `WEEK` or `MONTH` |
| `onSelect` | a click on a month cell, an all-day cell, or an hour slot | `{date}` — the day, `yyyy-MM-dd`; `{hour}` — the hour (0–23), or empty for a day; `{time}` — `HH:00`, or empty. Filled wherever the trigger carries them — its URL, or the nodes of an inline `PATCH`, so a pick can open a pre-filled "new event" dialog with no round trip |

Navigation happens in the page first: the calendar keeps the model it was
drawn from, and a button re-renders it for the new date and view from the
events it already has — so a calendar with no `onNavigate` at all still
moves. With one, each button is also a complete trigger link, filled in at
render time; it fires after the re-render, and the server may answer with the
events of that period as a `REPLACE` of the calendar node (or with nothing).
Previous and next step by a day, a week or a month depending on the view,
clamping the day of month where a month is shorter.

### Adding an event from the page

`updateCalendar(id, node => …)` in the browser bundle rewrites the model a
rendered calendar was drawn from and draws it again — for a page that lets
the user create an event without a round trip, say from a dialog that an
`onSelect` inline patch opened, saved by an `INVOKE` handler:

```js
bus.registerClientHandler("add-event", ctx => {
  const p = ctx.payload;                       // the dialog form's values
  updateCalendar("cal", n => ({ ...n, events: [...(n.events ?? []),
    { type: "calendar-event", id: crypto.randomUUID(), title: p.title, start: `${p.date}T${p.start}` }] }));
  return { patches: [{ op: "REMOVE", targetId: "new-event-dlg" }], toasts: [] };
});
```

## The nodes

`UiCalendar`:

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` and the patch target. |
| `view` | `DAY` · `WEEK` · `MONTH` | Which view. Default `MONTH`. |
| `date` | `String` | The day shown, or a day of the week or month shown, `yyyy-MM-dd`. |
| `today` | `String` | Today, `yyyy-MM-dd` — highlighted, and the target of the Today button. |
| `selectedDate` | `String` | A day to highlight. |
| `weekStart` | `MONDAY` · `SUNDAY` | Default `MONDAY`. |
| `startHour`, `endHour` | `Integer` | The hours the day and week views show. Default 0 to 24. |
| `maxEventsPerDay` | `Integer` | Chips a month cell shows before folding the rest into "+n more". Default 3. |
| `labels` | `Labels` | The words shown; any left null is English. |
| `events` | `List<UiCalendarEvent>` | The events — all of them; the painter picks what falls into the view. |
| `onNavigate`, `onSelect` | `UiTrigger` | See above. Both optional; without `onNavigate` the calendar still navigates, on its own. |
| `extras` | `List<UiNode>` | Widgets of the page's own in the header, after the view switch — a "New event" button, a filter, a legend. Any node type; `.extra(node)` adds one. |

`UiCalendarEvent`:

| Field | Type | Meaning |
|---|---|---|
| `id`, `title` | `String` | Id and what the chip shows. |
| `start` | `String` | `yyyy-MM-dd` for an all-day event, `yyyy-MM-ddTHH:mm` for a timed one. |
| `end` | `String` | Same form. Absent: one hour after the start, or the same day. |
| `allDay` | `Boolean` | Forces all-day; absent, a start without a time means all-day. |
| `color` | `String` | Accent colour — a hex value, a name, `rgb()`/`hsl()` or `var(--…)`; anything else is dropped. |
| `onClick` … | `UiTrigger` | The node-level events every node carries. |

A timed event lives on its start day; one that runs past midnight is cut
there. An all-day event that spans days shows a chip on each of them.

### Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiCalendar.of("cal", UiCalendar.View.WEEK, LocalDate.of(2026, 9, 21))
    .labels(Locale.GERMAN)
    .hours(7, 19)
    .event(UiCalendarEvent.timed("e1", "Standup",
            LocalDateTime.of(2026, 9, 21, 9, 0), LocalDateTime.of(2026, 9, 21, 9, 30)).color("#4f6bed"))
    .event(UiCalendarEvent.allDay("e2", "Offsite", LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 24)))
    .onNavigate(UiTrigger.go("/cal?date={date}&view={view}"))
    .onSelect(UiTrigger.api("POST", "/cal/pick?date={date}&hour={hour}"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "calendar", "id": "cal", "view": "WEEK", "date": "2026-09-21", "today": "2026-09-21",
  "startHour": 7, "endHour": 19,
  "events": [
    { "type": "calendar-event", "id": "e1", "title": "Standup",
      "start": "2026-09-21T09:00", "end": "2026-09-21T09:30", "color": "#4f6bed" },
    { "type": "calendar-event", "id": "e2", "title": "Offsite", "start": "2026-09-22", "end": "2026-09-24" } ],
  "onNavigate": { "url": "/cal?date={date}&view={view}" },
  "onSelect": { "url": "/cal/pick?date={date}&hour={hour}", "method": "POST" } }
```

</TabItem>
</Tabs>

## Styling

Everything is built on the core's tokens, so every theme applies. Two knobs
of the extension's own: `--sui-calendar-accent`, which an event's `color`
sets inline and the chip's left edge shows; and `--sui-calendar-hour-h`, the
height of one hour in the day and week views, which the event blocks are
placed against (set it on `.sui-calendar` to zoom the day). The states are
`is-today`, `is-selected` and `is-outside` on cells, `is-allday` on chips,
`is-active` on the current view's button.

## Both painters draw the same calendar

The painter exists twice — `CalendarPainter.java` and `calendar/extension.ts`
— and the two are held to byte-identical output by one fixture rendered in all
three views through both. That is why the model carries `today` and the
words: nothing is left to a clock or a locale on either side.

## Limits

Timed events that overlap are drawn on top of each other, not side by side.
Dragging an event to another time is not part of this version; give an event
an `onClick` that opens its detail instead.
