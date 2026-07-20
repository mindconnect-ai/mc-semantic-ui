package ai.mindconnect.sui.demo.shop.ui;

import ai.mindconnect.sui.demo.shop.DemoUser;
import ai.mindconnect.sui.demo.shop.ModeToggleController;
import ai.mindconnect.sui.demo.shop.SuiThemeFilter;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiHeader;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiSection;
import ai.mindconnect.ui.model.UiStack;

import java.util.List;

/**
 * Top-level admin shell — shared by every page in the demo. Renders as a
 * vertical stack:
 *
 * <ol>
 *   <li>{@link UiHeader} with the shop brand on the left and the current
 *       user's avatar (initials in a circle) + name on the right. The
 *       avatar links to {@code /admin/profile}.</li>
 *   <li>A {@link UiSection} with tabs for Products and Customers. Each tab
 *       is a real {@code <a href>} (SSR-friendly): clicking it navigates
 *       to the tab's URL, no JS needed.</li>
 *   <li>The page-specific body inside the active tab's panel — or no
 *       active tab when {@code activeTab} is {@code null} (used for the
 *       standalone profile view, where neither Products nor Customers
 *       should appear highlighted).</li>
 * </ol>
 *
 * <p>The header stays identical across all three destinations
 * (/admin/products, /admin/customers, /admin/profile), so the user never
 * loses their bearings when navigating.
 */
public final class AdminPage {

    /** Which top-level tab is highlighted. {@code null} = none (profile view). */
    public enum Tab {
        PRODUCTS("products", "Products", "/admin/products"),
        CUSTOMERS("customers", "Customers", "/admin/customers");

        public final String id;
        public final String label;
        public final String href;

        Tab(String id, String label, String href) {
            this.id = id; this.label = label; this.href = href;
        }
    }

    private final DemoUser user;
    private final Tab activeTab;
    private final UiNode activeContent;
    private final boolean spaMode;
    private final String theme;

    /**
     * @param user           current user for the header widget
     * @param activeTab      which tab to highlight; {@code null} for pages
     *                       that don't belong to either tab (e.g. profile)
     * @param activeContent  body rendered below the tab bar
     * @param spaMode        whether the page is currently rendered with the
     *                       SPA bootstrap injected — drives the label of
     *                       the SSR/SPA toggle button
     * @param theme          current theme name ({@code light}/{@code dark}/
     *                       {@code sbb}) — drives the label of the theme
     *                       toggle button
     */
    public AdminPage(DemoUser user, Tab activeTab, UiNode activeContent, boolean spaMode, String theme) {
        this.user = user;
        this.activeTab = activeTab;
        this.activeContent = activeContent;
        this.spaMode = spaMode;
        this.theme = theme != null ? theme : SuiThemeFilter.THEME_LIGHT;
    }

    /**
     * Convenience constructor that keeps the previous spaMode-only signature
     * working. Defaults the theme to {@code light}.
     */
    public AdminPage(DemoUser user, Tab activeTab, UiNode activeContent, boolean spaMode) {
        this(user, activeTab, activeContent, spaMode, SuiThemeFilter.THEME_LIGHT);
    }

    /**
     * Convenience constructor defaulting to SSR + light — used by tests and
     * any caller that doesn't care about the toggle button labels.
     */
    public AdminPage(DemoUser user, Tab activeTab, UiNode activeContent) {
        this(user, activeTab, activeContent, false, SuiThemeFilter.THEME_LIGHT);
    }

