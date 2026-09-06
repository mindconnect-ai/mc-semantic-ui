---
title: Stateful views
sidebar_position: 12
---

# Stateful views (server-side state, Vaadin-style)

Everything else in semantic-ui is stateless: a controller returns a `UiPage`
or a `UiPatch`, and the next request starts from nothing. **Stateful views**
add a second way to program the same UI, in the add-on module
`mc-semantic-ui-stateful`:

- you write a **view class** with a small **state** object and a `render`
  method that builds the tree with the usual builders;
- you attach **Java listeners** to nodes — `on(button).click(e -> …)` — no
  controller endpoint per action;
- the framework keeps the state between requests, re-renders after every
  event and sends **only the difference** as a `UiPatch`.

The browser client, the SSR renderer and the JavaFX client need nothing new:
a listener is an ordinary trigger to one generic endpoint, and the answer is
an ordinary patch. A view even works without JavaScript.

```xml
<dependency>
  <groupId>ai.mindconnect</groupId>
  <artifactId>mc-semantic-ui-stateful</artifactId>
</dependency>
```

## A view

```java
@SuiRoute("/products")
public class ProductsView extends SuiView<ProductsView.State> {

    /** Everything that lives between requests. Plain JSON-able class. */
    public static class State {
        String query = "";
        int page = 1;
        String editing;      // product id in the dialog, null = closed
    }

    private final ProductRepository products;   // injected, never persisted

    public ProductsView(ProductRepository products) { this.products = products; }

    @Override protected State initialState(RouteParams params) {
        State s = new State();
        s.query = params.query("q", "");
        return s;
    }

    @Override protected UiNode render(State s) {
        if (s.editing != null) dialog(editDialog(s));          // an open dialog is state

        UiForm search = UiForm.of("search", null)
            .field(UiField.text("q", "Search", s.query).asEditable())
            .action(on(UiAction.primary("go", "Search")).click(e -> {
                s.query = e.value("q", "");                    // the form travels with the event
                s.page = 1;
            }));

        UiTable table = UiTable.of("products", "Products")
            .column(UiColumn.of("sku", "SKU"))
            .column(UiColumn.of("name", "Name"))
            .rowAction(onRow(UiAction.secondary("edit", "Edit")).click(e -> s.editing = e.rowId()))
            .rowAction(onRow(UiAction.danger("del", "Delete").confirm("Delete?")).click(e -> {
                products.delete(UUID.fromString(e.rowId()));
                toast(UiToast.success("Deleted."));
            }))
            .paginate(s.page, 8, products.count(s.query));
        on(table).page(e -> s.page = e.intValue("page", 1));
        products.find(s.query, s.page, 8).forEach(p -> table.row(Map.of(
            "id", p.id().toString(), "sku", p.sku(), "name", p.name())));

        return UiStack.of("products-page").child(search).child(table);
    }
}
```

`GET /products` opens an instance and lands on `/products?_v=<id>`. From
then on every listener posts to `/sui/s/<id>/<node>/<event>` and gets back the
patch that brings the page up to date — a click on *Search* is one `MERGE`
per changed text plus the table's changed rows.

## The model

**`render(state)` is a pure function of the state.** It is called again after
every event and re-binds every listener while it runs. Listeners therefore
never have to survive between requests — they are ordinary lambdas — and only
the state does, as JSON. This is what makes the mode cluster-capable: any node
can load the state, render, and apply the event.

**Handlers change state and return nothing.** Toasts, navigation and dialogs
are not return values: `toast(...)` and `navigate(...)` are intents recorded
for this one response, and a dialog is declared in `render` because "open" is
a fact about the state.

**The diff runs on the JSON.** After the handler, the new tree is compared
with the one the client last received, property by property: a changed
attribute is a `MERGE` naming just that attribute, a child appended to a
stack or a row appended to a table is an `APPEND`, a removed child a `REMOVE`,
anything structural a `REPLACE` of the parent. Because it looks at the JSON
shape, it works for every node type — extensions included — with no per-type
code. If the patch would be bigger than 60 % of the page, the page is sent.

**Ids are the addresses.** Every node that receives events needs an id, and
ids should be unique on the page and stable across renders: derive them from
your data (`"order-" + order.id()`), never from a counter. A node whose id
changes on every render costs a `REPLACE` on every event.

**One instance per tab.** The instance id travels in the URL (`?_v=…`). A
reload keeps the state; the back button lands on the instance the URL names;
two tabs on the same route are two instances. Idle instances are dropped
after 30 minutes (configurable); an event on a gone instance reopens the
route and says so in a toast.

## Binding events

| Call | Meaning |
|---|---|
| `on(node).click(h)` | Also `.dblClick`, `.change`, `.input`, `.hover`, `.leave`. Returns the node, so it chains into the builders. |
| `onRow(action).click(h)` | Table row action; `e.rowId()` is the row. |
| `on(table).page(h)` | Pagination; `e.intValue("page", 1)` is the target page. Call `table.paginate(...)` first. |
| `on(upload).upload(h)` | `UiUpload` drop zone or `FILE` field; `e.attachments()` has the files. |
| `on(node).payload("other-form").click(h)` | Send another node's form values instead of the enclosing form's. |

`UiEvent` gives you the node, the event, the row, the harvested form
(`value`, `intValue`, `boolValue`, `values`), the attachments and the
principal.

## Without JavaScript

The SSR renderer turns the same trigger into a native `<form method="post">`.
The endpoint applies the event and answers `303 See Other` back to the
instance, whose `GET` renders the new state; toasts wait in the store for
that page. Nothing to configure — turn on the core's SSR
(`mindconnect.sui.ssr.enabled=true`) as for any page. The
[stateful demo](https://github.com/mindconnect-ai/mc-semantic-ui/tree/main/demo/mc-sui-stateful-demo)
switches between the two with a cookie.

## Configuration and clustering

```yaml
mindconnect:
  sui:
    stateful:
      event-base-path: /sui/s
      instance-param: _v
      idle-timeout: 30m
      max-instances-per-owner: 50
      full-page-threshold: 0.6
```

`ViewStateStore` is the SPI for where instances live. The in-memory
implementation is the default; define a bean of your own (a database, Redis)
and every node of a cluster can serve every event — no sticky sessions. The
store's version check catches a lost race; the event is applied once more
against the fresh state before a `409` is reported. Instances are bound to
the principal (or the HTTP session when anonymous) and invisible to anyone
else.

## Where it sits

Stateless pages and stateful views coexist in one application, page by page;
a view can `navigate("/admin/products")` to a controller page and a
controller page can link to a route. Compared with Vaadin Flow, the state is
a JSON document rather than a Java object graph on a sticky node, the
listeners are re-bound rather than serialised, and the same view is also a
no-JS site.

Not in this version: a persistent store (JDBC is next), server-initiated push
over SSE, and a binder for column sorting.
