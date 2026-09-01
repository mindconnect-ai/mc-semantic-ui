package ai.mindconnect.ui.javafx.icons;

/**
 * Turns an icon token into a JavaFX node — the desktop twin of the
 * {@code IconResolver} in {@code renderers/icon.ts}.
 *
 * <p>Nodes only ever carry a <em>token</em> ({@code "trash-2"}, {@code "brain"});
 * what that token becomes is decided here, so the icon library stays out of
 * the model and out of the renderers. Swap it with
 * {@link ai.mindconnect.ui.javafx.SuiFxRenderer#setIconResolver}.
 *
 * <p>The default is {@link SpriteIconResolver}, reading the same
 * {@code icons.svg} sprite the browser loads.
 */
@FunctionalInterface
public interface FxIconResolver {

    /**
     * @param name the icon token, a lowercase-kebab sprite id
     * @return the painted icon, or {@code null} when the token is unknown —
     *         callers treat that as "no icon" rather than failing, so a typo
     *         costs a glyph and not the screen
     */
    SuiFxIcon resolve(String name);
}
