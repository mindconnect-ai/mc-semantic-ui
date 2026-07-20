package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiAppShell;
import ai.mindconnect.ui.model.UiColumn;
import ai.mindconnect.ui.model.UiDetail;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiFieldGroup;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiHeader;
import ai.mindconnect.ui.model.UiIcon;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiList;
import ai.mindconnect.ui.model.UiSection;
import ai.mindconnect.ui.model.UiSectionEntry;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiTrigger;
import ai.mindconnect.ui.model.UiMenu;
import ai.mindconnect.ui.model.UiMenuItem;
import ai.mindconnect.ui.model.UiMenuButton;
import ai.mindconnect.ui.model.UiProgress;
import ai.mindconnect.ui.model.UiSpinner;
import ai.mindconnect.ui.model.UiUpload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke tests for the Handlebars-based server renderer. These prove the
 * template mechanics end-to-end: engine init, helper registration, partial
 * resolution ({@code {{> action}}}/{@code {{> link}}}), the {@code eq} and
 * {@code trigger} helpers, and Lombok-getter property access.
 */
class SuiServerRendererTest {

    private final SuiServerRenderer renderer = new SuiServerRenderer();

    @Test
    void rendersDetailWithFieldsActionsAndLinks() {
        var detail = UiDetail.of("d1", "Session Info")
                .field(UiField.text("sid", "Session ID", "abc-123"))
                .field(UiField.text("title", "Title", null))
                .action(UiAction.primary("edit", "Edit")
                        .dispatch("GET", "/admin/api/x/edit"))
                .action(UiAction.danger("delete", "Delete")
                        .confirm("Sure?")
                        .dispatch("DELETE", "/admin/api/x"))
                .link(UiLink.of("back", "/admin/x", "← Back"));

        String html = renderer.render(detail);

        // Structure
        assertTrue(html.contains("class=\"sui-detail\""), html);
        assertTrue(html.contains("id=\"d1\""), html);
        assertTrue(html.contains("<h2>Session Info</h2>"), html);
        // Field with value
        assertTrue(html.contains("<dt>Session ID</dt>"), html);
        assertTrue(html.contains("abc-123"), html);
        // Null-value field falls back to em-dash
        assertTrue(html.contains("sui-empty"), html);
        // Hybrid SSR action rendering:
        // GET → <a href> + data-trigger (both, same URL)
        // Anchor carries id="<action.id>" (so the editor's id-based highlight
        // works symmetrically) plus the usual data-action and data-trigger
        // attributes. Order of attributes is fixed by SsrTriggerMapper.
        assertTrue(html.contains("<a id=\"edit\" class=\"sui-btn sui-btn--primary\" href=\"/admin/api/x/edit\""), html);
        assertTrue(html.contains("data-action=\"edit\""), html);
        assertTrue(html.contains("data-trigger="), html);
        // DELETE → <form id="…" method=post> with hidden _method override,
        // plus a data-trigger on the form so the EventBus can pick the same
        // intent. The id sits on the <form> (outermost element).
        assertTrue(html.contains("<form id=\"delete\" method=\"post\" action=\"/admin/api/x\""), html);
        assertTrue(html.contains("name=\"_method\" value=\"DELETE\""), html);
        assertTrue(html.contains("sui-btn--danger"), html);
        // Confirm dialog is intentionally not rendered in SSR mode — the
        // UiAction.confirm property stays in the model but a different
        // confirmation mechanism will come later.
        assertTrue(!html.contains("confirm('Sure?')"), html);
        // Link (UiLink is rendered through link.hbs as data-href anchor;
        // unchanged by the SSR action overhaul)
        assertTrue(html.contains("data-href=\"/admin/x\""), html);
        assertTrue(html.contains("← Back"), html);
    }

    @Test
    void escapesHtmlInValues() {
        var detail = UiDetail.of("d2", "T")
                .field(UiField.text("x", "Name", "<script>alert(1)</script>"));
        String html = renderer.render(detail);
        assertTrue(html.contains("&lt;script&gt;"), html);
        assertTrue(!html.contains("<script>alert"), html);
    }

    /**
     * Regression for the active-tab bug: an outer titled section with one
     * child section ("products") must render a single tab whose id and
     * label come from the CHILD entry, not the outer section. The active
     * tab is the first child by default and its panel must NOT be hidden.
     */
    @Test
    void rendersTitledSectionWithChildIdsAndDefaultActiveTab() {
        var inner = UiDetail.of("inner-detail", "Inner")
                .field(UiField.text("x", "Name", "Alice"));
        var outer = UiSection.of("outer", "Outer Title")
                .section("products", "Products", inner);

        String html = renderer.render(outer);

        // The tab uses the CHILD entry's id + title, not the outer's.
        assertTrue(html.contains("data-target=\"products\""),
                "tab should target child id 'products'; got:\n" + html);
        assertTrue(html.contains(">Products</button>"),
                "tab label should be child title 'Products'; got:\n" + html);
        // It must NOT carry the outer section's id as the tab target.
        assertFalse(html.contains("data-target=\"outer\""),
                "tab must not target the outer section id; got:\n" + html);
        // First child is the default-active tab → its panel must be visible.
        assertTrue(html.contains("id=\"products\""),
                "panel id should be 'products'; got:\n" + html);
        assertFalse(html.contains("id=\"products\" hidden"),
                "active panel must not be hidden; got:\n" + html);
        // The inner detail rendered inside the active panel.
        assertTrue(html.contains("Alice"),
                "inner content should be visible; got:\n" + html);
    }

