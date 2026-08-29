# mc-sui-merge-demo

A page to poke at, for the `MERGE` patch operation.

```bash
mvn -f demo/mc-sui-merge-demo/pom.xml spring-boot:run   # http://localhost:9093
```

## What it is for

`REPLACE` needs a whole node. To grey out one button, the server has to rebuild
and resend the subtree that button sits in, and the client throws away the one
it had. `MERGE` names the fields that changed:

```java
UiPatch.Operation.hide("advanced-card")          // and show("advanced-card")
UiPatch.Operation.merge("save", Map.of("enabled", false, "label", "Saving…"))
```

Everything not named is left where it is. An explicit `null` clears a field, so
a state can be turned off as well as on — which is the only reason `show()` can
exist.

## What is on the page

**1 · Hide something, and put it back.** The click the operation was built for.
Three buttons — `hide()`, `hide(BLANK)`, `show()` — against a panel of four
fields. `HIDDEN` takes the panel out of the layout; `BLANK` leaves its space
behind. The panel itself is never sent.

**2 · Change two fields of a button.** A toggle that merges its own `label`,
`style` and `icon`. Its id, its trigger and its confirmation prompt are part of
the same node and none of them travels, because none of them is named.

**3 · Type here first.** A scratch pad. Put the cursor mid-word, then go press
a button in section 1 or 2: the field is not in the patch, so nothing happens
to it. This is the case that makes a streaming page usable — and the reason the
JavaFX renderer had to grow `FxViewState`, since it rebuilds where the browser
morphs.

**4 · What actually went over the wire.** The demo does not ask to be believed.
Every exchange prints the operation the server sent beside the `REPLACE` that
would have had the same effect on screen, with both byte counts. Hiding the
panel: **75 bytes against 1270**.

The log update is itself a `REPLACE` of the log node — the one place this demo
is its own counter-example. The byte counts are of the demonstrated operation
alone, or the log would be measuring itself.

## The hybrid page, which is the subtle part

This app serves **server-rendered HTML with the SPA bus attached on top**
(see [`SpaBootstrapFilter`](src/main/java/ai/mindconnect/sui/demo/merge/SpaBootstrapFilter.java)).

That combination is where `MERGE` gets interesting. The client never built this
tree — it was handed finished HTML — so it knows what every element *looks
like* and nothing about what any of them *are*. A merge needs the rest of the
node it is changing, and the rest is exactly what such a page has no idea
about.

So `UiPageHtmlMessageConverter` writes the page's own model into a
`<script type="application/json" id="sui-model">` at the end of the body, and
the bus seeds itself from it on startup. View source on the running page and it
is there to read. Only pages served with a SPA bootstrap carry it; a pure-SSR
page has no client to read it.

## Trying it on the desktop too

The same server answers the JavaFX renderer, which applies `MERGE` through its
own path — rebuilding the merged node and swapping it in, carrying focus and
scroll across:

```bash
mvn -f client/mc-sui-javafx-browser/pom.xml javafx:run -Djavafx.args="http://localhost:9093/"
```

## What it does not show

Nothing to do with compression. On the notification toggle the `REPLACE` is
nearly the same size, and the page prints that too. The operation is about not
having to know, or rebuild, the parts you are not changing — the bytes are a
consequence, not the argument.

The merge is shallow: a named field is replaced whole, not merged into.
