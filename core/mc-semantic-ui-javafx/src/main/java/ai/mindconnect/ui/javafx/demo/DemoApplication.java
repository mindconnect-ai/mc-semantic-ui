package ai.mindconnect.ui.javafx.demo;

import ai.mindconnect.ui.javafx.SuiFxEventBus;
import ai.mindconnect.ui.javafx.SuiFxOverlay;
import ai.mindconnect.ui.javafx.SuiFxRenderer;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiColumn;
import ai.mindconnect.ui.model.UiDetail;
import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiFieldGroup;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiList;
import ai.mindconnect.ui.model.UiMenu;
import ai.mindconnect.ui.model.UiMenuButton;
import ai.mindconnect.ui.model.UiMenuItem;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiProgress;
import ai.mindconnect.ui.model.UiSection;
import ai.mindconnect.ui.model.UiSpinner;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiToast;
import ai.mindconnect.ui.model.UiTree;
import ai.mindconnect.ui.model.UiTreeNode;
import ai.mindconnect.ui.model.UiTrigger;
import ai.mindconnect.ui.model.UiUpload;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A runnable showcase of the JavaFX renderer: the same {@code UiNode}
 * vocabulary the SSR templates and the SPA draw, painted as a desktop app.
 *
 * <p>Most of it is deliberately local: the triggers are {@code INVOKE}, so
 * each button lands in a plain Java method registered on the bus —
 * {@link #installHandlers} is the whole "backend", and the order list lives in
 * a field of this class. The <b>Server</b> tab is the exception, and shows what
 * changes once a server is involved: see {@link DemoServer}.
 *
 * <p>Run it with:
 * <pre>{@code
 * mvn -f core/mc-semantic-ui-javafx/pom.xml javafx:run
 * }</pre>
 *
 * <p>From an IDE, start {@link DemoLauncher} rather than this class — see
 * there for why.
 *
 * <p>One tab per idea, and between them every node type the renderer supports:
 * <ul>
 *   <li><b>Customer</b> — a form whose fields sit in a stack and a group, and
 *       still submit as one payload.</li>
 *   <li><b>Orders</b> — table, row actions, a menu-button and a progress bar;
 *       every change patches table and bar in one go.</li>
 *   <li><b>Catalog</b> — a tree driving a detail pane, which starts as a
 *       spinner.</li>
 *   <li><b>Navigation</b> — a menu with groups, badge, divider and a danger
 *       entry.</li>
 *   <li><b>Fields</b> — every {@code UiField.FieldType}, plus required, hint,
 *       validation error and read-only.</li>
 *   <li><b>Widgets</b> — text, links, every action shape, spinners, progress
 *       variants, a dialog.</li>
 *   <li><b>Documents</b> — a detail block, a list, and an upload zone whose
 *       handler reads the dropped file locally.</li>
 *   <li><b>Long task</b> — a handler that runs for seconds and reports its
 *       progress by patching, without freezing the window.</li>
 *   <li><b>Server</b> — a JDK HttpServer inside this demo answering with
 *       {@code UiPatch}, a multipart upload, and a foreign REST API mapped to
 *       UiNodes by hand.</li>
 * </ul>
 *
 * <p>Not shown, because the JavaFX renderer does not paint them yet:
 * {@code app-shell}, {@code header} and {@code icon}.
 */
public class DemoApplication extends Application {

    /** The demo's entire "database". */
    private final List<Map<String, Object>> orders = new ArrayList<>(List.of(
            order("1001", "Keyboard", "89.00", "shipped"),
            order("1002", "Monitor", "329.50", "open")
    ));

    /**
     * Open-Meteo needs no API key and no account, which is why it is in a demo
     * that anyone should be able to run. Munich; {@code current} asks for just
     * the three values shown.
     */
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=48.14&longitude=11.58"
            + "&current=temperature_2m,wind_speed_10m,weather_code"
            + "&timezone=Europe%2FBerlin";

    // The overlay is the host surface ("the DOM"); the renderer paints into it;
    // the bus drives the renderer and routes toasts + busy state to the overlay.
    // The overlay loads sui-fx.css itself, so there is no stylesheet wiring here.
    private final SuiFxOverlay overlay = new SuiFxOverlay();
    private final SuiFxRenderer renderer = SuiFxRenderer.createDefaultRenderer(overlay);
    private final SuiFxEventBus bus = new SuiFxEventBus(renderer);
    private DemoServer server;

    @Override
    public void start(Stage stage) throws Exception {
        server = new DemoServer();
        installHandlers();

        renderer.mount(ui());

        stage.setTitle("Semantic UI — JavaFX renderer demo");
        stage.setScene(new Scene(overlay, 900, 640));
        stage.show();
    }

    @Override
    public void stop() {
        if (server != null) server.stop();
    }

    /** The handful of WMO weather codes this demo bothers to name. */
    private static String describe(int wmoCode) {
        return switch (wmoCode) {
            case 0 -> "Clear sky";
            case 1, 2 -> "Mainly clear";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "WMO code " + wmoCode;
        };
    }

    // ── the model ─────────────────────────────────────────────────────────

    /** The whole screen as one {@code UiNode} tree — no JavaFX in sight. */
    private UiNode ui() {
        return UiSection.of("main", null)
                .section("customer", "Customer", customerForm())
                // The table is wrapped in a stack on purpose: a patch replaces a
                // node inside its parent, and a tab's content has no parent pane
                // to replace it in.
                .section("orders", "Orders", UiStack.of(ordersToolbar(), ordersTable()))
                .section("catalog", "Catalog", catalog())
                .section("navigation", "Navigation", navigation())
                .section("fields", "Fields", fieldGallery())
                .section("widgets", "Widgets", widgetGallery())
                .section("documents", "Documents", documents())
                .section("import", "Long task", importPanel())
                .section("server", "Server", serverPanel())
                .section("about", "About", about())
                .initialSection("customer");
    }

    /**
     * A menu-button (the "…" overflow) next to a progress bar. Both are model
     * nodes; neither needs a line of JavaFX here.
     */
    private UiNode ordersToolbar() {
        var overflow = UiMenuButton.of("orders-overflow",
                        UiMenuItem.of("export", "Export as CSV")
                                .onClick(UiTrigger.invoke("notImplemented")),
                        UiMenuItem.group("reports", "Reports",
                                UiMenuItem.of("monthly", "Monthly")
                                        .onClick(UiTrigger.invoke("notImplemented")),
                                UiMenuItem.of("yearly", "Yearly")
                                        .onClick(UiTrigger.invoke("notImplemented"))),
                        UiMenuItem.divider(),
                        UiMenuItem.of("purge", "Delete all orders")
                                .danger(true)
                                .confirm("Delete every order? This cannot be undone.")
                                .onClick(UiTrigger.invoke("purgeOrders")))
                .label("More…");

        return UiStack.of(overflow, fulfilmentProgress())
                .direction(UiStack.Direction.HORIZONTAL)
                .gap(16);
    }

    /** Share of orders already shipped — a determinate progress with its value shown. */
    private UiNode fulfilmentProgress() {
        long shipped = orders.stream().filter(o -> "shipped".equals(o.get("status"))).count();
        var progress = UiProgress.of(shipped, Math.max(1, orders.size()))
                .showValue(true)
                .status(shipped == orders.size()
                        ? UiProgress.Status.SUCCESS
                        : UiProgress.Status.NORMAL);
        progress.setId("fulfilment");
        return progress;
    }

    /** A sidebar menu with groups, a badge, a selected item and a danger entry. */
    private UiNode navigation() {
        var menu = UiMenu.of("nav", "Sections",
                        UiMenuItem.of("inbox", "Inbox").badge("3").selected(true)
                                .onClick(UiTrigger.invoke("navigate")),
                        UiMenuItem.of("archive", "Archive")
                                .onClick(UiTrigger.invoke("navigate")),
                        UiMenuItem.group("settings", "Settings",
                                UiMenuItem.of("profile", "Profile")
                                        .onClick(UiTrigger.invoke("navigate")),
                                UiMenuItem.of("billing", "Billing")
                                        .onClick(UiTrigger.invoke("navigate"))).open(true),
                        UiMenuItem.divider(),
                        UiMenuItem.of("wipe", "Reset everything").danger(true)
                                .confirm("Really reset?")
                                .onClick(UiTrigger.invoke("navigate")))
                .toggle(true);

        var detail = UiStack.of(UiText.of("Pick an entry on the left."));
        detail.setId("nav-detail");

        return UiStack.of(menu, detail)
                .direction(UiStack.Direction.HORIZONTAL)
                .gap(24);
    }

    /** A tree driving a detail pane — the classic master/detail of a rich client. */
    private UiNode catalog() {
        var tree = UiTree.of("catalog-tree", "Product groups")
                .node(UiTreeNode.of("hardware", "Hardware").open(true)
                        .child(UiTreeNode.of("keyboards", "Keyboards")
                                .onClick(UiTrigger.invoke("showGroup")))
                        .child(UiTreeNode.of("monitors", "Monitors")
                                .onClick(UiTrigger.invoke("showGroup"))))
                .node(UiTreeNode.of("services", "Services")
                        .child(UiTreeNode.of("support", "Support plans")
                                .onClick(UiTrigger.invoke("showGroup"))));

        // The detail side starts as a spinner: the declarative "still loading"
        // node, as opposed to the busy indicator around a dispatch.
        var detail = UiStack.of(UiSpinner.of("Pick a group on the left…"));
        detail.setId("catalog-detail");

        return UiStack.of(tree, detail)
                .direction(UiStack.Direction.HORIZONTAL)
                .gap(24);
    }

    private UiForm customerForm() {
        return UiForm.of("customer-form", "Customer details")
                .field(UiField.text("name", "Name", "Ada Lovelace").asEditable().asRequired())
                .field(UiField.text("email", "E-mail", "ada@example.com").asEditable()
                        .hint("We only use this for order confirmations."))
                // Laid out in a row, still part of the same submit — that is the
                // point of the form scope.
                .content(UiStack.of(
                                UiField.select("plan", "Plan", "pro", List.of(
                                        UiField.Option.of("free", "Free"),
                                        UiField.Option.of("pro", "Professional"),
                                        UiField.Option.of("enterprise", "Enterprise"))).asEditable(),
                                UiField.date("since", "Customer since", "2021-04-01").asEditable(),
                                UiField.bool("newsletter", "Newsletter", true).asEditable())
                        .direction(UiStack.Direction.HORIZONTAL)
                        .gap(16))
                .content(UiField.textarea("notes", "Notes", "Prefers e-mail over phone.").asEditable())
                .action(UiAction.primary("save", "Save").onClick(
                        UiTrigger.invoke("saveCustomer", "customer-form")))
                .action(UiAction.secondary("address", "Edit address…").onClick(
                        UiTrigger.invoke("editAddress")))
                .action(UiAction.secondary("reset", "Discard").onClick(
                        // A PATCH trigger needs no handler at all — the toast
                        // rides along on the patch itself.
                        UiTrigger.toast(UiToast.info("Nothing to discard in the demo."))));
    }

    private UiTable ordersTable() {
        var table = UiTable.of("orders-table", "Orders")
                .column(UiColumn.text("id", "Order").asSortable())
                .column(UiColumn.text("product", "Product"))
                .column(UiColumn.number("amount", "Amount"))
                .column(UiColumn.text("status", "Status"))
                .selectMode(UiTable.SelectMode.SINGLE)
                .action(UiAction.primary("add-order", "Add order")
                        .onClick(UiTrigger.invoke("addOrder")))
                .action(UiAction.secondary("sync", "Sync inventory")
                        .onClick(UiTrigger.invoke("syncInventory")))
                .rowAction(UiAction.danger("delete-order", "Delete")
                        .onClick(UiTrigger.invoke("deleteOrder")));

        orders.forEach(table::row);
        return table;
    }

    /**
     * Every {@link UiField.FieldType} the renderer knows, plus the field flags
     * that change how one is painted: required, hint, validation error and
     * read-only. All of it sits in one form, so "Collect" shows the lot as a
     * single payload.
     */
    private UiNode fieldGallery() {
        var currency = UiField.number("price", "Price (currency)", "19.99").asEditable();
        currency.setFieldType(UiField.FieldType.CURRENCY);
        var percent = UiField.number("margin", "Margin (percent)", "12.5").asEditable();
        percent.setFieldType(UiField.FieldType.PERCENT);
        var dateTime = UiField.text("slot", "Delivery slot (datetime)", "2026-07-21T09:30").asEditable();
        dateTime.setFieldType(UiField.FieldType.DATETIME);

        var texts = UiFieldGroup.of("group-text", "Text")
                .field(UiField.text("plain", "Text", "editable").asEditable())
                .field(UiField.text("needed", "Required text", "").asEditable().asRequired()
                        .placeholder("Must be filled"))
                .field(UiField.text("hinted", "With a hint", "value").asEditable()
                        .hint("A hint sits under the control."))
                .field(UiField.text("broken", "With a validation error", "not-an-email").asEditable()
                        .error("That is not a valid e-mail address."))
                .field(UiField.text("frozen", "Read-only", "painted as text, still submitted"))
                .field(UiField.textarea("essay", "Textarea", "Multiple\nlines.").asEditable());

        var numbers = UiFieldGroup.of("group-number", "Numbers and dates")
                .field(UiField.number("count", "Number", 42).asEditable().step("1"))
                .field(currency)
                .field(percent)
                .field(UiField.date("day", "Date", "2026-07-20").asEditable())
                .field(dateTime);

        var choices = UiFieldGroup.of("group-choice", "Choices")
                .field(UiField.bool("flag", "Boolean", true).asEditable())
                .field(UiField.select("single", "Select", "b", options()).asEditable())
                .field(UiField.multiselect("many", "Multiselect", List.of("a", "c"), options())
                        .asEditable())
                // submitOnChange makes the selection itself the action — no
                // separate button. Here it just fires the form's first action.
                .field(UiField.select("instant", "Select (submit on change)", "a", options())
                        .asEditable().submitOnChange());

        var misc = UiFieldGroup.of("group-misc", "Files and references")
                .field(UiField.file("upload", "File").accept(".pdf,.txt"))
                .field(UiField.reference("ref", "Reference", "customer/42").asEditable());

        return UiForm.of("field-form", "Every field type")
                .content(texts)
                .content(numbers)
                .content(choices)
                .content(misc)
                .action(UiAction.primary("collect", "Collect values")
                        .onClick(UiTrigger.invoke("collectFields", "field-form")));
    }

    private static List<UiField.Option> options() {
        return List.of(
                UiField.Option.of("a", "Option A"),
                UiField.Option.of("b", "Option B"),
                UiField.Option.of("c", "Option C"));
    }

    /** The non-input nodes: text, link, every action shape, spinners, progress. */
    private UiNode widgetGallery() {
        var actions = UiStack.of(
                        UiAction.primary("w-primary", "Primary"),
                        UiAction.secondary("w-secondary", "Secondary"),
                        UiAction.danger("w-danger", "Danger"),
                        UiAction.link("w-link", "Link appearance"),
                        UiAction.icon("w-icon", "⌘"),
                        UiAction.secondary("w-disabled", "Disabled")
                                .disabled("Hover me: this is the disabledReason."),
                        // The declarative busy state — no dispatch involved, the
                        // model simply says this action is already running.
                        UiAction.secondary("w-loading", "Loading").loading(true))
                .direction(UiStack.Direction.HORIZONTAL)
                .gap(8);

        var spinners = UiStack.of(
                        UiSpinner.of().size(UiSpinner.Size.SM),
                        UiSpinner.of().size(UiSpinner.Size.MD),
                        UiSpinner.of("Large, with a label").size(UiSpinner.Size.LG))
                .direction(UiStack.Direction.HORIZONTAL)
                .gap(24);

        var progress = UiStack.of(
                UiProgress.of(0.35).showValue(true),
                UiProgress.indeterminate(),
                UiProgress.of(3, 4).showValue(true).status(UiProgress.Status.SUCCESS),
                UiProgress.of(2, 4).showValue(true).status(UiProgress.Status.WARNING),
                UiProgress.of(1, 4).showValue(true).status(UiProgress.Status.ERROR),
                UiProgress.of(0.6).variant(UiProgress.Variant.CIRCLE));

        return UiStack.of(
                UiText.of("Text is the simplest node there is."),
                UiText.of("Actions"), actions,
                UiText.of("Links"), links(),
                UiText.of("Spinners — the declarative kind"), spinners,
                UiText.of("Progress"), progress,
                UiText.of("Dialog"),
                UiAction.primary("open-dialog", "Open a dialog")
                        .onClick(UiTrigger.invoke("editAddress")));
    }

    private UiNode links() {
        // An external link opens the desktop browser. A local one has no
        // universal meaning in a rich client — where "/customers" goes is the
        // app's decision — so it dispatches a handler instead.
        return UiStack.of(
                        UiLink.external("docs", "https://example.com", "External link (opens browser)"),
                        UiLink.of("internal", null, "Local link")
                                .onClick(UiTrigger.invoke("notImplemented")))
                .direction(UiStack.Direction.HORIZONTAL)
                .gap(16);
    }

    /**
     * The long-running handler. It blocks for about four seconds and reports
     * its progress by patching a {@code UiProgress} as it goes — from a
     * background thread, which is exactly why the window stays usable: switch
     * tabs while it runs and nothing stutters.
     */
    private UiNode importPanel() {
        return UiStack.of(
                UiText.of("Runs for about four seconds on a background thread and "
                        + "patches its progress into the tree as it goes. Switch tabs "
                        + "while it runs — the window keeps working."),
                UiAction.primary("start-import", "Import catalog")
                        .onClick(UiTrigger.invoke("importCatalog")),
                importProgress(0),
                importStatus("Idle."));
    }

    private UiNode importProgress(int percent) {
        var progress = UiProgress.of(percent, 100).showValue(true)
                .status(percent >= 100 ? UiProgress.Status.SUCCESS : UiProgress.Status.NORMAL);
        progress.setId("import-progress");
        return progress;
    }

    private UiNode importStatus(String message) {
        return UiText.of("import-status", message);
    }

    /**
     * Detail, list and upload.
     *
     * <p>The upload is the rich-client shape on purpose: {@code invoke}, not
     * {@code upload}. Picking a file and reading it is local work — the
     * handler gets the {@link java.io.File}s and reads them off the disk. The
     * multipart {@code UPLOAD} behaviour is for models that name a server url,
     * which this demo has none of.
     */
    private UiNode documents() {
        var detail = UiDetail.of("doc-detail", "Selected document")
                .field(UiField.text("name", "Name", "quarterly-report.pdf"))
                .field(UiField.text("size", "Size", "2.4 MB"))
                .field(UiField.text("owner", "Owner", "Ada Lovelace"))
                // An empty value renders as an em dash rather than vanishing —
                // "not set" is information.
                .field(UiField.text("shared", "Shared with", null))
                .action(UiAction.secondary("doc-download", "Download")
                        .onClick(UiTrigger.invoke("notImplemented")));

        var list = UiList.of("doc-list", "Recent documents")
                .item(UiList.Item.of("d1", "quarterly-report.pdf")
                        .description("Updated two days ago · 2.4 MB")
                        .action(UiAction.link("d1-open", "Open")
                                .onClick(UiTrigger.invoke("notImplemented"))))
                .item(UiList.Item.of("d2", "budget-2026.xlsx")
                        .description("Updated last week · 810 KB")
                        .onClick(UiTrigger.invoke("notImplemented")))
                .item(UiList.Item.of("d3", "notes.txt")
                        .description("A collapsible item — the body is a node of its own.")
                        .collapsible("notes.txt", false)
                        .content(UiText.of("Any UiNode can sit inside a list item.")))
                .action(UiAction.primary("doc-new", "New document")
                        .onClick(UiTrigger.invoke("notImplemented")));

        var upload = UiUpload.of("doc-upload", "Import a text file")
                .dropText("Drop a file here or")
                .buttonLabel("Choose file…")
                .hint("The handler reads the file locally — nothing is sent anywhere.")
                .onUpload(UiTrigger.invoke("readFile"));

        return UiStack.of(upload, importedPreview("Nothing imported yet."), detail, list);
    }

    private UiNode importedPreview(String text) {
        return UiText.of("import-preview", text);
    }

    /**
     * The three ways a client meets a server, side by side.
     *
     * <ul>
     *   <li><b>The server answers with UI.</b> "Load inventory" is a plain
     *       {@code api("GET", …)} trigger and there is <em>no handler for it in
     *       this class</em> — the endpoint returns a {@code UiPatch} and the
     *       {@code APPLY_RESPONSE} behaviour applies it. That is the whole
     *       premise of the vocabulary, in one button.</li>
     *   <li><b>The client posts files.</b> {@code uploadTo(url)} makes the
     *       upload zone POST multipart, and the server answers with a patch
     *       again.</li>
     *   <li><b>A foreign API.</b> Open-Meteo returns its own JSON, which no
     *       renderer understands — so a local handler maps it to UiNodes. This
     *       is what you do with any REST API that was not built for this
     *       vocabulary.</li>
     * </ul>
     */
    private UiNode serverPanel() {
        var inventoryPanel = UiStack.of(UiText.of("Not loaded yet."));
        inventoryPanel.setId("inventory-panel");

        var uploadResult = UiStack.of(UiText.of("Nothing uploaded yet."));
        uploadResult.setId("upload-result");

        var weatherPanel = UiStack.of(UiText.of("Not fetched yet."));
        weatherPanel.setId("weather-panel");

        return UiStack.of(
                UiText.of("A JDK HttpServer runs inside this demo on "
                        + server.url("") + " — the two endpoints below answer with UiPatch."),

                UiText.of("1 · The server answers with UI"),
                UiAction.primary("load-inventory", "Load inventory")
                        // No client handler: the response is a UiPatch and the
                        // bus applies it. This button is the entire wiring.
                        .onClick(UiTrigger.api("GET", server.url("/api/inventory"))),
                inventoryPanel,

                UiText.of("2 · The client posts a file"),
                UiUpload.of("server-upload", "Upload to the local server")
                        .dropText("Drop a file here or")
                        .buttonLabel("Choose file…")
                        .hint("Posted as multipart/form-data; the server replies with a patch.")
                        .uploadTo(server.url("/api/files")),
                uploadResult,

                UiText.of("3 · A foreign REST API"),
                UiAction.secondary("fetch-weather", "Fetch weather (open-meteo.com)")
                        .onClick(UiTrigger.invoke("weather")),
                weatherPanel);
    }

    private UiNode about() {
        return UiStack.of(
                UiText.of("This window is drawn from the same UiNode tree that the "
                        + "server renders to HTML and the SPA renders in the browser."),
                UiText.of("Every button here dispatches an INVOKE trigger, which the "
                        + "SuiFxEventBus routes to a plain Java method — no HTTP involved."),
                links());
    }

    // ── the "backend" ─────────────────────────────────────────────────────

    /** Every trigger in this demo lands in one of these. */
    private void installHandlers() {
        bus.registerClientHandler("saveCustomer", ctx -> {
            // ctx.payload() holds every field in the form — the flat ones, the
            // ones in the horizontal stack, the textarea.
            var summary = ctx.payload().entrySet().stream()
                    .map(e -> e.getKey() + " = " + e.getValue())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("(empty)");
            bus.toast(UiToast.success(summary).title("Saved customer"));
        });

        bus.registerClientHandler("addOrder", ctx -> {
            var next = String.valueOf(1001 + orders.size());
            orders.add(order(next, "New item", "0.00", "draft"));
            refreshOrders(UiToast.success("Order " + next + " added"));
        });

        // Handlers run off the FX thread, so blocking here is fine — no
        // runAsync, no Platform.runLater, just a method that takes a while.
        // The button stays busy and the scrim appears for exactly that long.
        bus.registerClientHandler("syncInventory", ctx -> {
            sleep(1500);
            bus.toast(UiToast.success("Inventory synced").title("Supplier"));
        });

        // The long one. Four seconds of "work", reporting as it goes: each
        // round patches the progress node and the status line. All of it from a
        // background thread — applyPatch gets itself onto the FX thread.
        bus.registerClientHandler("importCatalog", ctx -> {
            int steps = 20;
            for (int step = 1; step <= steps; step++) {
                sleep(200);
                int percent = step * 100 / steps;
                bus.applyPatch(UiPatch.of()
                        .patch(UiPatch.Operation.replace("import-progress", importProgress(percent)))
                        .patch(UiPatch.Operation.replace("import-status",
                                importStatus("Importing… " + percent + "% (" + step + "/" + steps + ")"))));
            }
            bus.applyPatch(UiPatch.of()
                    .patch(UiPatch.Operation.replace("import-status",
                            importStatus("Done — " + steps + " batches imported.")))
                    .toast(UiToast.success(steps + " batches imported").title("Catalog")));
        });

        bus.registerClientHandler("collectFields", ctx -> {
            var summary = ctx.payload().entrySet().stream()
                    .map(e -> e.getKey() + " = " + e.getValue())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("(empty)");
            bus.showDialog(UiDialog.of("Collected values", null,
                    UiText.of(summary)));
        });

        // A dialog is a UiNode like any other; showDialog paints it with the
        // normal renderer and puts it in a modal stage.
        bus.registerClientHandler("editAddress", ctx -> bus.showDialog(UiDialog.of(
                "Edit address", null,
                UiForm.of("address-form", null)
                        .field(UiField.text("street", "Street", "12 Analytical Way").asEditable())
                        .field(UiField.text("city", "City", "London").asEditable())
                        .action(UiAction.primary("save-address", "Save address")
                                .onClick(UiTrigger.invoke("saveAddress", "address-form"))))));

        bus.registerClientHandler("saveAddress", ctx ->
                bus.toast(UiToast.success(ctx.string("street") + ", " + ctx.string("city"))
                        .title("Address saved")));

        // The tree hands its node over as the trigger source, which is all the
        // handler needs to know what was clicked.
        bus.registerClientHandler("showGroup", ctx -> {
            var group = ctx.source() instanceof UiTreeNode node ? node.getLabel() : "?";
            bus.applyPatch(UiPatch.of()
                    .patch(UiPatch.Operation.replace("catalog-detail", detail("catalog-detail", group))));
        });

        // Menu items are actions, so they reach handlers the same way.
        bus.registerClientHandler("navigate", ctx -> {
            var label = ctx.source() instanceof UiMenuItem item ? item.getLabel() : "?";
            bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.replace(
                    "nav-detail", detail("nav-detail", label))));
        });

        // A REST API that knows nothing about this vocabulary: fetch it, then
        // build UiNodes out of the answer. The handler is already off the FX
        // thread, so the blocking call is fine as it stands.
        bus.registerClientHandler("weather", ctx -> {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(WEATHER_URL))
                    .header("Accept", "application/json")
                    .build();
            var response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                bus.toast(UiToast.error("Weather service answered " + response.statusCode()));
                return;
            }

            var current = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(response.body()).path("current");

            var detail = UiDetail.of("weather-panel", "Munich, right now")
                    .field(UiField.text("temp", "Temperature",
                            current.path("temperature_2m").asDouble() + " °C"))
                    .field(UiField.text("wind", "Wind",
                            current.path("wind_speed_10m").asDouble() + " km/h"))
                    .field(UiField.text("sky", "Conditions",
                            describe(current.path("weather_code").asInt())))
                    .field(UiField.text("observed", "Observed at",
                            current.path("time").asText()));

            bus.applyPatch(UiPatch.of()
                    .patch(UiPatch.Operation.replace("weather-panel", detail)));
        });

        // What "upload" means in a rich client: the files are already on this
        // machine, so the handler simply reads one. No server, no multipart.
        bus.registerClientHandler("readFile", ctx -> {
            var file = ctx.files().get(0);
            var header = file.getName() + " (" + file.length() + " bytes)";

            // Whatever gets dropped is a real file, and most files are not
            // UTF-8 text. Say so instead of throwing — the bus would catch it,
            // but the user would only see a log line.
            String text;
            try {
                text = java.nio.file.Files.readString(file.toPath());
            } catch (java.io.IOException notText) {
                bus.applyPatch(UiPatch.of()
                        .patch(UiPatch.Operation.replace("import-preview",
                                importedPreview(header + "\n\nNot a UTF-8 text file — nothing to preview.")))
                        .toast(UiToast.warn("Cannot read " + file.getName() + " as text")));
                return;
            }

            var preview = text.length() > 400 ? text.substring(0, 400) + "…" : text;
            bus.applyPatch(UiPatch.of()
                    .patch(UiPatch.Operation.replace("import-preview",
                            importedPreview(header + "\n\n" + preview)))
                    .toast(UiToast.success("Read " + file.getName())));
        });

        bus.registerClientHandler("notImplemented", ctx ->
                bus.toast(UiToast.info("Not part of the demo.")));

        bus.registerClientHandler("purgeOrders", ctx -> {
            orders.clear();
            refreshOrders(UiToast.warn("All orders deleted"));
        });

        bus.registerClientHandler("deleteOrder", ctx -> {
            // A row action's payload is the row it sits in.
            var id = ctx.string("id");
            orders.removeIf(o -> id != null && id.equals(o.get("id")));
            refreshOrders(UiToast.warn("Order " + id + " deleted"));
        });
    }

    /**
     * Repaints everything the order list feeds — the table and the fulfilment
     * bar — as a single patch. Two targets, one round: that is what a patch is
     * for, as opposed to remounting the window.
     */
    private void refreshOrders(UiToast toast) {
        bus.applyPatch(UiPatch.of()
                .patch(UiPatch.Operation.replace("orders-table", ordersTable()))
                .patch(UiPatch.Operation.replace("fulfilment", fulfilmentProgress()))
                .toast(toast));
    }

    /**
     * The catalog detail pane. Keeps the {@code catalog-detail} id, because
     * that is what the next patch will look for — a replacement has to be
     * findable itself.
     */
    private UiNode detail(String id, String label) {
        var detail = UiStack.of(
                UiText.of(label),
                UiText.of("Nothing else to show — this pane exists to demonstrate "
                        + "that a selection patches a sibling in place."));
        detail.setId(id);
        return detail;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, Object> order(String id, String product, String amount, String status) {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("product", product);
        row.put("amount", amount);
        row.put("status", status);
        return row;
    }

    // No main() here on purpose — starting an Application subclass directly
    // from the classpath fails with "JavaFX runtime components are missing".
    // Use DemoLauncher, which explains why.
}
