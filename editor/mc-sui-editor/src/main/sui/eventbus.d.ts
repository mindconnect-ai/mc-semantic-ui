import type { UiTrigger, UiPage, UiPatch } from "./model.js";
import type { SuiRenderer } from "./renderer.js";
/**
 * Context handed to every {@link BehaviorHandler}. Captures the trigger
 * declaration, the JSON payload (collected from a {@code UiForm}-like node
 * if the trigger names one), the source DOM element, and the resolved
 * URL/method/fetcher the handler is expected to use.
 */
export interface BehaviorContext {
    trigger: UiTrigger;
    payload: Record<string, unknown> | null;
    sourceElement: HTMLElement;
    /** Resolved URL — the {@link UrlRewriter} has already been applied. */
    url: string;
    /** Resolved HTTP method, upper-cased. Defaults to {@code "GET"}. */
    method: string;
    /** The bus's configured fetcher (auth-aware via {@link SuiEventBus#setFetcher}). */
    fetch: typeof fetch;
    /** The owning bus, so handlers can {@link SuiEventBus#dispatch} follow-on triggers. */
    bus: SuiEventBus;
    /**
     * Files selected/dropped for an upload dispatch (a {@code UiUpload} drop
     * zone or a {@code FILE} field). Present for the {@code UPLOAD} behaviour
     * and for {@code INVOKE} handlers wired to an upload — a client handler can
     * read the raw {@code File} objects (e.g. to preview an image) with no
     * backend. {@code undefined} for ordinary triggers.
     */
    files?: File[];
}
/** Behaviour handlers implement what happens after the bus dispatches a trigger. */
export type BehaviorHandler = (ctx: BehaviorContext) => Promise<void> | void;
/**
 * A client-side handler invoked by the built-in {@code INVOKE} behaviour —
 * the browser-only counterpart to a server endpoint. It receives the same
 * {@link BehaviorContext} a server call would (trigger, collected payload,
 * source element, owning bus) and may return a {@link UiPage} or
 * {@link UiPatch}; the bus applies the result through its
 * {@link ResponseHandler}, exactly as if it had come back over the wire.
 *
 * <p>Returning {@code void}/{@code null}/{@code undefined} means "I already
 * applied whatever I needed to" (e.g. the handler called
 * {@code ctx.bus.applyPatch(...)} itself) — the bus does nothing further.
 * Handlers registered by name via {@link SuiEventBus#registerClientHandler}.
 */
export type ClientHandler = (ctx: BehaviorContext) => UiPage | UiPatch | void | Promise<UiPage | UiPatch | void>;
/** Hook for app-specific URL rewriting (UI path → API path). */
export type UrlRewriter = (uiUrl: string) => string;
/**
 * Hook for response bodies returned by the {@code APPLY_RESPONSE} behaviour
 * and by {@link SuiEventBus#navigate}. The default handler routes
 * {@link UiPatch} bodies through {@link SuiRenderer#applyPatch} and
 * {@link UiPage} bodies through {@link SuiEventBus#applyPage}; apps with a
 * custom response envelope replace it via {@link SuiEventBus#setResponseHandler}.
 */
export type ResponseHandler = (body: unknown, fallbackHref: string | undefined, bus: SuiEventBus) => Promise<void> | void;
/**
 * Hook for plain {@code [data-href]} clicks. Defaults to
 * {@link SuiEventBus#navigate}; apps that want different behaviour
 * (e.g. delegate to an external router) override via
 * {@link SuiEventBus#setOnNavigate}.
 */
export type NavigateHandler = (href: string) => Promise<void> | void;
/**
 * Hook for 401 responses observed during any built-in fetching. Wire it to
 * {@code redirectToLogin(...)} from {@link "./bff.js"} when running behind a
 * BFF. Return {@code true} (or a thenable thereof) to abort the caller;
 * the default {@code undefined} lets the response flow on.
 */
