package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * The application frame: a {@link UiHeader} across the top, a {@link UiMenu}
 * down one side, and the page content filling the rest.
 *
 * <p>This is the one layout nearly every back-office application shares, and
 * composing it by hand from stacks is deceptively fiddly — the body needs to be
 * a positioning context for an overlay drawer, the content needs
 * {@code min-width: 0} so a wide table can't push the sidebar off-screen, and
 * the burger has to be wired to the menu by id while the menu is told not to
 * render its own. Getting any of those wrong fails in ways that look like a CSS
 * bug rather than a missing rule.
 *
 * <p>The node owns all of it: the renderers emit the three containers with the
 * {@code sui-shell*} classes that carry the layout rules in {@code sui.css},
 * and they wire {@link UiHeader#getMenuToggle()} to the menu's id while
 * suppressing the menu's own toggle. You supply the three parts; you write no
 * layout CSS.
 *
 * <pre>{@code
 * UiAppShell.of("shell")
 *     .header(UiHeader.of("Acme Admin").user(UiHeader.User.of("Ada", "AL", "/me")))
 *     .menu(UiMenu.of("nav", "Acme", dashboardItem, catalogGroup))
 *     .content(productsTable);
 * }</pre>
 *
 * <p>Content is addressable: the content container renders with the id
 * {@code <shell-id>-content}, so a navigation can return a {@link UiPatch} that
 * replaces only the content and leaves the header and menu untouched. See
 * {@link #contentId()}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiAppShell extends UiNode {

    /** Top bar. The renderer points its burger at {@link #menu}. */
    private UiHeader header;

    /**
     * Side navigation. The renderer forces {@code toggle = false} on it,
     * because the header owns the burger — otherwise the screen shows two.
     */
    private UiMenu menu;

    /** The page itself. Any node; usually a {@link UiStack}. */
    private UiNode content;

    /**
     * Optional bar across the bottom — status line, legal links, a version
     * number. Any node. It sits below the content and outside the scroll
     * area, so it stays put while the page scrolls.
     */
    private UiNode footer;

    /**
     * Whether the shell fills the viewport height (the default, and what
     * every admin layout does: the sidebar reaches the bottom of the window
     * and the content area scrolls inside it).
     *
     * <p>Set false when the shell is embedded in a larger page — a preview
     * pane, a docs example, a dashboard tile — and should take only the
     * height its container gives it.
     */
    private boolean fillViewport = true;

    public static UiAppShell of(String id) {
        var s = new UiAppShell();
        s.setId(id);
        return s;
    }

    public UiAppShell header(UiHeader header)   { this.header = header;   return this; }
    public UiAppShell menu(UiMenu menu)         { this.menu = menu;       return this; }
    public UiAppShell content(UiNode content)   { this.content = content; return this; }
    public UiAppShell footer(UiNode footer)     { this.footer = footer;   return this; }
    public UiAppShell fillViewport(boolean fill) { this.fillViewport = fill; return this; }

    /**
     * DOM id of the content container — the patch target for a navigation that
     * should leave the chrome alone:
     *
     * <pre>{@code
     * UiPatch.of().patch(UiPatch.Operation.replace(shell.contentId(), newPage));
     * }</pre>
     */
    public String contentId() {
        return contentId(getId());
    }

    /** Same rule as {@link #contentId()}, for callers that only have the id. */
    public static String contentId(String shellId) {
        return (shellId == null ? "shell" : shellId) + "-content";
    }
}