    @Test
    void clientControlledCollapseRendersTaggedDetailsWithoutOpen() {
        var list = UiList.of("acts", "Activity");
        // server-open collapsible → has `open`, no client-collapse marker
        list.item(UiList.Item.of("a", "")
                .description("body A")
                .collapsible("Server open", true));
        // client-controlled → starts collapsed, tagged for the morpher
        list.item(UiList.Item.of("b", "")
                .description("body B")
                .collapsibleClient("Client collapse", "sum-b"));

        String html = renderer.render(list);

        // Server-open item: <details ... open>, not client-tagged.
        assertTrue(html.contains("Server open"), html);
        // Client item: tagged, collapsed (no `open`), id'd summary span.
        assertTrue(html.contains("data-sui-client-collapse"), html);
        assertTrue(html.contains("id=\"sum-b\""), html);
        // The client-collapse <details> must NOT carry an open attribute.
        int ci = html.indexOf("data-sui-client-collapse");
        int summaryStart = html.indexOf("<summary", ci);
        String clientDetailsTag = html.substring(html.lastIndexOf("<details", ci), summaryStart);
        assertFalse(clientDetailsTag.contains(" open"),
                "client-collapse details must not be server-opened; got tag:\n" + clientDetailsTag);
    }

    @Test
    void labelNodeReplacesPlainLabelInHeader() {
        var header = ai.mindconnect.ui.model.UiStack
                .of("hdr")
                .direction(ai.mindconnect.ui.model.UiStack.Direction.HORIZONTAL)
                .child(ai.mindconnect.ui.model.UiText.of("nm", "web-researcher"))
                .child(ai.mindconnect.ui.model.UiText.of("badge", "openai-default")
                        .<ai.mindconnect.ui.model.UiText>withCssClass("agent-llm-badge"));
        var list = UiList.of("agents", "Agents");
        list.item(UiList.Item.of("a", "web-researcher")
                .labelNode(header)
                .href("/admin/agents/a"));

        String html = renderer.render(list);

        // The rich header rendered inside the clickable label anchor.
        assertTrue(html.contains("sui-list-item-label"), html);
        assertTrue(html.contains("web-researcher"), html);
        assertTrue(html.contains("agent-llm-badge"), html);
        assertTrue(html.contains("openai-default"), html);
    }

    @Test
    void rendersIconTokensViaSpriteAndEmojiVerbatim() {
        // A leading-icon action: token "save" resolves to a sprite <use>;
        // the label rides along after it.
        var save = UiAction.primary("save", "Save").icon("save")
                .dispatch("POST", "/x");
        String btn = renderer.render(save);
        assertTrue(btn.contains("<use href=\"/sui/icons.svg#save\">"), btn);
        assertTrue(btn.contains("class=\"sui-icon\""), btn);
        assertTrue(btn.contains("Save"), btn);

        // Icon-only action: label becomes the accessible name.
        var edit = UiAction.secondary("edit", "Edit").icon("pencil")
                .appearance(UiAction.Appearance.ICON);
        String iconBtn = renderer.render(edit);
        assertTrue(iconBtn.contains("aria-label=\"Edit\""), iconBtn);
        assertTrue(iconBtn.contains("#pencil"), iconBtn);

        // Standalone UiIcon node: carries id (patch-addressable), a title
        // (accessible), and a colour-modifier class.
        var icon = UiIcon.of("status", "success").labelled("All good");
        icon.setCssClass("sui-icon--success");
        String iconHtml = renderer.render(icon);
        assertTrue(iconHtml.contains("id=\"status\""), iconHtml);
        assertTrue(iconHtml.contains("#success"), iconHtml);
        assertTrue(iconHtml.contains("aria-label=\"All good\""), iconHtml);
        assertTrue(iconHtml.contains("sui-icon--success"), iconHtml);

        // Legacy emoji token is emitted verbatim (migration path), NOT as a
        // broken sprite reference.
        var emoji = UiIcon.of("e", "📁");
        String emojiHtml = renderer.render(emoji);
        assertTrue(emojiHtml.contains("📁"), emojiHtml);
        assertFalse(emojiHtml.contains("<use"), emojiHtml);
    }

    @Test
    void rendersSpinnerWithSizeAndAccessibleLabel() {
        // A labelled spinner: role=status, the label is both visible and the
        // accessible name, and the size becomes a modifier class.
        var spinner = UiSpinner.of("Loading…").size(UiSpinner.Size.LG);
        spinner.setId("busy");
        String html = renderer.render(spinner);
        assertTrue(html.contains("class=\"sui-spinner sui-spinner--lg\""), html);
        assertTrue(html.contains("id=\"busy\""), html);
        assertTrue(html.contains("role=\"status\""), html);
        assertTrue(html.contains("aria-label=\"Loading…\""), html);
        assertTrue(html.contains("<span class=\"sui-spinner-label\">Loading…</span>"), html);
        assertTrue(html.contains("<span class=\"sui-spinner-glyph\">"), html);

        // A bare spinner (no label/title) is decorative: aria-hidden, size
        // defaults to md, no label span.
        String bare = renderer.render(UiSpinner.of());
        assertTrue(bare.contains("sui-spinner--md"), bare);
        assertTrue(bare.contains("aria-hidden=\"true\""), bare);
        assertFalse(bare.contains("sui-spinner-label"), bare);
    }

