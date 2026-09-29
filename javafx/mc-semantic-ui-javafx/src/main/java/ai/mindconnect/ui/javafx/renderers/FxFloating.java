package ai.mindconnect.ui.javafx.renderers;

import javafx.geometry.Bounds;

/**
 * What floats in the drawer layer ({@code FxDrawer.Layer}): a lone
 * {@link FxDrawer}, or a {@link FxDrawerGroup} of them. The layer lays each
 * on its {@link #area()} after every layout pass; what happens inside that
 * area is the floating thing's own.
 */
interface FxFloating {

    /** The area it lies over, in the layer's coordinates, or null when it is in none. */
    Bounds area();

    /** Whether there is anything to show — a closed drawer has nothing. */
    boolean showing();

    /** Whether it is cut to its area — a CONTAINER-scoped one is. */
    boolean clipped();
}