    public UiPage render() {
        // 1. Header — same on every admin page. Mode + theme pickers sit
        // in the extras slot (between brand and user widget) so they're
        // always one click away from anywhere in the app.
        var header = UiHeader.of("🛒 Shop Admin")
                .brandHref("/admin/products")
                .extra(modeSwitcherForm())
                .extra(themeSwitcherForm())
                .user(UiHeader.User.of(user.name(), user.initials(), user.profileHref()));
        header.setId("admin-header");

        // 2. Tab bar + body. The titled UiSection renders the tabs from
        // its `sections` list; the active tab's panel carries the inbound
        // content. We use a generic title-less wrapper if there's no
        // active tab so the bar still shows but nothing is highlighted.
        var tabSection = UiSection.of("admin-root", null);
        for (Tab t : Tab.values()) {
            UiNode content = (t == activeTab) ? activeContent : empty(t);
            tabSection.section(t.id, t.label, t.href, content);
        }
        if (activeTab != null) {
            tabSection.initialSection(activeTab.id);
        }

        // 3. Profile view: no tab highlighted → render the activeContent
        // below the tab bar (not inside any tab panel). We stash it in a
        // vertical stack so both children sit one beneath the other.
        UiNode middleAndBelow = (activeTab != null)
                ? tabSection
                : UiStack.of("admin-noactive")
                        .child(tabSection)
                        .child(activeContent);

        // 4. Compose: header (carries both pickers in extras) on top,
        // tabs / content underneath, no separate toggle bar.
        var root = UiStack.of("admin-shell")
                .child(header)
                .child(middleAndBelow);

        return UiPage.of(pathFor(activeTab), root);
    }

    /**
     * Header dropdown: a {@code <select>} of SSR / SPA, marked
     * {@code submitOnChange} so picking an option POSTs to
     * {@code /admin/toggle-mode?mode=…} and {@code reloadOnSubmit} so the
     * browser does a native full-page navigation. The reload is essential —
     * swapping the SPA bootstrap script in/out of the {@code <head>} can't
     * happen via SPA mount (the EventBus only owns {@code #sui-root}).
     */
    private UiForm modeSwitcherForm() {
        var options = List.of(
                UiField.Option.of(ModeToggleController.MODE_SSR, "SSR"),
                UiField.Option.of(ModeToggleController.MODE_SPA, "SPA"));
        var current = spaMode ? ModeToggleController.MODE_SPA : ModeToggleController.MODE_SSR;
        var pick = UiField.select("mode", "Mode", current, options)
                .asEditable()
                .submitOnChange();
        return UiForm.of("mode-picker", null)
                .<UiForm>withCssClass("sui-mode-picker")
                .reloadOnSubmit()
                .field(pick)
                .action(UiAction.secondary("apply-mode", "Apply")
                        .dispatch("POST", "/admin/toggle-mode"));
    }

    /**
     * Header dropdown: a {@code <select>} of all known themes, marked
     * {@code submitOnChange} so picking an option immediately POSTs to
     * {@code /admin/toggle-theme?theme=…} and {@code reloadOnSubmit} so the
     * browser swaps the stylesheet link in {@code <head>} via a full page
     * navigation (the SPA mount path only refreshes {@code #sui-root}).
     *
     * <p>The form has no visible Save button — the change IS the action.
     * Framework CSS (.sui-header-extras) hides the implicit field label
     * and footer so only the select pill shows up in the header.
     */
    private UiForm themeSwitcherForm() {
        var options = List.of(
                UiField.Option.of(SuiThemeFilter.THEME_LIGHT, "Light"),
                UiField.Option.of(SuiThemeFilter.THEME_DARK,  "Dark"),
                UiField.Option.of(SuiThemeFilter.THEME_SBB,   "SBB"));
        var pick = UiField.select("theme", "Theme", theme, options)
                .asEditable()
                .submitOnChange();
        return UiForm.of("theme-picker", null)
                .<UiForm>withCssClass("sui-theme-picker")
                .reloadOnSubmit()
                .field(pick)
                .action(UiAction.secondary("apply", "Apply")
                        .dispatch("POST", "/admin/toggle-theme"));
    }

    private static String pathFor(Tab activeTab) {
        return activeTab == null ? "/admin/profile" : activeTab.href;
    }

    /** Placeholder for inactive tabs' panels — never seen by the user. */
    private static UiNode empty(Tab t) {
        return UiStack.of("placeholder-" + t.id);
    }
}
