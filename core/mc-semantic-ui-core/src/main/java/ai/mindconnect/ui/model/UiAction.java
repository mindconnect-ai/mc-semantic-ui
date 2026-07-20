package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Clickable UI element. The element answers two orthogonal questions:
 *
 * <ul>
 *   <li>{@link Appearance} — <em>what</em> it is in the DOM
 *       (button / link / icon). Drives the element type the renderer picks
 *       ({@code <button>} vs {@code <a>}) and the default CSS class.</li>
 *   <li>{@link Style} — <em>which colour scheme</em> it wears
 *       (primary / secondary / danger). Only meaningful for elements that
 *       look like buttons; links generally ignore it.</li>
 * </ul>
 *
 * <p>The behaviour when the element is clicked is captured in a single
 * {@link #onClick} {@link UiTrigger}. Splitting visuals from behaviour lets
 * the same trigger description be reused on other event sources (field
 * {@code onChange}, section {@code onVisible}, …), and grows naturally —
 * adding a new response mode (e.g. {@code COPY_TO_CLIPBOARD}) is one enum
 * value in {@link UiTrigger.Behavior} plus one switch-case in the JS
 * dispatcher, with no changes here.
 *
 * <p>Legacy builder helpers ({@link #dispatch}, {@link #stream},
 * {@link #download}, {@link #openBlob}, {@link #setHref}) are preserved as
 * thin wrappers around {@code onClick(...)} so existing assemblers keep
 * compiling unchanged.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiAction extends UiNode {

    /**
     * Colour scheme of a button-shaped action. Has no effect for
     * {@link Appearance#LINK} (links use the link colour from CSS).
     */
    public enum Style { PRIMARY, SECONDARY, DANGER }

    /**
     * How the action is rendered in the DOM. The renderer maps each value
     * to a specific element type and base CSS class.
     */
    public enum Appearance {
        /** Standard {@code <button>} with {@code sui-btn sui-btn--{style}} class. */
        BUTTON,
        /** Inline {@code <a class="sui-link">} — semantically a navigation link. */
        LINK,
        /** Icon-only {@code <button class="sui-icon-btn">} — label is the icon glyph. */
        ICON
    }

    private String label;
    private Style style;
    /** DOM shape — defaults to {@link Appearance#BUTTON} when null in JSON. */
    private Appearance appearance;
    private boolean enabled = true;
    private String disabledReason;
    /** Optional confirm-dialog text shown before the {@link #onClick} fires. */
    private String confirm;
    /**
     * Leading icon token (e.g. {@code "save"}, {@code "delete"}). Rendered
     * before the label for BUTTON/LINK; for {@link Appearance#ICON} it IS the
     * button glyph and {@link #label} becomes the accessible name. Resolved by
     * the swappable icon layer — a semantic alias, a raw sprite id, or (legacy)
     * an emoji rendered verbatim. See {@link UiIcon}.
     */
    private String icon;
    /**
     * Force the busy state declaratively: renders with the {@code is-loading}
     * spinner and disabled. Use it when the <em>server</em> owns the state —
     * push {@code loading:true} via a patch, then replace the button with the
     * result. The event bus already toggles the same class automatically for
     * the duration of a click's own request, so leave this false for that case.
     */
    private boolean loading;

    // ── factories ──────────────────────────────────────────────────────────

    public static UiAction primary(String id, String label) {
        return build(id, label, Style.PRIMARY, Appearance.BUTTON);
    }

    public static UiAction secondary(String id, String label) {
        return build(id, label, Style.SECONDARY, Appearance.BUTTON);
    }

    public static UiAction danger(String id, String label) {
        return build(id, label, Style.DANGER, Appearance.BUTTON);
    }

    /**
     * Semantic navigation link — renders as {@code <a>}. {@link Style} is not
     * applied; use the {@code sui-link} CSS class for visual tweaks. Common
     * use: "← Back to X" headers, "View" / "Edit" links inside list items.
     */
    public static UiAction link(String id, String label) {
        return build(id, label, null, Appearance.LINK);
    }

    /**
     * Icon-only button — the label IS the icon glyph (e.g. "🗑", "▶", "⬇").
     * Style still applies (e.g. danger for delete icons).
     */
    public static UiAction icon(String id, String iconGlyph) {
        return build(id, iconGlyph, Style.SECONDARY, Appearance.ICON);
    }

    private static UiAction build(String id, String label, Style style, Appearance appearance) {
        var a = new UiAction();
        a.setId(id);
        a.label = label;
        a.style = style;
        a.appearance = appearance;
        return a;
    }

    // ── visual modifiers ──────────────────────────────────────────────────

    public UiAction disabled(String reason) {
        this.enabled = false;
        this.disabledReason = reason;
        return this;
    }

    public UiAction confirm(String message) {
        this.confirm = message;
        return this;
    }

    public UiAction enabledIf(boolean condition, String disabledReason) {
        return condition ? this : disabled(disabledReason);
    }

    /** Override the style after construction (e.g. promoting an icon to danger). */
    public UiAction style(Style style) {
        this.style = style;
        return this;
    }

    /**
     * Set the leading icon token (fluent). For a BUTTON/LINK it renders before
     * the label; combine with {@code .appearance(ICON)} for an icon-only
     * control whose {@link #label} becomes the accessible name.
     */
    public UiAction icon(String iconToken) {
        this.icon = iconToken;
        return this;
    }

    /** Set the DOM appearance (fluent). */
    public UiAction appearance(Appearance appearance) {
        this.appearance = appearance;
        return this;
    }

    /** Force the declarative busy state (fluent). See {@link #loading}. */
    public UiAction loading(boolean loading) {
        this.loading = loading;
        return this;
    }

    // ── canonical behaviour setter ────────────────────────────────────────

    /** Primary API: attach any {@link UiTrigger} as the click behaviour. */
    public UiAction onClick(UiTrigger trigger) {
        setOnClick(trigger);
        return this;
    }

    // ── legacy helpers — write into onClick so existing code keeps working ─

    /**
     * Sets a plain navigation target (GET, render returned page). Kept for
     * compatibility with call sites that used to write directly to the
     * {@code href} field via the Lombok-generated setter.
     */
    public void setHref(String href) {
        setOnClick(UiTrigger.go(href));
    }

    /**
     * Convenience shortcut for {@code onClick(UiTrigger.api(method, href))}.
     */
    public UiAction dispatch(String method, String href) {
        return onClick(UiTrigger.api(method, href));
    }

    /**
     * Convenience shortcut for {@code onClick(UiTrigger.api(method, href, payloadNodeId))}.
     */
    public UiAction dispatch(String method, String href, String payloadNodeId) {
        return onClick(UiTrigger.api(method, href, payloadNodeId));
    }

    /**
     * Convenience shortcut for {@code onClick(UiTrigger.stream(method, href, payloadNodeId))}.
     */
    public UiAction stream(String method, String href, String payloadNodeId) {
        return onClick(UiTrigger.stream(method, href, payloadNodeId));
    }

    /**
     * Convenience shortcut for an authenticated file download. The
     * {@code filenameHint} parameter is kept for API compatibility but is
     * no longer used directly — the browser save dialog reads the filename
     * from the server's {@code Content-Disposition} header.
     */
    public UiAction download(String href, String filenameHint) {
        return onClick(UiTrigger.download(href));
    }

    /**
     * Convenience shortcut for {@code onClick(UiTrigger.openInTab(href))}.
     */
    public UiAction openBlob(String href) {
        return onClick(UiTrigger.openInTab(href));
    }
}
