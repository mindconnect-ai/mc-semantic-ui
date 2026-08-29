package ai.mindconnect.sui.demo.merge.ui;

import ai.mindconnect.sui.demo.merge.DemoState;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;

/**
 * The demo page, built fresh from {@link DemoState} every time.
 *
 * <p>Each node the demo patches is also built by a method of its own, so the
 * controller can ask for "what this node looks like now" — which is what a
 * {@code REPLACE} would have had to send, and therefore what the wire log
 * compares against.
 */
public class MergeDemoPage {

    /** The panel the visibility buttons hide and show. */
    public static final String ADVANCED = "advanced-card";
    /** The button that changes its own label and style. */
    public static final String NOTIFY = "notify-toggle";
    /** The line that says what the notification setting is. */
    public static final String NOTIFY_STATUS = "notify-status";
    /** The wire log, replaced wholesale on every exchange. */
    public static final String WIRE = "wire-log";

    private final DemoState state;

    public MergeDemoPage(DemoState state) {
        this.state = state;
    }

    public UiPage render() {
        var page = UiStack.of("merge-demo").gap(28)
                .child(header())
                .child(visibilitySection())
                .child(attributesSection())
                .child(typingSection())
                .child(wireSection());
        return UiPage.of("/", page);
    }

    // ── 1. the case it was built for ──────────────────────────────────────

    private UiNode header() {
        return UiStack.of("header").gap(8)
                .child(UiText.of("title", "MERGE — change what you name")
                        .withCssClass("demo-title"))
                .child(UiText.of("subtitle",
                        "REPLACE needs a whole node. To grey out one button, the server "
                        + "rebuilds and resends the subtree it sits in, and the client throws "
                        + "away the one it had. MERGE names the fields that changed.")
                        .withCssClass("demo-subtitle"))
                .child(UiText.of("hybrid-note",
                        "This page is server-rendered HTML with the SPA bus attached on top. "
                        + "The client never built this tree — it reads the page's own model "
                        + "out of the <script id=\"sui-model\"> blob at the end of the body, "
                        + "which is what gives a merge the rest of the node to merge into. "
                        + "View source and it is there.")
                        .withCssClass("demo-note"));
    }

    private UiNode visibilitySection() {
        return card("sec-visibility", "1 · Hide something, and put it back",
                "The click this operation exists for. Nothing about the panel changes "
                + "except its display state, so nothing about the panel is sent.",
                UiStack.of("visibility-body").gap(14)
                        .child(advancedCard())
                        .child(UiStack.of("visibility-controls")
                                .direction(UiStack.Direction.HORIZONTAL).gap(8)
                                .child(UiAction.secondary("btn-hide", "hide() — out of the layout")
                                        .icon("eye-off")
                                        .dispatch("POST", "/api/advanced/hidden"))
                                .child(UiAction.secondary("btn-blank", "hide(BLANK) — keeps its space")
                                        .icon("eye-off")
                                        .dispatch("POST", "/api/advanced/blank"))
                                .child(UiAction.primary("btn-show", "show()")
                                        .icon("eye")
                                        .dispatch("POST", "/api/advanced/visible"))));
    }

    /**
     * The panel that gets hidden. Deliberately not a one-liner: a
     * {@code REPLACE} has to carry all of this to change the one field that
     * actually differs, and the wire log is there to show it doing so.
     */
    public UiStack advancedCard() {
        var card = UiStack.of(ADVANCED).gap(10)
                .child(UiText.of("advanced-heading", "Advanced options")
                        .withCssClass("panel-heading"))
                .child(UiForm.of("advanced-form", null)
                        .field(UiField.text("endpoint", "Endpoint",
                                "https://api.example.com/v2").asEditable())
                        .field(UiField.number("timeout", "Timeout (seconds)", 30).asEditable())
                        .field(UiField.bool("retry", "Retry on failure", true).asEditable())
                        .field(UiField.select("region", "Region", "eu-central",
                                java.util.List.of(
                                        UiField.Option.of("eu-central", "Europe (Frankfurt)"),
                                        UiField.Option.of("us-east", "US East (Virginia)"),
                                        UiField.Option.of("ap-south", "Asia (Singapore)")))
                                .asEditable()));
        card.setCssClass("panel");
        card.setDisplay(state.advanced());
        return card;
    }

    // ── 2. two fields, not a node ─────────────────────────────────────────

