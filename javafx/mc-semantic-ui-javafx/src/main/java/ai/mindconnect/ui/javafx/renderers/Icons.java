package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxRenderContext;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuItem;

/**
 * Hangs a leading glyph off a control — the desktop counterpart of the
 * {@code renderIcon()} call the TS renderers make before a label.
 *
 * <p>Nine model types carry an icon token, so the rule lives here once:
 * resolve, attach, and let it inherit the control's font and text fill. An
 * absent or unknown token leaves the control alone.
 */
final class Icons {

    private Icons() {
    }

    /**
     * Puts {@code token}'s glyph in front of {@code control}'s text.
     *
     * <p>A control that already has a graphic keeps it — that is how the busy
     * spinner on a loading action wins over the action's own icon, the same
     * swap the web makes.
     */
    static void lead(Labeled control, String token, FxRenderContext ctx) {
        if (control == null || control.getGraphic() != null) return;
        var icon = ctx.icon(token);
        if (icon != null) control.setGraphic(icon.inherit(control));
    }

    /**
     * The {@link MenuItem} overload. A menu item is not a {@link Labeled} and
     * exposes no font or text fill to inherit, so the glyph keeps the icon's
     * own defaults instead of tracking the item.
     */
    static void lead(MenuItem item, String token, FxRenderContext ctx) {
        if (item == null || item.getGraphic() != null) return;
        var icon = ctx.icon(token);
        if (icon != null) item.setGraphic(icon);
    }
}
