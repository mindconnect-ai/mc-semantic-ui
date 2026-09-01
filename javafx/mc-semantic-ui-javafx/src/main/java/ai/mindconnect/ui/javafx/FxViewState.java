package ai.mindconnect.ui.javafx;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;

import java.util.HashMap;
import java.util.Map;

/**
 * What the user was doing, carried across a repaint.
 *
 * <p>The browser gets this for free: the SPA morphs a patch into the existing
 * DOM, so elements that did not change are never touched and the caret, the
 * selection and the scroll position never notice. JavaFX has no equivalent —
 * a repaint builds new controls and swaps them in, and everything the user had
 * going is thrown away with the old ones. Typing in a field while a stream
 * patched the page above it meant losing the cursor mid-word.
 *
 * <p>This is not a morph. It reconciles nothing and compares nothing: it notes
 * a few pieces of state before the swap and puts them back after, matching by
 * the id the renderer already stamps on every painted node. That is a fraction
 * of the work of diffing typed controls — a real morph would need per-control
 * update logic, which is every renderer written a second time — and it covers
 * what a user actually notices.
 *
 * <p>What it carries: focus, the caret and selection of a text control, scroll
 * offsets, which tab is showing, and whether a titled pane is open.
 */
final class FxViewState {

    /**
     * The nearest id-bearing ancestor of whatever had focus, plus the child
     * indices from it down to the control itself.
     *
     * <p>Ids come from the model, and a model node is often painted as several
     * controls: a field is a label and an input in a box, and only the box
     * carries the id. Matching on ids alone would therefore never find the
     * input the user was actually typing in.
     */
    private String focusedId;
    private int[] focusPath;
    private int caret = -1;
    private int anchor = -1;
    private final Map<String, double[]> scrolls = new HashMap<>();
    private final Map<String, Boolean> expanded = new HashMap<>();
    private final Map<String, Integer> selectedTabs = new HashMap<>();
    private ScrollPane ancestorScroller;
    private double ancestorH;
    private double ancestorV;

    private FxViewState() {
    }

    /** Notes the state of {@code subtree} and of the scroller it sits in. */
    static FxViewState of(Node subtree) {
        var state = new FxViewState();
        if (subtree == null) return state;
        state.capture(subtree);
        state.captureFocus(subtree);

        // The subtree's own height is about to change, which moves the
        // scroller around it — a chat transcript being patched is the case
        // that makes this obvious.
        for (Node p = subtree.getParent(); p != null; p = p.getParent()) {
            if (p instanceof ScrollPane scroller) {
                state.ancestorScroller = scroller;
                state.ancestorH = scroller.getHvalue();
                state.ancestorV = scroller.getVvalue();
                break;
            }
        }
        return state;
    }

    /** Notes what had focus, as a path from the nearest named ancestor. */
    private void captureFocus(Node subtree) {
        // The scene's focus owner, not Node#isFocused: the latter is also
        // false whenever the window itself is inactive, so a repaint arriving
        // while the user is in another window would forget where they were.
        var scene = subtree.getScene();
        var focused = scene == null ? null : scene.getFocusOwner();
        if (focused == null || !within(focused, subtree)) return;

        if (focused instanceof TextInputControl input) {
            caret = input.getCaretPosition();
            anchor = input.getAnchor();
        }

        var path = new java.util.ArrayList<Integer>();
        for (Node node = focused; node != null; node = node.getParent()) {
            var id = node.getId();
            if (id != null && !id.isBlank()) {
                focusedId = id;
                focusPath = path.stream().mapToInt(Integer::intValue).toArray();
                return;
            }
            var parent = node.getParent();
            if (parent == null) return;
            path.add(0, parent.getChildrenUnmodifiable().indexOf(node));
        }
    }

    /** Whether {@code node} is {@code subtree} or sits inside it. */
    private static boolean within(Node node, Node subtree) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == subtree) return true;
        }
        return false;
    }

    /** The node {@code focusPath} leads to from {@code anchorNode}, if it still exists. */
    private Node followFocusPath(Node anchorNode) {
        Node node = anchorNode;
        for (int index : focusPath) {
            if (!(node instanceof Parent parent)) return null;
            var children = parent.getChildrenUnmodifiable();
            // The repaint may have built a different shape; then there is
            // nothing sensible to focus and leaving it alone beats guessing.
            if (index < 0 || index >= children.size()) return null;
            node = children.get(index);
        }
        return node;
    }

    private void capture(Node node) {
        var id = node.getId();
        if (id != null && !id.isBlank()) {
            if (node instanceof ScrollPane scroller) {
                scrolls.put(id, new double[] { scroller.getHvalue(), scroller.getVvalue() });
            }
            if (node instanceof TitledPane titled) {
                expanded.put(id, titled.isExpanded());
            }
            if (node instanceof TabPane tabs) {
                selectedTabs.put(id, tabs.getSelectionModel().getSelectedIndex());
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) capture(child);
        }
    }

    /** Puts back what was noted, on whatever in {@code subtree} carries the same id. */
    void restoreInto(Node subtree) {
        if (subtree == null) return;
        apply(subtree);

        if (ancestorScroller != null) {
            // After layout: the scroller clamps a value against a content
            // height it has not measured yet, so setting it now would round to
            // whatever the old content allowed.
            Platform.runLater(() -> {
                ancestorScroller.setHvalue(ancestorH);
                ancestorScroller.setVvalue(ancestorV);
            });
        }
    }

    private void apply(Node node) {
        var id = node.getId();
        if (id != null && !id.isBlank()) {
            var scroll = scrolls.get(id);
            if (scroll != null && node instanceof ScrollPane scroller) {
                Platform.runLater(() -> {
                    scroller.setHvalue(scroll[0]);
                    scroller.setVvalue(scroll[1]);
                });
            }
            var open = expanded.get(id);
            if (open != null && node instanceof TitledPane titled) titled.setExpanded(open);

            var tab = selectedTabs.get(id);
            if (tab != null && node instanceof TabPane tabs
                    && tab >= 0 && tab < tabs.getTabs().size()) {
                tabs.getSelectionModel().select(tab);
            }
            if (id.equals(focusedId)) {
                var target = followFocusPath(node);
                if (target != null) restoreFocus(target);
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) apply(child);
        }
    }

    private void restoreFocus(Node node) {
        // Also deferred: a node that is not in a scene yet cannot take focus,
        // and it is not in one until the swap that is calling us finishes.
        Platform.runLater(() -> {
            node.requestFocus();
            if (node instanceof TextInputControl input && caret >= 0) {
                int length = input.getLength();
                int to = Math.min(caret, length);
                int from = anchor < 0 ? to : Math.min(anchor, length);
                // selectRange rather than positionCaret: it restores a
                // selection as well as a bare cursor, and a bare cursor is
                // just a selection of nothing.
                input.selectRange(from, to);
            }
        });
    }
}
