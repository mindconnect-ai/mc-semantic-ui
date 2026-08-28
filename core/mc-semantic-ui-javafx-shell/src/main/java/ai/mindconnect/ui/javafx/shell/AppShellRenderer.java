package ai.mindconnect.ui.javafx.shell;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiAppShell;
import ai.mindconnect.ui.model.UiHeader;
import ai.mindconnect.ui.model.UiMenu;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Paints {@link UiAppShell}: header on top, then a body holding the menu and
 * the content side by side, then the footer.
 *
 * <p>The content sits in a container of its own registered under
 * {@link UiAppShell#contentId()}, so a patch can swap the page while the
 * header and menu stay put — the desktop counterpart of the web shell's
 * {@code data-sui-slot="content"}.
 *
 * <p>Two model tweaks mirror the web renderer, and like it they are made on
 * copies: the menu loses its own toggle, because the header's hamburger is
 * the one that drives it, and the header learns the menu's id when it does
 * not already name one. The caller may be holding these objects for the next
 * page, so neither is mutated.
 */
public class AppShellRenderer implements FxNodeRenderer<UiAppShell> {

    @Override
    public Node render(UiAppShell node, FxRenderContext ctx) {
        var shell = new VBox();
        shell.getStyleClass().add("sui-shell");
        if (!node.isFillViewport()) shell.getStyleClass().add("sui-shell--fit");

        var menu = withoutOwnToggle(node.getMenu());
        var header = withMenuToggle(node.getHeader(), menu == null ? null : menu.getId());

        if (header != null) shell.getChildren().add(ctx.render(header));

        var body = new HBox();
        body.getStyleClass().add("sui-shell-body");
        VBox.setVgrow(body, Priority.ALWAYS);

        Node menuNode = menu == null ? null : ctx.render(menu);
        Node content = content(node, ctx);

        // Menu first for a LEFT sidebar, after the content for a RIGHT one —
        // the same ordering rule the web shell follows.
        if (menuNode != null && node.getMenu().getSide() == UiMenu.Side.RIGHT) {
            body.getChildren().addAll(content, menuNode);
        } else if (menuNode != null) {
            body.getChildren().addAll(menuNode, content);
        } else {
            body.getChildren().add(content);
        }
        shell.getChildren().add(body);

        if (node.getFooter() != null) {
            var footer = new StackPane(ctx.render(node.getFooter()));
            footer.getStyleClass().add("sui-shell-footer");
            shell.getChildren().add(footer);
        }
        return shell;
    }

    /** The swappable page, under the id {@link UiAppShell#contentId()} names. */
    private Node content(UiAppShell node, FxRenderContext ctx) {
        var slot = new StackPane();
        slot.getStyleClass().add("sui-shell-content");
        if (node.getContent() != null) slot.getChildren().add(ctx.render(node.getContent()));

        HBox.setHgrow(slot, Priority.ALWAYS);
        slot.setMinWidth(0);   // the menu keeps its width; the content gives way
        ctx.indexSlot(node.contentId(), slot);
        return slot;
    }

    /**
     * A copy of the menu with its own toggle off. In a shell the header owns
     * the hamburger, and two of them would fight over the same sidebar.
     */
    private UiMenu withoutOwnToggle(UiMenu menu) {
        if (menu == null) return null;
        var copy = new UiMenu();
        copy.setId(menu.getId());
        copy.setTitle(menu.getTitle());
        copy.setCssClass(menu.getCssClass());
        copy.setItems(menu.getItems());
        copy.setState(menu.getState());
        copy.setMode(menu.getMode());
        copy.setSide(menu.getSide());
        copy.setToggle(Boolean.FALSE);
        return copy;
    }

    /** A copy of the header pointed at the shell's menu, unless it names one already. */
    private UiHeader withMenuToggle(UiHeader header, String menuId) {
        if (header == null) return null;
        if (header.getMenuToggle() != null || menuId == null) return header;

        var copy = new UiHeader();
        copy.setId(header.getId());
        copy.setTitle(header.getTitle());
        copy.setCssClass(header.getCssClass());
        copy.setBrand(header.getBrand());
        copy.setBrandHref(header.getBrandHref());
        copy.setBrandLogo(header.getBrandLogo());
        copy.setUser(header.getUser());
        copy.setExtras(header.getExtras());
        copy.setExtrasOverflow(header.getExtrasOverflow());
        copy.setMenuToggle(menuId);
        return copy;
    }
}
