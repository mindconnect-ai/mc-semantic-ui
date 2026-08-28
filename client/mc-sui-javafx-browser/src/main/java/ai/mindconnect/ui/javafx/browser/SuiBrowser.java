package ai.mindconnect.ui.javafx.browser;

import ai.mindconnect.ui.javafx.SuiFxEventBus;
import ai.mindconnect.ui.javafx.SuiFxOverlay;
import ai.mindconnect.ui.javafx.SuiFxRenderer;
import ai.mindconnect.ui.javafx.SuiFxStyles;
import ai.mindconnect.ui.javafx.shell.SuiFxShell;
import ai.mindconnect.ui.model.UiTrigger;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.net.URI;

/**
 * A browser for {@code UiNode} servers: type a url, and whatever the server
 * sends back is painted by the JavaFX renderer.
 *
 * <p>It knows nothing about any particular application — no endpoint list, no
 * model classes of its own, no assumptions about what a page contains. It
 * fetches one url; everything after that is driven by the triggers in the tree
 * the server sent. That is the whole point: if a screen works in the browser's
 * SPA renderer, the same JSON should work here, and where it does not, the gap
 * is a renderer gap worth knowing about.
 *
 * <p>Navigation is an ordinary {@code APPLY_RESPONSE} GET — the same trigger a
 * link in the page would fire. The address bar is this client's answer to the
 * one thing a desktop window lacks, so the bus's navigate hint lands here
 * rather than in a browser's location bar.
 */
public class SuiBrowser extends BorderPane {

    private final SuiFxOverlay overlay = new SuiFxOverlay();
    private final SuiFxRenderer renderer = SuiFxRenderer.createDefaultRenderer(overlay);
    private final SuiFxEventBus bus = new SuiFxEventBus(renderer);
    private final BrowserHistory history = new BrowserHistory();

    private final TextField address = new TextField();
    private final Button back = new Button();
    private final Button forward = new Button();
    private final Button reload = new Button();

    public SuiBrowser() {
        SuiFxShell.install(renderer);
        // The overlay carries sui-fx.css for its own subtree, but the toolbar
        // sits outside it and is written in the same -sui-* palette — so the
        // window root needs the stylesheet as well.
        SuiFxStyles.install(this);
        SuiFxShell.style(this);
        style(this);

        // createDefaultRenderer(overlay) already routes toasts and the busy
        // state there, so the overlay needs no wiring of its own.
        // A page that says where it now lives updates the address bar — the
        // desktop's stand-in for a history push.
        // The bus hands this over already resolved against the page it came
        // from, so what lands in the address bar is always absolute — a
        // relative entry would be meaningless once typed back in.
        bus.setNavigateHandler(url -> {
            address.setText(url);
            history.visit(url);
        });

        setTop(toolbar());
        setCenter(overlay);
        getStyleClass().add("sui-browser");
    }

    /** The bus driving this window — hand it your own client handlers if you like. */
    public SuiFxEventBus bus() {
        return bus;
    }

    public SuiFxRenderer renderer() {
        return renderer;
    }

    public BrowserHistory history() {
        return history;
    }

    /**
     * Fetches {@code url} and paints whatever comes back.
     *
     * <p>Deliberately an ordinary trigger rather than a private fetch path: a
     * typed url and a clicked link must take exactly the same route, or this
     * client would be testing something the real one never does.
     */
    public void go(String url) {
        var target = normalize(url);
        if (target == null) return;

        // A typed url is a fresh start, so it becomes the base immediately:
        // the response's own links resolve against it, and a server that
        // answers with a bare node rather than a page still gets a base.
        renderer.setDocumentBase(target);
        address.setText(target);
        history.visit(target);
        bus.dispatch(UiTrigger.go(target), null, renderer.context());
    }

    /**
     * Fills in what a person leaves out. Typing {@code localhost:8080/ui} into
     * an address bar and getting an error about a missing scheme is the kind of
     * thing that makes a tool feel hostile.
     */
    static String normalize(String url) {
        if (url == null) return null;
        var trimmed = url.trim();
        if (trimmed.isEmpty()) return null;
        if (!trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) trimmed = "http://" + trimmed;
        try {
            URI.create(trimmed);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
        return trimmed;
    }

    private HBox toolbar() {
        icon(back, "arrow-left", "Back");
        icon(forward, "arrow-right", "Forward");
        icon(reload, "rotate-cw", "Reload");

        back.disableProperty().bind(history.canGoBackProperty().not());
        forward.disableProperty().bind(history.canGoForwardProperty().not());

        // Back and forward replay a url without recording it again, or the two
        // buttons would push each other's entries onto the stack forever.
        back.setOnAction(e -> replay(history.back()));
        forward.setOnAction(e -> replay(history.forward()));
        reload.setOnAction(e -> replay(history.current()));

        address.setPromptText("http://localhost:8080/ui");
        address.getStyleClass().add("sui-browser-address");
        address.setOnAction(e -> go(address.getText()));
        HBox.setHgrow(address, Priority.ALWAYS);

        var bar = new HBox(6, back, forward, reload, address);
        bar.getStyleClass().add("sui-browser-toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void replay(String url) {
        if (url == null) return;
        renderer.setDocumentBase(url);
        address.setText(url);
        bus.dispatch(UiTrigger.go(url), null, renderer.context());
    }

    private void icon(Button button, String token, String tooltip) {
        var glyph = renderer.icon(token);
        if (glyph != null) button.setGraphic(glyph.inherit(button));
        else button.setText(tooltip);
        button.setAccessibleText(tooltip);
        button.getStyleClass().add("sui-browser-btn");
    }

    private static void style(javafx.scene.Parent root) {
        var css = SuiBrowser.class.getResource("/sui-fx/sui-fx-browser.css");
        if (css == null) return;
        var url = css.toExternalForm();
        if (!root.getStylesheets().contains(url)) root.getStylesheets().add(url);
    }
}