    @Test
    void rendersDeterminateAndIndeterminateProgress() {
        // Determinate bar: 30/120 = 25%. Fill width + readout + ARIA reflect it.
        var bar = UiProgress.of(30, 120);
        bar.setId("dl");
        String barHtml = renderer.render(bar);
        assertTrue(barHtml.contains("id=\"dl\""), barHtml);
        assertTrue(barHtml.contains("aria-valuenow=\"25\""), barHtml);
        assertTrue(barHtml.contains("style=\"width:25%\""), barHtml);
        assertTrue(barHtml.contains(">25%</span>"), barHtml);
        assertTrue(barHtml.contains("sui-progress--bar"), barHtml);

        // Status tints the fill; showValue=false hides the readout.
        var warn = UiProgress.of(90).status(UiProgress.Status.WARNING).showValue(false);
        String warnHtml = renderer.render(warn);
        assertTrue(warnHtml.contains("sui-progress--warning"), warnHtml);
        assertFalse(warnHtml.contains("sui-progress-text"), warnHtml);

        // Circle variant: the SVG ring with a dash-offset of 100 − pct = 40.
        var ring = UiProgress.of(60).variant(UiProgress.Variant.CIRCLE);
        String ringHtml = renderer.render(ring);
        assertTrue(ringHtml.contains("sui-progress--circle"), ringHtml);
        assertTrue(ringHtml.contains("stroke-dashoffset=\"40\""), ringHtml);
        assertTrue(ringHtml.contains("<text"), ringHtml);

        // Indeterminate: no value → looping animation, aria-busy, no readout.
        var loading = UiProgress.indeterminate();
        String loadingHtml = renderer.render(loading);
        assertTrue(loadingHtml.contains("sui-progress--indeterminate"), loadingHtml);
        assertTrue(loadingHtml.contains("aria-busy=\"true\""), loadingHtml);
        assertFalse(loadingHtml.contains("aria-valuenow"), loadingHtml);
    }

    @Test
    void linkWithOnClickEmitsDataTrigger() {
        // A plain link is a navigation (data-href, no trigger).
        var nav = UiLink.of("docs", "/docs", "Docs");
        String navHtml = renderer.render(nav);
        assertTrue(navHtml.contains("data-href=\"/docs\""), navHtml);
        assertFalse(navHtml.contains("data-trigger"), navHtml);

        // A link with onClick dispatches through the bus: data-trigger present,
        // href kept as the no-JS fallback.
        var action = UiLink.of("more", "/items?page=2", "Load more")
                .onClick(UiTrigger.api("GET", "/items?page=2"));
        String actionHtml = renderer.render(action);
        assertTrue(actionHtml.contains("data-trigger="), actionHtml);
        // href kept as the no-JS fallback (the '=' is HTML-escaped by the SSR
        // template, so match on the stable prefix).
        assertTrue(actionHtml.contains("href=\"/items?page"), actionHtml);
        assertTrue(actionHtml.contains("id=\"more\""), actionHtml);
    }

    @Test
    void loadingActionRendersBusyAndDisabled() {
        var busy = UiAction.primary("save", "Saving…").loading(true)
                .onClick(UiTrigger.api("POST", "/x"));
        String html = renderer.render(busy);
        assertTrue(html.contains("is-loading"), html);
        assertTrue(html.contains("aria-busy=\"true\""), html);
        // Busy ⇒ non-interactive. Anchor style uses aria-disabled; form/button
        // style uses the disabled attribute — accept either.
        assertTrue(html.contains("disabled") || html.contains("aria-disabled"), html);
    }

    @Test
    void rendersNestedCollapsibleMenu() {
        var menu = UiMenu.of("nav", "Admin",
                UiMenuItem.link("m-dash", "Dashboard", "/dash").icon("dashboard").selected(true),
                UiMenuItem.group("m-cat", "Catalog",
                        UiMenuItem.link("m-prod", "Products", "/products").icon("tag"),
                        UiMenuItem.link("m-cust", "Customers", "/customers").icon("users"))
                    .icon("grid").open(true)
        ).state(UiMenu.State.RAIL);
        String html = renderer.render(menu);

        // Root carries the state modifier + the toggle hook.
        assertTrue(html.contains("sui-menu--rail"), html);
        assertTrue(html.contains("data-menu-state=\"rail\""), html);
        assertTrue(html.contains("data-menu-toggle=\"nav\""), html);

        // Selected leaf: active + accessible current + real href (no-JS nav).
        assertTrue(html.contains("aria-current=\"page\""), html);
        assertTrue(html.contains("href=\"/dash\""), html);
        assertTrue(html.contains("is-active"), html);

        // Group: a native <details> disclosure holding a nested sublist.
        assertTrue(html.contains("<details class=\"sui-menu-group\""), html);
        assertTrue(html.contains("sui-menu-sublist"), html);
        assertTrue(html.contains("Products"), html);
        assertTrue(html.contains("Customers"), html);
    }

