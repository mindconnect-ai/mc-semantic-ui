package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Options;

import java.util.Collection;
import java.util.Map;

/**
 * The Handlebars helper library shared by every SUI template. These are the
 * SSR counterpart to the small logic functions in {@code renderer.ts}
 * (escaping, trigger encoding, pagination math, MULTISELECT membership). By
 * moving that logic into named helpers, the {@code .hbs} templates stay free
 * of computation — which is exactly what lets the same template later run in
 * the browser (Iteration 2: handlebars.js gets a behaviour-equivalent helper
 * set).
 *
 * <p>Helpers (kept deliberately small + generic):
 * <ul>
 *   <li>{@code render} — recurse into a child node via {@link SuiServerRenderer}.</li>
 *   <li>{@code trigger} — a {@link UiTrigger} → escaped JSON for a
 *       {@code data-trigger='…'} attribute (= TS {@code encodeTrigger}).</li>
 *   <li>{@code ssrIsAnchor} / {@code ssrFormOpen} / {@code ssrFormClose} /
 *       {@code ssrHref} — native HTML for JS-free triggers (see
 *       {@link SsrTriggerMapper}).</li>
 *   <li>{@code ceil} / {@code divide} / {@code min} — pagination + select-size math.</li>
 *   <li>{@code eq} — equality test (block helper).</li>
 *   <li>{@code includes} — collection / comma-string membership (block helper).</li>
 *   <li>{@code subst} — replace a literal token (used for {@code {page}}).</li>
 * </ul>
 */
public final class SuiHandlebarsHelpers {

    private SuiHandlebarsHelpers() {}

    private static final Map<String, String> HTML_ESCAPE = Map.of(
            "&", "&amp;", "<", "&lt;", ">", "&gt;", "\"", "&quot;", "'", "&#39;");

