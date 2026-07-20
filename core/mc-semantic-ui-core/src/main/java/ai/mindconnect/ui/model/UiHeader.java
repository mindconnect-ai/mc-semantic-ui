package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Page-level header: brand on the left, current-user widget on the right.
 * The intended placement is at the very top of a page, above any
 * {@link UiSection} that holds the main content (typical layout: header
 * → tabs → body).
 *
 * <p>Deliberately small and opinionated — the goal is a reusable
 * "application chrome" primitive, not a generic top-bar builder. Apps
 * that need more (search bar, notifications, multi-language switcher, …)
 * should compose the header with adjacent nodes or contribute a richer
 * widget through an extension node type.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiHeader extends UiNode {

    /**
     * Brand text rendered on the left, e.g. {@code "🛒 Shop Admin"}. Stays
     * a plain string so it survives the renderer's HTML escaping
     * unchanged — emoji and accents work, raw HTML doesn't.
     */
    private String brand;

    /**
     * Optional link target for the brand. When set, the brand renders as
     * an anchor; clicks navigate to {@code brandHref}. Useful as a "home"
     * shortcut. Null = inert text.
     */
    private String brandHref;

    /**
     * Optional logo image URL rendered to the left of the {@link #brand}
     * text, inside the same brand anchor/span. When set, the renderer emits
     * an {@code <img>} before the brand label. Null = text-only brand.
     */
    private String brandLogo;

    /** Current-user widget on the right. Null hides the widget entirely. */
    private User user;

    /**
     * Optional id of a {@link UiMenu} this header controls. When set, a
     * hamburger button is rendered at the very left of the header; clicking it
     * cycles that menu's state (the same {@code data-menu-toggle} hook the
     * menu's own toggle uses). This is how you move the hamburger out of the
     * sidebar and into the top bar — the usual place for it in an admin shell,
     * and the natural pairing for an overlay/drawer menu. Null = no burger.
     */
    private String menuToggle;

    /**
     * Optional extra widgets rendered between the brand and the user widget
     * — e.g. a theme picker, a language switcher, notifications. Each entry
     * is a full {@link UiNode} that the renderer recurses into, so apps can
     * drop a {@link UiForm} (with a {@code submitOnChange} {@code SELECT})
     * or any custom node in here.
     *
     * <p>Null / empty = nothing rendered (clean two-part header). Multiple
     * entries are laid out side-by-side via the {@code .sui-header-extras}
     * flex container.
     */
    private List<UiNode> extras;

    /**
     * What happens to {@link #extras} when they don't fit the bar's width —
     * the same choice {@link UiSection#getTabOverflow()} offers for tabs.
     *
     * <ul>
     *   <li>{@link ExtrasOverflow#WRAP} (default) — the header grows taller and
     *       the extras wrap onto a second line. No JavaScript involved, so it
     *       also works in plain SSR.</li>
     *   <li>{@link ExtrasOverflow#MENU} — the bar stays one row high and the
     *       entries that don't fit collapse into a trailing "⋯" dropdown.
     *       Needs the browser bundle ({@code wireHeaderOverflow}); without it
     *       the extras simply wrap, so nothing is ever unreachable.</li>
     * </ul>
     */
    public enum ExtrasOverflow { WRAP, MENU }

    private ExtrasOverflow extrasOverflow;

    public UiHeader extrasOverflow(ExtrasOverflow overflow) {
        this.extrasOverflow = overflow;
        return this;
    }

    /**
     * Compact current-user representation for the header widget. Avatar
     * is drawn as a CSS-styled circle with {@link #initials} inside —
     * no image asset needed, no JavaScript involved.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class User {
        /** Display name shown next to the avatar. */
        private String name;
        /**
         * 1–2 character abbreviation shown inside the avatar circle.
         * Typically the user's initials (e.g. {@code "DA"}). The
         * renderer doesn't truncate — pass exactly what you want shown.
         */
        private String initials;
        /** Navigation target when the user clicks the widget. */
        private String profileHref;

        public static User of(String name, String initials, String profileHref) {
            var u = new User();
            u.name = name; u.initials = initials; u.profileHref = profileHref;
            return u;
        }
    }

    public static UiHeader of(String brand) {
        var h = new UiHeader();
        h.brand = brand;
        return h;
    }

    public UiHeader brandHref(String href) { this.brandHref = href; return this; }
    public UiHeader brandLogo(String url)  { this.brandLogo = url;   return this; }
    public UiHeader user(User user)        { this.user = user;       return this; }
    /** Render a leading hamburger that toggles the {@link UiMenu} with this id. */
    public UiHeader menuToggle(String menuId) { this.menuToggle = menuId; return this; }

    /** Appends an extra widget to the header. Initialises the list lazily. */
    public UiHeader extra(UiNode node) {
        if (this.extras == null) this.extras = new ArrayList<>();
        this.extras.add(node);
        return this;
    }
}
