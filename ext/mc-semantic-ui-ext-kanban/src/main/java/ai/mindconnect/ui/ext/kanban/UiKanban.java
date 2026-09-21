package ai.mindconnect.ui.ext.kanban;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * A kanban board: lanes side by side, each holding cards. In the browser a
 * card is dragged from one lane to another (or to another place in its own
 * lane), and the board tells the server through {@link #onMove}.
 *
 * <p>Lanes and cards are full nodes ({@code kanban-lane}, {@code kanban-card})
 * with ids of their own, so a patch can {@code REPLACE} one card, {@code MERGE}
 * a lane's {@code cards}, or swap the whole board — the same way a tree row or
 * a table row is addressed.
 *
 * <p>The move itself reaches the server as a request built from
 * {@link #onMove}: the placeholders {@code {card}}, {@code {from}}, {@code {to}}
 * and {@code {index}} in its URL are filled with the card's id, the lane it
 * left, the lane it landed in and its new position there (zero-based). The
 * board moves the card in the page at once; whatever the server answers — a
 * patch, a re-rendered board, a toast — settles it.
 *
 * <pre>{@code
 * UiKanban.of("board",
 *         UiKanbanLane.of("todo", "To do", UiKanbanCard.of("t1", "Write the spec")),
 *         UiKanbanLane.of("doing", "Doing").limit(3),
 *         UiKanbanLane.of("done", "Done"))
 *     .onMove(UiTrigger.api("POST", "/board/move?card={card}&to={to}&index={index}"));
 * }</pre>
 */
@JsonTypeName("kanban")
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiKanban extends UiNode {

    /** The lanes, left to right. */
    private List<UiKanbanLane> lanes;
    /**
     * Fired when a card has been dropped somewhere else. Its URL may carry
     * {@code {card}}, {@code {from}}, {@code {to}} and {@code {index}}.
     */
    private UiTrigger onMove;
    /** When true nothing can be dragged; the board is a picture of its state. */
    private boolean readOnly;

    public static UiKanban of(String id, UiKanbanLane... lanes) {
        var b = new UiKanban();
        b.setId(id);
        b.lanes = new ArrayList<>(List.of(lanes));
        return b;
    }

    public UiKanban lane(UiKanbanLane lane) {
        if (lanes == null) lanes = new ArrayList<>();
        lanes.add(lane);
        return this;
    }

    public UiKanban onMove(UiTrigger trigger) {
        this.onMove = trigger;
        return this;
    }

    public UiKanban readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }
}