    /**
     * Registers every helper on the given Handlebars instance. Called once
     * from the {@link SuiServerRenderer} constructor.
     */
    public static void register(Handlebars hb, SuiServerRenderer renderer, ObjectMapper mapper) {

        // Recurse into a child node — mirrors r.render(child) in the TS handlers.
        // {{{render child}}} (triple-stash: the helper already returns safe HTML).
        hb.registerHelper("render", (ctx, opts) -> renderer.renderChild(ctx));

        // {{{trigger onClick}}} → escaped JSON for data-trigger='…'.
        hb.registerHelper("trigger", (ctx, opts) -> {
            if (!(ctx instanceof UiTrigger t)) return "";
            return encodeTrigger(t, mapper);
        });

        // ── SSR action rendering ─────────────────────────────────────────────
        // {{{action this}}} → hybrid HTML for the UiAction. Renders the full
        // shape (anchor or form-wrapped button) for stand-alone use (detail
        // page action bar, list-item actions, etc.).
        hb.registerHelper("action", (ctx, opts) -> {
            if (!(ctx instanceof ai.mindconnect.ui.model.UiAction a)) return "";
            return new com.github.jknack.handlebars.Handlebars.SafeString(
                    SsrTriggerMapper.render(a));
        });

        // {{#if (hasPrimaryAction this)}} → true when the form has at least
        // one action (so the outer <form> can carry method+action attrs).
        hb.registerHelper("hasPrimaryAction", (ctx, opts) -> {
            boolean has = primaryFormAction(ctx) != null;
            return opts.tagType.inline() ? Boolean.valueOf(has) : blockResult(opts, has);
        });

        // ── Form-level submit metadata ────────────────────────────────────
        // All four helpers take the UiForm itself (not a UiAction) — picking
        // the primary action is an internal detail. This keeps the template
        // free of {{#with}} acrobatics and the context unambiguous.

        // {{formMethod this}} → "get" or "post" for the form's method attribute.
        hb.registerHelper("formMethod", (ctx, opts) -> {
            String m = primaryActionMethod(ctx);
            return "GET".equalsIgnoreCase(m) ? "get" : "post";
        });

        // {{formActionUrl this}} → URL for the form's action attribute.
        hb.registerHelper("formActionUrl", (ctx, opts) -> primaryActionUrl(ctx));

        // {{#if (formNeedsMethodOverride this)}} → true when the primary
        // action's verb is DELETE/PUT/PATCH and a hidden _method input is
        // needed alongside method=post.
        hb.registerHelper("formNeedsMethodOverride", (ctx, opts) -> {
            String m = primaryActionMethod(ctx);
            boolean override = m != null && !"GET".equalsIgnoreCase(m) && !"POST".equalsIgnoreCase(m);
            return opts.tagType.inline() ? Boolean.valueOf(override) : blockResult(opts, override);
        });

        // {{formMethodOverride this}} → uppercase verb for the hidden _method field.
        hb.registerHelper("formMethodOverride", (ctx, opts) -> {
            String m = primaryActionMethod(ctx);
            return m == null ? "" : m.toUpperCase();
        });

        // {{{formAction this}}} → button-only variant for actions that live
        // INSIDE a <form> already (form.hbs uses this for the action footer).
        // Avoids nested <form> elements (invalid HTML) when a Save button's
        // trigger would otherwise wrap itself in a form. The outer UiForm
        // wrapper carries the action URL and HTTP method instead.
        hb.registerHelper("formAction", (ctx, opts) -> {
            if (!(ctx instanceof ai.mindconnect.ui.model.UiAction a)) return "";
            return new com.github.jknack.handlebars.Handlebars.SafeString(
                    SsrTriggerMapper.renderButtonOnly(a));
        });

        // {{{rowAction action row}}} → like {{action}} but with the row's id
        // substituted for {id} in the trigger URL first. Used by table.hbs
        // so a Delete-button's href becomes /admin/products/abc-123 instead
        // of the literal /admin/products/{id}.
        hb.registerHelper("rowAction", (ctx, opts) -> {
            if (!(ctx instanceof ai.mindconnect.ui.model.UiAction a)) return "";
            Object row = opts.param(0, null);
            // Accept either a UiRow (preferred — has its own id + data map)
            // or a bare Map (legacy callers / row.data passed in directly).
            String rowId = "";
            if (row instanceof ai.mindconnect.ui.model.UiRow r) {
                if (r.getId() != null) rowId = r.getId();
                else if (r.getData() != null && r.getData().get("id") != null) {
                    rowId = r.getData().get("id").toString();
                }
            } else if (row instanceof java.util.Map<?, ?> m) {
                Object v = m.get("id");
                rowId = v == null ? "" : v.toString();
            }
            return new com.github.jknack.handlebars.Handlebars.SafeString(
                    SsrTriggerMapper.render(a, java.util.Map.of("id", rowId)));
        });

        // {{{pageButton pagination label targetPage disabled}}} → renders a
        // single pagination button as a native <a> or <form> with the
        // {page} placeholder substituted. Pagination has two buttons
        // (Prev = page-1, Next = page+1), each needs a different target
        // page, so a helper is more honest than nested template arithmetic.
        hb.registerHelper("pageButton", (ctx, opts) -> {
            if (!(ctx instanceof ai.mindconnect.ui.model.UiTable.Pagination
                    || ctx instanceof ai.mindconnect.ui.model.UiList.Pagination)) {
                return "";
            }
            String label  = str(opts.param(0, ""));
            long target   = toLong(opts.param(1, 0));
            boolean disabled = Boolean.TRUE.equals(opts.param(2, Boolean.FALSE));

            ai.mindconnect.ui.model.UiTrigger trigger = pageTrigger(ctx);
            if (trigger == null || disabled) {
                return new com.github.jknack.handlebars.Handlebars.SafeString(
                        "<button type=\"button\" class=\"sui-btn sui-btn--secondary\" disabled>"
                                + escapeHtml(label) + "</button>");
            }
            // Build a per-button UiAction wrapping the page-substituted trigger.
            var perButton = ai.mindconnect.ui.model.UiAction.secondary("page-" + target, label);
            var triggerCopy = new ai.mindconnect.ui.model.UiTrigger();
            triggerCopy.setMethod(trigger.getMethod());
            triggerCopy.setBehavior(trigger.getBehavior());
            triggerCopy.setPayload(trigger.getPayload());
            triggerCopy.setUrl(trigger.getUrl());
            perButton.onClick(triggerCopy);
            String rendered = SsrTriggerMapper.render(perButton,
                    java.util.Map.of("page", String.valueOf(target)));
            return new com.github.jknack.handlebars.Handlebars.SafeString(rendered);
        });

        // {{{sortTh table column}}} → one <th>. A plain header for a normal
        // column; for a sortable one a <button> carrying the sort state, and
        // — when the table has a sortTrigger — a data-trigger with {column}
        // and {direction} substituted. Byte-for-byte parity with
        // sortAttrs()/sortControl() in renderers/table.ts.
        hb.registerHelper("sortTh", (ctx, opts) -> {
            if (!(ctx instanceof ai.mindconnect.ui.model.UiTable table)
                    || !(opts.param(0, null) instanceof ai.mindconnect.ui.model.UiColumn col)) {
                return "";
            }
            String idAttr = col.getId() != null ? " id=\"" + escapeHtml(col.getId()) + "\"" : "";
            String label  = escapeHtml(col.getLabel() == null ? "" : col.getLabel());
            if (!col.isSortable()) {
                return new com.github.jknack.handlebars.Handlebars.SafeString(
                        "<th" + idAttr + ">" + label + "</th>");
            }
            String key = col.getDataKey() != null ? col.getDataKey()
                    : (col.getId() != null ? col.getId() : "");
            boolean active = table.getSortColumn() != null && table.getSortColumn().equals(key);
            boolean asc = table.getSortDirection() != ai.mindconnect.ui.model.UiTable.SortDirection.DESC;
            String ariaSort = active ? (asc ? "ascending" : "descending") : "none";
            String nextDir  = (active && asc) ? "DESC" : "ASC";
            String indicator = active ? (asc ? "↑" : "↓") : "↕";

            String trigger = "";
            if (table.getSortTrigger() != null) {
                var t = table.getSortTrigger();
                var copy = new ai.mindconnect.ui.model.UiTrigger();
                copy.setMethod(t.getMethod());
                copy.setBehavior(t.getBehavior());
                copy.setPayload(t.getPayload());
                copy.setUrl(t.getUrl() == null ? null : t.getUrl()
                        .replace("{column}", java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8))
                        .replace("{direction}", nextDir.toLowerCase(java.util.Locale.ROOT)));
                trigger = " data-trigger='" + encodeTrigger(copy, mapper) + "'";
            }
            return new com.github.jknack.handlebars.Handlebars.SafeString(
                    "<th" + idAttr + " class=\"sui-th-sortable" + (active ? " is-sorted" : "")
                            + "\" aria-sort=\"" + ariaSort + "\">"
                            + "<button type=\"button\" class=\"sui-sort-btn\" data-sui-sort=\"" + escapeHtml(key) + "\""
                            + " data-sui-sort-dir=\"" + nextDir + "\"" + trigger + ">"
                            + label + "<span class=\"sui-sort-indicator\" aria-hidden=\"true\">" + indicator + "</span>"
                            + "</button></th>");
        });

