package ai.mindconnect.sui.demo.stateful;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiColumn;
import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiToast;
import ai.mindconnect.ui.model.UiTrigger;
import ai.mindconnect.ui.stateful.RouteParams;
import ai.mindconnect.ui.stateful.SuiRoute;
import ai.mindconnect.ui.stateful.SuiView;
import ai.mindconnect.ui.stateful.UiEvent;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Product admin: search, pagination, an edit dialog, delete with confirm —
 * the whole thing as one view class, with the listeners next to the nodes
 * they belong to. Compare with the shop demo's {@code ProductController} +
 * four page classes doing the same with one endpoint per action.
 */
@SuiRoute("/products")
public class ProductsView extends SuiView<ProductsView.State> {

    static final int PAGE_SIZE = 8;

    /** Everything that lives between requests. Plain JSON. */
    public static class State {
        String query = "";
        int page = 1;
        /** Id of the product in the edit dialog; {@code "new"} for a fresh one; {@code null} = closed. */
        String editing;
        /** Validation message for the dialog form; {@code null} = none. */
        String formError;
        /** Values typed into the dialog so a failed save does not wipe them. */
        Map<String, String> draft = new LinkedHashMap<>();
    }

    private final ProductRepository products;
    private final HttpServletRequest request;

    public ProductsView(ProductRepository products, HttpServletRequest request) {
        this.products = products;
        this.request = request;
    }

    @Override
    protected State initialState(RouteParams params) {
        State s = new State();
        s.query = params.query("q", "");
        s.page = Math.max(1, params.queryInt("page", 1));
        return s;
    }

    @Override
    protected UiNode render(State s) {
        title("Products · stateful demo");
        if (s.editing != null) dialog(editDialog(s));

        UiForm search = UiForm.of("search", null)
                .field(UiField.text("q", "Search", s.query).asEditable().placeholder("name, sku or category"))
                .action(on(UiAction.primary("do-search", "Search")).click(e -> {
                    s.query = e.value("q", "");
                    s.page = 1;
                }))
                .action(on(UiAction.secondary("clear", "Clear")).click(e -> {
                    s.query = "";
                    s.page = 1;
                }));

        long total = products.count(s.query);
        int pages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        if (s.page > pages) s.page = pages;

        UiTable table = UiTable.of("products", "Products (" + total + ")")
                .column(UiColumn.of("sku", "SKU"))
                .column(UiColumn.of("name", "Name"))
                .column(UiColumn.of("category", "Category"))
                .column(UiColumn.of("price", "Price"))
                .column(UiColumn.of("active", "Active"))
                .action(on(UiAction.primary("new", "New product")).click(e -> openEditor(s, "new", null)))
                .rowAction(onRow(UiAction.secondary("edit", "Edit")).click(e ->
                        products.byId(UUID.fromString(e.rowId())).ifPresent(p -> openEditor(s, p.id().toString(), p))))
                .rowAction(onRow(UiAction.danger("delete", "Delete").confirm("Delete this product?")).click(e -> {
                    boolean gone = products.delete(UUID.fromString(e.rowId()));
                    toast(gone ? UiToast.success("Product deleted.") : UiToast.warn("Already gone."));
                }))
                .paginate(s.page, PAGE_SIZE, total);
        on(table).page(e -> s.page = Math.max(1, e.intValue("page", 1)));
        for (Product p : products.find(s.query, s.page, PAGE_SIZE)) {
            table.row(Map.of(
                    "id", p.id().toString(),
                    "sku", p.sku(),
                    "name", p.name(),
                    "category", p.category(),
                    "price", p.price(),
                    "active", p.active() ? "yes" : "no"));
        }

        return UiStack.of("products-page")
                .child(modeBar())
                .child(UiText.of("intro", "Every button here is a Java listener in ProductsView; "
                        + "the server keeps the state and sends only what changed."))
                .child(search)
                .child(table);
    }

    // ── the edit dialog ────────────────────────────────────────────────────