    private UiNode attributesSection() {
        return card("sec-attributes", "2 · Change two fields of a button",
                "Label and style change; the id, the icon, the confirmation prompt and "
                + "the trigger stay exactly as they were, because they were never mentioned.",
                UiStack.of("attributes-body").gap(12)
                        .child(notifyButton())
                        .child(notifyStatus()));
    }

    /**
     * The toggle. Note what is <em>not</em> in the merge the controller sends:
     * the icon, the confirm text and the onClick trigger are all part of this
     * node and none of them is named, so none of them travels.
     */
    public UiAction notifyButton() {
        boolean on = state.notifications();
        var button = on
                ? UiAction.primary(NOTIFY, "Notifications are on")
                : UiAction.secondary(NOTIFY, "Notifications are off");
        return button
                .icon(on ? "bell" : "bell-off")
                .dispatch("POST", "/api/notifications/toggle");
    }

    public UiText notifyStatus() {
        return UiText.of(NOTIFY_STATUS, state.notifications()
                        ? "You will be told when a run finishes."
                        : "Runs finish quietly.")
                .withCssClass(state.notifications() ? "status status-on" : "status status-off");
    }

    // ── 3. what a patch does not touch ────────────────────────────────────

    private UiNode typingSection() {
        return card("sec-typing", "3 · Type here first, then click above",
                "A patch reaches one node. Type something into this field, leave the "
                + "cursor mid-word, and go press a button in section 1 or 2 — the field "
                + "is not in the patch, so nothing happens to it. This is the case that "
                + "makes a streaming page usable.",
                UiForm.of("typing-form", null)
                        .field(UiField.textarea("scratch", "Scratch pad",
                                        "Put the cursor in the middle of this sentence.")
                                .asEditable()));
    }

    // ── 4. the receipts ───────────────────────────────────────────────────

    private UiNode wireSection() {
        return card("sec-wire", "4 · What actually went over the wire",
                "Newest first. The left column is the operation the server sent; the "
                + "right is the REPLACE that would have had the same effect on screen. "
                + "Both counts are of the operation alone, compact, without the log "
                + "update that carries it here — which is itself a REPLACE, and the one "
                + "place this demo is its own counter-example.",
                UiStack.of("wire-body").gap(14)
                        .child(wireLog())
                        .child(UiAction.secondary("btn-reset", "Start over")
                                .icon("refresh")
                                .dispatch("POST", "/api/reset")));
    }

    /** The log, as its own node so a response can replace just this. */
    public UiStack wireLog() {
        var log = UiStack.of(WIRE).gap(12);
        var exchanges = state.wire();
        if (exchanges.isEmpty()) {
            log.child(UiText.of("wire-empty", "Nothing yet. Press a button above.")
                    .withCssClass("wire-empty"));
            return log;
        }
        int index = 0;
        for (var exchange : exchanges) {
            log.child(entry(index++, exchange));
        }
        return log;
    }

    private UiNode entry(int index, DemoState.Exchange exchange) {
        int saved = exchange.insteadBytes() - exchange.sentBytes();
        var headline = UiText.of("wire-what-" + index, exchange.what())
                .withCssClass("wire-what");
        var summary = UiText.of("wire-sum-" + index,
                        exchange.sentBytes() + " bytes instead of " + exchange.insteadBytes()
                        + (saved > 0 ? "  —  " + saved + " not sent" : ""))
                .withCssClass("wire-summary");

        var columns = UiStack.of("wire-cols-" + index)
                .direction(UiStack.Direction.HORIZONTAL).gap(12)
                .child(column("wire-sent-" + index, "sent", exchange.sent(), "wire-sent"))
                .child(column("wire-instead-" + index, "instead of", exchange.instead(),
                        "wire-instead"));

        return UiStack.of("wire-entry-" + index).gap(6)
                .child(headline)
                .child(summary)
                .child(columns);
    }

    private UiNode column(String id, String label, String json, String cssClass) {
        var box = UiStack.of(id).gap(4)
                .child(UiText.of(id + "-label", label).withCssClass("wire-label"))
                .child(UiText.of(id + "-json", json).withCssClass("wire-json"));
        box.setCssClass(cssClass);
        return box;
    }

    // ── plumbing ──────────────────────────────────────────────────────────

    private UiNode card(String id, String title, String blurb, UiNode body) {
        var card = UiStack.of(id).gap(10)
                .child(UiText.of(id + "-title", title).withCssClass("section-title"))
                .child(UiText.of(id + "-blurb", blurb).withCssClass("section-blurb"))
                .child(body);
        card.setCssClass("section");
        return card;
    }
}