        // {{{shellHeader shell}}} / {{{shellMenu shell}}} → the app shell's two
        // chrome parts, rendered with the couplings the node exists to make:
        // the header's burger points at the menu's id, and the menu's own
        // toggle is off. Both operate on a copy so a menu reused across pages
        // is never mutated. Mirrors renderAppShell() in app-shell.ts.
        hb.registerHelper("shellHeader", (ctx, opts) -> {
            if (!(ctx instanceof ai.mindconnect.ui.model.UiAppShell shell)
                    || shell.getHeader() == null) {
                return "";
            }
            var h = shell.getHeader();
            var copy = new ai.mindconnect.ui.model.UiHeader();
            copy.setId(h.getId());
            copy.setCssClass(h.getCssClass());
            copy.setBrand(h.getBrand());
            copy.setBrandHref(h.getBrandHref());
            copy.setBrandLogo(h.getBrandLogo());
            copy.setUser(h.getUser());
            copy.setExtras(h.getExtras());
            copy.setMenuToggle(shell.getMenu() != null ? shell.getMenu().getId() : h.getMenuToggle());
            return new com.github.jknack.handlebars.Handlebars.SafeString(renderer.render(copy));
        });
        hb.registerHelper("shellMenu", (ctx, opts) -> {
            if (!(ctx instanceof ai.mindconnect.ui.model.UiAppShell shell)
                    || shell.getMenu() == null) {
                return "";
            }
            var m = shell.getMenu();
            var copy = new ai.mindconnect.ui.model.UiMenu();
            copy.setId(m.getId());
            copy.setTitle(m.getTitle());
            copy.setCssClass(m.getCssClass());
            copy.setItems(m.getItems());
            copy.setState(m.getState());
            copy.setMode(m.getMode());
            copy.setSide(m.getSide());
            copy.setToggle(false);              // the header owns the burger
            return new com.github.jknack.handlebars.Handlebars.SafeString(renderer.render(copy));
        });

        // {{{events this}}} → data-sui-on-<event>='<trigger JSON>' for the
        // node's event triggers. Parity with evt() in renderers/util.ts.
        // Emitted as a leading-space attribute string so templates splice it
        // straight into a tag.
        hb.registerHelper("events", (ctx, opts) -> {
            if (!(ctx instanceof ai.mindconnect.ui.model.UiNode node)) return "";
            // Trailing arguments name events the template already emits itself
            // ({{{events this "click"}}}), so the same trigger never lands on
            // an element twice.
            var skip = new java.util.ArrayList<String>();
            for (int i = 0; i < opts.params.length; i++) skip.add(str(opts.param(i, "")));
            String attrs = eventAttrs(node, mapper, skip.toArray(new String[0]));
            return attrs.isEmpty() ? "" : new com.github.jknack.handlebars.Handlebars.SafeString(attrs);
        });

        // ── Math (pagination, select size) ───────────────────────────────────
        // {{ceil a b}} → ceil(a / b) as a long. Defensive: a zero divisor
        // would produce Infinity, which casts to Long.MAX_VALUE and surfaces
        // as a nonsense page count in the UI. Treat zero or missing divisor
        // as 1 so the result equals the numerator (the only sensible default
        // for the pagination case).
        hb.registerHelper("ceil", (ctx, opts) -> {
            double divisor = toDouble(opts.param(0, 1));
            if (divisor == 0d) divisor = 1d;
            return (long) Math.ceil(toDouble(ctx) / divisor);
        });
        hb.registerHelper("divide", (ctx, opts) ->
                toDouble(ctx) / toDouble(opts.param(0, 1)));

