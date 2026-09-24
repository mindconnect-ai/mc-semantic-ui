package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class UiList extends UiNode {

    /**
     * One row of a list. A node like any other — {@code "type": "item"} on
     * the wire, so a patch can name it: REMOVE takes the row out, REPLACE
     * swaps it for another item, MERGE changes a field of it. Rendered by the
     * list it sits in, never on its own.
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("item")
    // An item read as an item: JSON from before items carried a type has
    // none, and inside a list there is nothing else it could be.
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = Item.class)
    public static class Item extends UiNode {
        private String label;
        /**
         * Optional rich label: when set, the item's header renders this node
         * instead of the plain {@link #label} text. Lets a row title carry
         * structure — e.g. a name plus a status/config badge — via a
         * {@code UiStack} of {@code UiText} nodes. The {@link #label} string is
         * still kept as an accessible/plain-text fallback.
         */
        private UiNode labelNode;
        /** Leading icon token rendered before the plain label. See {@link UiIcon}. */
        private String icon;
        private String description;
        private List<UiAction> actions = new ArrayList<>();
        private UiNode content;
        /** When set, item renders as a <details> disclosure widget. */
        private String collapseSummary;
        /** If true the <details> starts open. */
        private boolean collapseOpen;
        /**
         * Shorthand for a {@link #collapseSummaryNode} that is a {@link UiText}
         * with this id and {@link #collapseSummary} as its text: the summary
         * becomes a node a patch can name — REPLACE it with another text, or
         * MERGE {@code {"text": …}} on it — while the item's body streams on.
         */
        private String collapseSummaryId;
        /**
         * The summary as a node of its own, rendered inside the
         * {@code <summary>} instead of {@link #collapseSummary}. Anything goes
         * — a text with an id (what {@link #collapseSummaryId} builds), a
         * stack with an icon and a badge.
         */
        private UiNode collapseSummaryNode;
        /**
         * When true the {@code <details>} open/closed state is owned by the
         * CLIENT, not the server: it renders collapsed (no server {@code open}
         * attribute) and is tagged {@code data-sui-client-collapse} so the
         * morpher preserves whatever the user toggled across re-renders /
         * streaming patches. Use for live-updating cards (tool calls, sub-agent
         * activity) where a server-driven open state would otherwise fight the
         * user's manual expand/collapse.
         */
        private boolean collapseClientControlled;

        public static Item of(String id, String label) {
            var i = new Item();
            i.setId(id); i.label = label;
            return i;
        }

        public Item description(String description)      { this.description = description; return this; }
        /** Leading icon token before the plain label (ignored when {@link #labelNode} is set). */
        public Item icon(String iconToken)               { this.icon = iconToken;          return this; }
        /** Rich header: render {@code node} as the item title instead of the plain label text. */
        public Item labelNode(UiNode node)               { this.labelNode = node;          return this; }
        /** Primary API: any UiTrigger as click behaviour. */
        public Item onClick(UiTrigger trigger)           { setOnClick(trigger);            return this; }
        /** Legacy: plain navigation link (GET, render page). */
        public Item href(String href)                    { setOnClick(UiTrigger.go(href)); return this; }
        /** Legacy: dispatches an API call (method + href) instead of navigating. */
        public Item dispatch(String method, String href) {
            setOnClick(UiTrigger.api(method, href));
            return this;
        }
        public Item action(UiAction action)              { actions.add(action);            return this; }
        public Item content(UiNode content)              { this.content = content;         return this; }
        public Item collapsible(String summary, boolean open) {
            this.collapseSummary = summary;
            this.collapseOpen    = open;
            return this;
        }
        /** Same as {@link #collapsible(String, boolean)} but the summary is a text node with {@code summaryId}, for targeted patches. */
        public Item collapsible(String summary, boolean open, String summaryId) {
            this.collapseSummary   = summary;
            this.collapseOpen      = open;
            this.collapseSummaryId = summaryId;
            return this;
        }
        /** A summary that is a node of its own — an icon beside the text, a badge, a counter with an id. */
        public Item collapsible(UiNode summary, boolean open) {
            this.collapseSummaryNode = summary;
            this.collapseOpen        = open;
            return this;
        }
        /**
         * Collapsible whose open/closed state is owned by the client: starts
         * collapsed, and the user's manual toggle survives server re-renders.
         * {@code summaryId} makes the summary a text node with that id, for
         * targeted patches.
         */
        public Item collapsibleClient(String summary, String summaryId) {
            this.collapseSummary          = summary;
            this.collapseOpen             = false;
            this.collapseSummaryId        = summaryId;
            this.collapseClientControlled = true;
            return this;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Pagination {
        private int page;
        private int size;
        private long total;
        /**
         * Trigger template fired when the user clicks a page button. The
         * renderer substitutes the literal {@code {page}} in the trigger's
         * {@code url} with the target page number before emitting the
         * button's {@code data-trigger} attribute. Null falls back to a
         * plain "render disabled" — pagination becomes informational only.
         */
        private UiTrigger pageTrigger;

        public static Pagination of(int page, int size, long total) {
            var p = new Pagination();
            p.page = page; p.size = size; p.total = total;
            return p;
        }

        public Pagination pageTrigger(UiTrigger t) { this.pageTrigger = t; return this; }
    }

    private List<Item>     items      = new ArrayList<>();
    private Pagination     pagination;
    private List<UiAction> actions    = new ArrayList<>();
    /** What the header's button bar does when its buttons do not fit one row. Defaults to {@link Overflow#WRAP}. */
    private Overflow       actionsOverflow;
    /** Optional node rendered inside the header row, between title and actions — e.g. a compact search form. */
    private UiNode         headerExtra;
    /** Leading icon token rendered in the header before the title. See {@link UiIcon}. */
    private String         icon;

    public UiList item(Item item)         { items.add(item);   return this; }
    public UiList action(UiAction action) { actions.add(action); return this; }
    /** The buttons that do not fit the header's row go into a "⋯" menu ({@link Overflow#MENU}) or wrap. */
    public UiList actionsOverflow(Overflow overflow) { this.actionsOverflow = overflow; return this; }
    public UiList headerExtra(UiNode node) { this.headerExtra = node; return this; }
    public UiList icon(String iconToken)  { this.icon = iconToken; return this; }
    public UiList paginate(int page, int size, long total) {
        this.pagination = Pagination.of(page, size, total);
        return this;
    }
    public UiList paginate(int page, int size, long total, UiTrigger pageTrigger) {
        this.pagination = Pagination.of(page, size, total).pageTrigger(pageTrigger);
        return this;
    }

    public static UiList of(String id, String title) {
        var l = new UiList();
        l.setId(id); l.setTitle(title);
        return l;
    }
}
