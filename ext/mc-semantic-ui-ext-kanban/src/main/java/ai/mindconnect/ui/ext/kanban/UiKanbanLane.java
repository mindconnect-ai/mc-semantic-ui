package ai.mindconnect.ui.ext.kanban;

import ai.mindconnect.ui.model.UiNode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * One column of a {@link UiKanban}. {@code title} (inherited) is its heading;
 * the count beside it shows how many cards it holds — as {@code n/limit} when
 * a {@link #limit} is set, and a lane at its limit accepts no further card.
 */
@JsonTypeName("kanban-lane")
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiKanbanLane extends UiNode {

    /** The cards, top to bottom. */
    private List<UiKanbanCard> cards;
    /** Work-in-progress limit: shown beside the count, and no drop lands here once reached. */
    private Integer limit;
    /** Accent colour (any CSS colour) drawn along the lane's top edge. */
    private String color;
    /** When true no card can be dropped here; its own cards can still leave. */
    private boolean locked;

    public static UiKanbanLane of(String id, String title, UiKanbanCard... cards) {
        var l = new UiKanbanLane();
        l.setId(id);
        l.setTitle(title);
        l.cards = new ArrayList<>(List.of(cards));
        return l;
    }

    /** What the lane's count shows — {@code 3}, or {@code 3/5} with a limit. Rendering only. */
    @JsonIgnore
    public String getCountLabel() {
        int n = cards == null ? 0 : cards.size();
        return limit == null ? String.valueOf(n) : n + "/" + limit;
    }

    public UiKanbanLane card(UiKanbanCard card) {
        if (cards == null) cards = new ArrayList<>();
        cards.add(card);
        return this;
    }

    public UiKanbanLane limit(Integer limit) {
        this.limit = limit;
        return this;
    }

    public UiKanbanLane color(String color) {
        this.color = color;
        return this;
    }

    public UiKanbanLane locked(boolean locked) {
        this.locked = locked;
        return this;
    }
}