        // {{pageCount pagination}} → number of pages = ceil(total / size).
        // Reads both numbers from the Pagination object so the template
        // doesn't have to pass `size` as a separate Handlebars expression.
        //
        // Why this exists: {{ceil total size}} in pagination.hbs failed to
        // resolve `size` in handlebars.java — it didn't fall through to the
        // POJO getter and the helper saw a missing arg. The defensive
        // "divisor==0 → 1" fallback then produced ceil(total/1) = total,
        // which surfaced as the "page count equals item count" bug. Doing
        // the entire computation in the helper sidesteps the lookup.
        hb.registerHelper("pageCount", (ctx, opts) -> {
            long total = readLongProperty(ctx, "getTotal");
            long size  = readLongProperty(ctx, "getSize");
            if (size <= 0) size = 1;
            return (long) Math.ceil((double) total / (double) size);
        });
        hb.registerHelper("min", (ctx, opts) ->
                Math.min(toLong(ctx), toLong(opts.param(0, 0))));

        // ── Comparisons / membership (block helpers) ─────────────────────────
        // {{#if (eq a b)}}: as a sub-expression we must return a primitive
        // boolean (Handlebars truthiness check). As a block helper
        // ({{#eq a b}}…{{/eq}}) we render fn/inverse. Both call shapes are
        // supported by branching on whether the helper has a body.
        hb.registerHelper("eq", (ctx, opts) -> {
            boolean equal = java.util.Objects.equals(ctx, opts.param(0, null))
                    || str(ctx).equals(str(opts.param(0, null)));
            return opts.tagType.inline() ? Boolean.valueOf(equal) : blockResult(opts, equal);
        });
        hb.registerHelper("includes", (ctx, opts) -> {
            boolean has = includes(ctx, opts.param(0, null));
            return opts.tagType.inline() ? Boolean.valueOf(has) : blockResult(opts, has);
        });

        // {{#if (or a b …)}} → true when any argument is truthy. Works as a
        // sub-expression (returns Boolean) or as a block helper.
        hb.registerHelper("or", (ctx, opts) -> {
            boolean any = isTruthy(ctx);
            for (int i = 0; i < opts.params.length && !any; i++) any = isTruthy(opts.param(i));
            return opts.tagType.inline() ? Boolean.valueOf(any) : blockResult(opts, any);
        });

        // ── String substitution ({page} placeholder) ─────────────────────────
        hb.registerHelper("subst", (ctx, opts) -> {
            String s     = str(ctx);
            String token = str(opts.param(0, ""));
            String with  = str(opts.param(1, ""));
            return s.replace(token, with);
        });

        // {{lower style}} — lowercase, with a fallback for null enum values.
        // Mirrors the TS `(a.style || "SECONDARY").toLowerCase()` default.
        hb.registerHelper("lower", (ctx, opts) -> {
            String fallback = str(opts.param(0, ""));
            String s = (ctx == null || str(ctx).isEmpty()) ? fallback : str(ctx);
            return s.toLowerCase();
        });

        // {{len xs}} → collection length as a long. Handlebars has no
        // built-in length accessor; the TS renderer reads options.length
        // directly. Used for the MULTISELECT size attribute and anywhere
        // else a list count is shown.
        //
        // Named `len` (not `size`) on purpose: a helper called `size`
        // shadows the legitimate `size` property on UiTable.Pagination /
        // UiList.Pagination, which broke {{ceil total size}} by feeding it
        // the helper's zero-arg result (0) instead of the property value.
        hb.registerHelper("len", (ctx, opts) -> {
            if (ctx instanceof Collection<?> c) return (long) c.size();
            if (ctx instanceof Object[] a) return (long) a.length;
            return 0L;
        });

        // {{add a b}} / {{plus a b}} → simple integer addition. Used where
        // the TS does `options.length + 1` etc. — keeps the templates from
        // sprouting their own arithmetic.
        hb.registerHelper("add", (ctx, opts) ->
                toLong(ctx) + toLong(opts.param(0, 0)));

        // {{subtract a b}} → a − b. Used for pagination prev = page − 1.
        hb.registerHelper("subtract", (ctx, opts) ->
                toLong(ctx) - toLong(opts.param(0, 0)));

        // {{selectionActive mode}} → true iff mode is SINGLE or MULTI.
        // Handlebars's #if helper coerces nulls/enums weirdly; this gives the
        // template a single, predictable boolean. Accepts both the Java enum
        // (UiTable.SelectMode) and a plain string (when the template is fed
        // from a deserialised JSON tree).
        hb.registerHelper("selectionActive", (ctx, opts) -> {
            String s = ctx == null ? "NONE" : ctx.toString();
            return "SINGLE".equals(s) || "MULTI".equals(s);
        });

