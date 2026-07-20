package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiSection;
import ai.mindconnect.ui.model.UiSectionEntry;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;

/**
 * Paints {@link UiSection} as a {@link TabPane} — the tabs of the vocabulary.
 *
 * <p>Panels are painted eagerly, not on first selection. That matters: fields
 * sitting in an unselected tab must still be registered with the enclosing
 * form scope, or a submit would silently drop them. Same guarantee the web
 * renderers give.
 *
 * <p>A collapsible section ({@link UiSection#getCollapseSummary()}) wraps the
 * pane in a {@link TitledPane}, the JavaFX stand-in for {@code <details>}.
 *
 * <p>First draft: {@link UiSection.TabOverflow} is ignored — a JavaFX TabPane
 * scrolls its header on its own.
 */
public class SectionRenderer implements FxNodeRenderer<UiSection> {

    @Override
    public Node render(UiSection node, FxRenderContext ctx) {
        var pane = new TabPane();
        pane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        for (UiSectionEntry entry : node.getSections()) {
            var tab = new Tab(entry.getTitle() == null ? entry.getId() : entry.getTitle());
            tab.setId(entry.getId());
            if (entry.getContent() != null) {
                tab.setContent(scrollable(ctx.render(entry.getContent())));
            }
            pane.getTabs().add(tab);
        }

        if (node.getInitialSection() != null) {
            pane.getTabs().stream()
                    .filter(t -> node.getInitialSection().equals(t.getId()))
                    .findFirst()
                    .ifPresent(t -> pane.getSelectionModel().select(t));
        }

        wireSelection(node, pane, ctx);

        if (node.getCollapseSummary() != null) {
            var titled = new TitledPane(node.getCollapseSummary(), pane);
            titled.setExpanded(node.isCollapseOpen());
            return titled;
        }
        return pane;
    }

    /**
     * Puts a tab's panel in a {@link ScrollPane}.
     *
     * <p>Without this, a panel taller than the window is simply clipped and
     * the fields at the bottom become unreachable — with no scrollbar to hint
     * that anything is missing. The web renderers never had the problem: a
     * page scrolls by default. A JavaFX tab does not, so the renderer has to
     * put it back, or the same model would be usable in the browser and
     * broken on the desktop.
     *
     * <p>{@code fitToWidth} stretches the panel to the tab's width, so a form
     * fills it instead of huddling on the left. Content that genuinely cannot
     * shrink that far — a wide row of controls — still gets a horizontal
     * scrollbar rather than being cut off.
     */
    private Node scrollable(Node content) {
        var scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("sui-section-scroll");
        return scroll;
    }

    /**
     * Dispatches a tab's {@code onClick}, and honours
     * {@link UiSectionEntry#getSelectOnClick()}: when a tab opts out of
     * selecting itself, the switch is undone and only the trigger fires — the
     * handler then selects the tab itself (via a patch) once it is happy. That
     * is how a tab gets gated behind a confirmation or a save.
     */
    private void wireSelection(UiSection node, TabPane pane, FxRenderContext ctx) {
        pane.getSelectionModel().selectedItemProperty().addListener((obs, previous, selected) -> {
            if (selected == null) return;
            var entry = entryFor(node, selected.getId());
            if (entry == null) return;

            if (Boolean.FALSE.equals(entry.getSelectOnClick()) && previous != null) {
                pane.getSelectionModel().select(previous);
            }
            if (entry.getOnClick() != null) {
                ctx.bus().dispatch(entry.getOnClick(), entry, ctx);
            }
        });
    }

    private UiSectionEntry entryFor(UiSection node, String id) {
        if (id == null) return null;
        return node.getSections().stream()
                .filter(e -> id.equals(e.getId()))
                .findFirst()
                .orElse(null);
    }
}
