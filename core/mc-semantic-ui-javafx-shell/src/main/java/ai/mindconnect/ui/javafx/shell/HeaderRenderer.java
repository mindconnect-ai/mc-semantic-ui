package ai.mindconnect.ui.javafx.shell;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiHeader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Paints {@link UiHeader} as the app's top bar: an optional hamburger, the
 * brand, then the extras and the user pushed to the trailing edge.
 *
 * <p>{@link UiHeader#getMenuToggle()} names a menu by id rather than holding
 * it, and the header is painted before that menu exists. So the button looks
 * its target up when it is pressed, not when it is built — by then the whole
 * shell is mounted and the menu is in the index.
 *
 * <p>{@link UiHeader.ExtrasOverflow#MENU} is not implemented: the extras row
 * wraps instead of collapsing into a dropdown. Same call
 * {@code SectionRenderer} makes about tab overflow — a desktop window is
 * resized deliberately and rarely to widths where it would matter.
 */
public class HeaderRenderer implements FxNodeRenderer<UiHeader> {

    @Override
    public Node render(UiHeader node, FxRenderContext ctx) {
        var bar = new HBox(12);
        bar.getStyleClass().add("sui-header");
        bar.setAlignment(Pos.CENTER_LEFT);

        if (node.getMenuToggle() != null) bar.getChildren().add(burger(node.getMenuToggle(), ctx));
        var brand = brand(node, ctx);
        if (brand != null) bar.getChildren().add(brand);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().add(spacer);

        if (node.getExtras() != null && !node.getExtras().isEmpty()) {
            var extras = new HBox(8);
            extras.getStyleClass().add("sui-header-extras");
            extras.setAlignment(Pos.CENTER_RIGHT);
            node.getExtras().forEach(extra -> extras.getChildren().add(ctx.render(extra)));
            bar.getChildren().add(extras);
        }
        if (node.getUser() != null) bar.getChildren().add(user(node.getUser(), ctx));
        return bar;
    }

    /**
     * The hamburger. Resolving the menu on each press keeps this working when
     * the menu is re-rendered under the same id — the button never holds a
     * stale node.
     */
    private Button burger(String menuId, FxRenderContext ctx) {
        var toggle = new Button();
        toggle.getStyleClass().addAll("sui-menu-toggle", "sui-header-burger");
        toggle.setAccessibleText("Toggle menu");
        var icon = ctx.icon("menu");
        if (icon != null) toggle.setGraphic(icon.inherit(toggle));
        else toggle.setText("☰");

        toggle.setOnAction(e -> {
            var menu = ctx.byId(menuId);
            if (menu == null) return;
            boolean showing = menu.isVisible();
            menu.setVisible(!showing);
            menu.setManaged(!showing);
        });
        return toggle;
    }

    private Node brand(UiHeader node, FxRenderContext ctx) {
        if (node.getBrand() == null && node.getBrandLogo() == null) return null;

        Labeled brand = node.getBrandHref() != null
                ? new Hyperlink(node.getBrand())
                : new Label(node.getBrand());
        brand.getStyleClass().add("sui-header-brand");

        var logo = logo(node.getBrandLogo());
        if (logo != null) brand.setGraphic(logo);

        if (node.getBrandHref() != null) {
            brand.setOnMouseClicked(e ->
                    ctx.bus().dispatch(ai.mindconnect.ui.model.UiTrigger.go(node.getBrandHref()), node, ctx));
        }
        return brand;
    }

    /**
     * The brand logo, loaded in the background so a slow or unreachable URL
     * never blocks the window coming up. A logo that fails to load is simply
     * absent — the brand text carries the header on its own.
     */
    private ImageView logo(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            var view = new ImageView(new Image(url, true));
            view.getStyleClass().add("sui-header-logo");
            view.setPreserveRatio(true);
            view.setFitHeight(24);
            return view;
        } catch (Exception e) {
            return null;
        }
    }

    private Node user(UiHeader.User user, FxRenderContext ctx) {
        var box = new HBox(8);
        box.getStyleClass().add("sui-header-user");
        box.setAlignment(Pos.CENTER_LEFT);

        if (user.getInitials() != null) {
            var avatar = new Label(user.getInitials());
            avatar.getStyleClass().add("sui-header-avatar");
            box.getChildren().add(avatar);
        }
        if (user.getName() != null) {
            var name = new Label(user.getName());
            name.getStyleClass().add("sui-header-username");
            box.getChildren().add(name);
        }
        if (user.getProfileHref() != null) {
            box.setOnMouseClicked(e ->
                    ctx.bus().dispatch(ai.mindconnect.ui.model.UiTrigger.go(user.getProfileHref()), null, ctx));
            box.getStyleClass().add("sui-header-user--clickable");
        }
        return box;
    }
}
