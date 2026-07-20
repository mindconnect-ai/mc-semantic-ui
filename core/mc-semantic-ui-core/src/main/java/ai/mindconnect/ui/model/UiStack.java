package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain composition container: renders {@link #children} one after another,
 * vertically by default. The semantic-ui's lego brick for "I just want to
 * stick these things on top of each other" — no tab bar, no panel switching,
 * no chrome of its own.
 *
 * <p>Different from {@link UiSection}: a section is a tabbed container,
 * every child gets a tab. A stack is just a vertical (or horizontal) box.
 * Use a stack when you'd reach for a {@code <div>} grouping a header, a
 * toolbar, and a body underneath; use a section when the children should
 * compete for one viewport with the user picking which to see.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiStack extends UiNode {

    /**
     * Layout direction. Vertical is the common case (header / tabs / body
     * stacked). Horizontal lays children side-by-side for toolbars and
     * similar inline groupings.
     */
    public enum Direction { VERTICAL, HORIZONTAL }

    private List<UiNode> children = new ArrayList<>();
    /** Defaults to {@link Direction#VERTICAL} when unset. */
    private Direction direction;
    /** Optional CSS gap between children in pixels — falls back to a token default. */
    private Integer gap;

    public UiStack child(UiNode node) {
        children.add(node);
        return this;
    }

    public UiStack direction(Direction direction) {
        this.direction = direction;
        return this;
    }

    public UiStack gap(int px) {
        this.gap = px;
        return this;
    }

    /** Sugar: {@code UiStack.of(a, b, c)} mounts children in one call. */
    public static UiStack of(UiNode... children) {
        var s = new UiStack();
        for (var c : children) if (c != null) s.children.add(c);
        return s;
    }

    /** Empty stack with an id, to be filled fluently with {@link #child}. */
    public static UiStack of(String id) {
        var s = new UiStack();
        s.setId(id);
        return s;
    }
}
