package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiAppShell;
import ai.mindconnect.ui.model.UiHeader;
import ai.mindconnect.ui.model.UiMenu;
import ai.mindconnect.ui.model.UiMenuItem;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiText;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The app shell and its header — the frame a whole screen hangs in.
 *
 * <p>Base components, so they are registered by the default renderer and need
 * no installing. Only {@code iframe} was ever worth a module of its own, and
 * only because a WebView drags a WebKit build in behind it.
 */
class SuiFxAppShellTest {

    @BeforeAll
    static void startToolkit() {
        System.setProperty("prism.order", "sw");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Another test class got there first — fine.
        } catch (Throwable noDisplay) {
            assumeTrue(false, "No JavaFX toolkit available here: " + noDisplay);
        }
        Platform.setImplicitExit(false);
    }

    private static SuiFxEventBus bus() {
        return new SuiFxEventBus();
    }

    @Test
    void aShellStacksHeaderBodyAndFooter() {
        var shell = UiAppShell.of("app")
                .header(UiHeader.of("Acme"))
                .content(UiText.of("page", "hello"))
                .footer(UiText.of("foot", "© Acme"));

        var painted = (VBox) onFxThread(() -> bus().mount(shell));

        assertThat(painted.getStyleClass()).contains("sui-shell");
        assertThat(painted.getChildren()).hasSize(3);
        assertThat(painted.getChildren().get(0).getStyleClass()).contains("sui-header");
        assertThat(painted.getChildren().get(1).getStyleClass()).contains("sui-shell-body");
        assertThat(painted.getChildren().get(2).getStyleClass()).contains("sui-shell-footer");
    }

    @Test
    void theContentSlotIsPatchableWhileTheChromeStaysPut() {
        var shell = UiAppShell.of("app")
                .header(UiHeader.of("Acme"))
                .content(UiText.of("page", "before"));

        var bus = bus();
        var painted = (VBox) onFxThread(() -> bus.mount(shell));
        Node header = painted.getChildren().get(0);

        // The whole point of the slot: swap the page, keep the chrome.
        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(
                    UiPatch.Operation.replace(shell.contentId(), UiText.of("page", "after"))));
            return null;
        });

        assertThat(painted.getChildren().get(0)).isSameAs(header);
        assertThat(onFxThread(() -> bus.context().byId(shell.contentId()))).isNotNull();
    }

    @Test
    void aRightSidedMenuIsPaintedAfterTheContent() {
        var menu = UiMenu.of("nav", "Nav", UiMenuItem.of("home", "Home"));
        menu.setSide(UiMenu.Side.RIGHT);
        var shell = UiAppShell.of("app").menu(menu).content(UiText.of("page", "hi"));

        var painted = (VBox) onFxThread(() -> bus().mount(shell));
        var body = (HBox) painted.getChildren().get(0);

        // The page comes first, the menu after it. It is behind its scrollbar,
        // so the ordering is asserted on the wrapper the shell actually adds.
        assertThat(body.getChildren().get(0).getStyleClass()).contains("sui-shell-scroll");
        assertThat(body.getChildren().get(1).getStyleClass()).contains("sui-menu");
    }

    @Test
    void theHeaderLearnsTheShellsMenuWithoutMutatingTheModel() {
        var menu = UiMenu.of("nav", "Nav", UiMenuItem.of("home", "Home"));
        var header = UiHeader.of("Acme");
        var shell = UiAppShell.of("app").header(header).menu(menu).content(UiText.of("p", "x"));

        onFxThread(() -> bus().mount(shell));

        // The caller may hold these for the next page — the renderer copies.
        assertThat(header.getMenuToggle()).isNull();
        assertThat(menu.getToggle()).isNull();
    }

    @Test
    void aHeaderPutsTheUserAndExtrasAfterTheBrand() {
        var header = UiHeader.of("Acme")
                .user(UiHeader.User.of("Ada Lovelace", "AL", null))
                .extra(UiText.of("env", "staging"));

        var bar = (HBox) onFxThread(() -> bus().mount(header));

        assertThat(bar.getChildren().get(0).getStyleClass()).contains("sui-header-brand");
        // A spacer between them is what pushes the trailing group right.
        assertThat(bar.getChildren()).anySatisfy(n ->
                assertThat(n.getStyleClass()).contains("sui-header-extras"));
        assertThat(bar.getChildren()).anySatisfy(n ->
                assertThat(n.getStyleClass()).contains("sui-header-user"));
    }

    @Test
    void theBurgerTogglesTheMenuItNamesByIdEvenThoughItIsPaintedFirst() {
        var menu = UiMenu.of("nav", "Nav", UiMenuItem.of("home", "Home"));
        var shell = UiAppShell.of("app")
                .header(UiHeader.of("Acme")).menu(menu).content(UiText.of("p", "x"));

        var bus = bus();
        var painted = (VBox) onFxThread(() -> bus.mount(shell));
        var bar = (HBox) painted.getChildren().get(0);
        var burger = (javafx.scene.control.Button) bar.getChildren().get(0);

        assertThat(burger.getStyleClass()).contains("sui-header-burger");
        Node menuNode = onFxThread(() -> bus.context().byId("nav"));
        assertThat(menuNode.isVisible()).isTrue();

        onFxThread(() -> { burger.fire(); return null; });
        assertThat(menuNode.isVisible()).isFalse();

        onFxThread(() -> { burger.fire(); return null; });
        assertThat(menuNode.isVisible()).isTrue();
    }

    @Test
    void theHeaderIsPaintedAsTheDarkBandTheWebDraws() {
        var header = UiHeader.of("Acme").user(UiHeader.User.of("Ada", "AL", null));

        var bar = (HBox) onFxThread(() -> {
            var overlay = new ai.mindconnect.ui.javafx.SuiFxOverlay();
            var renderer = SuiFxRenderer.createDefaultRenderer(overlay);
            new javafx.scene.Scene(overlay, 900, 200);
            var painted = renderer.mount(header);
            overlay.applyCss();
            overlay.layout();
            return painted;
        });

        // The header is its own band and the library's default for it is dark.
        // This renderer painted it in the surface colour with dark text, which
        // is the same header in name only.
        assertThat(bar.getBackground().getFills().get(0).getFill())
                .isEqualTo(javafx.scene.paint.Color.web("#1e293b"));
        var brand = (javafx.scene.control.Labeled) bar.lookup(".sui-header-brand");
        assertThat(brand.getTextFill()).isEqualTo(javafx.scene.paint.Color.WHITE);
    }

    @Test
    void anSvgLogoThatCannotBeFetchedLeavesTheBrandTextAlone() {
        // Line-art SVG is drawn now, but a logo that cannot be had at all —
        // unreachable, or using gradients this cannot draw — must not take the
        // header with it. The brand text carries it.
        var header = UiHeader.of("Acme").brandLogo("http://127.0.0.1:1/nope.svg");

        var bar = (HBox) onFxThread(() -> bus().mount(header));

        var brand = (javafx.scene.control.Labeled) bar.lookup(".sui-header-brand");
        assertThat(brand.getGraphic()).isNull();
        assertThat(brand.getText()).isEqualTo("Acme");
    }

    private static <T> T onFxThread(Supplier<T> work) {
        var result = new AtomicReference<T>();
        var error = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("FX thread timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        if (error.get() != null) throw new AssertionError(error.get());
        return result.get();
    }
}
