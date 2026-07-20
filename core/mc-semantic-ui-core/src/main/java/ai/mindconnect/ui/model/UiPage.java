package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level document envelope: a renderable {@link #node} subtree plus
 * page-wide chrome — navigation hint, toast queue, open dialogs.
 *
 * <p>Extends {@link UiNode} so editors, schemas and any other tooling that
 * walks the model see a {@code UiPage} as just another node ({@code type:
 * "page"}). The renderers continue to treat it specially when it arrives at
 * the root of a response — toasts and dialogs hang off a UiPage, not off
 * arbitrary nodes — but nothing else in the system has to special-case it.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiPage extends UiNode {

    private String navigate;
    private UiNode node;

    /**
     * Transient toasts to surface alongside the page content. Pulled out of
     * the {@link #node} tree so adding feedback doesn't ripple through the
     * UI structure — the SSR converter and the SPA EventBus both consume
     * them through a dedicated overlay container at the body root. {@code null}
     * / empty = no toasts.
     */
    private List<UiToast> toasts;

    /**
     * Dialogs open on this page, each a {@link UiDialog} node identified by
     * its {@code id}. Rendered into a body-level {@code #sui-dialogs} host that
     * sits above {@link #node} via fixed-position CSS — the SSR converter and
     * the SPA EventBus both paint the same host, so an SSR-navigated modal and
     * a patch-mounted one are identical. Opening a dialog later is an
     * {@code APPEND} into {@code #sui-dialogs}; closing it a {@code REMOVE} by
     * id. {@code null} / empty = no open dialogs (the common case).
     */
    private List<UiDialog> dialogs;

    /**
     * Optional list of SSE streams the server knows are still running for
     * this page's scope. Lets the SPA reconnect after a page-navigate, an
     * F5, or a tab-switch without losing live updates: when the client
     * applies a page that names a stream it isn't already reading, it
     * opens a GET to the resume endpoint and replays from the ring buffer.
     *
     * <p>{@code null} / empty = no live streams (the common case);
     * page renderers don't have to set it unless they want resume support.
     */
    private List<ActiveStream> activeStreams;

    /**
     * One SSE stream the SPA should re-attach to. Identified by
     * {@code channelId} — the same value the server emits in the
     * {@code Sui-Stream-Channel} header on the original POST response.
     * The client uses it as a key into its local registry of live
     * readers: if no reader exists, the client GETs {@code resumeUrl}
     * with {@code lastSeq=0} and treats the result as a regular SSE
     * stream of patches / done / error events.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ActiveStream {
        private String channelId;
        /** GET URL that opens a new SSE connection and replays missed events. */
        private String resumeUrl;
        /** Human-readable label for status surfaces (toasts, indicators). */
        private String label;
        /** Where the user can navigate to see this stream's owning page. */
        private String returnHref;

        public static ActiveStream of(String channelId, String resumeUrl,
                                      String label, String returnHref) {
            var s = new ActiveStream();
            s.channelId = channelId;
            s.resumeUrl = resumeUrl;
            s.label = label;
            s.returnHref = returnHref;
            return s;
        }
    }

    public static UiPage of(String navigate, UiNode node) {
        var p = new UiPage();
        p.navigate = navigate;
        p.node = node;
        return p;
    }

    public static UiPage show(UiNode node) {
        var p = new UiPage();
        p.node = node;
        return p;
    }

    /** Appends a toast to this page's queue. Initialises the list lazily. */
    public UiPage toast(UiToast toast) {
        if (this.toasts == null) this.toasts = new ArrayList<>();
        this.toasts.add(toast);
        return this;
    }

    /** Opens a dialog on this page (mounts it into {@code #sui-dialogs}). */
    public UiPage dialog(UiDialog dialog) {
        if (this.dialogs == null) this.dialogs = new ArrayList<>();
        this.dialogs.add(dialog);
        return this;
    }

}
