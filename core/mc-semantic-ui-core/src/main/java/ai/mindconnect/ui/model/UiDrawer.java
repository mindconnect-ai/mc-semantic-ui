package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * A panel that slides in from an edge and can be minimized to a handle — a
 * chat that rises from the bottom of a mail composer, an inspector at the
 * right of a page.
 *
 * <pre>{@code
 * UiDrawer.of("ai-chat", "Draft with AI", UiCustom.of("chat-widget").prop("session", sid))
 *     .edge(UiDrawer.Edge.BOTTOM).size("45%").minSize("200px").maxSize("80%")
 *     .resizable().scope(UiDrawer.Scope.CONTAINER)
 *     .icon("sparkles").closable().onClose(UiTrigger.api("DELETE", "/chat/" + sid));
 * }</pre>
 *
 * <h2>States</h2>
 * {@link State#OPEN} shows the panel; {@link State#MINIMIZED} only a handle
 * at the edge — title, icon, {@link #badge}; {@link State#CLOSED} nothing.
 * Minimizing and opening again happen in the browser alone, and the content
 * is not redrawn: what was typed and the state of a widget inside survive. A
 * patch that replaces the drawer keeps the state the user chose unless it
 * sets one ({@link #state} is null by default, and null means "as it is").
 *
 * <h2>Where</h2>
 * {@link Scope#VIEWPORT} (the default) pins it to the window;
 * {@link Scope#CONTAINER} to the nearest positioned container — a dialog, or
 * any node with the css class {@code sui-drawer-host} — over its content and
 * inside its bounds. {@link Mode#OVERLAY} (the default) lies over the content;
 * {@link Mode#PUSH} takes room beside it, as a sidebar menu does: put the
 * drawer next to the content in a flex row (LEFT/RIGHT) or column
 * (TOP/BOTTOM). On a narrow screen a LEFT or RIGHT drawer comes from the
 * bottom instead.
 *
 * <p>Several drawers may be open at once, at different edges; minimized
 * handles at the same edge line up side by side. At one edge of one area
 * one drawer is open at a time: when the user opens a second, the first goes
 * to its handle rather than lying hidden under the new one. Two that should
 * be open together belong in a {@link UiDrawerGroup}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiDrawer extends UiNode {

    /** A CSS length: a number and a unit — nothing else can reach the style attribute. */
    public static final java.util.regex.Pattern CSS_LENGTH =
            java.util.regex.Pattern.compile("\\d{1,5}(?:\\.\\d{1,3})?(?:px|rem|em|vh|vw|dvh|svh|lvh|%)");

    /** The edge it slides in from. */
    public enum Edge { TOP, BOTTOM, LEFT, RIGHT }

    /** Shown, reduced to its handle, or gone. */
    public enum State { OPEN, MINIMIZED, CLOSED }

    /** What its position is relative to. */
    public enum Scope { VIEWPORT, CONTAINER }

    /** Over the content, or beside it. */
    public enum Mode { OVERLAY, PUSH }

    /** Its content — any node; the drawer never redraws it on its own. */
    private UiNode content;
    /** Leading icon on the handle and in the header. */
    private String icon;
    /** Short count or status on the handle ({@code "3"}, {@code "new"}). */
    private String badge;
    /** Where it comes from. Null means {@link Edge#RIGHT}. */
    private Edge edge;
    /**
     * Shown, minimized or closed. Null means "as it is": a new drawer opens,
     * a replaced one keeps what the user made of it.
     */
    private State state;
    /** Height for TOP/BOTTOM, width for LEFT/RIGHT — a CSS length ({@code "40%"}, {@code "360px"}). */
    private String size;
    /** Lower bound of {@link #size}, also when resized. */
    private String minSize;
    /** Upper bound of {@link #size}, also when resized. */
    private String maxSize;
    /** The inner edge can be dragged to resize it. Omitted from JSON when false. */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean resizable;
    /** Null means {@link Scope#VIEWPORT}. */
    private Scope scope;
    /** Null means {@link Mode#OVERLAY}. */
    private Mode mode;
    /** An X closes it. Omitted from JSON when false. */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean closable;
    /** Fired when the X closes it, so the server can clean up. */
    private UiTrigger onClose;
    /**
     * Fired whenever the user changes its state; {@code {state}} in the URL
     * becomes {@code OPEN}, {@code MINIMIZED} or {@code CLOSED}.
     */
    private UiTrigger onStateChange;

    /** A drawer with {@code content}, titled {@code title}. */
    public static UiDrawer of(String id, String title, UiNode content) {
        UiDrawer d = new UiDrawer();
        d.setId(id);
        d.setTitle(title);
        d.content = content;
        return d;
    }

    public UiDrawer edge(Edge edge) { this.edge = edge; return this; }

    public UiDrawer state(State state) { this.state = state; return this; }

    /** Starts open (and, on a patch, opens it). */
    public UiDrawer open() { return state(State.OPEN); }

    /** Starts as its handle (and, on a patch, minimizes it). */
    public UiDrawer minimized() { return state(State.MINIMIZED); }

    /** Starts closed (and, on a patch, closes it). */
    public UiDrawer closed() { return state(State.CLOSED); }

    /** @throws IllegalArgumentException unless a CSS length such as {@code "40%"} */
    public UiDrawer size(String size) { setSize(size); return this; }

    /** @throws IllegalArgumentException unless a CSS length */
    public UiDrawer minSize(String size) { setMinSize(size); return this; }

    /** @throws IllegalArgumentException unless a CSS length */
    public UiDrawer maxSize(String size) { setMaxSize(size); return this; }

    public UiDrawer resizable() { this.resizable = true; return this; }

    public UiDrawer scope(Scope scope) { this.scope = scope; return this; }

    public UiDrawer mode(Mode mode) { this.mode = mode; return this; }

    public UiDrawer closable() { this.closable = true; return this; }

    public UiDrawer onClose(UiTrigger trigger) { this.closable = true; this.onClose = trigger; return this; }

    public UiDrawer onStateChange(UiTrigger trigger) { this.onStateChange = trigger; return this; }

    public UiDrawer icon(String icon) { this.icon = icon; return this; }

    public UiDrawer badge(String badge) { this.badge = badge; return this; }

    public void setSize(String size) { this.size = length("size", size); }

    public void setMinSize(String size) { this.minSize = length("minSize", size); }

    public void setMaxSize(String size) { this.maxSize = length("maxSize", size); }

    /** The sizes go into a style attribute: a number and a unit, nothing else. */
    private static String length(String what, String value) {
        if (value != null && !CSS_LENGTH.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be a CSS length such as \"40%\" or \"360px\": " + value);
        }
        return value;
    }
}
