package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class UiList extends UiNode {

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private String id;
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
        /** What happens when the item's label is clicked. {@code null} = static label, no click. */
        private UiTrigger onClick;
        private List<UiAction> actions = new ArrayList<>();
        private UiNode content;
        /** When set, item renders as a <details> disclosure widget. */
        private String collapseSummary;
        /** If true the <details> starts open. */
        private boolean collapseOpen;
        /**
         * Optional id placed on the {@code <summary>} element. Lets a patch
         * REPLACE just the summary text (e.g. flip a running marker to done)
         * without re-rendering the whole item — important when the item's
         * body holds live-streamed content a full replace would clobber.
         */
        private String collapseSummaryId;
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
            i.id = id; i.label = label;
            return i;
        }

        public Item description(String description)      { this.description = description; return this; }
        /** Leading icon token before the plain label (ignored when {@link #labelNode} is set). */
        public Item icon(String iconToken)               { this.icon = iconToken;          return this; }
        /** Rich header: render {@code node} as the item title instead of the plain label text. */
        public Item labelNode(UiNode node)               { this.labelNode = node;          return this; }
        /** Primary API: any UiTrigger as click behaviour. */
        public Item onClick(UiTrigger trigger)           { this.onClick = trigger;         return this; }
        /** Legacy: plain navigation link (GET, render page). */
        public Item href(String href)                    { this.onClick = UiTrigger.go(href); return this; }
        /** Legacy: dispatches an API call (method + href) instead of navigating. */
        public Item dispatch(String method, String href) {
            this.onClick = UiTrigger.api(method, href);
            return this;
        }
        public Item action(UiAction action)              { actions.add(action);            return this; }
        public Item content(UiNode content)              { this.content = content;         return this; }
        public Item collapsible(String summary, boolean open) {
            this.collapseSummary = summary;
            this.collapseOpen    = open;
            return this;
        }
        /** Same as {@link #collapsible(String, boolean)} but tags the {@code <summary>} with an id for targeted REPLACE. */
        public Item collapsible(String summary, boolean open, String summaryId) {
            this.collapseSummary   = summary;
            this.collapseOpen      = open;
            this.collapseSummaryId = summaryId;
            return this;
        }
        /**
         * Collapsible whose open/closed state is owned by the client: starts
         * collapsed, and the user's manual toggle survives server re-renders.
         * {@code summaryId} tags the {@code <summary>} for targeted REPLACE.
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

    public UiList item(Item item)         { items.add(item);   return this; }
    public UiList action(UiAction action) { actions.add(action); return this; }
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