export type UnauthenticatedHandler = (response: Response, ctx: BehaviorContext | null) => Promise<boolean | void> | boolean | void;
/**
 * Classifies why a built-in fetch failed, so the {@link ErrorHandler} can
 * phrase the message accordingly.
 *
 * <ul>
 *   <li>{@code "network"} — the request never produced a response (backend
 *       down, DNS failure, CORS abort, offline). The browser rejects
 *       {@code fetch} with a {@code TypeError}; {@link #response} is
 *       {@code null}.</li>
 *   <li>{@code "http"} — a response came back but its status was not ok
 *       (4xx/5xx, excluding the 401 that the {@link UnauthenticatedHandler}
 *       already owns). {@link #response} is the failing {@link Response}.</li>
 * </ul>
 */
export interface SuiFetchError {
    kind: "network" | "http";
    /** The failing response for {@code "http"}; {@code null} for {@code "network"}. */
    response: Response | null;
    /** The thrown error for {@code "network"}; {@code null} for {@code "http"}. */
    cause: unknown;
    /** Resolved URL that was requested. */
    url: string;
    /** Behaviour context when the failure happened during a dispatch, else {@code null}. */
    ctx: BehaviorContext | null;
}
/**
 * Hook invoked whenever a built-in fetch fails — either the network never
 * produced a response or the response status was not ok. Defaults to
 * {@link SuiEventBus#showErrorToast}, which surfaces a red error toast
 * through the same toast container the server-driven toasts use. Apps that
 * want different UX (inline banner, retry button, telemetry) override via
 * {@link SuiEventBus#setOnError}.
 */
export type ErrorHandler = (error: SuiFetchError) => Promise<void> | void;
/**
 * Hook for individual SSE events arriving on a {@code STREAM} behaviour.
 *
 * <p>Handlers may inspect the live stream via {@code ctx.bus.activeStreams()}
 * to find the {@link StreamHandle} for the current channel. Returning
 * {@code "deferred"} signals the event could not be applied right now (e.g.
 * the target DOM is gone because the user navigated away) — the bus then
 * buffers the raw event and replays it once the page that owns the stream
 * is re-mounted.
 */
export type StreamEventHandler = (data: string, ctx: BehaviorContext) => Promise<void | "deferred"> | void | "deferred";
/**
 * A live SSE stream, registered with the bus's {@link StreamRegistry}. The
 * stream's lifecycle is decoupled from any single page render — once it
 * starts, it keeps reading even while the user navigates away, and any
 * events received while the owning page is detached are buffered on this
 * handle.
 *
 * <p>The {@link #returnHref} is the URL that, when navigated to, will
 * re-render the originating page. The status-toast uses it as the click
 * target so the user can jump back to the chat in one click.
 */
export interface StreamHandle {
    readonly channelId: string;
    readonly returnHref: string;
    readonly label: string;
    state: "running" | "completed" | "errored";
    /** Raw SSE event records buffered while the owning page is detached. */
    bufferedEvents: {
        event: string;
        data: string;
    }[];
    /** {@code true} while the owning page's DOM is mounted in the renderer root. */
    pageAttached: boolean;
    /**
     * Highest SSE id seen so far on this stream. Tracked so a reconnect
     * (after F5 / tab close) can pass it as {@code lastSeq} and the server
     * replays only the events that arrived in between. 0 = no events seen.
     */
    lastSeq: number;
    /** Cancels the underlying fetch reader. */
    abort: () => void;
}
/**
 * Loading-indicator policy. {@code "auto"} (default) wraps every dispatch
 * in {@code renderer.showLoading()/hideLoading()}; {@code "manual"} leaves
 * it to the handler; a function lets the app filter per-behaviour.
 */