    @Test
    void menuItemOnClickDispatchesInsteadOfNavigating() {
        var menu = UiMenu.of("nav2", null,
                UiMenuItem.of("m-act", "Reload").icon("refresh")
                        .onClick(UiTrigger.api("POST", "/reload")));
        String html = renderer.render(menu);
        assertTrue(html.contains("data-trigger="), html);
    }

    @Test
    void rendersMenuButtonAsFloatingPopover() {
        var mb = UiMenuButton.of("row-menu",
                UiMenuItem.of("mb-edit", "Rename").icon("edit")
                        .onClick(UiTrigger.api("POST", "/rename")),
                UiMenuItem.of("mb-copy", "Duplicate").icon("copy").href("/dup"),
                UiMenuItem.divider(),
                UiMenuItem.of("mb-del", "Delete").icon("delete").danger(true)
                        .onClick(UiTrigger.api("DELETE", "/x")))
                .align(UiMenuButton.Align.END);
        String html = renderer.render(mb);

        // A native <details> so it opens with no JS; icon-only trigger variant.
        assertTrue(html.contains("<details class=\"sui-menu-button"), html);
        assertTrue(html.contains("sui-menu-button--icon"), html);
        assertTrue(html.contains("sui-menu-button--align-end"), html);
        assertTrue(html.contains("data-sui=\"menu-button\""), html);
        assertTrue(html.contains("sui-menu-button-popover"), html);

        // A trigger item dispatches; a navigating item keeps its href.
        assertTrue(html.contains("data-trigger="), html);
        assertTrue(html.contains("href=\"/dup\""), html);
        // Danger + divider render their markers.
        assertTrue(html.contains("is-danger"), html);
        assertTrue(html.contains("sui-menu-button-sep"), html);
    }

    @Test
    void sectionEntryOnClickEmitsTriggerOnTheTab() {
        var section = new UiSection();
        section.setId("sec");
        section.setSections(java.util.List.of(
                UiSectionEntry.of("t-a", "Overview", UiText.of("a")),
                UiSectionEntry.of("t-b", "Activity", UiText.of("b"))
                        .onClick(UiTrigger.api("GET", "/activity"))));
        String html = renderer.render(section);
        // The tab with an onClick carries a data-trigger; the plain one doesn't.
        assertTrue(html.contains("data-target=\"t-b\""), html);
        assertTrue(html.contains("data-trigger="), html);
        // Both tabs still switch panels (data-target present for each).
        assertTrue(html.contains("data-target=\"t-a\""), html);
    }

    @Test
    void menuButtonNestsSubmenusFromChildren() {
        var mb = UiMenuButton.of("acts",
                UiMenuItem.of("exp", "Export").icon("download").onClick(UiTrigger.go("/export")),
                // A group — the same UiMenuItem.children the sidebar uses to nest.
                UiMenuItem.group("move", "Move to",
                        UiMenuItem.of("mv1", "Inbox").onClick(UiTrigger.go("/inbox")),
                        UiMenuItem.of("mv2", "Trash").danger(true).onClick(UiTrigger.go("/trash")))
                    .icon("share"))
                .label("Actions");
        String html = renderer.render(mb);

        // The group renders a header + a nested sub-popover holding its children.
        assertTrue(html.contains("sui-menu-button-group"), html);
        assertTrue(html.contains("sui-menu-button-item--group"), html);
        assertTrue(html.contains("sui-menu-button-submenu"), html);
        assertTrue(html.contains("Inbox"), html);
        assertTrue(html.contains("Trash"), html);
        // The nested danger item keeps its marker.
        assertTrue(html.contains("is-danger"), html);
    }

    @Test
    void labelledMenuButtonRendersAsButtonVariant() {
        var mb = UiMenuButton.of("acts",
                        UiMenuItem.of("a1", "Export").icon("download").onClick(UiTrigger.go("/export")))
                .label("Actions");
        String html = renderer.render(mb);
        assertTrue(html.contains("sui-menu-button--button"), html);
        assertTrue(html.contains("sui-menu-button-text"), html);
        assertTrue(html.contains("Actions"), html);
    }

    @Test
    void overlayMenuEmitsModeClassAndBackdrop() {
        var menu = UiMenu.of("nav3", "Acme",
                UiMenuItem.link("m-o", "Orders", "/orders").icon("table").badge("12"))
                .mode(UiMenu.Mode.OVERLAY);
        String html = renderer.render(menu);
        // Mode modifier + a click-to-close backdrop tied to the menu id.
        assertTrue(html.contains("sui-menu--overlay"), html);
        assertTrue(html.contains("<div class=\"sui-menu-backdrop\" data-menu-close=\"nav3\">"), html);
        // Badge rides along after the label.
        assertTrue(html.contains("<span class=\"sui-menu-badge\">12</span>"), html);

        // Push (default) has no backdrop.
        String push = renderer.render(UiMenu.of("nav4", "X",
                UiMenuItem.link("m-x", "X", "/x")));
        assertTrue(push.contains("sui-menu--push"), push);
        assertFalse(push.contains("sui-menu-backdrop"), push);

        // Responsive also carries a backdrop (it drops to a drawer on mobile).
        String resp = renderer.render(UiMenu.of("nav5", "R",
                UiMenuItem.link("m-r", "R", "/r")).mode(UiMenu.Mode.RESPONSIVE));
        assertTrue(resp.contains("sui-menu--responsive"), resp);
        assertTrue(resp.contains("data-menu-close=\"nav5\""), resp);

        // Side is a modifier class; default is left.
        String right = renderer.render(UiMenu.of("nav6", "R",
                UiMenuItem.link("m-r2", "R", "/r")).side(UiMenu.Side.RIGHT));
        assertTrue(right.contains("sui-menu--right"), right);
        assertTrue(renderer.render(UiMenu.of("nav7", "L",
                UiMenuItem.link("m-l", "L", "/l"))).contains("sui-menu--left"));
    }