        // {{{selectionInput table row}}} → renders the radio (SINGLE) or
        // checkbox (MULTI) for one row. Name pattern matches the TS
        // renderer ({@code <table.id>__selection}) so a server-side form
        // submit reads the same key in both modes.
        hb.registerHelper("selectionInput", (ctx, opts) -> {
            var row = opts.param(0);
            String mode = "NONE";
            String tableId = "";
            java.util.Set<String> preselected = java.util.Set.of();
            if (ctx instanceof ai.mindconnect.ui.model.UiTable t) {
                mode = t.getSelectMode() == null ? "NONE" : t.getSelectMode().name();
                tableId = t.getId() == null ? "" : t.getId();
                if (t.getSelectedRowIds() != null) preselected = new java.util.HashSet<>(t.getSelectedRowIds());
            } else if (ctx instanceof Map<?, ?> tm) {
                Object m = tm.get("selectMode");
                mode = m == null ? "NONE" : m.toString();
                Object idv = tm.get("id");
                tableId = idv == null ? "" : idv.toString();
                Object sel = tm.get("selectedRowIds");
                if (sel instanceof java.util.Collection<?> coll) {
                    preselected = new java.util.HashSet<>();
                    for (Object o : coll) if (o != null) preselected.add(o.toString());
                }
            }
            if (!"SINGLE".equals(mode) && !"MULTI".equals(mode)) return "";
            String rowId = "";
            if (row instanceof ai.mindconnect.ui.model.UiRow rr && rr.getId() != null) {
                rowId = rr.getId();
            } else if (row instanceof Map<?, ?> rm) {
                Object idv = rm.get("id");
                rowId = idv == null ? "" : idv.toString();
            }
            String inputType = "SINGLE".equals(mode) ? "radio" : "checkbox";
            String checked = preselected.contains(rowId) ? " checked" : "";
            String name = escapeAttr(tableId + "__selection");
            return new com.github.jknack.handlebars.Handlebars.SafeString(
                    "<input type=\"" + inputType + "\" name=\"" + name + "\""
                    + " value=\"" + escapeAttr(rowId) + "\"" + checked + ">");
        });

        // {{cell row col}} → renders one table cell. Two modes:
        //
        //  (a) col.cellTemplate is set → clone the template, run
        //      {dataKey}-substitution against (row.data + row.id), suffix
        //      every id with __<row.id> for HTML uniqueness, then dispatch
        //      through the SuiServerRenderer so it picks the right .hbs
        //      template for whatever sits in the template (text / link /
        //      action / stack / …).
        //
        //  (b) no cellTemplate → fall back to the plain dataKey-into-row.data
        //      lookup, escaped as text. Matches the original cell behaviour.
        //
        // Accepts UiRow / UiColumn (live Java) AND Map<String,Object>
        // (deserialised JSON) on both sides — the SSR controller path uses
        // the former, the editor preview's SSR-via-JSON path uses the latter.
        hb.registerHelper("cell", (ctx, opts) -> {
            var col = opts.param(0);
            Map<String, Object> dataMap = null;
            String rowId = null;
            if (ctx instanceof ai.mindconnect.ui.model.UiRow r) {
                dataMap = r.getData();
                rowId = r.getId();
            } else if (ctx instanceof Map<?, ?> rowMap) {
                if (rowMap.get("data") instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    var cast = (Map<String, Object>) m;
                    dataMap = cast;
                }
                Object idVal = rowMap.get("id");
                rowId = idVal == null ? null : idVal.toString();
            }
            if (dataMap == null) dataMap = java.util.Map.of();
            // Build substitution context: row.data first, row.id overrides {id}.
            java.util.Map<String, Object> subCtx = new java.util.LinkedHashMap<>(dataMap);
            if (rowId != null) subCtx.put("id", rowId);

            Object cellTemplate = null;
            String dataKey = null;
            if (col instanceof ai.mindconnect.ui.model.UiColumn c) {
                cellTemplate = c.getCellTemplate();
                dataKey = (c.getDataKey() != null && !c.getDataKey().isEmpty()) ? c.getDataKey() : c.getId();
            } else if (col instanceof Map<?, ?> colMap) {
                cellTemplate = colMap.get("cellTemplate");
                Object k = colMap.get("dataKey");
                if (k == null || (k instanceof String s && s.isEmpty())) k = colMap.get("id");
                dataKey = k == null ? null : k.toString();
            }
            if (cellTemplate != null) {
                Object substituted = substituteCellTemplate(cellTemplate, subCtx,
                        rowId == null ? "" : rowId, mapper);
                return new com.github.jknack.handlebars.Handlebars.SafeString(
                        renderer.renderChild(substituted));
            }
            if (dataKey == null) return "";
            Object v = dataMap.get(dataKey);
            return v == null ? "" : v;
        });

        // {{default x fallback}} → x if non-null/non-empty, else fallback.
        // Used e.g. by section/tabs.hbs to pick `initialSection ?? sections[0].id`.
        hb.registerHelper("default", (ctx, opts) -> {
            String s = str(ctx);
            return s.isEmpty() ? str(opts.param(0, "")) : s;
        });

        // {{first xs}} → first element of a list, or null when empty. Used
        // to grab sections[0] for the default-active tab pick.
        hb.registerHelper("first", (ctx, opts) -> {
            if (ctx instanceof java.util.List<?> l && !l.isEmpty()) return l.get(0);
            if (ctx instanceof Collection<?> c) return c.stream().findFirst().orElse(null);
            return null;
        });