export type LoadingPolicy = "auto" | "manual" | ((ctx: BehaviorContext) => boolean);
/**
 * Centralised event handling, behaviour dispatch and SPA navigation for
 * one semantic-ui root element. Owns one {@code click} and one
 * {@code submit} listener at the root, plus built-in behaviours for the
 * four canonical {@link UiTrigger.Behavior} values (APPLY_RESPONSE,
 * STREAM, DOWNLOAD, OPEN_IN_TAB), and an in-memory router that handles
 * {@code [data-href]} clicks, {@code popstate} and the {@code UiPage} →
 * {@code pushState} flow.
 *
 * <h2>Built-in interactions (no app code required)</h2>
 * <ul>
 *   <li>Click on {@code [data-trigger]} / {@code [data-action]} →
 *       dispatched through the matching behaviour.</li>
 *   <li>Click on {@code [data-href]} without a trigger → routed through
 *       {@link #navigate}.</li>
 *   <li>Click on {@code .sui-tab} → either client-side panel switch
 *       (no {@code data-href}/{@code href}) or SPA navigation when the
 *       tab is rendered as an {@code <a>} with a navigation target
 *       (the SSR-friendly tab variant).</li>
 *   <li>{@code submit} on a form rendered with {@code data-sui="form"} →
 *       dispatches the form's first action trigger.</li>
 *   <li>Buttons with {@code data-confirm} prompt before firing.</li>
 *   <li>{@code APPLY_RESPONSE} fetches JSON, hands the body to the
 *       configured {@link ResponseHandler} (default: branch between
 *       {@code UiPage} and {@code UiPatch}).</li>
 *   <li>{@code DOWNLOAD} fetches a blob and triggers the browser save
 *       dialog, picking the filename from {@code Content-Disposition}.</li>
 *   <li>{@code OPEN_IN_TAB} fetches a blob and opens it in a new tab.</li>
 *   <li>{@code STREAM} POSTs and parses an SSE response; each
 *       {@code event:patch} block is fed into
 *       {@link SuiRenderer#applyPatch}. App-defined event names go through
 *       the {@link StreamEventHandler} map.</li>
 *   <li>401 responses go through the {@link UnauthenticatedHandler}.</li>
 *   <li>Every dispatch is wrapped in
 *       {@code renderer.showLoading()/hideLoading()} unless the policy is
 *       overridden via {@link #setLoadingPolicy}.</li>
 *   <li>{@code navigate(href)} fetches and applies a {@code UiPage};
 *       {@code popstate} re-navigates to the current path. History
 *       updates can be turned off via {@link #setHistoryEnabled}.</li>
 * </ul>
 */