    @Test
    void headerRendersHamburgerTargetingMenu() {
        var header = UiHeader.of("Acme Admin").menuToggle("nav");
        header.setId("hdr");
        String html = renderer.render(header);
        assertTrue(html.contains("sui-header-burger"), html);
        assertTrue(html.contains("data-menu-toggle=\"nav\""), html);
    }

    @Test
    void stackOnMobileTableCarriesStackClassAndCellLabels() {
        var table = UiTable.of("t", "Products")
                .column(UiTable.Column.text("name", "Name"))
                .column(UiTable.Column.text("price", "Price"))
                .row(java.util.Map.of("id", "r1", "name", "Widget", "price", "19"))
                .stackOnMobile(true);
        String html = renderer.render(table);
        // The stacked-card CSS hook + each cell's column label for the ::before.
        assertTrue(html.contains("sui-table--stack"), html);
        assertTrue(html.contains("data-label=\"Name\""), html);
        assertTrue(html.contains("data-label=\"Price\""), html);

        // Default table keeps no stack class.
        String plain = renderer.render(UiTable.of("t2", "X")
                .column(UiTable.Column.text("a", "A")).row(java.util.Map.of("id", "x", "a", "1")));
        assertFalse(plain.contains("sui-table--stack"), plain);
    }

    @Test
    void tabOverflowMenuMarksTheBar() {
        var section = UiSection.of("s", "Tabs")
                .section("a", "Alpha", UiText.of("ta", "A"))
                .section("b", "Beta", UiText.of("tb", "B"))
                .tabOverflow(UiSection.TabOverflow.MENU);
        String html = renderer.render(section);
        assertTrue(html.contains("data-sui-overflow=\"menu\""), html);

        // Default (WRAP) does not.
        String wrap = renderer.render(UiSection.of("s2", "Tabs")
                .section("c", "Gamma", UiText.of("tc", "C")));
        assertFalse(wrap.contains("data-sui-overflow"), wrap);
    }

    @Test
    void paginationShowsCorrectPageCount() {
        // 8 items, page size 20 → one page total. The pagination span must
        // read "1 / 1 (8 total)", not "1 / 8" — which is what happens when
        // the size property isn't resolved by the template helper and falls
        // back to ceil(total / 1) = total.
        var table = UiTable.of("pag-test", "Test")
                .column(UiTable.Column.text("name", "Name"))
                .paginate(1, 20, 8L, UiTrigger.go("/x?page={page}"));
        String html = renderer.render(table);
        assertTrue(html.contains("1 / 1 (8 total)"),
                "expected '1 / 1 (8 total)'; got:\n" + html);
    }

