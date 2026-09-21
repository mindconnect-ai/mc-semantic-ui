package ai.mindconnect.ui.ext.kanban;

import ai.mindconnect.ui.html.CssColor;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * One card on a {@link UiKanban}. {@code title} (inherited) is the headline;
 * the rest is optional. A card reacts to clicks like any node — set
 * {@link #onClick(UiTrigger)} to open its detail, say — and is dragged unless
 * {@link #locked}.
 */
@JsonTypeName("kanban-card")
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiKanbanCard extends UiNode {

    /** A line or two under the title. Plain text. */
    private String description;
    /** Short trailing marker beside the title — a count, a priority, an initial. */
    private String badge;
    /** Small labels along the bottom edge. */
    private List<String> tags;
    /** Accent colour (any CSS colour) drawn along the card's left edge. */
    private String color;
    /** When true the card stays where it is; it cannot be dragged. */
    private boolean locked;

    public static UiKanbanCard of(String id, String title) {
        var c = new UiKanbanCard();
        c.setId(id);
        c.setTitle(title);
        return c;
    }

    /** {@link #color} if it is plain colour syntax, else null — what the templates write into the style attribute. */
    @JsonIgnore
    public String getAccentColor() {
        return CssColor.orNull(color);
    }

    public UiKanbanCard description(String description) {
        this.description = description;
        return this;
    }

    public UiKanbanCard badge(String badge) {
        this.badge = badge;
        return this;
    }

    public UiKanbanCard tag(String tag) {
        if (tags == null) tags = new ArrayList<>();
        tags.add(tag);
        return this;
    }

    public UiKanbanCard color(String color) {
        this.color = color;
        return this;
    }

    public UiKanbanCard locked(boolean locked) {
        this.locked = locked;
        return this;
    }

    public UiKanbanCard onClick(UiTrigger trigger) {
        setOnClick(trigger);
        return this;
    }
}