export declare class SuiEventBus {
    private readonly renderer;
    private readonly root;
    private readonly behaviors;
    /** Named client-side handlers dispatched by the built-in {@code INVOKE} behaviour. */
    private readonly clientHandlers;
    private readonly streamEventHandlers;
    /**
     * Live SSE streams that survive navigation. Keyed by channel id (taken
     * from the {@code Sui-Stream-Channel} response header, falling back to a
     * synthetic uuid). See {@link StreamHandle} for the lifecycle contract.
     */
    private readonly streams;
    /** DOM id of the toast element that shows the "Agent running…" indicator. */
    private statusToastId;
    private rewriter;
    private responseHandler;
    private navigateHandler;
    private unauthenticatedHandler;
    private errorHandler;
    private fetcher;
    private loadingPolicy;
    private historyEnabled;
    private popstateInstalled;
    /** Pre-bound {@link #navigate} so callers can pass it as a function. */
    readonly navigate: (href: string) => Promise<void>;
    constructor(renderer: SuiRenderer, root: HTMLElement);
    /** Registers (or replaces) a behaviour handler. */
    registerBehavior(name: string, handler: BehaviorHandler): this;
    /**
     * Registers (or replaces) a client-side handler for the built-in
     * {@code INVOKE} behaviour. A trigger with
     * {@code behavior: "INVOKE", handler: "<name>"} calls the function
     * registered here under {@code name} instead of fetching a URL — the
     * handler is a browser-local "endpoint". See {@link ClientHandler}.
     */
    registerClientHandler(name: string, handler: ClientHandler): this;
    /** Registers (or replaces) a stream-event handler used by the {@code STREAM} behaviour. */
    onStreamEvent(name: string, handler: StreamEventHandler): this;
    /** Replaces the fetcher used by every built-in behaviour and by {@link #navigate}. */
    setFetcher(fetcher: typeof fetch): this;
    /** Installs the URL-rewriting hook (UI path → API path). */
    setUrlRewriter(rewriter: UrlRewriter): this;
    /**
     * Replaces the response handler used by {@code APPLY_RESPONSE} and by
     * {@link #navigate}. The default branches between {@code UiPage} (full
     * swap + {@code pushState}) and {@code UiPatch} (in-place via
     * {@link SuiRenderer#applyPatch}); apps with a custom envelope override
     * to unwrap their own shape first.
     */
    setResponseHandler(handler: ResponseHandler): this;
    /**
     * Replaces the navigation handler invoked for plain {@code [data-href]}
     * clicks. The default is {@link #navigate} (built-in router); apps
     * that route through an external SPA library override here.
     */
    setOnNavigate(handler: NavigateHandler): this;
    /** Installs the 401 handler invoked by every built-in fetching path. */
    setOnUnauthenticated(handler: UnauthenticatedHandler): this;
    /**
     * Replaces the handler invoked when a built-in fetch fails (network down
     * or non-ok HTTP status). The default shows a red error toast; override
     * to integrate an inline banner, retry affordance, or error telemetry.
     */
    setOnError(handler: ErrorHandler): this;
    /**
     * Turns the built-in {@code history.pushState} updates and the
     * {@code popstate} listener on or off. Off is useful for embedded
     * widgets that mustn't touch the host page's address bar.
     */
    setHistoryEnabled(enabled: boolean): this;
    /**
     * Picks whether the renderer's loading indicator is shown around each
     * dispatch.
     */
    setLoadingPolicy(policy: LoadingPolicy): this;
    /**
     * Fetches {@code href} (after URL-rewriting) and applies the response
     * via {@link ResponseHandler}. Pushes {@code href} into the address bar
     * when history is enabled and the response was a {@link UiPage}.
     *
     * <p>The method is also exposed as the bound property
     * {@link #navigate} so callers can pass it as a function reference
     * without losing {@code this}.
     */
    private doNavigate;
    /**
     * Applies a {@link UiPage} to the root and pushes {@code page.navigate}
     * (or {@code fallbackHref}) into the address bar when history is on.
     * Called by the default {@link ResponseHandler}; apps with custom
     * routing can call this directly after unwrapping their envelope.
     */
    applyPage(page: UiPage | null | undefined, fallbackHref?: string): void;
    /**
     * For every server-listed active stream whose channelId we don't already
     * have a {@link StreamHandle} for, open a GET to the server's resume URL
     * and feed the resulting SSE stream through the same {@link #consumeSse}
     * path as a fresh POST stream. {@code lastSeq} is sent as 0 — the client
     * had no prior knowledge of this stream, so the server replays whatever
     * it still has in its ring buffer.
     *
     * <p>Quiet failure model: if a resume URL 404s (stream finished between
     * page-render and client-applyPage), we just log and move on. The next
     * applyPage will see no entry for that channel and the UI converges.
     */
    private reconnectMissingStreams;
    /**
     * GET-side counterpart to {@link #streamBehavior}. Same lifecycle (handle
     * in the registry, pageAttached reconcile, dispatchStreamEvent) but
     * issues a GET with {@code lastSeq=0} instead of a POST with a payload.
     */
    private openReconnectStream;
    /**
     * Public view of all currently registered streams. Handlers and apps
     * can use this to surface "agent X is still running" UI outside the
     * stream's originating page.
     */
    activeStreams(): readonly StreamHandle[];
    /**
     * Walks every registered stream and re-evaluates its {@code pageAttached}
     * flag against the live DOM. When a stream that was detached comes back
     * (the user navigated back to the chat), all events buffered while
     * detached are replayed through their registered handlers — then the
     * buffer is cleared.
     *
     * <p>"Attached" means there is a DOM element with id
     * {@code data-sui-stream-target="<channelId>"} (or, as a convenience,
     * an element with id equal to the channel id itself).
     */
    private reconcileStreamAttachments;
    /**
     * Returns the DOM element marking that a given stream's owning page is
     * mounted. Convention: the originating page renders a hidden anchor
     * with {@code data-sui-stream-target="<channelId>"} so the bus can
     * detect re-mount without coupling to specific component shapes.
     */
    private findStreamTarget;
    /** Builds a minimal BehaviorContext for replay (no source element / payload). */
    private syntheticReplayContext;
    /**
     * Renders or removes the floating "Agent running…" status toast based on
     * the current stream registry. Click navigates back to the originating
     * page; on completion the toast briefly switches to "Antwort fertig"
     * before auto-dismissing.
     */
    private updateStatusToast;
    /**
     * Ensures the persistent body-level dialog host ({@code #sui-dialogs})
     * exists, with the bus's listeners bound to it. Dialogs are appended here
     * as {@code UiDialog} nodes (each a fixed-position {@code .sui-dialog-host}
     * overlay carrying its own id), so several can stack and each is removed
     * individually by a {@code REMOVE} patch on its id or by its close button.
     * The host sits at body level so it overlays {@code #sui-root}; its own
     * listeners are needed because that subtree is outside the root.
     */
    private ensureDialogHost;
    /**
     * Replaces the dialog host's contents with the page's open dialogs. Called
     * on every full page render: the previous page's dialogs are cleared, then
     * each {@code UiDialog} in {@code dialogs} is rendered into the host. Empty
     * / undefined just clears the host.
     */
    private renderDialogs;
    /** Closes the dialog that {@code el} sits inside (its × / backdrop). */
    private closeDialogAround;
    private autoObserver;
    private enhanceScheduled;
    private installAutoEnhance;
    private observeForEnhance;
    private scheduleEnhance;
    /** Runs the idempotent post-render enhancers over the bus's root. */
    private enhance;
    /** Applies a {@link UiPatch} via the renderer. Convenience wrapper. */
    applyPatch(patch: UiPatch): void;
    /**
     * Convenience boot: wires the {@code popstate} listener (if history is
     * enabled) and navigates to {@code initialHref}. Apps typically call
     * this from their entry-point script after wiring hooks.
     */
    start(initialHref: string): Promise<void>;
    private installPopstateListener;
    /** Invokes the configured fetcher. Lets custom behaviours share the auth wiring. */
    fetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response>;
    /** Applies the configured URL rewriter. */
    rewriteUrl(uiUrl: string): string;
    /**
     * Routes a response through the 401 handler if it is a 401. Returns
     * {@code true} when the caller should abort. Pass {@code null} as
     * {@code ctx} from non-behaviour call sites (e.g. {@link #navigate}).
     */
    handleUnauthenticated(res: Response, ctx: BehaviorContext | null): Promise<boolean>;
    /**
     * Invokes the configured fetcher and converts a rejected fetch (backend
     * unreachable, DNS failure, offline — the browser's {@code TypeError:
     * Failed to fetch}) into a routed {@link ErrorHandler} call plus a
     * {@code null} return. Every built-in fetching path goes through here so
     * a dead backend surfaces a visible error instead of an uncaught promise
     * rejection in the console.
     *
     * <p>Returns {@code null} when the request never produced a response —
     * callers must treat {@code null} as "already reported, stop".
     */
    private safeFetch;
    /**
     * Ensures every bus fetch advertises {@code Accept: application/json}. The
     * bus only ever consumes UiPage / UiPatch JSON, so this lets servers do
     * content negotiation: a backend can serve the SPA shell (text/html) on a
     * direct browser navigation to a URL while still returning JSON to the bus.
     * A caller's explicit Accept header (e.g. SSE's text/event-stream) is kept.
     */
    private withJsonAccept;
    /**
     * Routes a non-ok response through the {@link ErrorHandler}. Skips 401
     * (owned by {@link #handleUnauthenticated}). Returns {@code true} when an
     * error was reported so callers can {@code if (await reportHttpError(...))
     * return;} in one line.
     */
    private reportHttpError;
    /** Dispatches an error to the configured handler, guarding against handler throws. */
    private reportError;
    /**
     * Default {@link ErrorHandler}: renders a red, sticky-ish error toast via
     * the same body-level toast container the server-driven toasts use, so a
     * failed fetch is visible without any app wiring. Network failures and
     * HTTP errors get distinct, human-readable German copy (the admin UI is
     * German); apps needing other locales/UX replace this via
     * {@link #setOnError}.
     */
    showErrorToast(error: SuiFetchError): void;
    /**
     * Fires a trigger imperatively, as if a user had clicked an element
     * carrying it. The loading indicator is shown around the dispatch
     * according to {@link #setLoadingPolicy}.
     */
    dispatch(trigger: UiTrigger, sourceElement?: HTMLElement, files?: File[]): Promise<void>;
    /**
     * Marks the clicked control as busy: adds `.is-loading` (CSS spinner +
     * pointer-events:none) and `aria-busy`. Skips the renderer root — that is
     * the fallback source for element-less imperative dispatches, and painting
     * a spinner across the whole surface is the global indicator's job, not
     * this one's. Returns the element so {@link #clearBusy} can undo it, or
     * null when nothing was marked.
     */
    private markBusy;
    /** Reverts {@link #markBusy}. Safe on a since-detached element. */
    private clearBusy;
    private shouldShowLoading;
    private installRootListeners;
    /** Pre-bound handler set; keeps {@code add/removeEventListener} symmetric. */
    private boundHandlers;
    /** Dialog-host element we've attached listeners to; null when no dialog open. */
    private dialogListenerHost;
    private installListenersOn;
    private removeListenersFrom;
    /**
     * Returns true when {@code el} is part of the SPA-controlled subtree —
     * either inside the renderer's root or inside a currently-open dialog
     * overlay. With listeners now bound directly to those subtrees,
     * inScope() is belt-and-braces: it filters out stray events (e.g. those
     * synthesised from Shadow DOM or composed-path quirks) but in practice
     * nothing outside scope will reach our handlers anyway.
     */
    private inScope;
    /**
     * Submits the surrounding form when Enter is pressed inside a textarea
     * marked with {@code data-submit-on-enter}. The actual submit goes
     * through {@link HTMLFormElement#requestSubmit} so {@link #handleSubmit}
     * picks it up identically to a click on the primary button — no
     * separate dispatch path.
     */
    /**
     * Fires the surrounding form when an input marked
     * {@code data-submit-on-change} reports a {@code change} event. Works
     * for {@code <select>}, {@code <input type="checkbox">}, and any other
     * native control that bubbles {@code change}. Routes through
     * {@link HTMLFormElement#requestSubmit} so {@link #handleSubmit} picks
     * it up like a regular submit.
     *
     * <p>The pure-SSR path uses an inline auto-wiring script in
     * {@code UiPageHtmlMessageConverter} that does the same thing — the two
     * scripts are mutually exclusive (SPA bootstrap replaces the inline
     * script), so no double-submit guard is needed.
     */
    private handleChange;
    /**
     * Dispatches the upload trigger for a file {@code <input>} that just
     * changed. The trigger comes from the surrounding {@code [data-sui-upload]}
     * zone ({@code data-upload-trigger}) or, for a standalone FILE field, the
     * input's own {@code data-change-trigger}. The zone is passed as the source
     * element so the {@code UPLOAD} behaviour can read {@code data-sui-upload-name}.
     */
    private handleFileSelection;
    /** The {@code [data-sui-upload]} zone an event landed in, or null. */
    private uploadZoneOf;
    private handleDragOver;
    private handleDragLeave;
    private handleDrop;
    /** Parses a trigger from a raw JSON string, logging (not throwing) on error. */
    private parseTriggerJson;
    private handleKeydown;
    private handleClick;
    /**
     * Best-effort client-side sort for a table without a {@code sortTrigger}.
     *
     * <p>Reads the table's serialised model from {@code data-node} (the same
     * mechanism row/column patches use), reorders {@code rows} by the clicked
     * column, records the new sort state on the model and re-renders the
     * table. Because it works on the model rather than the DOM, the header
     * indicator, {@code aria-sort} and any later patch all stay consistent.
     *
     * <p>Comparison is deliberately simple: numeric when both values parse as
     * finite numbers, otherwise a locale-aware string compare. Blank values
     * sort last in both directions — an empty cell is "no value", not "the
     * smallest value".
     *
     * <p>This only ever sees the rows currently in the DOM. For a paginated
     * table that is one page of many, which is why {@link UiTable} should
     * carry a {@code sortTrigger} whenever it carries {@code pagination}.
     */
    private sortTableClientSide;
    /**
     * Generic {@code data-sui-on-<event>} dispatch — the mechanism behind
     * {@code UiNode.events}, which every node type inherits.
     *
     * <p>Walks up from the event target to the nearest ancestor carrying the
     * attribute for this event, so a click on a label inside a stack still
     * reaches the stack. Returns true when a trigger was dispatched, which lets
     * the click path skip its own handling.
     */
    private handleNodeEvent;
    /**
     * hover / leave. {@code mouseenter} and {@code mouseleave} don't bubble, so
     * they can't be delegated from the root; we listen to the bubbling
     * {@code mouseover} / {@code mouseout} instead and drop the events that
     * merely move between descendants of the same element — otherwise a hover
     * trigger would fire again for every child the pointer crosses.
     */
    private handleHover;
    private handleSubmit;
    /**
     * Mutates {@code trigger.payload} to point at the surrounding
     * {@code <form data-sui="form">}'s id when the action didn't declare an
     * explicit payload source. Matches native HTML-form submit semantics —
     * all named inputs in the enclosing form ride along — so a search action
     * built as {@code .dispatch("GET", "/admin/products")} without a node-id
     * still picks up the {@code ?q=…} from the surrounding form.
     *
     * <p>Called from both the click path (where the button intercepts before
     * the browser fires a submit event) and the submit path (Enter / native
     * form submit).
     */
    private inferImplicitPayload;
    private switchTab;
    private parseTrigger;
    /**
     * Collects editable field values from a {@code UiForm}-like node by id.
     *
     * <p>Walks every named {@code <input>}/{@code <select>}/{@code <textarea>}
     * inside the form and reads its value. The semantic type ({@code NUMBER},
     * {@code CURRENCY}, …) is taken from {@code data-sui-type} on the
     * control — both the SPA renderer and the SSR Handlebars template emit
     * it, so this one code path serves both modes.
     */
    private collectPayload;
    private registerDefaultBehaviors;
    /**
     * Built-in {@code UPLOAD} behaviour: POSTs {@code ctx.files} to
     * {@code ctx.url} as {@code multipart/form-data} and applies the response
     * through the configured {@link ResponseHandler} — same as a normal
     * fetch, but with a file body. The multipart field name comes from the
     * source element's {@code data-sui-upload-name} (or its {@code name}),
     * falling back to {@code "files"}. Fired by a {@code UiUpload} drop zone
     * or a {@code FILE} field's change.
     */
    private uploadBehavior;
    /**
     * Built-in {@code PATCH} behaviour: applies the {@link UiPatch} carried
     * inline on the trigger ({@code trigger.patch}) — no server call, no JS
     * handler. The patch is baked into the trigger at render time, so this is
     * the leanest way to express static, known-ahead UI logic: a list row
     * that fills a detail panel, a button that opens a fixed dialog, a toggle
     * that reveals another field — all with zero round-trip.
     */
    private inlinePatchBehavior;
    /**
     * Built-in {@code INVOKE} behaviour: looks up the client handler named by
     * {@code trigger.handler} and runs it — no network. A returned
     * {@link UiPage} / {@link UiPatch} is applied through the configured
     * {@link ResponseHandler} (same path a fetched response takes), so a
     * handler can swap the page, open a dialog, or emit a partial patch. A
     * {@code void} return means the handler already applied its own changes.
     */
    private invokeBehavior;
    private applyResponseBehavior;
    private downloadBehavior;
    private openInTabBehavior;
    private streamBehavior;
    private consumeSse;
    /**
     * Routes one SSE event to its registered handler. If the owning page is
     * detached (the user navigated away mid-stream), the event is buffered
     * on the handle and will be replayed when the page is re-mounted. If
     * the handler runs but returns {@code "deferred"} the event is also
     * buffered (the handler determined it couldn't apply right now, e.g.
     * the target id wasn't in the live DOM despite the page being mounted).
     */
    private dispatchStreamEvent;
}
