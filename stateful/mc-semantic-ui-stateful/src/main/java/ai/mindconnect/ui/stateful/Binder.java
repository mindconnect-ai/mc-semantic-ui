package ai.mindconnect.ui.stateful;

import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiTrigger;
import ai.mindconnect.ui.model.UiUpload;

/**
 * Attaches server-side listeners to one node. Obtained from
 * {@link SuiView#on(UiNode)} / {@link SuiView#onRow(UiNode)}; every event
 * method returns the node so the call chains into the core builders.
 *
 * <p>The trigger written onto the node is a plain {@code POST} to the generic
 * event endpoint with the core's {@code APPLY_RESPONSE} behaviour. The
 * browser client, the SSR renderer and the JavaFX client all know that
 * trigger already; nothing new travels in the tree.
 */
public final class Binder<N extends UiNode> {

    private final SuiView<?> view;
    private final N node;
    private final boolean rowScoped;
    private String payloadNodeId;

    Binder(SuiView<?> view, N node, boolean rowScoped) {
        this.view = view;
        this.node = node;
        this.rowScoped = rowScoped;
    }

    /**
     * Which node's form values travel with the event. By default the client
     * sends the enclosing {@code UiForm}; name another node to send its
     * values instead, or to send a form's values from a button outside it.
     */
    public Binder<N> payload(String payloadNodeId) {
        this.payloadNodeId = payloadNodeId;
        return this;
    }

    public N click(UiEventHandler handler) {
        node.setOnClick(bind("click", handler));
        return node;
    }

    public N dblClick(UiEventHandler handler) {
        node.setOnDblClick(bind("dblclick", handler));
        return node;
    }

    /** A value was committed: a select picked, a checkbox toggled, an input blurred. */
    public N change(UiEventHandler handler) {
        node.setOnChange(bind("change", handler));
        return node;
    }

    /** A value changes as the user types — one request per keystroke, so use with care. */
    public N input(UiEventHandler handler) {
        node.setOnInput(bind("input", handler));
        return node;
    }

    public N hover(UiEventHandler handler) {
        node.setOnHover(bind("hover", handler));
        return node;
    }

    public N leave(UiEventHandler handler) {
        node.setOnLeave(bind("leave", handler));
        return node;
    }

    /**
     * Files were dropped or picked. Works on a {@link UiUpload} drop zone and
     * on a {@link UiField} of type {@code FILE}; the files arrive as
     * {@link UiEvent#attachments()}.
     */
    public N upload(UiEventHandler handler) {
        view.bind(node, "upload", handler);
        UiTrigger trigger = view.context().uploadTrigger(node.getId(), "upload");
        if (node instanceof UiUpload upload) {
            upload.setOnUpload(trigger);
        } else if (node instanceof UiField field) {
            field.setOnChange(trigger);
        } else {
            throw new IllegalArgumentException("upload() needs a UiUpload or a FILE UiField, got "
                    + node.getClass().getSimpleName());
        }
        return node;
    }

    /**
     * The user picked a page of a paginated {@link UiTable}. Call
     * {@code table.paginate(...)} first; the target page arrives as
     * {@code e.intValue("page", 0)}, from the core's {@code {page}}
     * placeholder that both renderers substitute per button.
     */
    public N page(UiEventHandler handler) {
        if (!(node instanceof UiTable table) || table.getPagination() == null) {
            throw new IllegalArgumentException("page() needs a UiTable with pagination, got "
                    + node.getClass().getSimpleName());
        }
        view.bind(node, "page", handler);
        UiTrigger trigger = view.context().eventTrigger(node.getId(), "page", false, payloadNodeId);
        trigger.setUrl(trigger.getUrl() + "?page={page}");
        table.getPagination().setPageTrigger(trigger);
        return node;
    }

    private UiTrigger bind(String event, UiEventHandler handler) {
        view.bind(node, event, handler);
        return view.context().eventTrigger(node.getId(), event, rowScoped, payloadNodeId);
    }
}
