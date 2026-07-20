package ai.mindconnect.ui.editor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiDetail;
import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiAppShell;
import ai.mindconnect.ui.model.UiHeader;
import ai.mindconnect.ui.model.UiMenu;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiList;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiSection;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Hand-maintained catalogue of editable {@link UiNode} types. Drives both
 * the editor's "add child" picker (which types can I add here?) and the
 * property panel (which properties does this type have?).
 *
 * <p>Hand-coded on purpose: reflection over {@code @JsonSubTypes} works for
 * the discriminator but stumbles over Generics ({@code List<UiField>},
 * {@code List<UiAction>}, …) and over Lombok-vs-explicit getter mixing.
 * One screenful of explicit metadata is dull but durable.
 *
 * <p>Each entry exposes:
 * <ul>
 *   <li>{@code type} — the JSON discriminator, matches {@code @JsonSubTypes.Type#name}.</li>
 *   <li>{@code label} / {@code category} — UI hints for the picker / tree.</li>
 *   <li>{@code properties} — flat property descriptors driving the panel.</li>
 *   <li>{@code children} — for container types, which property holds the
 *        child {@code UiNode} list and which types are allowed inside.</li>
 *   <li>{@code factory} — produces a fresh instance with sane defaults.</li>
 * </ul>
 */
public final class NodeRegistry {

    /** Property semantic kinds — drives the editor's input control. */
    public enum PropertyKind {
        STRING, NUMBER, BOOLEAN, ENUM,
        /** A list of strings / primitives. */
        STRING_LIST,
        /** A nested UiNode list — children-style; handled by the tree, not the panel. */
        NODE_LIST,
        /** A nested object with its own sub-properties (e.g. UiHeader.User). */
        OBJECT
    }

    @Data
    public static final class PropertyMeta {
        private final String name;
        private final PropertyKind kind;
        private final boolean required;
        /** ENUM only: legal values. */
        private final List<String> enumValues;

        public static PropertyMeta of(String name, PropertyKind kind) {
            return new PropertyMeta(name, kind, false, null);
        }
        public static PropertyMeta required(String name, PropertyKind kind) {
            return new PropertyMeta(name, kind, true, null);
        }
        public static PropertyMeta enumOf(String name, List<String> values) {
            return new PropertyMeta(name, PropertyKind.ENUM, false, values);
        }
    }

    /**
     * Whether a children-property carries one node or many. {@code LIST} is
     * the common case (UiForm.fields, UiSection.sections, …); {@code SINGLE}
     * covers single-node slots like {@code UiPage.node}.
     */
    public enum Cardinality { LIST, SINGLE }

    @Data
    public static final class ChildrenMeta {
        /** Name of the property that holds the child(ren). */
        private final String property;
        private final Cardinality cardinality;
        /** {@code type} discriminators allowed in this slot. */
        private final List<String> allowedTypes;

        public static ChildrenMeta of(String property, String... allowed) {
            return new ChildrenMeta(property, Cardinality.LIST, List.of(allowed));
        }

        public static ChildrenMeta single(String property, String... allowed) {
            return new ChildrenMeta(property, Cardinality.SINGLE, List.of(allowed));
        }
    }

    @Data
    public static final class NodeMeta {
        private final String type;
        private final String label;
        /** Loose grouping for the add-picker ("container", "input", "display", …). */
        private final String category;
        private final List<PropertyMeta> properties;
        /** May be empty for leaf nodes. */
        private final List<ChildrenMeta> children;
        /**
         * Default-instance factory. {@link JsonIgnore} so the schema endpoint
         * doesn't try to serialise the lambda — the frontend obtains defaults
         * via {@link EditorRestController#getDefault(String)} which invokes
         * the factory server-side.
         */
        @JsonIgnore
        private final Supplier<UiNode> factory;
    }

    private final Map<String, NodeMeta> byType = new LinkedHashMap<>();

    /**
     * Node types that compose freely inside a generic container slot — a Stack's
     * children, a Page body, a tab's content. Containers plus the display/input
     * leaves (text, link, action, field) so those can be dropped straight into a
     * layout without wrapping them in a form first. Structural-only types
     * ({@code page}, {@code column}, {@code row}, {@code section-entry}) are
     * excluded — they only make sense under their specific parents.
     */
    private static final String[] COMPOSABLE = {
            "stack", "section", "form", "detail", "list", "table", "chart", "header",
            "text", "icon", "spinner", "progress", "menu", "menu-button", "link", "action", "field"
    };

    public NodeRegistry() {
        register(pageMeta());
        register(stackMeta());
        register(formMeta());
        register(sectionMeta());
        register(sectionEntryMeta());
        register(detailMeta());
        register(listMeta());
        register(tableMeta());
        register(columnMeta());
        register(rowMeta());
        register(textMeta());
        register(iconMeta());
        register(spinnerMeta());
        register(progressMeta());
        register(menuMeta());
        register(menuItemMeta());
        register(menuButtonMeta());
        // Extension node types (chart, diagram) are deliberately absent: the
        // editor depends on the core only, so it offers what the core can
        // render. An app that wants them registers their metadata itself.
        register(appShellMeta());
        register(headerMeta());
        register(fieldMeta());
        register(actionMeta());
        register(linkMeta());
        register(dialogMeta());
    }

    private void register(NodeMeta meta) {
        byType.put(meta.getType(), meta);
    }

    /** All known types in registration order — keeps the picker stable. */
    public List<NodeMeta> all() {
        return List.copyOf(byType.values());
    }

    public NodeMeta get(String type) {
        return byType.get(type);
    }

    // ── per-type metadata ────────────────────────────────────────────────

    private NodeMeta pageMeta() {
        return new NodeMeta("page", "Page", "document",
                List.of(
                        PropertyMeta.of("navigate", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                // The page's "node" property is a single UiNode rather than a
                // list. We expose it as a single-slot child group so the tree
                // shows it like any other child. {@code page} is intentionally
                // excluded — pages don't nest inside pages. toasts stay edited
                // via the property panel for now.
                List.of(
                        ChildrenMeta.single("node", COMPOSABLE),
                        // Open dialogs: a list of "dialog" nodes, each addressed
                        // by its id. Rendered into the body-level #sui-dialogs
                        // host; a patch opens/closes them by APPEND/REMOVE.
                        ChildrenMeta.of("dialogs", "dialog")
                ),
                () -> {
                    var p = new UiPage();
                    p.setId("page-" + shortId());
                    return p;
                });
    }

    private NodeMeta stackMeta() {
        return new NodeMeta("stack", "Stack", "container",
                List.of(
                        PropertyMeta.of("id", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING),
                        PropertyMeta.enumOf("direction", List.of("VERTICAL", "HORIZONTAL")),
                        PropertyMeta.of("gap", PropertyKind.NUMBER)
                ),
                List.of(
                        // Stacks are the universal composition box: any container
                        // plus display/input leaves (text, link, action, field).
                        ChildrenMeta.of("children", COMPOSABLE)
                ),
                () -> UiStack.of("stack-" + shortId()));
    }

    private NodeMeta formMeta() {
        return new NodeMeta("form", "Form", "container",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.of("title", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING),
                        PropertyMeta.of("reloadOnSubmit", PropertyKind.BOOLEAN)
                ),
                List.of(
                        ChildrenMeta.of("fields",  "field"),
                        ChildrenMeta.of("actions", "action"),
                        ChildrenMeta.of("links",   "link")
                ),
                () -> {
                    var f = UiForm.of("form-" + shortId(), null);
                    return f;
                });
    }

    private NodeMeta sectionMeta() {
        return new NodeMeta("section", "Section", "container",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.of("title", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING),
                        PropertyMeta.of("initialSection", PropertyKind.STRING),
                        PropertyMeta.enumOf("tabOverflow", List.of("WRAP", "MENU")),
                        PropertyMeta.of("collapseSummary", PropertyKind.STRING),
                        PropertyMeta.of("collapseOpen", PropertyKind.BOOLEAN)
                ),
                // sections holds {@link UiSectionEntry} children — a first-class
                // UiNode with its own id/title/href, plus a single content slot.
                List.of(
                        ChildrenMeta.of("sections", "section-entry")
                ),
                () -> UiSection.of("section-" + shortId(), "New section"));
    }

    private NodeMeta sectionEntryMeta() {
        return new NodeMeta("section-entry", "Tab", "container",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.required("title", PropertyKind.STRING),
                        PropertyMeta.of("href", PropertyKind.STRING),
                        PropertyMeta.of("icon", PropertyKind.STRING),
                        PropertyMeta.of("selectOnClick", PropertyKind.BOOLEAN),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(
                        // Single content slot for the panel body — same set
                        // of allowed children as a Stack / Page.node.
                        ChildrenMeta.single("content", COMPOSABLE)
                ),
                () -> {
                    var e = new ai.mindconnect.ui.model.UiSectionEntry();
                    e.setId("tab-" + shortId());
                    e.setTitle("New tab");
                    return e;
                });
    }

    private NodeMeta detailMeta() {
        return new NodeMeta("detail", "Detail", "container",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.of("title", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(
                        ChildrenMeta.of("fields",  "field"),
                        ChildrenMeta.of("actions", "action"),
                        ChildrenMeta.of("links",   "link")
                ),
                () -> UiDetail.of("detail-" + shortId(), null));
    }

    private NodeMeta listMeta() {
        return new NodeMeta("list", "List", "container",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.of("title", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(
                        ChildrenMeta.of("actions", "action")
                ),
                () -> {
                    var l = new UiList();
                    l.setId("list-" + shortId());
                    return l;
                });
    }

    private NodeMeta tableMeta() {
        return new NodeMeta("table", "Table", "container",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.of("title", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING),
                        PropertyMeta.of("selectedRowId", PropertyKind.STRING),
                        PropertyMeta.of("stackOnMobile", PropertyKind.BOOLEAN),
                        PropertyMeta.enumOf("selectMode",
                                List.of("NONE", "SINGLE", "MULTI"))
                ),
                List.of(
                        // columns + rows are first-class UiNodes (UiColumn /
                        // UiRow), so they show up in the tree like any other
                        // child and respond to add/delete/select.
                        ChildrenMeta.of("columns",    "column"),
                        ChildrenMeta.of("rows",       "row"),
                        ChildrenMeta.of("actions",    "action"),
                        ChildrenMeta.of("rowActions", "action")
                ),
                () -> {
                    var t = new UiTable();
                    t.setId("table-" + shortId());
                    return t;
                });
    }

    private NodeMeta columnMeta() {
        return new NodeMeta("column", "Column", "table",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.required("label", PropertyKind.STRING),
                        PropertyMeta.of("dataKey", PropertyKind.STRING),
                        PropertyMeta.of("sortable",   PropertyKind.BOOLEAN),
                        PropertyMeta.of("cssClass",   PropertyKind.STRING)
                ),
                List.of(
                        // Optional cellTemplate slot. When set, every row's
                        // cell for this column renders this node (with
                        // {dataKey} substituted per row) instead of the
                        // default plain-text dataKey lookup. Allowed types
                        // cover the common cell shapes: bare text, links,
                        // editable fields, action buttons, or a stack of any
                        // of the above for multi-element cells.
                        ChildrenMeta.single("cellTemplate",
                                "text", "link", "field", "action", "stack")
                ),
                () -> {
                    var key = "col-" + shortId();
                    return ai.mindconnect.ui.model.UiColumn.text(key, "Column");
                });
    }

    private NodeMeta textMeta() {
        return new NodeMeta("text", "Text", "display",
                List.of(
                        PropertyMeta.of("id", PropertyKind.STRING),
                        PropertyMeta.required("text", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(),
                () -> ai.mindconnect.ui.model.UiText.of("text-" + shortId(), "Text"));
    }

    private NodeMeta rowMeta() {
        return new NodeMeta("row", "Row", "table",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                        // .data lives in the JSON panel for now — a per-row
                        // map UI lands when we have field-by-field editing.
                ),
                List.of(),
                () -> {
                    var r = new ai.mindconnect.ui.model.UiRow();
                    r.setId("row-" + shortId());
                    return r;
                });
    }

    private NodeMeta appShellMeta() {
        return new NodeMeta("app-shell", "App shell", "chrome",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                // Three single slots. The renderer wires the header's burger to
                // the menu and suppresses the menu's own toggle, so neither has
                // to be configured here.
                List.of(
                        ChildrenMeta.single("header", "header"),
                        ChildrenMeta.single("menu", "menu"),
                        ChildrenMeta.single("content", COMPOSABLE)
                ),
                () -> {
                    var shell = UiAppShell.of("shell-" + shortId());
                    shell.header(UiHeader.of("Brand"));
                    shell.menu(UiMenu.of("nav-" + shortId(), "Navigation"));
                    return shell;
                });
    }

    private NodeMeta headerMeta() {
        return new NodeMeta("header", "Header", "chrome",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.required("brand", PropertyKind.STRING),
                        PropertyMeta.of("brandHref", PropertyKind.STRING),
                        PropertyMeta.of("menuToggle", PropertyKind.STRING),
                        PropertyMeta.enumOf("extrasOverflow", List.of("WRAP", "MENU")),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(
                        // Header extras are typically toggles / small forms /
                        // links; the model accepts any UiNode but page/header
                        // inside a header makes no sense and is excluded.
                        ChildrenMeta.of("extras",
                                "stack", "form", "section", "detail", "list", "table", "chart", "text", "field", "action", "link")
                ),
                () -> {
                    var h = UiHeader.of("Brand");
                    h.setId("header-" + shortId());
                    return h;
                });
    }

    private NodeMeta fieldMeta() {
        return new NodeMeta("field", "Field", "input",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.required("label", PropertyKind.STRING),
                        PropertyMeta.enumOf("fieldType",
                                List.of("TEXT", "TEXTAREA", "NUMBER", "CURRENCY", "PERCENT",
                                        "DATE", "DATETIME", "BOOLEAN",
                                        "SELECT", "MULTISELECT", "FILE", "REFERENCE")),
                        PropertyMeta.of("value", PropertyKind.STRING),
                        PropertyMeta.of("placeholder", PropertyKind.STRING),
                        PropertyMeta.of("icon", PropertyKind.STRING),
                        PropertyMeta.of("hint", PropertyKind.STRING),
                        PropertyMeta.of("editable", PropertyKind.BOOLEAN),
                        PropertyMeta.of("required", PropertyKind.BOOLEAN),
                        PropertyMeta.of("submitOnEnter", PropertyKind.BOOLEAN),
                        PropertyMeta.of("submitOnChange", PropertyKind.BOOLEAN),
                        PropertyMeta.of("min", PropertyKind.STRING),
                        PropertyMeta.of("max", PropertyKind.STRING),
                        PropertyMeta.of("step", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                // Options are a UiField.Option list rather than UiNode list — edited inline via the
                // property panel, not the tree. The MVP doesn't surface them as tree children.
                List.of(),
                () -> UiField.text("field-" + shortId(), "Label", null).asEditable());
    }

    private NodeMeta actionMeta() {
        return new NodeMeta("action", "Action", "input",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.required("label", PropertyKind.STRING),
                        PropertyMeta.enumOf("style",   List.of("PRIMARY", "SECONDARY", "DANGER")),
                        PropertyMeta.enumOf("appearance", List.of("BUTTON", "LINK", "ICON")),
                        PropertyMeta.of("enabled", PropertyKind.BOOLEAN),
                        PropertyMeta.of("loading", PropertyKind.BOOLEAN),
                        PropertyMeta.of("disabledReason", PropertyKind.STRING),
                        PropertyMeta.of("confirm", PropertyKind.STRING),
                        PropertyMeta.of("icon", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(),
                () -> UiAction.secondary("action-" + shortId(), "Action"));
    }

    private NodeMeta iconMeta() {
        return new NodeMeta("icon", "Icon", "display",
                List.of(
                        PropertyMeta.of("id", PropertyKind.STRING),
                        PropertyMeta.required("name", PropertyKind.STRING),
                        PropertyMeta.of("title", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(),
                () -> ai.mindconnect.ui.model.UiIcon.of("icon-" + shortId(), "star"));
    }

    private NodeMeta spinnerMeta() {
        return new NodeMeta("spinner", "Spinner", "display",
                List.of(
                        PropertyMeta.of("id", PropertyKind.STRING),
                        PropertyMeta.enumOf("size", List.of("SM", "MD", "LG")),
                        PropertyMeta.of("label", PropertyKind.STRING),
                        PropertyMeta.of("title", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(),
                () -> {
                    var s = ai.mindconnect.ui.model.UiSpinner.of("Loading…");
                    s.setId("spinner-" + shortId());
                    return s;
                });
    }

    private NodeMeta progressMeta() {
        return new NodeMeta("progress", "Progress", "display",
                List.of(
                        PropertyMeta.of("id", PropertyKind.STRING),
                        PropertyMeta.of("value", PropertyKind.NUMBER),
                        PropertyMeta.of("max", PropertyKind.NUMBER),
                        PropertyMeta.enumOf("variant", List.of("BAR", "CIRCLE")),
                        PropertyMeta.enumOf("status", List.of("NORMAL", "SUCCESS", "WARNING", "ERROR")),
                        PropertyMeta.of("showValue", PropertyKind.BOOLEAN),
                        PropertyMeta.of("title", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(),
                () -> {
                    var p = ai.mindconnect.ui.model.UiProgress.of(60);
                    p.setId("progress-" + shortId());
                    return p;
                });
    }

    private NodeMeta menuMeta() {
        return new NodeMeta("menu", "Menu", "chrome",
                List.of(
                        PropertyMeta.of("id", PropertyKind.STRING),
                        PropertyMeta.of("title", PropertyKind.STRING),
                        PropertyMeta.enumOf("state", List.of("EXPANDED", "RAIL", "HIDDEN")),
                        PropertyMeta.enumOf("mode", List.of("PUSH", "OVERLAY", "RESPONSIVE")),
                        PropertyMeta.enumOf("side", List.of("LEFT", "RIGHT")),
                        PropertyMeta.of("toggle", PropertyKind.BOOLEAN),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(
                        ChildrenMeta.of("items", "menu-item")
                ),
                () -> {
                    var m = ai.mindconnect.ui.model.UiMenu.of();
                    m.setId("menu-" + shortId());
                    m.setTitle("Menu");
                    return m;
                });
    }

    private NodeMeta menuItemMeta() {
        return new NodeMeta("menu-item", "Menu item", "chrome",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.required("label", PropertyKind.STRING),
                        PropertyMeta.of("icon", PropertyKind.STRING),
                        PropertyMeta.of("href", PropertyKind.STRING),
                        PropertyMeta.of("confirm", PropertyKind.STRING),
                        PropertyMeta.of("badge", PropertyKind.STRING),
                        PropertyMeta.of("selected", PropertyKind.BOOLEAN),
                        PropertyMeta.of("open", PropertyKind.BOOLEAN),
                        PropertyMeta.of("danger", PropertyKind.BOOLEAN),
                        PropertyMeta.of("divider", PropertyKind.BOOLEAN),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(
                        // Nested entries — a menu item can hold a submenu.
                        ChildrenMeta.of("children", "menu-item")
                ),
                () -> ai.mindconnect.ui.model.UiMenuItem.of("mi-" + shortId(), "Item"));
    }

    private NodeMeta menuButtonMeta() {
        return new NodeMeta("menu-button", "Menu button", "chrome",
                List.of(
                        PropertyMeta.of("id", PropertyKind.STRING),
                        PropertyMeta.of("title", PropertyKind.STRING),
                        PropertyMeta.of("icon", PropertyKind.STRING),
                        PropertyMeta.of("label", PropertyKind.STRING),
                        PropertyMeta.enumOf("variant", List.of("ICON", "BUTTON")),
                        PropertyMeta.enumOf("align", List.of("START", "END")),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(
                        ChildrenMeta.of("items", "menu-item")
                ),
                () -> {
                    var b = ai.mindconnect.ui.model.UiMenuButton.of();
                    b.setId("menu-button-" + shortId());
                    return b;
                });
    }

    private NodeMeta dialogMeta() {
        return new NodeMeta("dialog", "Dialog", "overlay",
                List.of(
                        PropertyMeta.of("id", PropertyKind.STRING),
                        PropertyMeta.of("title", PropertyKind.STRING),
                        PropertyMeta.of("closeHref", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                // A dialog carries a single body node — same allowed set as a
                // page body. Only offered in the page's "dialogs" slot.
                List.of(
                        ChildrenMeta.single("node", COMPOSABLE)
                ),
                () -> {
                    var d = new UiDialog();
                    d.setId("dialog-" + shortId());
                    d.setTitle("Dialog");
                    return d;
                });
    }

    private NodeMeta linkMeta() {
        return new NodeMeta("link", "Link", "input",
                List.of(
                        PropertyMeta.required("id", PropertyKind.STRING),
                        PropertyMeta.required("href", PropertyKind.STRING),
                        PropertyMeta.required("label", PropertyKind.STRING),
                        PropertyMeta.of("icon", PropertyKind.STRING),
                        PropertyMeta.of("cssClass", PropertyKind.STRING)
                ),
                List.of(),
                () -> UiLink.of("link-" + shortId(), "#", "Link"));
    }

    /** 6-char base36 nonce used in default ids — keeps the picker output collision-free without UUID noise. */
    private static String shortId() {
        return Long.toString(Math.abs(System.nanoTime()), 36).substring(0, 6);
    }
}