        // {{activeSection uiSection}} → which Entry id should be active in
        // a UiSection's tab bar. Mirrors the TS `node.initialSection
        // || node.sections[0]?.id`. Done as a single helper because
        // chained sub-expressions ({{default initialSection (lookup (first
        // sections) "id")}}) hit handlebars.java scoping quirks when nested
        // inside an {{#each}} that references the outer context.
        hb.registerHelper("activeSection", (ctx, opts) -> {
            if (!(ctx instanceof ai.mindconnect.ui.model.UiSection s)) return "";
            if (s.getInitialSection() != null && !s.getInitialSection().isEmpty()) {
                return s.getInitialSection();
            }
            // No initialSection set:
            //  - For local (button) tabs, default to the first entry so the
            //    user sees something — that's the TS-renderer parity case.
            //  - For navigation tabs (entries carry hrefs), do NOT auto-
            //    activate one: leaving initialSection null is the explicit
            //    "we're on a non-tab page" signal (e.g. the profile view).
            if (s.getSections() != null && !s.getSections().isEmpty()) {
                var first = s.getSections().get(0);
                boolean isNavTab = first.getHref() != null && !first.getHref().isEmpty();
                if (!isNavTab) return first.getId();
            }
            return "";
        });

        // {{#if (hasTabs this)}} → true when the section should render its
        // tab bar even without a title: a child entry carries an `href`,
        // which is the SSR-friendly signal "this is a navigation tab".
        // Lets a header-less admin shell still show the Products/Customers
        // tabs without spending a title row on it.
        //
        // The companion case — neither title NOR any entry title NOR any
        // href — is "this section is just a vertical stack of panels".
        // Emitting tab buttons there shows empty buttons and (worse) hides
        // every panel except the first. {{#if (stackOnly this)}} catches
        // that shape; the template branch on it switches to a plain
        // <div class="sui-panels">…</div> with every panel visible.
        hb.registerHelper("hasTabs", (ctx, opts) -> {
            boolean tabs = false;
            if (ctx instanceof ai.mindconnect.ui.model.UiSection s
                    && s.getSections() != null) {
                for (var e : s.getSections()) {
                    if (e.getHref() != null && !e.getHref().isEmpty()) {
                        tabs = true; break;
                    }
                }
            }
            return opts.tagType.inline() ? Boolean.valueOf(tabs) : blockResult(opts, tabs);
        });

        hb.registerHelper("stackOnly", (ctx, opts) -> {
            boolean stack = false;
            if (ctx instanceof ai.mindconnect.ui.model.UiSection s
                    && s.getSections() != null && !s.getSections().isEmpty()) {
                // Stack mode = no titles at all AND no nav hrefs. Same
                // shape the chat session sends: one entry holding the
                // message list, a second one holding the input form, both
                // unnamed.
                boolean anyTitle = false, anyHref = false;
                for (var e : s.getSections()) {
                    if (e.getTitle() != null && !e.getTitle().isEmpty()) anyTitle = true;
                    if (e.getHref()  != null && !e.getHref().isEmpty())  anyHref  = true;
                }
                stack = !anyTitle && !anyHref;
            }
            return opts.tagType.inline() ? Boolean.valueOf(stack) : blockResult(opts, stack);
        });

        // {{{nodeJson this}}} → escape-for-attribute JSON of the whole node.
        // Used by form.hbs to emit data-node='{...}' so the EventBus can
        // resolve field metadata without re-fetching from the server
        // (mirrors `escapeHtml(JSON.stringify(node))` in the TS renderer).
        hb.registerHelper("nodeJson", (ctx, opts) -> {
            if (ctx == null) return "";
            try {
                return escapeHtml(mapper.writeValueAsString(ctx));
            } catch (Exception e) {
                return "";
            }
        });

        // {{{icon token}}} or {{{icon token "extra-class"}}} → sprite <use>
        // (or a literal emoji/text glyph). Mirrors renderIcon() in the TS
        // icon module; resolution is swappable via IconRenderer.
        hb.registerHelper("icon", (ctx, opts) -> {
            if (ctx == null) return "";
            String name = String.valueOf(ctx);
            if (name.isEmpty()) return "";
            String cssClass = opts.params.length > 0 ? String.valueOf(opts.param(0)) : null;
            return IconRenderer.render(name, cssClass, null);
        });

        // {{{iconNode this}}} → renders a UiIcon node (name + inherited title +
        // cssClass). Used by icon.hbs.
        hb.registerHelper("iconNode", (ctx, opts) -> {
            if (!(ctx instanceof ai.mindconnect.ui.model.UiIcon i)) return "";
            return IconRenderer.render(i.getName(), i.getCssClass(), i.getTitle(), i.getId());
        });