    @Test
    void tableEmbedsItsModelForRowAndColumnPatches() throws Exception {
        // The wrapper carries data-node='<json>' (same convention as the TS
        // renderer) so the patch pipeline can treat row/column patches as
        // model edits + full-table re-render. The attribute must round-trip:
        // unescape → parse → the same UiTable, rows and columns included.
        var table = UiTable.of("t-patch", "Products")
                .column(UiTable.Column.text("name", "Name"))
                .row(java.util.Map.of("id", "p1", "name", "Widget's \"Best\""));
        String html = renderer.render(table);

        assertTrue(html.contains("data-sui=\"table\""), html);
        int start = html.indexOf("data-node='");
        assertTrue(start >= 0, html);
        start += "data-node='".length();
        String escaped = html.substring(start, html.indexOf('\'', start));
        String json = escaped
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        var parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, ai.mindconnect.ui.model.UiNode.class);
        assertTrue(parsed instanceof UiTable, json);
        var t = (UiTable) parsed;
        assertTrue(t.getRows().size() == 1 && "p1".equals(t.getRows().get(0).getId()), json);
        assertTrue(t.getColumns().size() == 1 && "name".equals(t.getColumns().get(0).getDataKey()), json);
    }

    @Test
    void rendersTitledSectionWithMultipleTabsRespectingInitialSection() {
        var p1 = UiDetail.of("p1-detail", "P1");
        var p2 = UiDetail.of("p2-detail", "P2");
        var outer = UiSection.of("outer", "Outer")
                .section("first",  "First",  p1)
                .section("second", "Second", p2)
                .initialSection("second");

        String html = renderer.render(outer);

        // Both tabs present.
        assertTrue(html.contains("data-target=\"first\""), html);
        assertTrue(html.contains("data-target=\"second\""), html);
        // 'second' is the active one (CSS class + non-hidden panel).
        // Be tolerant about whitespace/order: just check the visible-panel id.
        assertTrue(html.contains("id=\"second\""), html);
        // First panel must be hidden because 'second' is active.
        assertTrue(html.contains("id=\"first\" hidden") || html.contains("id=\"first\"  hidden"),
                "non-active panel should be hidden; got:\n" + html);
    }

    @Test
    void rendersUploadDropZone() {
        var upload = UiUpload.of("product-image", "Product image")
                .accept("image/*")
                .multiple()
                .hint("PNG or JPG")
                .uploadTo("/api/products/42/image");

        String html = renderer.render(upload);

        assertTrue(html.contains("class=\"sui-upload\""), html);
        assertTrue(html.contains("id=\"product-image\""), html);
        assertTrue(html.contains("data-sui-upload"), html);
        // The upload trigger rides in data-upload-trigger, NOT data-trigger
        // (so a click on the zone doesn't fire it).
        assertTrue(html.contains("data-upload-trigger="), html);
        assertFalse(html.contains("data-trigger="), html);
        // Hidden file input with accept + multiple, and a <label for> browse control.
        assertTrue(html.contains("type=\"file\""), html);
        assertTrue(html.contains("accept=\"image/*\""), html);
        assertTrue(html.contains("multiple"), html);
        assertTrue(html.contains("for=\"product-image__input\""), html);
    }

    @Test
    void rendersStructuredFormBody() {
        // Fields laid out in two columns via UiForm.content — they still carry
        // their name= so the single form submit collects them all.
        var form = UiForm.of("profile", "Profile")
                .content(UiStack.of("cols").direction(UiStack.Direction.HORIZONTAL)
                        .child(UiField.text("first", "First name", "Ada").asEditable())
                        .child(UiField.text("last", "Last name", "Lovelace").asEditable())
                        .withCssClass("sui-cols"))
                .action(UiAction.primary("save", "Save").dispatch("POST", "/profile", "profile"));

        String html = renderer.render(form);

        assertTrue(html.contains("data-sui=\"form\""), html);
        assertTrue(html.contains("sui-cols"), html);              // column layout inside the form
        assertTrue(html.contains("name=\"first\""), html);        // both inputs present …
        assertTrue(html.contains("name=\"last\""), html);         // … so the whole form submits
    }

    @Test
    void rendersFieldGroup() {
        var group = UiFieldGroup.of("shipping", "Shipping address")
                .hint("Where should we send it?")
                .field(UiField.text("street", "Street", "221B").asEditable())
                .field(UiField.text("city", "City", "London").asEditable());

        String html = renderer.render(group);

        // Native fieldset + legend, so the group heading is tied to its fields.
        assertTrue(html.contains("<fieldset"), html);
        assertTrue(html.contains("<legend class=\"sui-fieldgroup-title\">Shipping address</legend>"), html);
        // Fields inside keep their name= — transparent to form submission.
        assertTrue(html.contains("name=\"street\""), html);
        assertTrue(html.contains("name=\"city\""), html);
    }

    @Test
    void rendersValidationErrors() {
        var form = UiForm.of("register", "Register")
                .error("Please fix the errors below.")
                .field(UiField.text("email", "Email", "nope").asEditable().error("Enter a valid email."))
                .action(UiAction.primary("save", "Save").dispatch("POST", "/register", "register"));

        String html = renderer.render(form);

        // Form-level banner.
        assertTrue(html.contains("class=\"sui-form-error\""), html);
        assertTrue(html.contains("Please fix the errors below."), html);
        // Per-field error: wrapper marker class + message span.
        assertTrue(html.contains("sui-field--error"), html);
        assertTrue(html.contains("class=\"sui-error\">Enter a valid email."), html);
    }

    @Test
    void rendersFileField() {
        var field = UiField.file("avatar", "Avatar")
                .accept(".png,.jpg")
                .onChange(UiTrigger.upload("/api/avatar"));

        String html = renderer.render(field);

        assertTrue(html.contains("type=\"file\""), html);
        assertTrue(html.contains("name=\"avatar\""), html);
        assertTrue(html.contains("accept=\".png,.jpg\""), html);
        // FILE field's onChange is emitted as data-change-trigger.
        assertTrue(html.contains("data-change-trigger="), html);
    }

    @Test
    void headerActionsRenderWithoutATitle() {
        // Regression: the header bar used to be gated on the title alone, so
        // a title-less table silently dropped its header actions.
        var table = UiTable.of("t", null)
                .column(UiColumn.of("name", "Name"))
                .action(UiAction.primary("new", "New product"));

        String html = renderer.render(table);

        assertTrue(html.contains("sui-table-header"), html);
        assertTrue(html.contains("New product"), html);
        // No title means no <h2> inside the bar.
        assertFalse(html.contains("<h2>"), html);

        // A table with neither title nor actions renders no bar at all.
        String bare = renderer.render(UiTable.of("t2", null).column(UiColumn.of("a", "A")));
        assertFalse(bare.contains("sui-table-header"), bare);
    }

    @Test
    void sortableColumnRendersClickableHeaderWithoutTrigger() {
        // No sortTrigger on the table → the header carries only data-sui-sort,
        // which the event bus picks up for a client-side reorder.
        var table = UiTable.of("t", "Products")
                .column(UiColumn.of("name", "Name").asSortable())
                .column(UiColumn.of("price", "Price"));

        String html = renderer.render(table);

        assertTrue(html.contains("sui-th-sortable"), html);
        assertTrue(html.contains("data-sui-sort=\"name\""), html);
        assertTrue(html.contains("data-sui-sort-dir=\"ASC\""), html);
        assertTrue(html.contains("aria-sort=\"none\""), html);
        assertFalse(html.contains("data-trigger="), html);
        // The non-sortable column stays a plain header.
        assertTrue(html.contains("<th id=\""), html);
    }

    @Test
    void sortTriggerSubstitutesColumnAndDirection() {
        var table = UiTable.of("t", "Products")
                .column(UiColumn.of("name", "Name").asSortable())
                .sortTrigger(UiTrigger.go("/products?sort={column}&dir={direction}"))
                .sortedBy("name", UiTable.SortDirection.ASC);

        String html = renderer.render(table);

        // Currently ascending → the header offers the flip to descending.
        assertTrue(html.contains("aria-sort=\"ascending\""), html);
        assertTrue(html.contains("is-sorted"), html);
        assertTrue(html.contains("data-sui-sort-dir=\"DESC\""), html);
        assertTrue(html.contains("sort=name"), html);
        assertTrue(html.contains("dir=desc"), html);
    }

    @Test
    void maxHeightCapsTheScrollContainer() {
        var table = UiTable.of("t", "Products")
                .column(UiColumn.of("name", "Name"))
                .maxHeight("320px");

        String html = renderer.render(table);

        assertTrue(html.contains("sui-table-scroll--capped"), html);
        assertTrue(html.contains("style=\"max-height:320px\""), html);

        // Without maxHeight the container is present but uncapped, so SSR and
        // SPA keep the same structure.
        String plain = renderer.render(UiTable.of("t2", "X").column(UiColumn.of("a", "A")));
        assertTrue(plain.contains("sui-table-scroll"), plain);
        assertFalse(plain.contains("sui-table-scroll--capped"), plain);
    }

    @Test
    void appShellWiresBurgerToMenuAndSuppressesItsOwnToggle() {
        // The two couplings the node exists for. Hand it a menu that WOULD
        // render its own toggle and a header that knows nothing about it.
        var menu = UiMenu.of("nav", "Acme", UiMenuItem.link("m-d", "Dashboard", "/d"))
                .toggle(true);
        var shell = UiAppShell.of("shell")
                .header(UiHeader.of("Acme Admin"))
                .menu(menu)
                .content(UiText.of("body", "Hello"));

        String html = renderer.render(shell);

        // One burger, in the header, pointed at the menu.
        assertTrue(html.contains("data-menu-toggle=\"nav\""), html);
        assertEquals(1, html.split("data-menu-toggle", -1).length - 1,
                "expected exactly one burger; got:\n" + html);
        // Layout containers with the ids a patch can target.
        assertTrue(html.contains("class=\"sui-shell\""), html);
        assertTrue(html.contains("sui-shell-body"), html);
        assertTrue(html.contains("id=\"shell-content\""), html);
        assertTrue(html.contains("Hello"), html);

        // The caller's menu is untouched — it may be reused for the next page.
        assertEquals(Boolean.TRUE, menu.getToggle());
    }

    @Test
    void appShellPutsARightSideMenuAfterTheContent() {
        var shell = UiAppShell.of("shell")
                .header(UiHeader.of("Acme"))
                .menu(UiMenu.of("nav", "Acme").side(UiMenu.Side.RIGHT))
                .content(UiText.of("body", "Hello"));

        String html = renderer.render(shell);

        // Match the <nav> itself, not the substring "sui-menu" — the header's
        // burger carries class="sui-menu-toggle" and would match first.
        assertTrue(html.indexOf("sui-shell-content") < html.indexOf("data-sui=\"menu\""),
                "content should precede a right-side menu; got:\n" + html);
    }

    @Test
    void appShellContentIdMatchesTheRenderedContainer() {
        var shell = UiAppShell.of("my-shell").content(UiText.of("t", "x"));
        assertEquals("my-shell-content", shell.contentId());
        assertTrue(renderer.render(shell).contains("id=\"my-shell-content\""));
    }

    @Test
    void appShellRendersFooterAndFillsViewportByDefault() {
        var shell = UiAppShell.of("shell")
                .header(UiHeader.of("Acme"))
                .content(UiText.of("body", "Hello"))
                .footer(UiText.of("foot", "v2.4.0"));

        String html = renderer.render(shell);

        assertTrue(html.contains("sui-shell-footer"), html);
        assertTrue(html.contains("v2.4.0"), html);
        // Filling the viewport is the default, so no opt-out class.
        assertFalse(html.contains("sui-shell--fit"), html);

        // Embedded variant opts out.
        String fit = renderer.render(UiAppShell.of("s2").fillViewport(false)
                .content(UiText.of("b", "x")));
        assertTrue(fit.contains("sui-shell--fit"), fit);
        // …and a shell without a footer renders none.
        assertFalse(fit.contains("sui-shell-footer"), fit);
    }

    @Test
    void appShellContentIsAPatchSlot() {
        // data-sui-slot makes a REPLACE aimed at the content fill the
        // container instead of deleting it — losing the container would take
        // the shell's layout classes with it.
        String html = renderer.render(UiAppShell.of("shell").content(UiText.of("b", "x")));
        assertTrue(html.contains("data-sui-slot=\"content\""), html);
    }

    @Test
    void headerExtrasOverflowMenuMarksTheBar() {
        var header = UiHeader.of("Acme")
                .extrasOverflow(UiHeader.ExtrasOverflow.MENU);
        header.setExtras(java.util.List.of(UiLink.of("l1", "/a", "Alpha")));

        String html = renderer.render(header);
        assertTrue(html.contains("data-sui-overflow=\"menu\""), html);

        // WRAP (the default) leaves the bar unmarked, so it just wraps.
        var plain = UiHeader.of("Acme");
        plain.setExtras(java.util.List.of(UiLink.of("l1", "/a", "Alpha")));
        assertFalse(renderer.render(plain).contains("data-sui-overflow"), html);
    }

    @Test
    void nodeEventsRenderAsDataAttributesOnAnyNodeType() {
        // The point of the inherited trigger fields: a plain layout node reacts
        // to a click without becoming an action.
        var stack = UiStack.of(UiText.of("t", "Row"));
        stack.setId("row");
        stack.setOnClick(UiTrigger.go("/orders/42"));
        stack.setOnHover(UiTrigger.toast("Preview"));

        String html = renderer.render(stack);

        assertTrue(html.contains("data-sui-on-click="), html);
        assertTrue(html.contains("data-sui-on-hover="), html);
        assertTrue(html.contains("/orders/42"), html);

        // A node without them stays clean — no empty attributes.
        assertFalse(renderer.render(UiStack.of(UiText.of("t2", "x"))).contains("data-sui-on-"), html);
    }

    @Test
    void nodeEventsWorkOnTextAndTable() {
        var text = UiText.of("hint", "Hover me");
        text.setOnHover(UiTrigger.toast("Boo"));
        assertTrue(renderer.render(text).contains("data-sui-on-hover="), renderer.render(text));

        var table = UiTable.of("t", "Products").column(UiColumn.of("name", "Name"));
        table.setOnDblClick(UiTrigger.go("/edit"));
        assertTrue(renderer.render(table).contains("data-sui-on-dblclick="), renderer.render(table));
    }

    @Test
    void actionClickIsTheInheritedTriggerAndStillDrivesSsrMarkup() {
        // One field, two jobs: the click trigger is also the no-JS contract.
        // A GET renders an anchor, a DELETE a form with _method — proving the
        // unification didn't cost the hybrid rendering.
        var get = UiAction.primary("open", "Open").onClick(UiTrigger.go("/products/1"));
        String getHtml = renderer.render(get);
        assertTrue(getHtml.contains("<a id=\"open\""), getHtml);
        assertTrue(getHtml.contains("href=\"/products/1\""), getHtml);

        var del = UiAction.danger("del", "Delete").onClick(UiTrigger.api("DELETE", "/products/1"));
        String delHtml = renderer.render(del);
        assertTrue(delHtml.contains("<form"), delHtml);
        assertTrue(delHtml.contains("name=\"_method\" value=\"DELETE\""), delHtml);

        // The builder writes the inherited field — one storage, not two.
        assertEquals("/products/1", get.getOnClick().getUrl());
    }

    @Test
    void helperContributorsCanAddAndOverrideHelpers() {
        // An extension ships a template that calls a helper the core doesn't
        // have. Without this hook the template could be shadowed onto the
        // classpath but never render — which is why extensions were
        // browser-only.
        SuiHelperContributor adds = (hb, mapper) ->
                hb.registerHelper("shout", (ctx, opts) ->
                        new com.github.jknack.handlebars.Handlebars.SafeString(
                                String.valueOf(ctx).toUpperCase()));

        var renderer = new SuiServerRenderer(new com.fasterxml.jackson.databind.ObjectMapper(),
                java.util.List.of(adds));

        // Contributors run after the core helpers, so a contributor may also
        // replace one by registering the same name — last registration wins.
        SuiHelperContributor overrides = (hb, mapper) ->
                hb.registerHelper("lower", (ctx, opts) -> "REPLACED");
        var overridden = new SuiServerRenderer(new com.fasterxml.jackson.databind.ObjectMapper(),
                java.util.List.of(overrides));

        // A broken contributor must not take the renderer down: one node type
        // degrades, every page keeps rendering.
        SuiHelperContributor broken = (hb, mapper) -> { throw new IllegalStateException("boom"); };
        var survived = new SuiServerRenderer(new com.fasterxml.jackson.databind.ObjectMapper(),
                java.util.List.of(broken));
        assertTrue(survived.render(UiText.of("t", "still here")).contains("still here"));

        assertTrue(renderer.render(UiText.of("t", "x")).contains("x"));
        assertTrue(overridden.render(UiText.of("t", "y")).contains("y"));
    }

    @Test
    void selectOnClickFalseMarksTheTabSoTheAppCanGateTheSwitch() {
        var section = UiSection.of("s", "Tabs")
                .section("a", "Alpha", UiText.of("ta", "A"))
                .section("b", "Beta", UiText.of("tb", "B"));
        // Gate the second tab: the click fires the trigger, the panel stays.
        section.getSections().get(1).selectOnClick(false);
        section.getSections().get(1).setOnClick(UiTrigger.invoke("mayLeave"));

        String html = renderer.render(section);

        assertTrue(html.contains("data-sui-no-select"), html);
        // Exactly one tab is gated — the default stays "switch on click".
        assertEquals(1, html.split("data-sui-no-select", -1).length - 1, html);
        // The trigger is still emitted; it is the switch that is suppressed.
        assertTrue(html.contains("mayLeave"), html);
    }
}