    private void openEditor(State s, String id, Product p) {
        s.editing = id;
        s.formError = null;
        s.draft.clear();
        if (p != null) {
            s.draft.put("sku", p.sku());
            s.draft.put("name", p.name());
            s.draft.put("category", p.category());
            s.draft.put("price", p.price());
            s.draft.put("active", Boolean.toString(p.active()));
        } else {
            s.draft.put("category", ProductRepository.CATEGORIES.get(0));
            s.draft.put("active", "true");
        }
    }

    private UiDialog editDialog(State s) {
        boolean fresh = "new".equals(s.editing);
        List<UiField.Option> categories = ProductRepository.CATEGORIES.stream()
                .map(c -> UiField.Option.of(c, c)).toList();
        UiForm form = UiForm.of("edit-form", null)
                .field(UiField.text("sku", "SKU", s.draft.get("sku")).asEditable().asRequired())
                .field(UiField.text("name", "Name", s.draft.get("name")).asEditable().asRequired())
                .field(UiField.select("category", "Category", s.draft.get("category"), categories).asEditable())
                .field(UiField.number("price", "Price", s.draft.get("price")).asEditable().step("0.01").min("0"))
                .field(UiField.bool("active", "Active", Boolean.parseBoolean(s.draft.get("active"))).asEditable())
                .action(on(UiAction.primary("save", fresh ? "Create" : "Save")).click(e -> save(s, e)))
                .action(on(UiAction.secondary("cancel", "Cancel")).click(e -> s.editing = null));
        if (s.formError != null) form.error(s.formError);

        UiDialog dialog = UiDialog.of(fresh ? "New product" : "Edit product", context().instanceUrl(), form);
        dialog.setId("edit-dialog");
        return dialog;
    }

    private void save(State s, UiEvent e) {
        // Keep what was typed, whatever happens next.
        for (String key : List.of("sku", "name", "category", "price")) s.draft.put(key, e.value(key, ""));
        s.draft.put("active", Boolean.toString(e.boolValue("active")));

        String sku = e.value("sku", "").trim();
        String name = e.value("name", "").trim();
        if (sku.isEmpty() || name.isEmpty()) {
            s.formError = "SKU and name are required.";
            return;
        }
        long cents;
        try {
            cents = Math.round(Double.parseDouble(e.value("price", "0").replace(',', '.')) * 100);
            if (cents < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            s.formError = "Price must be a number ≥ 0.";
            return;
        }
        String category = e.value("category", ProductRepository.CATEGORIES.get(0));
        boolean active = e.boolValue("active");

        if ("new".equals(s.editing)) {
            products.save(new Product(UUID.randomUUID(), sku, name, category, cents, active));
            toast(UiToast.success("Product created."));
        } else {
            UUID id = UUID.fromString(s.editing);
            products.byId(id).ifPresentOrElse(
                    p -> {
                        products.save(p.with(sku, name, category, cents, active));
                        toast(UiToast.success("Product saved."));
                    },
                    () -> toast(UiToast.warn("This product was deleted meanwhile.")));
        }
        s.editing = null;
        s.formError = null;
    }

    // ── SSR / SPA switch ───────────────────────────────────────────────────

    /**
     * The demo runs SSR-first; a cookie switches it to the SPA. Switching to
     * SSR needs a real reload, which only the SPA can do — so that link is an
     * {@code INVOKE} of a client handler the bootstrap script registers.
     */
    private UiNode modeBar() {
        boolean spa = SuiModeFilter.isSpaMode(request);
        UiStack bar = UiStack.of("mode-bar").direction(UiStack.Direction.HORIZONTAL).withCssClass("sui-toggle-row");
        bar.child(UiText.of("mode-label", "Mode: " + (spa ? "SPA (patches over fetch)" : "SSR (no JavaScript)")));
        if (spa) {
            bar.child(UiAction.link("to-ssr", "Switch to SSR").onClick(UiTrigger.invoke("switch-to-ssr")));
        } else {
            bar.child(UiLink.of("to-spa", "/mode?mode=spa", "Switch to SPA"));
        }
        bar.child(UiLink.of("to-counter", "/counter", "Counter demo →"));
        return bar;
    }
}
