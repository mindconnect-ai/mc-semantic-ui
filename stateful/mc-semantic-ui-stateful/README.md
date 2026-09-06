# mc-semantic-ui-stateful

Server-side **stateful views** for semantic-ui. You write a view class with
plain Java listeners; the framework keeps its state between requests,
re-renders after every event and ships only the difference as a `UiPatch`.
No controller endpoint per button. The state is JSON in a pluggable store, so
a cluster needs no sticky sessions.

```java
@SuiRoute("/counter")
public class CounterView extends SuiView<CounterView.State> {

    public static class State { int count; }

    @Override protected State initialState(RouteParams params) { return new State(); }

    @Override protected UiNode render(State s) {
        return UiStack.of("counter")
            .child(UiText.of("count", "Clicked " + s.count + " times"))
            .child(on(UiAction.primary("inc", "+1")).click(e -> s.count++));
    }
}
```

That is the whole screen. `GET /counter` opens an instance; a click on *+1*
posts to the framework's one event endpoint, the handler runs, and the client
receives `{"patches":[{"op":"MERGE","targetId":"count","attributes":{"text":"Clicked 1 times"}}]}`.

It speaks the core protocol **unchanged**: the browser client, the SSR
renderer and the JavaFX client need nothing new. A view even works without
JavaScript — the same trigger is a native form post and the answer is a
redirect back to the page.

```xml
<dependency>
  <groupId>ai.mindconnect</groupId>
  <artifactId>mc-semantic-ui-stateful</artifactId>
</dependency>
```

Under Spring Boot the module autoconfigures itself: views are found by
scanning the application's packages for `@SuiRoute`, an in-memory store is
provided unless you define a `ViewStateStore` bean, and the routes plus the
event endpoint are mapped. Turn on the core's SSR renderer
(`mindconnect.sui.ssr.enabled=true`, handlebars on the classpath) so a
browser gets HTML.

## The model in five sentences

1. **`render(state)` is a pure function.** It is called again after every
   event and re-binds every listener while it runs; listeners never have to
   survive between requests. Only the state does, as JSON.
2. **Handlers change state and return nothing.** The framework renders again,
   diffs the new tree against the one the client has, and sends the
   difference — `MERGE` for attributes, `APPEND`/`REMOVE` for list ends,
   `REPLACE` where the structure changed, the whole page when the diff would
   be bigger.
3. **Ids are the addresses.** Every node that receives events needs an id;
   ids should be unique on the page and stable across renders — derived from
   your data, never from a counter.
4. **An open dialog is state.** Decide in `render` whether to call
   `dialog(...)`; the diff opens or closes it on the client.
5. **One instance per tab.** The instance id travels in the URL (`?_v=…`),
   so a reload keeps the state and two tabs never share one.

## API

| Call | What it does |
|---|---|
| `on(node).click(h)` / `.change(h)` / `.input(h)` / `.dblClick(h)` / `.hover(h)` / `.leave(h)` | Binds a listener, returns the node. Two events on one node: call `on()` twice. |
| `onRow(action).click(h)` | For a table row action; the handler gets the row through `e.rowId()`. |
| `on(table).page(h)` | For a paginated table; `e.intValue("page", 1)` is the target page. |
| `on(upload).upload(h)` | For a `UiUpload` or a `FILE` field; files arrive as `e.attachments()`. |
| `on(node).payload("form-id").click(h)` | Sends another node's form values instead of the enclosing form's. |
| `dialog(UiDialog)` | In `render`: this dialog is open. |
| `title(String)` | In `render`: the browser title. |
| `toast(...)`, `navigate(url)`, `navigate(OtherView.class[, params])` | In a handler: feedback and leaving the view. |
| `context()` | The instance's id, route and address. |

`UiEvent` carries `nodeId()`, `event()`, `rowId()`, the harvested form as
`payload()` with `value(name)`, `intValue(name, fallback)`, `boolValue(name)`,
`values(name)`, plus `attachments()` and `principal()`.

## Configuration

```yaml
mindconnect:
  sui:
    stateful:
      enabled: true              # false removes the endpoint and the routes
      event-base-path: /sui/s    # where listeners post to
      instance-param: _v         # query parameter carrying the instance id
      idle-timeout: 30m          # an instance untouched this long is dropped
      max-instances-per-owner: 50
      full-page-threshold: 0.6   # patch/page size ratio above which the page is sent
      reap-interval: 1m
```

## Clustering

`ViewStateStore` is the SPI: `load`, `insert`, `update` (compare-and-set on a
version), `delete`, `expireIdle`, `trimOwner`. The in-memory implementation
ships; a shared one (JDBC, Redis) makes every node able to serve every event,
and the version check catches the rare race — the event is applied once more
against the fresh state, and only a second loss is reported to the client.
Instances are bound to their owner (the principal, or the HTTP session for
anonymous users) and cannot be reached by anyone else.

## What is not in this version

- No persistent store yet — the JDBC store is next.
- No server-initiated push; a change made elsewhere shows on the next event.
- Sorting a table by column has no binder yet (`pagination` has).
- A form with several buttons submits to its first action's URL on the no-JS
  path; that is the core's SSR form rendering, not this module's.

See `demo/mc-sui-stateful-demo` for the shop's product admin as views, and
`doc/concept.md` for where this sits next to Vaadin Flow.