        // {{{progress this}}} → renders a UiProgress node (bar or circle) via
        // ProgressRenderer, mirroring renderProgress() in renderers/progress.ts.
        // Kept in Java (not a branchy template) because the circular variant
        // needs SVG + dash-offset math — same reasoning as the icon helper.
        hb.registerHelper("progress", (ctx, opts) -> {
            if (!(ctx instanceof ai.mindconnect.ui.model.UiProgress p)) return "";
            return new com.github.jknack.handlebars.Handlebars.SafeString(
                    ProgressRenderer.render(p));
        });
    }

    /**
     * Form → primary submit action. Prefers a PRIMARY-styled action;
     * falls back to the first action in the list. Returns null when the
     * form has no actions at all.
     */
    private static ai.mindconnect.ui.model.UiAction primaryFormAction(Object formCtx) {
        if (!(formCtx instanceof ai.mindconnect.ui.model.UiForm f)) return null;
        if (f.getActions() == null || f.getActions().isEmpty()) return null;
        for (var a : f.getActions()) {
            if (a.getStyle() == ai.mindconnect.ui.model.UiAction.Style.PRIMARY) return a;
        }
        return f.getActions().get(0);
    }

    /** UiForm → primary action's HTTP method, defaulting to GET. */
    private static String primaryActionMethod(Object formCtx) {
        var a = primaryFormAction(formCtx);
        if (a == null) return "GET";
        var t = a.getOnClick();
        return t == null || t.getMethod() == null ? "GET" : t.getMethod();
    }

    /** UiForm → primary action's URL, or empty string. */
    private static String primaryActionUrl(Object formCtx) {
        var a = primaryFormAction(formCtx);
        if (a == null) return "";
        var t = a.getOnClick();
        return t == null || t.getUrl() == null ? "" : t.getUrl();
    }

    /**
     * Calls a no-arg getter named {@code methodName} on {@code target} and
     * coerces the result to {@code long}. Returns 0 when the method is
     * absent or its result is null/non-numeric. Used by {@link
     * #register(Handlebars, SuiServerRenderer, ObjectMapper) pageCount} to
     * read total and size off a Pagination POJO without binding the helper
     * to a specific UiTable.Pagination / UiList.Pagination class.
     */
    private static long readLongProperty(Object target, String methodName) {
        if (target == null) return 0L;
        try {
            var m = target.getClass().getMethod(methodName);
            Object v = m.invoke(target);
            if (v instanceof Number n) return n.longValue();
            if (v != null) return Long.parseLong(v.toString());
        } catch (Exception ignored) {}
        return 0L;
    }

    /** Reflective-but-tiny pageTrigger accessor: works for both
     *  UiTable.Pagination and UiList.Pagination without a shared interface. */
    private static ai.mindconnect.ui.model.UiTrigger pageTrigger(Object pagination) {
        try {
            var m = pagination.getClass().getMethod("getPageTrigger");
            Object t = m.invoke(pagination);
            return (ai.mindconnect.ui.model.UiTrigger) t;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Helper internals ─────────────────────────────────────────────────────

    /** Block-helper result: body when true, inverse ({{else}}) when false. */
    private static CharSequence blockResult(Options opts, boolean condition) {
        try {
            return condition ? opts.fn() : opts.inverse();
        } catch (Exception e) {
            return "";
        }
    }

    /** Truthiness for the {@code or} helper: non-null, non-false, non-empty. */
    private static boolean isTruthy(Object o) {
        if (o == null || Boolean.FALSE.equals(o)) return false;
        // Empty collections/maps are falsy, matching Handlebars' own {{#if}}
        // and JS semantics for "has entries". Without this an empty list
        // stringifies to "[]", which is non-empty and would read as true.
        if (o instanceof Collection<?> c) return !c.isEmpty();
        if (o instanceof java.util.Map<?, ?> m) return !m.isEmpty();
        if (o.getClass().isArray()) return java.lang.reflect.Array.getLength(o) > 0;
        String s = String.valueOf(o);
        return !s.isEmpty() && !"false".equals(s);
    }

    /** Membership test: collection contains, or comma-string contains. */
    private static boolean includes(Object haystack, Object needle) {
        if (haystack == null || needle == null) return false;
        if (haystack instanceof Collection<?> c) {
            for (Object o : c) {
                if (java.util.Objects.equals(o, needle) || str(o).equals(str(needle))) return true;
            }
            return false;
        }
        // Fall back to comma-separated string membership (matches the TS
        // MULTISELECT handling: String(value).split(",")).
        String[] parts = str(haystack).split(",");
        for (String p : parts) {
            if (p.trim().equals(str(needle))) return true;
        }
        return false;
    }

    // ── Public escaping (also used by SuiServerRenderer + SsrTriggerMapper) ──

    /**
     * Escapes user data for HTML element bodies and double-quoted attribute
     * values. Mirrors {@code escapeHtml} in {@code renderer.ts} (same set,
     * including the single quote so single-quoted data-attrs are safe too).
     */
    /**
     * {@code data-sui-on-<event>} attributes for a node's event map — the same
     * output as the {@code events} Handlebars helper, for the few renderers
     * that build their markup in Java instead of a template (actions,
     * progress). Keeping one implementation is what keeps SSR and SPA in step.
     */
    public static String eventAttrs(ai.mindconnect.ui.model.UiNode node, ObjectMapper mapper,
                                    String... skip) {
        if (node == null) return "";
        var skipped = java.util.Set.of(skip);
        var sb = new StringBuilder();
        if (!skipped.contains("click")) appendEvent(sb, "click", node.getOnClick(), mapper);
        appendEvent(sb, "dblclick", node.getOnDblClick(), mapper);
        appendEvent(sb, "hover",    node.getOnHover(),    mapper);
        appendEvent(sb, "leave",    node.getOnLeave(),    mapper);
        if (!skipped.contains("change")) appendEvent(sb, "change", node.getOnChange(), mapper);
        appendEvent(sb, "input",    node.getOnInput(),    mapper);
        return sb.toString();
    }

    private static void appendEvent(StringBuilder sb, String name, UiTrigger trigger, ObjectMapper mapper) {
        if (trigger == null) return;
        sb.append(" data-sui-on-").append(name)
          .append("='").append(encodeTrigger(trigger, mapper)).append("'");
    }

    public static String escapeHtml(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            sb.append(HTML_ESCAPE.getOrDefault(ch, ch));
        }
        return sb.toString();
    }

    /** Attribute-context escaping. Same set as {@link #escapeHtml} today. */
    public static String escapeAttr(Object value) {
        return escapeHtml(value);
    }

    /**
     * Encodes a {@link UiTrigger} as a single-quoted {@code data-trigger}
     * attribute payload — JSON with single quotes HTML-escaped to
     * {@code &#39;}. Mirrors {@code encodeTrigger} in {@code renderer.ts}, so
     * the SSR-emitted attribute is byte-compatible with what the EventBus
     * parses if the page later upgrades to the SPA.
     */
    public static String encodeTrigger(UiTrigger trigger, ObjectMapper mapper) {
        try {
            String json = mapper.writeValueAsString(trigger);
            return json.replace("'", "&#39;");
        } catch (Exception e) {
            return "";
        }
    }

    // ── Cell-template substitution ────────────────────────────────────────────

    /**
     * Returns a deep-cloned copy of {@code template} with two transformations
     * applied to every nested node:
     *
     * <ul>
     *   <li>Every string field has {@code {key}} placeholders replaced from
     *       {@code ctx}. Unknown keys are left intact so the author sees the
     *       broken placeholder rather than a silent empty value.</li>
     *   <li>Every {@code id} string gets the suffix {@code __<rowSuffix>}
     *       so HTML id uniqueness holds across rows.</li>
     * </ul>
     *
     * <p>Round-trip through Jackson (toJson → parse → mutate) is the cheapest
     * way to clone an arbitrary UiNode subtree without a hand-coded visitor
     * for every subtype.
     */
    @SuppressWarnings("unchecked")
    static Object substituteCellTemplate(Object template, Map<String, Object> ctx,
                                          String rowSuffix, ObjectMapper mapper) {
        try {
            // Round-trip through JSON: serialise → walk the generic Map tree
            // (substituting strings + suffixing ids) → re-deserialise as a
            // UiNode so the SSR renderer's renderChild() finds a typed
            // instance and dispatches to the right template. Map-only output
            // would slip past renderChild's UiNode check and render to "".
            String json = mapper.writeValueAsString(template);
            Object cloned = mapper.readValue(json, Object.class);
            Object substituted = substituteWalk(cloned, ctx, rowSuffix);
            String mutatedJson = mapper.writeValueAsString(substituted);
            return mapper.readValue(mutatedJson, ai.mindconnect.ui.model.UiNode.class);
        } catch (Exception e) {
            return template;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object substituteWalk(Object node, Map<String, Object> ctx, String rowSuffix) {
        if (node instanceof java.util.List<?> list) {
            java.util.List<Object> out = new java.util.ArrayList<>(list.size());
            for (Object item : list) out.add(substituteWalk(item, ctx, rowSuffix));
            return out;
        }
        if (node instanceof Map<?, ?> map) {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>(map.size());
            for (var e : map.entrySet()) {
                String key = e.getKey().toString();
                Object value = e.getValue();
                if ("id".equals(key) && value instanceof String s && !rowSuffix.isEmpty()) {
                    out.put(key, s + "__" + rowSuffix);
                } else {
                    out.put(key, substituteWalk(value, ctx, rowSuffix));
                }
            }
            return out;
        }
        if (node instanceof String s) return substituteString(s, ctx);
        return node;
    }

    private static String substituteString(String s, Map<String, Object> ctx) {
        if (s == null || s.indexOf('{') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int open = s.indexOf('{', i);
            if (open < 0) { out.append(s, i, s.length()); break; }
            int close = s.indexOf('}', open + 1);
            if (close < 0) { out.append(s, i, s.length()); break; }
            String key = s.substring(open + 1, close);
            out.append(s, i, open);
            if (ctx.containsKey(key)) {
                Object v = ctx.get(key);
                out.append(v == null ? "" : v.toString());
            } else {
                // Unknown key: keep the literal placeholder so the author
                // sees it in the rendered UI rather than a silent blank.
                out.append(s, open, close + 1);
            }
            i = close + 1;
        }
        return out.toString();
    }

    // ── Coercion ──────────────────────────────────────────────────────────────

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(str(o)); } catch (Exception e) { return 0; }
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(str(o)); } catch (Exception e) { return 0; }
    }
}
