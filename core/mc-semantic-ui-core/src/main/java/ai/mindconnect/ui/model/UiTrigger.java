package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Declarative description of what happens when a UI event fires — reused
 * across all event sources (button click, field change, lazy-load on
 * visible, timer-based polling, …).
 *
 * <p>The same {@code UiTrigger} value is wired to {@code onClick} on a
 * {@link UiAction}, to a list item's {@code onClick}, and (in time) to
 * field {@code onChange} or section {@code onVisible}. The renderer
 * serialises it as a single JSON sub-object and the client's
 * {@code executeTrigger(...)} function dispatches on {@link #behavior}
 * without each event source having to know about the variants.
 *
 * <p>Adding a new variant — say {@code COPY_TO_CLIPBOARD} or
 * {@code OPEN_MODAL} — is a one-line change to {@link Behavior} plus one
 * switch-case in the JS dispatcher. Event sources are untouched.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiTrigger {

    /**
     * What the client does with the response.
     */
    public enum Behavior {
        /** JSON response is either a {@code UiPage} (full swap) or {@code UiPatch} (in-place). */
        APPLY_RESPONSE,
        /** Response is a Server-Sent Events stream of {@code UiPatch} frames. */
        STREAM,
        /** Response body is saved as a file via the browser's save dialog. */
        DOWNLOAD,
        /** Response body is opened in a new tab via a blob URL. */
        OPEN_IN_TAB,
        /**
         * No server call at all: the client invokes a locally-registered JS
         * handler (named by {@link #handler}) and applies whatever
         * {@code UiPage} / {@code UiPatch} it returns. Lets a screen run
         * entirely in the browser without a backend.
         */
        INVOKE,
        /**
         * No server call and no handler: the client applies the {@link #patch}
         * carried inline on this trigger. The patch is baked in at render
         * time — the leanest way to express static, known-ahead UI logic
         * (a list row that fills a detail panel, a button that opens a fixed
         * dialog, a toggle that reveals a field) with zero round-trip.
         */
        PATCH,
        /**
         * File upload: the selected files are POSTed to {@link #url} as
         * {@code multipart/form-data} and the response is applied as a
         * {@code UiPage} / {@code UiPatch}. Fired by a {@link UiUpload} drop
         * zone or a {@link UiField.FieldType#FILE} field's change.
         */
        UPLOAD
    }

    /** Target URL for the call. Required except for {@link Behavior#INVOKE}. */
    private String url;
    /** HTTP method ({@code GET}/{@code POST}/{@code PUT}/{@code DELETE}). Defaults to {@code GET}. */
    private String method;
    /**
     * Optional id of a UI node whose editable field values are gathered and
     * sent as the JSON request body. Null = no body (or a plain navigation).
     */
    private String payload;
    /** How the client handles the response. Defaults to {@link Behavior#APPLY_RESPONSE}. */
    private Behavior behavior;
    /**
     * Name of a client-side handler registered on the {@code SuiEventBus} via
     * {@code registerClientHandler(name, fn)}. Only used with
     * {@link Behavior#INVOKE}: the bus calls that function (passing the
     * collected {@link #payload}) instead of fetching {@link #url}, and
     * applies its returned {@code UiPage} / {@code UiPatch}.
     */
    private String handler;
    /**
     * Inline patch applied directly when the trigger fires. Only used with
     * {@link Behavior#PATCH}: the client applies this {@link UiPatch} as-is,
     * with no server call and no handler. Baked into the trigger at render
     * time for static UI logic (list → detail fill, toggle → reveal).
     */
    private UiPatch patch;

    // ── factories for the common shapes ────────────────────────────────────

    /**
     * Plain navigation / page swap — {@code GET} the URL and render the
     * returned {@code UiPage} or {@code UiPatch}. The most common case.
     */
    public static UiTrigger go(String url) {
        return api("GET", url, null);
    }

    /**
     * Generic API call. Body is collected from {@code payloadNodeId}'s fields
     * when non-null. Response is applied as a {@code UiPage} or {@code UiPatch}.
     */
    public static UiTrigger api(String method, String url) {
        return api(method, url, null);
    }

    public static UiTrigger api(String method, String url, String payloadNodeId) {
        UiTrigger t = new UiTrigger();
        t.url      = url;
        t.method   = method;
        t.payload  = payloadNodeId;
        t.behavior = Behavior.APPLY_RESPONSE;
        return t;
    }

    /**
     * SSE-streamed turn. Used by the chat send button: the request body comes
     * from {@code payloadNodeId}, the response is a stream of UiPatch frames
     * applied as they arrive.
     */
    public static UiTrigger stream(String method, String url, String payloadNodeId) {
        UiTrigger t = api(method, url, payloadNodeId);
        t.behavior = Behavior.STREAM;
        return t;
    }

    /**
     * Authenticated file download — the client fetches {@code url}, builds a
     * blob, and triggers the browser save dialog. Filename comes from the
     * server's {@code Content-Disposition} header.
     */
    public static UiTrigger download(String url) {
        UiTrigger t = api("GET", url, null);
        t.behavior = Behavior.DOWNLOAD;
        return t;
    }

    /**
     * Authenticated "view file" — the client fetches {@code url} and opens
     * the response body in a new tab via a blob URL.
     */
    public static UiTrigger openInTab(String url) {
        UiTrigger t = api("GET", url, null);
        t.behavior = Behavior.OPEN_IN_TAB;
        return t;
    }

    /**
     * Client-side invocation — no server call. The bus calls the JS handler
     * registered under {@code handlerName} and applies whatever
     * {@code UiPage} / {@code UiPatch} it returns.
     */
    public static UiTrigger invoke(String handlerName) {
        return invoke(handlerName, null);
    }

    /**
     * Client-side invocation with a payload source: the editable field values
     * of {@code payloadNodeId} are collected and handed to the handler as its
     * payload argument (same collection rules as a server-bound trigger).
     */
    public static UiTrigger invoke(String handlerName, String payloadNodeId) {
        UiTrigger t = new UiTrigger();
        t.handler  = handlerName;
        t.payload  = payloadNodeId;
        t.behavior = Behavior.INVOKE;
        return t;
    }

    /**
     * Inline patch — no server call, no handler. The client applies
     * {@code patch} directly when the trigger fires. Bake the finished patch
     * in at render time for static UI logic (fill a detail panel from a list
     * row, open a fixed dialog, reveal a field) with zero round-trip.
     */
    public static UiTrigger patch(UiPatch patch) {
        UiTrigger t = new UiTrigger();
        t.patch    = patch;
        t.behavior = Behavior.PATCH;
        return t;
    }

    /**
     * Inline patch from one or more operations — convenience over
     * {@link #patch(UiPatch)}. Assembles a single {@link UiPatch} whose
     * {@code patches} list holds the given operations, in order. Lets a caller
     * REPLACE several targets in one click without building the envelope by
     * hand (e.g. fill a detail panel <em>and</em> highlight the picked row).
     */
    public static UiTrigger patch(UiPatch.Operation... operations) {
        return patch(operations == null ? List.of() : List.of(operations));
    }

    /** Inline patch from a list of operations. See {@link #patch(UiPatch.Operation...)}. */
    public static UiTrigger patch(List<UiPatch.Operation> operations) {
        UiPatch p = UiPatch.of();
        if (operations != null) {
            operations.forEach(p::patch);
        }
        return patch(p);
    }

    /**
     * A fully client-side toast: {@link Behavior#PATCH} with an empty patch that
     * carries a single {@link UiToast}. No server round-trip — the bus shows the
     * toast when the trigger fires. Handy for a nav item / button that just needs
     * quick feedback.
     */
    public static UiTrigger toast(String message) {
        return toast(UiToast.info(message));
    }

    public static UiTrigger toast(UiToast toast) {
        return patch(UiPatch.of().toast(toast));
    }

    /**
     * File upload — the client POSTs the selected files to {@code url} as
     * {@code multipart/form-data} and applies the returned
     * {@code UiPage} / {@code UiPatch}. Used by {@link UiUpload} and by a
     * {@link UiField.FieldType#FILE} field's {@code onChange}.
     */
    public static UiTrigger upload(String url) {
        return upload("POST", url);
    }

    public static UiTrigger upload(String method, String url) {
        UiTrigger t = new UiTrigger();
        t.url      = url;
        t.method   = method;
        t.behavior = Behavior.UPLOAD;
        return t;
    }
}
