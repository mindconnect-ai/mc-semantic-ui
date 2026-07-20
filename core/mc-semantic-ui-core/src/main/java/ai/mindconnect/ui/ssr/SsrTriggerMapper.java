package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Translates a {@link UiAction} into native, JavaScript-free HTML for the
 * server-rendered (SSR) mode.
 *
 * <p>In the SPA the TS renderer emits {@code data-trigger='{…JSON…}'} and
 * the EventBus intercepts the click. Without the EventBus loaded the
 * {@code data-trigger} does nothing — so in SSR mode triggers must become
 * plain {@code <a>} / {@code <form>} elements the browser handles natively:
 * <ul>
 *   <li>GET without payload → {@code <a href="url">label</a>}</li>
 *   <li>POST → {@code <form method="post" action="url"><button>label</button></form>}</li>
 *   <li>DELETE / PUT → {@code <form method="post">} with a hidden
 *       {@code _method} field (Spring's
 *       {@link org.springframework.web.filter.HiddenHttpMethodFilter}
 *       rewrites it)</li>
 *   <li>download / open-in-tab → plain {@code <a href>} (browser handles
 *       {@code Content-Disposition} / {@code target=_blank} natively)</li>
 *   <li>STREAM → no JS-free equivalent; rendered as a plain POST so the
 *       page at least submits (the response is a full reload, not a
 *       stream).</li>
 * </ul>
 *
 * <p>Row-action triggers carry placeholders like {@code {id}} (and pagination
 * carries {@code {page}}) that the TS renderer substitutes per row/button at
 * render time. The SSR side does the same via
 * {@link #render(UiAction, Map)}: the substitutions argument is a map of
 * placeholder→value pairs, applied to the trigger URL before HTML emission.
 *
 * <p>Called from Handlebars via the {@code action} helper (see
 * {@link SuiHandlebarsHelpers}). Pure logic, no Handlebars dependency, so it
 * stays unit-testable on its own.
 */
public final class SsrTriggerMapper {

    private SsrTriggerMapper() {}

    /** Default Jackson mapper for serialising the embedded data-trigger JSON. */
    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

    /** {@link #render(UiAction, Map)} with no placeholder substitutions. */
    public static String render(UiAction action) {
        return render(action, Map.of());
    }

    /**
     * Renders {@code action} as <b>hybrid</b> HTML — works without
     * JavaScript (native browser behaviour through {@code href} /
     * {@code <form action>}) <i>and</i> with the EventBus loaded
     * (intercepted via {@code data-trigger}).
     *
     * <p>Both attributes carry the SAME information derived from one
     * source: {@code action.onClick}, with placeholder substitutions
     * applied identically to both. Drift between the JS-free and the
     * EventBus path is therefore impossible.
     *
     * @param substitutions keys are placeholder names (without braces) and
     *                      values replace {@code {key}} in the trigger
     *                      URL — used for row-action {@code {id}} and
     *                      pagination {@code {page}} expansion.
     */
    public static String render(UiAction action, Map<String, String> substitutions) {
        return render(action, substitutions, DEFAULT_MAPPER);
    }

    /**
     * Renders an action as just a {@code <button type="submit">}, no
     * surrounding form. Used for actions that sit inside a {@code UiForm}
     * already — the outer {@code <form>} carries the action URL + method;
     * nesting another form here would be invalid HTML.
     *
     * <p>The button still carries {@code data-trigger} and {@code data-action}
     * so the EventBus can intercept the click; native browsers fall through
     * to the surrounding form's submit.
     */
    public static String renderButtonOnly(UiAction action) {
        if (action == null) return "";
        UiAction.Appearance appearance = action.getAppearance() != null
                ? action.getAppearance() : UiAction.Appearance.BUTTON;
        UiAction.Style style = action.getStyle() != null
                ? action.getStyle() : UiAction.Style.SECONDARY;
        boolean enabled = action.isEnabled() && !action.isLoading();
        String label = action.getLabel() == null ? "" : action.getLabel();
        String title = action.getDisabledReason() != null && !action.getDisabledReason().isEmpty()
                ? action.getDisabledReason() : label;

        UiTrigger trigger = action.getOnClick();
        String dataTrigger = trigger != null ? encodeTrigger(trigger, DEFAULT_MAPPER) : "";

        String cls = (appearance == UiAction.Appearance.LINK
                ? "sui-link"
                : appearance == UiAction.Appearance.ICON
                    ? "sui-icon-btn sui-icon-btn--" + style.name().toLowerCase()
                    : "sui-btn sui-btn--" + style.name().toLowerCase()) + busy(action);
        StringBuilder sb = new StringBuilder();
        sb.append("<button id=\"").append(escapeAttr(action.getId())).append("\"")
          .append(SuiHandlebarsHelpers.eventAttrs(action, DEFAULT_MAPPER, "click"))
          .append(" type=\"submit\" class=\"").append(cls).append("\"")
          .append(" data-action=\"").append(escapeAttr(action.getId())).append("\"");
        if (!dataTrigger.isEmpty()) {
            sb.append(" data-trigger='").append(dataTrigger).append("'");
        }
        sb.append(busyAttr(action));
        sb.append(" title=\"").append(escapeAttr(title)).append("\"");
        if (!enabled) sb.append(" disabled");
        sb.append(">").append(escapeHtml(label)).append("</button>");
        return sb.toString();
    }

    /** Variant with a caller-supplied ObjectMapper (for testing / shared instance). */
    public static String render(UiAction action, Map<String, String> substitutions, ObjectMapper mapper) {
        if (action == null) return "";

        UiAction.Appearance appearance = action.getAppearance() != null
                ? action.getAppearance() : UiAction.Appearance.BUTTON;
        UiAction.Style style = action.getStyle() != null
                ? action.getStyle() : UiAction.Style.SECONDARY;
        boolean enabled = action.isEnabled() && !action.isLoading();
        String label = action.getLabel() == null ? "" : action.getLabel();
        String title = action.getDisabledReason() != null && !action.getDisabledReason().isEmpty()
                ? action.getDisabledReason() : label;
        // Confirm dialog deliberately not emitted here — the UiAction.confirm
        // property stays in the model but the SSR renderer ignores it. A
        // future iteration will introduce a different confirmation mechanism
        // (server-side confirmation page, modal component, etc.) rather than
        // the legacy data-confirm / window.confirm approach.

        UiTrigger trigger = action.getOnClick();

        // ── No trigger: inert element ───────────────────────────────────────
        if (trigger == null) {
            return inertElement(appearance, style, action, actionDomId(action, substitutions),
                    enabled, title, label);
        }

        String method   = trigger.getMethod()   != null ? trigger.getMethod()   : "GET";
        String url      = trigger.getUrl()      != null ? trigger.getUrl()      : "";
        String payload  = trigger.getPayload();
        String behavior = trigger.getBehavior() != null ? trigger.getBehavior().name() : "APPLY_RESPONSE";

        // One substitution pass applied to both render paths: a UiTrigger
        // copy with the substituted URL becomes the source of truth for
        // both the native href/form action and the data-trigger JSON.
        String substitutedUrl = applySubstitutions(url, substitutions);
        UiTrigger triggerForBus = copyTriggerWithUrl(trigger, substitutedUrl);
        String dataTrigger = encodeTrigger(triggerForBus, mapper);

        // ── Anchor-style: <a href> + data-trigger ───────────────────────────
        String domId = actionDomId(action, substitutions);
        if (isAnchorStyle(method, payload, behavior, appearance)) {
            return anchor(action, domId, substitutedUrl, behavior, appearance, style, enabled,
                    title, label, dataTrigger);
        }

        // ── Form-wrapped button + data-trigger on the form ──────────────────
        return form(action, domId, substitutedUrl, method, appearance, style, enabled,
                title, label, dataTrigger);
    }

    /** Shallow copy of the trigger with the URL replaced. Keeps method / behavior / payload. */
    private static UiTrigger copyTriggerWithUrl(UiTrigger src, String url) {
        UiTrigger out = new UiTrigger();
        out.setMethod(src.getMethod());
        out.setBehavior(src.getBehavior());
        out.setPayload(src.getPayload());
        // Carry the client-handler name through so an INVOKE trigger emitted
        // via SSR still dispatches to the right handler once the bus loads.
        out.setHandler(src.getHandler());
        // Carry the inline patch through so a PATCH trigger keeps working once
        // the bus loads (no server URL involved).
        out.setPatch(src.getPatch());
        out.setUrl(url);
        return out;
    }

    /**
     * Effective DOM id for an action element. Plain actions use
     * {@code action.id}; row-actions get suffixed with the row id from
     * {@code substitutions} so the rendered button stays unique across rows.
     * The DOM id is what the editor's id-based selection uses — also what
     * downstream JS that does {@code getElementById} would read.
     */
    private static String actionDomId(UiAction action, Map<String, String> substitutions) {
        String base = action.getId() == null ? "" : action.getId();
        String rowId = substitutions == null ? null : substitutions.get("id");
        return (rowId != null && !rowId.isEmpty()) ? base + "__" + rowId : base;
    }

    /** UiTrigger → escaped JSON suitable for a single-quoted {@code data-trigger='…'} attribute. */
    private static String encodeTrigger(UiTrigger t, ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(t).replace("'", "&#39;");
        } catch (Exception e) {
            return "";
        }
    }

    // ── Decision helpers ──────────────────────────────────────────────────────

    /**
     * True when the trigger should render as an {@code <a>}. We pick a link
     * for plain navigation (GET with no payload) AND for action.appearance =
     * LINK regardless of method, so a "+ New" link doesn't suddenly become a
     * one-button form. Download / open-in-tab always anchor too.
     */
    private static boolean isAnchorStyle(String method, String payload, String behavior,
                                          UiAction.Appearance appearance) {
        if ("DOWNLOAD".equals(behavior) || "OPEN_IN_TAB".equals(behavior)) return true;
        if (appearance == UiAction.Appearance.LINK
                && "GET".equalsIgnoreCase(method)) return true;
        String m = method == null ? "GET" : method;
        return "GET".equalsIgnoreCase(m) && (payload == null || payload.isBlank());
    }

    // ── Render variants ───────────────────────────────────────────────────────

    private static String anchor(UiAction action, String domId, String url, String behavior,
                                  UiAction.Appearance appearance, UiAction.Style style,
                                  boolean enabled, String title, String label,
                                  String dataTrigger) {
        StringBuilder sb = new StringBuilder();
        String cls = (appearance == UiAction.Appearance.LINK
                ? "sui-link"
                : appearance == UiAction.Appearance.ICON
                    ? "sui-icon-btn sui-icon-btn--" + style.name().toLowerCase()
                    : "sui-btn sui-btn--" + style.name().toLowerCase()) + busy(action);
        sb.append("<a id=\"").append(escapeAttr(domId)).append("\"")
          .append(SuiHandlebarsHelpers.eventAttrs(action, DEFAULT_MAPPER, "click"))
          .append(" class=\"").append(cls).append("\"")
          .append(" href=\"").append(escapeAttr(url)).append("\"")
          .append(" data-action=\"").append(escapeAttr(action.getId())).append("\"")
          // data-trigger carries the same intent in EventBus-readable form.
          // Hybrid: native browser navigates via href when no JS, EventBus
          // intercepts and dispatches via data-trigger when present.
          .append(" data-trigger='").append(dataTrigger).append("'")
          .append(iconAriaLabel(appearance, action))
          .append(busyAttr(action))
          .append(" title=\"").append(escapeAttr(title)).append("\"");
        if ("OPEN_IN_TAB".equals(behavior)) {
            sb.append(" target=\"_blank\" rel=\"noopener\"");
        }
        if ("DOWNLOAD".equals(behavior)) {
            sb.append(" download");
        }
        if (!enabled) sb.append(" aria-disabled=\"true\"");
        sb.append(">").append(actionInner(appearance, action, label)).append("</a>");
        return sb.toString();
    }

    private static String form(UiAction action, String domId, String url, String method,
                                UiAction.Appearance appearance, UiAction.Style style,
                                boolean enabled, String title, String label,
                                String dataTrigger) {
        String m = method.toUpperCase();
        StringBuilder sb = new StringBuilder();
        // Native form methods are GET/POST. Everything else (DELETE/PUT/PATCH)
        // tunnels through POST + _method.
        boolean tunneled = !"GET".equals(m) && !"POST".equals(m);
        // The outermost element is the <form>, so that's where id="…" goes —
        // matches the "id on the wrapper" convention used by every other
        // node renderer and keeps the editor's id-based selection working.
        sb.append("<form id=\"").append(escapeAttr(domId)).append("\"")
          .append(" method=\"").append(tunneled ? "post" : m.toLowerCase()).append("\"")
          .append(" action=\"").append(escapeAttr(url)).append("\"")
          .append(" data-trigger='").append(dataTrigger).append("'")
          .append(" class=\"sui-ssr-form\">");
        if (tunneled) {
            sb.append("<input type=\"hidden\" name=\"_method\" value=\"")
              .append(escapeAttr(m)).append("\">");
        }
        String cls = (appearance == UiAction.Appearance.ICON
                ? "sui-icon-btn sui-icon-btn--" + style.name().toLowerCase()
                : "sui-btn sui-btn--" + style.name().toLowerCase()) + busy(action);
        sb.append("<button type=\"submit\" class=\"").append(cls).append("\"")
          .append(" data-action=\"").append(escapeAttr(action.getId())).append("\"")
          .append(iconAriaLabel(appearance, action))
          .append(busyAttr(action))
          .append(" title=\"").append(escapeAttr(title)).append("\"");
        if (!enabled) sb.append(" disabled");
        sb.append(">").append(actionInner(appearance, action, label)).append("</button>")
          .append("</form>");
        return sb.toString();
    }

    private static String inertElement(UiAction.Appearance appearance, UiAction.Style style,
                                        UiAction action, String domId, boolean enabled,
                                        String title, String label) {
        String cls = (appearance == UiAction.Appearance.LINK
                ? "sui-link"
                : appearance == UiAction.Appearance.ICON
                    ? "sui-icon-btn sui-icon-btn--" + style.name().toLowerCase()
                    : "sui-btn sui-btn--" + style.name().toLowerCase()) + busy(action);
        return "<button id=\"" + escapeAttr(domId) + "\""
                + SuiHandlebarsHelpers.eventAttrs(action, DEFAULT_MAPPER, "click")
                + " type=\"button\" class=\"" + cls + "\""
                + " data-action=\"" + escapeAttr(action.getId()) + "\""
                + iconAriaLabel(appearance, action)
                + busyAttr(action)
                + " title=\"" + escapeAttr(title) + "\""
                + (enabled ? "" : " disabled")
                + ">" + actionInner(appearance, action, label) + "</button>";
    }

    // ── Icon helpers (mirror renderers/action.ts) ─────────────────────────────

    /**
     * Inner HTML of an action element. For BUTTON/LINK: leading icon + label.
     * For ICON: the icon glyph alone (label becomes the accessible name), or
     * the label text when no icon is set (legacy emoji-as-label buttons).
     */
    private static String actionInner(UiAction.Appearance appearance, UiAction action, String label) {
        String icon = action.getIcon();
        boolean hasIcon = icon != null && !icon.isEmpty();
        if (appearance == UiAction.Appearance.ICON) {
            return hasIcon ? IconRenderer.render(icon, null, action.getLabel()) : escapeHtml(label);
        }
        String lead = hasIcon ? IconRenderer.render(icon) + " " : "";
        return lead + escapeHtml(label);
    }

    /** Busy modifier: {@code " is-loading"} when the action is declaratively
     *  loading (mirrors the SPA renderer), else empty. The action is also
     *  rendered disabled — see the {@code enabled} computation. */
    private static String busy(UiAction action) {
        return action != null && action.isLoading() ? " is-loading" : "";
    }

    /** {@code aria-busy="true"} for a loading action, else empty. */
    private static String busyAttr(UiAction action) {
        return action != null && action.isLoading() ? " aria-busy=\"true\"" : "";
    }

    /** aria-label for an icon-only button whose visible content is a glyph. */
    private static String iconAriaLabel(UiAction.Appearance appearance, UiAction action) {
        boolean hasIcon = action.getIcon() != null && !action.getIcon().isEmpty();
        return appearance == UiAction.Appearance.ICON && hasIcon
                ? " aria-label=\"" + escapeAttr(action.getLabel()) + "\"" : "";
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Replaces {@code {key}} with {@code value} for every entry of {@code subs}. */
    public static String applySubstitutions(String s, Map<String, String> subs) {
        if (s == null || subs == null || subs.isEmpty()) return s;
        String out = s;
        for (var entry : subs.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}",
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return out;
    }

    private static String escapeHtml(Object value) { return SuiHandlebarsHelpers.escapeHtml(value); }
    private static String escapeAttr(Object value) { return SuiHandlebarsHelpers.escapeAttr(value); }
}
