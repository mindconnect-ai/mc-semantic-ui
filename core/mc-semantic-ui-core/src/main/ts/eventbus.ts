import type { UiTrigger, UiPage, UiPatch, UiToast, UiDialog } from "./model.js";
import type { SuiRenderer } from "./renderer.js";
import { applyMenuState, nextMenuState, restoreMenuState } from "./renderers/menu.js";
import { renderIcon } from "./renderers/icon.js";
import { wireOverflow } from "./renderers/overflow.js";
import { wireMenuButtons } from "./renderers/menu-button.js";

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
export type ClientHandler =
    (ctx: BehaviorContext) => UiPage | UiPatch | void | Promise<UiPage | UiPatch | void>;

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
export type UnauthenticatedHandler =
    (response: Response, ctx: BehaviorContext | null) => Promise<boolean | void> | boolean | void;

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
export type StreamEventHandler =
    (data: string, ctx: BehaviorContext) => Promise<void | "deferred"> | void | "deferred";

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
    bufferedEvents: { event: string; data: string }[];
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
export class SuiEventBus {

    private readonly renderer: SuiRenderer;
    private readonly root: HTMLElement;
    private readonly behaviors = new Map<string, BehaviorHandler>();
    /** Named client-side handlers dispatched by the built-in {@code INVOKE} behaviour. */
    private readonly clientHandlers = new Map<string, ClientHandler>();
    private readonly streamEventHandlers = new Map<string, StreamEventHandler>();
    /**
     * Live SSE streams that survive navigation. Keyed by channel id (taken
     * from the {@code Sui-Stream-Channel} response header, falling back to a
     * synthetic uuid). See {@link StreamHandle} for the lifecycle contract.
     */
    private readonly streams = new Map<string, StreamHandle>();
    /** DOM id of the toast element that shows the "Agent running…" indicator. */
    private statusToastId: string | null = null;
    private rewriter: UrlRewriter = (u) => u;
    private responseHandler: ResponseHandler = defaultResponseHandler;
    private navigateHandler: NavigateHandler | null = null;
    private unauthenticatedHandler: UnauthenticatedHandler | null = null;
    private errorHandler: ErrorHandler = (e) => this.showErrorToast(e);
    private fetcher: typeof fetch = (input, init) => fetch(input, init);
    private loadingPolicy: LoadingPolicy = "auto";
    private historyEnabled = true;
    private popstateInstalled = false;
    /** Pre-bound {@link #navigate} so callers can pass it as a function. */
    readonly navigate: (href: string) => Promise<void>;

    constructor(renderer: SuiRenderer, root: HTMLElement) {
        this.renderer = renderer;
        this.root = root;
        this.navigate = this.doNavigate.bind(this);
        this.installRootListeners();
        this.ensureDialogHost();
        this.registerDefaultBehaviors();
        this.installAutoEnhance();
        // A patch SSE event is so universal that we wire it as a built-in
        // stream handler; apps can override by registering another handler
        // under the same name.
        this.onStreamEvent("patch", (data) => {
            try { this.renderer.applyPatch(JSON.parse(data)); }
            catch (err) { console.error("SuiEventBus: patch parse error", err, data); }
        });
    }

    // ── Configuration ─────────────────────────────────────────────────────

    /** Registers (or replaces) a behaviour handler. */
    registerBehavior(name: string, handler: BehaviorHandler): this {
        this.behaviors.set(name, handler);
        return this;
    }

    /**
     * Registers (or replaces) a client-side handler for the built-in
     * {@code INVOKE} behaviour. A trigger with
     * {@code behavior: "INVOKE", handler: "<name>"} calls the function
     * registered here under {@code name} instead of fetching a URL — the
     * handler is a browser-local "endpoint". See {@link ClientHandler}.
     */
    registerClientHandler(name: string, handler: ClientHandler): this {
        this.clientHandlers.set(name, handler);
        return this;
    }

    /** Registers (or replaces) a stream-event handler used by the {@code STREAM} behaviour. */
    onStreamEvent(name: string, handler: StreamEventHandler): this {
        this.streamEventHandlers.set(name, handler);
        return this;
    }

    /** Replaces the fetcher used by every built-in behaviour and by {@link #navigate}. */
    setFetcher(fetcher: typeof fetch): this {
        this.fetcher = fetcher;
        return this;
    }

    /** Installs the URL-rewriting hook (UI path → API path). */
    setUrlRewriter(rewriter: UrlRewriter): this {
        this.rewriter = rewriter;
        return this;
    }

    /**
     * Replaces the response handler used by {@code APPLY_RESPONSE} and by
     * {@link #navigate}. The default branches between {@code UiPage} (full
     * swap + {@code pushState}) and {@code UiPatch} (in-place via
     * {@link SuiRenderer#applyPatch}); apps with a custom envelope override
     * to unwrap their own shape first.
     */
    setResponseHandler(handler: ResponseHandler): this {
        this.responseHandler = handler;
        return this;
    }

    /**
     * Replaces the navigation handler invoked for plain {@code [data-href]}
     * clicks. The default is {@link #navigate} (built-in router); apps
     * that route through an external SPA library override here.
     */
    setOnNavigate(handler: NavigateHandler): this {
        this.navigateHandler = handler;
        return this;
    }

    /** Installs the 401 handler invoked by every built-in fetching path. */
    setOnUnauthenticated(handler: UnauthenticatedHandler): this {
        this.unauthenticatedHandler = handler;
        return this;
    }

    /**
     * Replaces the handler invoked when a built-in fetch fails (network down
     * or non-ok HTTP status). The default shows a red error toast; override
     * to integrate an inline banner, retry affordance, or error telemetry.
     */
    setOnError(handler: ErrorHandler): this {
        this.errorHandler = handler;
        return this;
    }

    /**
     * Turns the built-in {@code history.pushState} updates and the
     * {@code popstate} listener on or off. Off is useful for embedded
     * widgets that mustn't touch the host page's address bar.
     */
    setHistoryEnabled(enabled: boolean): this {
        this.historyEnabled = enabled;
        if (enabled) this.installPopstateListener();
        return this;
    }

    /**
     * Picks whether the renderer's loading indicator is shown around each
     * dispatch.
     */
    setLoadingPolicy(policy: LoadingPolicy): this {
        this.loadingPolicy = policy;
        return this;
    }

    // ── Navigation (built-in router) ──────────────────────────────────────

    /**
     * Fetches {@code href} (after URL-rewriting) and applies the response
     * via {@link ResponseHandler}. Pushes {@code href} into the address bar
     * when history is enabled and the response was a {@link UiPage}.
     *
     * <p>The method is also exposed as the bound property
     * {@link #navigate} so callers can pass it as a function reference
     * without losing {@code this}.
     */
    private async doNavigate(href: string): Promise<void> {
        // Delegate to the app's external navigate handler if one is set —
        // typical for apps that integrate an external SPA router.
        if (this.navigateHandler) {
            await this.navigateHandler(href);
            return;
        }
        const url = this.rewriter(href);
        const res = await this.safeFetch(url, undefined, null);
        if (!res) return; // network failure already reported
        if (await this.handleUnauthenticated(res, null)) return;
        if (await this.reportHttpError(res, url, null)) return;
        const ct = res.headers.get("content-type") ?? "";
        const body = (res.status !== 204 && ct.includes("application/json"))
            ? await res.json()
            : null;
        await this.responseHandler(body, href, this);
    }

    /**
     * Applies a {@link UiPage} to the root and pushes {@code page.navigate}
     * (or {@code fallbackHref}) into the address bar when history is on.
     * Called by the default {@link ResponseHandler}; apps with custom
     * routing can call this directly after unwrapping their envelope.
     */
    applyPage(page: UiPage | null | undefined, fallbackHref?: string): void {
        if (page && page.node) this.renderer.mount(page.node);
        // A full page render is a fresh screen: drop the previous page's open
        // dialogs and paint this page's own (page.dialogs) into the host. Each
        // dialog is a UiDialog node (a fixed-position .sui-dialog-host overlay
        // carrying its own id); later opens/closes are APPEND/REMOVE patches.
        this.renderDialogs(page?.dialogs);
        if (page?.toasts) showToasts(page.toasts);
        // The DOM just swapped. Some live streams' target containers may
        // have come back (user navigated back to the chat) or just left
        // (user navigated away). Reconcile both directions: replay buffered
        // events into freshly-mounted targets, mark abandoned streams.
        this.reconcileStreamAttachments();
        // Server-driven resume: the page may name streams it still considers
        // running. For any channel we don't already track locally (F5, second
        // tab, freshly opened admin window), open a GET reconnect so live
        // patches start flowing into this page's DOM.
        if (page?.activeStreams) this.reconnectMissingStreams(page.activeStreams);
        if (!this.historyEnabled) return;
        const dest = page?.navigate ?? fallbackHref;
        if (dest) window.history.pushState({}, "", dest);
    }

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
    private reconnectMissingStreams(entries: NonNullable<UiPage["activeStreams"]>): void {
        for (const entry of entries) {
            if (!entry?.channelId || !entry.resumeUrl) continue;
            if (this.streams.has(entry.channelId)) continue;
            void this.openReconnectStream(entry).catch(err => {
                console.warn(`SuiEventBus: reconnect ${entry.channelId} failed`, err);
            });
        }
    }

    /**
     * GET-side counterpart to {@link #streamBehavior}. Same lifecycle (handle
     * in the registry, pageAttached reconcile, dispatchStreamEvent) but
     * issues a GET with {@code lastSeq=0} instead of a POST with a payload.
     */
    private async openReconnectStream(entry: NonNullable<UiPage["activeStreams"]>[number]): Promise<void> {
        const abortController = new AbortController();
        const url = entry.resumeUrl.includes("?")
            ? `${entry.resumeUrl}&lastSeq=0`
            : `${entry.resumeUrl}?lastSeq=0`;
        const res = await this.fetcher(url, {
            method: "GET",
            headers: { "Accept": "text/event-stream" },
            signal: abortController.signal,
        });
        if (!res.ok || !res.body) {
            console.warn(`SuiEventBus: reconnect ${entry.channelId} got`, res.status);
            return;
        }
        const handle: StreamHandle = {
            channelId: entry.channelId,
            returnHref: entry.returnHref ?? (window.location.pathname + window.location.search),
            label: entry.label ?? "Agent",
            state: "running",
            bufferedEvents: [],
            pageAttached: this.findStreamTarget(entry.channelId) != null,
            lastSeq: 0,
            abort: () => abortController.abort(),
        };
        this.streams.set(entry.channelId, handle);
        this.updateStatusToast();

        // Reuse the regular streaming loop. A synthetic BehaviorContext is
        // enough — the patch handler only reads {bus} from closure.
        const ctx: BehaviorContext = this.syntheticReplayContext(handle);
        void this.consumeSse(res.body, ctx, handle).catch(err => {
            console.warn(`SuiEventBus: reconnect ${entry.channelId} aborted`, err);
            handle.state = "errored";
            this.updateStatusToast();
        });
    }

    /**
     * Public view of all currently registered streams. Handlers and apps
     * can use this to surface "agent X is still running" UI outside the
     * stream's originating page.
     */
    activeStreams(): readonly StreamHandle[] {
        return Array.from(this.streams.values());
    }

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
    private reconcileStreamAttachments(): void {
        for (const stream of this.streams.values()) {
            const wasAttached = stream.pageAttached;
            const nowAttached = this.findStreamTarget(stream.channelId) != null;
            stream.pageAttached = nowAttached;
            if (!wasAttached && nowAttached && stream.bufferedEvents.length > 0) {
                const buffered = stream.bufferedEvents.splice(0);
                for (const ev of buffered) {
                    const handler = this.streamEventHandlers.get(ev.event);
                    if (!handler) continue;
                    try {
                        // We don't have the original BehaviorContext anymore;
                        // a synthetic one is enough for the built-in patch
                        // handler (it only reads {bus} through closure).
                        handler(ev.data, this.syntheticReplayContext(stream));
                    } catch (err) {
                        console.error(`SuiEventBus: replay handler "${ev.event}" failed`, err);
                    }
                }
            }
        }
        this.updateStatusToast();
    }

    /**
     * Returns the DOM element marking that a given stream's owning page is
     * mounted. Convention: the originating page renders a hidden anchor
     * with {@code data-sui-stream-target="<channelId>"} so the bus can
     * detect re-mount without coupling to specific component shapes.
     */
    private findStreamTarget(channelId: string): HTMLElement | null {
        const sel = `[data-sui-stream-target="${cssEscape(channelId)}"]`;
        return this.root.querySelector<HTMLElement>(sel)
            ?? document.getElementById(channelId);
    }

    /** Builds a minimal BehaviorContext for replay (no source element / payload). */
    private syntheticReplayContext(stream: StreamHandle): BehaviorContext {
        return {
            trigger: { url: stream.returnHref, behavior: "STREAM" } as UiTrigger,
            payload: null,
            sourceElement: this.root,
            url: stream.returnHref,
            method: "POST",
            fetch: this.fetcher,
            bus: this,
        };
    }

    /**
     * Renders or removes the floating "Agent running…" status toast based on
     * the current stream registry. Click navigates back to the originating
     * page; on completion the toast briefly switches to "Antwort fertig"
     * before auto-dismissing.
     */
    private updateStatusToast(): void {
        const detached = Array.from(this.streams.values()).filter(s => !s.pageAttached);
        const running = detached.filter(s => s.state === "running");
        const completed = detached.filter(s => s.state === "completed" || s.state === "errored");

        // No detached streams → drop the toast if it was shown.
        if (running.length === 0 && completed.length === 0) {
            if (this.statusToastId) {
                document.getElementById(this.statusToastId)?.remove();
                this.statusToastId = null;
            }
            return;
        }

        // Pick what to display: prefer "running" (urgent), else "completed".
        const showRunning = running.length > 0;
        const focus = showRunning ? running[0] : completed[0];
        const label = showRunning
            ? `${focus.label} läuft…`
            : `${focus.label}: Antwort bereit`;

        let toast = this.statusToastId ? document.getElementById(this.statusToastId) : null;
        if (!toast) {
            toast = document.createElement("div");
            toast.id = "sui-stream-status-toast";
            toast.className = "sui-toast sui-toast--info sui-stream-status";
            // Position bottom-right — independent of the regular toast
            // container so it stays put while regular toasts come and go.
            toast.setAttribute("role", "status");
            this.statusToastId = toast.id;
            document.body.appendChild(toast);
        }
        toast.innerHTML = "";
        const text = document.createElement("span");
        text.textContent = label;
        toast.appendChild(text);
        const link = document.createElement("button");
        link.type = "button";
        link.className = "sui-toast-link";
        link.textContent = "Zum Chat";
        link.addEventListener("click", () => { void this.doNavigate(focus.returnHref); });
        toast.appendChild(link);

        // Completed streams self-dismiss after a few seconds so the toast
        // doesn't stick around once acknowledged.
        if (!showRunning) {
            setTimeout(() => {
                // Drop the completed stream from the registry and re-render
                // the toast (which removes it once the registry is empty).
                this.streams.delete(focus.channelId);
                this.updateStatusToast();
            }, 5000);
        }
    }

    /**
     * Ensures the persistent body-level dialog host ({@code #sui-dialogs})
     * exists, with the bus's listeners bound to it. Dialogs are appended here
     * as {@code UiDialog} nodes (each a fixed-position {@code .sui-dialog-host}
     * overlay carrying its own id), so several can stack and each is removed
     * individually by a {@code REMOVE} patch on its id or by its close button.
     * The host sits at body level so it overlays {@code #sui-root}; its own
     * listeners are needed because that subtree is outside the root.
     */
    private ensureDialogHost(): HTMLElement {
        let host = document.getElementById("sui-dialogs");
        if (!host) {
            host = document.createElement("div");
            host.id = "sui-dialogs";
            host.className = "sui-dialogs";
            document.body.appendChild(host);
            this.installListenersOn(host);
            // inScope() consults this so events from within dialogs are handled.
            this.dialogListenerHost = host;
        }
        return host;
    }

    /**
     * Replaces the dialog host's contents with the page's open dialogs. Called
     * on every full page render: the previous page's dialogs are cleared, then
     * each {@code UiDialog} in {@code dialogs} is rendered into the host. Empty
     * / undefined just clears the host.
     */
    private renderDialogs(dialogs: UiDialog[] | null | undefined): void {
        const host = this.ensureDialogHost();
        const html = (dialogs ?? []).map(d => this.renderer.render(d)).join("");
        host.innerHTML = html;
    }

    /** Closes the dialog that {@code el} sits inside (its × / backdrop). */
    private closeDialogAround(el: HTMLElement): void {
        el.closest(".sui-dialog-host")?.remove();
    }

    // ── Automatic progressive enhancement ─────────────────────────────────────
    //
    // Some node types need a touch of post-render wiring that can't live in the
    // pure (SSR-shared) renderer: tab bars that collapse overflow into a "⋯"
    // menu, menu-button popovers, a menu's persisted collapse state. Rather than
    // make every app remember to call wireTabOverflow()/wireMenuButtons()/… by
    // hand, the bus watches its root and re-runs these enhancers whenever the DOM
    // changes — after a mount, a patch, an SSR hydrate, anything. They're all
    // idempotent, so re-running is cheap and safe. The standalone functions stay
    // exported for apps that render without a bus.

    private autoObserver: MutationObserver | null = null;
    private enhanceScheduled = false;

    private installAutoEnhance(): void {
        if (typeof MutationObserver === "undefined") { this.enhance(); return; }
        this.autoObserver = new MutationObserver(() => this.scheduleEnhance());
        this.observeForEnhance();
        this.scheduleEnhance();   // handle SSR-hydrated content already in root
    }

    private observeForEnhance(): void {
        this.autoObserver?.observe(this.root, { childList: true, subtree: true });
    }

    private scheduleEnhance(): void {
        if (this.enhanceScheduled) return;
        this.enhanceScheduled = true;
        const run = () => {
            this.enhanceScheduled = false;
            // The enhancers mutate the DOM (moving tabs into the overflow menu);
            // pause observation across the run so we don't self-trigger a loop.
            this.autoObserver?.disconnect();
            try { this.enhance(); } finally { this.observeForEnhance(); }
        };
        if (typeof requestAnimationFrame === "function") requestAnimationFrame(run);
        else setTimeout(run, 0);
    }

    /** Runs the idempotent post-render enhancers over the bus's root. */
    private enhance(): void {
        try { restoreMenuState(this.root); } catch { /* ignore */ }
        // One call covers every container marked data-sui-overflow="menu":
        // tab bars, header extras, application toolbars.
        try { wireOverflow(this.root); } catch { /* ignore */ }
        try { wireMenuButtons(this.root); } catch { /* ignore */ }
    }

    /** Applies a {@link UiPatch} via the renderer. Convenience wrapper. */
    applyPatch(patch: UiPatch): void {
        // A patch may APPEND a dialog node into the dialog host (open) or
        // REMOVE one by id (close) — make sure the host exists first.
        this.ensureDialogHost();
        this.renderer.applyPatch(patch);
        if (patch?.toasts) showToasts(patch.toasts);
    }

    /**
     * Convenience boot: wires the {@code popstate} listener (if history is
     * enabled) and navigates to {@code initialHref}. Apps typically call
     * this from their entry-point script after wiring hooks.
     */
    start(initialHref: string): Promise<void> {
        if (this.historyEnabled) this.installPopstateListener();
        return this.navigate(initialHref);
    }

    private installPopstateListener(): void {
        if (this.popstateInstalled) return;
        this.popstateInstalled = true;
        window.addEventListener("popstate", () => {
            void this.navigate(window.location.pathname + window.location.search);
        });
    }

    // ── Helpers exposed for co-operating modules / custom behaviours ──────

    /** Invokes the configured fetcher. Lets custom behaviours share the auth wiring. */
    fetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
        return this.fetcher(input, init);
    }

    /** Applies the configured URL rewriter. */
    rewriteUrl(uiUrl: string): string {
        return this.rewriter(uiUrl);
    }

    /**
     * Routes a response through the 401 handler if it is a 401. Returns
     * {@code true} when the caller should abort. Pass {@code null} as
     * {@code ctx} from non-behaviour call sites (e.g. {@link #navigate}).
     */
    async handleUnauthenticated(res: Response, ctx: BehaviorContext | null): Promise<boolean> {
        if (res.status !== 401) return false;
        if (!this.unauthenticatedHandler) return true;
        const handled = await this.unauthenticatedHandler(res, ctx);
        return handled !== false;
    }

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
    private async safeFetch(url: string, init: RequestInit | undefined,
                            ctx: BehaviorContext | null): Promise<Response | null> {
        try {
            return await this.fetcher(url, this.withJsonAccept(init));
        } catch (cause) {
            void this.reportError({ kind: "network", response: null, cause, url, ctx });
            return null;
        }
    }

    /**
     * Ensures every bus fetch advertises {@code Accept: application/json}. The
     * bus only ever consumes UiPage / UiPatch JSON, so this lets servers do
     * content negotiation: a backend can serve the SPA shell (text/html) on a
     * direct browser navigation to a URL while still returning JSON to the bus.
     * A caller's explicit Accept header (e.g. SSE's text/event-stream) is kept.
     */
    private withJsonAccept(init: RequestInit | undefined): RequestInit {
        const merged: RequestInit = { ...(init ?? {}) };
        const headers = new Headers(merged.headers);
        if (!headers.has("Accept")) headers.set("Accept", "application/json");
        merged.headers = headers;
        return merged;
    }

    /**
     * Routes a non-ok response through the {@link ErrorHandler}. Skips 401
     * (owned by {@link #handleUnauthenticated}). Returns {@code true} when an
     * error was reported so callers can {@code if (await reportHttpError(...))
     * return;} in one line.
     */
    private async reportHttpError(res: Response, url: string,
                                  ctx: BehaviorContext | null): Promise<boolean> {
        if (res.ok || res.status === 401) return false;
        await this.reportError({ kind: "http", response: res, cause: null, url, ctx });
        return true;
    }

    /** Dispatches an error to the configured handler, guarding against handler throws. */
    private async reportError(error: SuiFetchError): Promise<void> {
        try {
            await this.errorHandler(error);
        } catch (err) {
            console.error("SuiEventBus: error handler itself failed", err, error);
        }
    }

    /**
     * Default {@link ErrorHandler}: renders a red, sticky-ish error toast via
     * the same body-level toast container the server-driven toasts use, so a
     * failed fetch is visible without any app wiring. Network failures and
     * HTTP errors get distinct, human-readable German copy (the admin UI is
     * German); apps needing other locales/UX replace this via
     * {@link #setOnError}.
     */
    showErrorToast(error: SuiFetchError): void {
        const toast: UiToast = error.kind === "network"
            ? {
                level: "ERROR",
                title: "Verbindungsfehler",
                message: "Das Backend ist nicht erreichbar. Bitte prüfen Sie die "
                    + "Verbindung und versuchen Sie es erneut.",
                durationMs: 8000,
            }
            : {
                level: "ERROR",
                title: "Fehler",
                message: `Die Anfrage ist fehlgeschlagen (HTTP ${error.response?.status}).`,
                durationMs: 8000,
            };
        showToasts([toast]);
    }

    // ── Trigger dispatch ──────────────────────────────────────────────────

    /**
     * Fires a trigger imperatively, as if a user had clicked an element
     * carrying it. The loading indicator is shown around the dispatch
     * according to {@link #setLoadingPolicy}.
     */
    async dispatch(trigger: UiTrigger, sourceElement?: HTMLElement, files?: File[]): Promise<void> {
        const ctx: BehaviorContext = {
            trigger,
            payload: trigger.payload ? this.collectPayload(trigger.payload) : null,
            sourceElement: sourceElement ?? this.root,
            url: this.rewriter(trigger.url ?? ""),
            method: (trigger.method ?? "GET").toUpperCase(),
            fetch: this.fetcher,
            bus: this,
            files,
        };
        const name = trigger.behavior ?? "APPLY_RESPONSE";
        const handler = this.behaviors.get(name);
        if (!handler) {
            console.warn(`SuiEventBus: no behaviour registered for "${name}"`);
            return;
        }
        const showLoading = this.shouldShowLoading(ctx);
        // Inline feedback on the very control the user clicked: an `is-loading`
        // class (CSS paints a small spinner and blocks re-clicks) plus
        // `aria-busy`. Tied to the same policy/lifecycle as the global loading
        // indicator, so a `manual` policy suppresses both. Only a concrete
        // source element is marked — an imperative dispatch() with no element
        // falls back to the root, which we never decorate.
        const busyEl = showLoading ? this.markBusy(sourceElement) : null;
        if (showLoading) this.renderer.showLoading();
        try {
            await handler(ctx);
        } catch (err) {
            console.error(`SuiEventBus: behaviour "${name}" failed`, err);
        } finally {
            if (showLoading) this.renderer.hideLoading();
            if (busyEl) this.clearBusy(busyEl);
        }
    }

    /**
     * Marks the clicked control as busy: adds `.is-loading` (CSS spinner +
     * pointer-events:none) and `aria-busy`. Skips the renderer root — that is
     * the fallback source for element-less imperative dispatches, and painting
     * a spinner across the whole surface is the global indicator's job, not
     * this one's. Returns the element so {@link #clearBusy} can undo it, or
     * null when nothing was marked.
     */
    private markBusy(el: HTMLElement | undefined): HTMLElement | null {
        if (!el || el === this.root) return null;
        el.classList.add("is-loading");
        el.setAttribute("aria-busy", "true");
        return el;
    }

    /** Reverts {@link #markBusy}. Safe on a since-detached element. */
    private clearBusy(el: HTMLElement): void {
        el.classList.remove("is-loading");
        el.removeAttribute("aria-busy");
    }

    private shouldShowLoading(ctx: BehaviorContext): boolean {
        if (this.loadingPolicy === "manual") return false;
        if (this.loadingPolicy === "auto") return true;
        return this.loadingPolicy(ctx);
    }

    // ── DOM wiring ────────────────────────────────────────────────────────

    private installRootListeners(): void {
        // The bus is scoped to its own root container. Apps that embed the
        // bus alongside other DOM (e.g. an editor preview with its own
        // suppression layer) are not affected by our listeners because they
        // never bubble out of {@code root}. Dialog overlays — which live at
        // body level for modal-stacking reasons — get the same listener set
        // bound dynamically by {@link #mountDialog}.
        this.installListenersOn(this.root);
    }

    /** Pre-bound handler set; keeps {@code add/removeEventListener} symmetric. */
    private boundHandlers: {
        click: (e: Event) => void;
        submit: (e: Event) => void;
        keydown: (e: Event) => void;
        change: (e: Event) => void;
        dblclick: (e: Event) => void;
        input: (e: Event) => void;
        mouseover: (e: Event) => void;
        mouseout: (e: Event) => void;
        dragover: (e: Event) => void;
        dragleave: (e: Event) => void;
        drop: (e: Event) => void;
    } | null = null;
    /** Dialog-host element we've attached listeners to; null when no dialog open. */
    private dialogListenerHost: HTMLElement | null = null;

    private installListenersOn(target: HTMLElement): void {
        if (!this.boundHandlers) {
            this.boundHandlers = {
                click:     (e) => this.handleClick(e as MouseEvent),
                submit:    (e) => this.handleSubmit(e as SubmitEvent),
                keydown:   (e) => this.handleKeydown(e as KeyboardEvent),
                change:    (e) => this.handleChange(e),
                dblclick:  (e) => this.handleNodeEvent(e, "dblclick"),
                input:     (e) => this.handleNodeEvent(e, "input"),
                // mouseenter/leave don't bubble, so delegation uses the
                // bubbling mouseover/mouseout pair and filters out moves that
                // stay inside the same element (relatedTarget still inside).
                mouseover: (e) => this.handleHover(e as MouseEvent, "hover"),
                mouseout:  (e) => this.handleHover(e as MouseEvent, "leave"),
                dragover:  (e) => this.handleDragOver(e as DragEvent),
                dragleave: (e) => this.handleDragLeave(e as DragEvent),
                drop:      (e) => this.handleDrop(e as DragEvent),
            };
        }
        const h = this.boundHandlers;
        target.addEventListener("click",     h.click);
        target.addEventListener("submit",    h.submit);
        target.addEventListener("keydown",   h.keydown);
        target.addEventListener("change",    h.change);
        target.addEventListener("dblclick",  h.dblclick);
        target.addEventListener("input",     h.input);
        target.addEventListener("mouseover", h.mouseover);
        target.addEventListener("mouseout",  h.mouseout);
        target.addEventListener("dragover",  h.dragover);
        target.addEventListener("dragleave", h.dragleave);
        target.addEventListener("drop",      h.drop);
    }

    private removeListenersFrom(target: HTMLElement): void {
        if (!this.boundHandlers) return;
        const h = this.boundHandlers;
        target.removeEventListener("click",     h.click);
        target.removeEventListener("submit",    h.submit);
        target.removeEventListener("keydown",   h.keydown);
        target.removeEventListener("change",    h.change);
        target.removeEventListener("dblclick",  h.dblclick);
        target.removeEventListener("input",     h.input);
        target.removeEventListener("mouseover", h.mouseover);
        target.removeEventListener("mouseout",  h.mouseout);
        target.removeEventListener("dragover",  h.dragover);
        target.removeEventListener("dragleave", h.dragleave);
        target.removeEventListener("drop",      h.drop);
    }

    /**
     * Returns true when {@code el} is part of the SPA-controlled subtree —
     * either inside the renderer's root or inside a currently-open dialog
     * overlay. With listeners now bound directly to those subtrees,
     * inScope() is belt-and-braces: it filters out stray events (e.g. those
     * synthesised from Shadow DOM or composed-path quirks) but in practice
     * nothing outside scope will reach our handlers anyway.
     */
    private inScope(el: Element | null): boolean {
        if (!el) return false;
        if (this.root.contains(el)) return true;
        const dialog = this.dialogListenerHost;
        return !!dialog && dialog.contains(el);
    }

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
    private handleChange(e: Event): void {
        const target = e.target as HTMLElement | null;
        if (!target) return;
        // File inputs (a FILE field or a UiUpload's hidden input) upload their
        // selected files instead of following the ordinary change path.
        if (target instanceof HTMLInputElement && target.type === "file") {
            this.handleFileSelection(target);
            return;
        }
        // A node-level change (UiNode.events) fires first — it sits on a
        // wrapper, so a field's own data-change-trigger below still wins for
        // the input itself.
        if (this.handleNodeEvent(e, "change")) return;
        // Field-level onChange trigger takes precedence over submitOnChange:
        // the control carries a data-change-trigger (a separate attribute from
        // the click-owned data-trigger, so the control still toggles/commits
        // natively on click). We dispatch it directly — folding in the
        // surrounding form's values as payload — instead of submitting the
        // whole form. Lets one field drive UI logic on its own.
        const changeRaw = target.dataset?.changeTrigger;
        if (changeRaw) {
            let trigger: UiTrigger | null = null;
            try { trigger = JSON.parse(changeRaw) as UiTrigger; }
            catch (err) { console.error("SuiEventBus: bad data-change-trigger JSON", err, changeRaw); }
            if (trigger) {
                this.inferImplicitPayload(trigger, target);
                void this.dispatch(trigger, target);
                return;
            }
        }
        if ((target as HTMLElement).dataset?.submitOnChange !== "true") return;
        const form = target.closest<HTMLFormElement>("form");
        if (form) form.requestSubmit();
    }

    // ── File upload (UiUpload drop zone + FILE field) ─────────────────────

    /**
     * Dispatches the upload trigger for a file {@code <input>} that just
     * changed. The trigger comes from the surrounding {@code [data-sui-upload]}
     * zone ({@code data-upload-trigger}) or, for a standalone FILE field, the
     * input's own {@code data-change-trigger}. The zone is passed as the source
     * element so the {@code UPLOAD} behaviour can read {@code data-sui-upload-name}.
     */
    private handleFileSelection(input: HTMLInputElement): void {
        const files = Array.from(input.files ?? []);
        if (files.length === 0) return;
        const zone = input.closest<HTMLElement>("[data-sui-upload]");
        const raw = zone?.dataset.uploadTrigger ?? input.dataset.changeTrigger;
        const trigger = this.parseTriggerJson(raw);
        if (!trigger) return;
        void this.dispatch(trigger, zone ?? input, files);
        // Let the same file be re-selected later (change won't fire otherwise).
        input.value = "";
    }

    /** The {@code [data-sui-upload]} zone an event landed in, or null. */
    private uploadZoneOf(e: Event): HTMLElement | null {
        const t = e.target as HTMLElement | null;
        return t && typeof t.closest === "function"
            ? t.closest<HTMLElement>("[data-sui-upload]")
            : null;
    }

    private handleDragOver(e: DragEvent): void {
        const zone = this.uploadZoneOf(e);
        if (!zone || !this.inScope(zone)) return;
        e.preventDefault(); // required so the following "drop" fires
        if (e.dataTransfer) e.dataTransfer.dropEffect = "copy";
        zone.classList.add("sui-upload--dragover");
    }

    private handleDragLeave(e: DragEvent): void {
        const zone = this.uploadZoneOf(e);
        // Only clear when the pointer actually left the zone (not a child).
        if (zone && !zone.contains(e.relatedTarget as Node | null)) {
            zone.classList.remove("sui-upload--dragover");
        }
    }

    private handleDrop(e: DragEvent): void {
        const zone = this.uploadZoneOf(e);
        if (!zone || !this.inScope(zone)) return;
        e.preventDefault();
        zone.classList.remove("sui-upload--dragover");
        const files = Array.from(e.dataTransfer?.files ?? []);
        if (files.length === 0) return;
        const trigger = this.parseTriggerJson(zone.dataset.uploadTrigger);
        if (!trigger) return;
        void this.dispatch(trigger, zone, files);
    }

    /** Parses a trigger from a raw JSON string, logging (not throwing) on error. */
    private parseTriggerJson(raw: string | undefined | null): UiTrigger | null {
        if (!raw) return null;
        try { return JSON.parse(raw) as UiTrigger; }
        catch (err) { console.error("SuiEventBus: bad upload-trigger JSON", err, raw); return null; }
    }

    private handleKeydown(e: KeyboardEvent): void {
        if (e.key !== "Enter" || e.shiftKey || e.isComposing) return;
        const target = e.target as HTMLElement | null;
        if (!(target instanceof HTMLTextAreaElement)) return;
        if (target.dataset.submitOnEnter !== "true") return;
        const form = target.closest("form");
        if (!form) return;
        e.preventDefault();
        // requestSubmit() (vs. submit()) fires the submit event, runs
        // constraint validation, and finds the form's default submitter —
        // exactly what we want so handleSubmit() picks it up.
        form.requestSubmit();
    }

    private async handleClick(e: MouseEvent): Promise<void> {
        const target = e.target as HTMLElement | null;
        if (!target) return;

        // Dialog close: button or anchor marked data-sui-dialog-close, or a
        // bare backdrop click. We swallow the SSR href navigation and remove
        // the overlay in place — the underlying page is still mounted, so
        // there's nothing to fetch. URL stays where it was (the dialog was
        // a layer on top, not a route).
        const closeEl = target.closest<HTMLElement>("[data-sui-dialog-close]");
        if (closeEl && closeEl.closest(".sui-dialog-host")) {
            e.preventDefault();
            this.closeDialogAround(closeEl);
            return;
        }

        // Menu hamburger: a purely client-side state cycle (expanded → rail →
        // hidden), like tab-switching. No server round-trip; the choice is
        // persisted to localStorage by applyMenuState. Handled before the
        // generic [data-trigger]/[data-action] paths so the toggle never
        // dispatches a fetch.
        const menuToggle = target.closest<HTMLElement>("[data-menu-toggle]");
        if (menuToggle && this.inScope(menuToggle)) {
            e.preventDefault();
            const menu = document.getElementById(menuToggle.dataset.menuToggle!)
                ?? menuToggle.closest<HTMLElement>(".sui-menu");
            if (menu) applyMenuState(menu, nextMenuState(menu));
            return;
        }

        // Overlay backdrop: close the drawer (→ hidden) without cycling.
        const menuClose = target.closest<HTMLElement>("[data-menu-close]");
        if (menuClose && this.inScope(menuClose)) {
            e.preventDefault();
            const menu = document.getElementById(menuClose.dataset.menuClose!);
            if (menu) applyMenuState(menu, "hidden");
            return;
        }

        const tab = target.closest<HTMLElement>(".sui-tab");
        if (tab && this.inScope(tab)) {
            // SSR-style navigation tab: rendered as <a class="sui-tab"
            // href="…" data-href="…">. The href makes it work without JS;
            // here we intercept so the SPA can route through navigate()
            // instead of the browser doing a full page load. The plain
            // switchTab() panel-toggle path is only correct for the
            // SPA-only client-side tab variant (no data-href / no href).
            if (tab.dataset.href) {
                e.preventDefault();
                await this.navigate(tab.dataset.href);
                return;
            }
            if (tab instanceof HTMLAnchorElement && tab.getAttribute("href")) {
                e.preventDefault();
                await this.navigate(tab.getAttribute("href")!);
                return;
            }
            // selectOnClick:false hands the decision to the application: the
            // trigger still fires, but the panel stays where it is, so a
            // handler can gate the switch behind a confirmation, a permission
            // check or an unsaved-changes prompt — and then select a tab by
            // patching the section's initialSection.
            if (!tab.hasAttribute("data-sui-no-select")) this.switchTab(tab);
            // A section-entry may also carry an onClick trigger: fire it
            // alongside the panel switch (e.g. lazy-load the panel's content).
            const tabTrigger = this.parseTrigger(tab);
            if (tabTrigger) {
                this.inferImplicitPayload(tabTrigger, tab);
                await this.dispatch(tabTrigger, tab);
            }
            return;
        }

        const action = target.closest<HTMLElement>("[data-action]");
        if (action && this.inScope(action)) {
            // If this action sits inside a reload-on-submit form, let the
            // browser's native submit run instead of dispatching via fetch.
            // Without this the click would be swallowed and the native
            // navigation (the one that swaps the <head>) would never happen.
            const reloadForm = action.closest<HTMLFormElement>("form[data-sui-reload='true']");
            if (reloadForm) return;
            e.preventDefault();
            if (action.dataset.confirm && !window.confirm(action.dataset.confirm)) return;
            const trigger = this.parseTrigger(action);
            if (trigger) {
                this.inferImplicitPayload(trigger, action);
                await this.dispatch(trigger, action);
            }
            return;
        }

        // A sortable table header with no data-trigger means the table has no
        // sortTrigger: nobody is going to re-sort this for us, so the browser
        // reorders the rows it already has. Headers that DO carry a trigger
        // fall through to the generic dispatch path below.
        const sortBtn = target.closest<HTMLElement>("[data-sui-sort]:not([data-trigger])");
        if (sortBtn && this.inScope(sortBtn)) {
            e.preventDefault();
            this.sortTableClientSide(sortBtn);
            return;
        }

        const link = target.closest<HTMLElement>("[data-trigger], [data-href]");
        if (link && this.inScope(link)) {
            e.preventDefault();
            // A menu item (or any triggerable) may carry data-confirm, just like
            // an action button — prompt before dispatching.
            if (link.dataset.confirm && !window.confirm(link.dataset.confirm)) return;
            const trigger = this.parseTrigger(link);
            if (trigger) {
                await this.dispatch(trigger, link);
            } else if (link.dataset.href) {
                await this.navigate(link.dataset.href);
            }
            return;
        }

        // Last: a node-level click (UiNode.events), which is how a plain
        // container becomes clickable. It runs only when nothing more specific
        // did — the innermost element wins, the container is the fallback. So a
        // button or link inside a clickable row keeps its own behaviour.
        if (this.handleNodeEvent(e, "click")) e.preventDefault();
    }

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
    private sortTableClientSide(header: HTMLElement): void {
        const wrapper = header.closest<HTMLElement>('[data-sui="table"][data-node]');
        if (!wrapper) return;
        let model: {
            rows?: Array<{ data?: Record<string, unknown>; id?: string }>;
            sortColumn?: string;
            sortDirection?: string;
        };
        try { model = JSON.parse(wrapper.getAttribute("data-node")!); }
        catch (err) {
            console.warn("SuiEventBus: cannot sort, bad data-node JSON", err);
            return;
        }
        const key = header.dataset.suiSort ?? "";
        // The renderer already computed which direction this click means.
        const dir = header.dataset.suiSortDir === "DESC" ? "DESC" : "ASC";
        const factor = dir === "DESC" ? -1 : 1;

        const value = (row: { data?: Record<string, unknown>; id?: string }): unknown =>
            key === "id" ? (row.id ?? row.data?.["id"]) : row.data?.[key];
        const blank = (v: unknown): boolean => v == null || v === "";

        (model.rows ?? []).sort((a, b) => {
            const av = value(a), bv = value(b);
            // Blanks last, regardless of direction.
            if (blank(av) && blank(bv)) return 0;
            if (blank(av)) return 1;
            if (blank(bv)) return -1;
            const an = Number(av), bn = Number(bv);
            if (Number.isFinite(an) && Number.isFinite(bn)) return (an - bn) * factor;
            return String(av).localeCompare(String(bv), undefined, { numeric: true, sensitivity: "base" }) * factor;
        });

        model.sortColumn = key;
        model.sortDirection = dir;
        // Replace the wrapper itself, not its contents: data-node and the
        // header's aria-sort live on elements that innerHTML would not touch.
        this.renderer.replaceElement(wrapper, model as never);
    }


    /**
     * Generic {@code data-sui-on-<event>} dispatch — the mechanism behind
     * {@code UiNode.events}, which every node type inherits.
     *
     * <p>Walks up from the event target to the nearest ancestor carrying the
     * attribute for this event, so a click on a label inside a stack still
     * reaches the stack. Returns true when a trigger was dispatched, which lets
     * the click path skip its own handling.
     */
    private handleNodeEvent(e: Event, name: string): boolean {
        const target = e.target as HTMLElement | null;
        if (!target?.closest) return false;
        const el = target.closest<HTMLElement>(`[data-sui-on-${name}]`);
        if (!el || !this.inScope(el)) return false;
        const raw = el.getAttribute(`data-sui-on-${name}`);
        if (!raw) return false;
        let trigger: UiTrigger;
        try { trigger = JSON.parse(raw); }
        catch (err) {
            console.error(`SuiEventBus: bad data-sui-on-${name} JSON`, err, raw);
            return false;
        }
        // A node-level confirm applies to any event, same as on an action.
        if (el.dataset.confirm && !window.confirm(el.dataset.confirm)) return true;
        void this.dispatch(trigger, el);
        return true;
    }

    /**
     * hover / leave. {@code mouseenter} and {@code mouseleave} don't bubble, so
     * they can't be delegated from the root; we listen to the bubbling
     * {@code mouseover} / {@code mouseout} instead and drop the events that
     * merely move between descendants of the same element — otherwise a hover
     * trigger would fire again for every child the pointer crosses.
     */
    private handleHover(e: MouseEvent, name: "hover" | "leave"): void {
        const target = e.target as HTMLElement | null;
        const el = target?.closest?.<HTMLElement>(`[data-sui-on-${name}]`);
        if (!el) return;
        const related = e.relatedTarget as Node | null;
        if (related && el.contains(related)) return;   // still inside: not a real enter/leave
        this.handleNodeEvent(e, name);
    }

    private async handleSubmit(e: SubmitEvent): Promise<void> {
        const form = e.target as HTMLElement | null;
        if (!(form instanceof HTMLFormElement)) return;
        if (form.dataset.sui !== "form") return;
        // Reload-on-submit forms opt out of the SPA fetch path. Let the
        // browser navigate natively so anything outside #sui-root (the
        // theme stylesheet, the SPA bootstrap script) also gets refreshed.
        if (form.dataset.suiReload === "true") return;
        e.preventDefault();
        const submitter = (e.submitter as HTMLElement | null)
            ?? form.querySelector<HTMLElement>("button[type=submit][data-action]")
            ?? form.querySelector<HTMLElement>("[data-action]");
        if (!submitter) return;
        if (submitter.dataset.confirm && !window.confirm(submitter.dataset.confirm)) return;
        const trigger = this.parseTrigger(submitter);
        if (!trigger) return;
        this.inferImplicitPayload(trigger, submitter);
        await this.dispatch(trigger, submitter);
    }

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
    private inferImplicitPayload(trigger: UiTrigger, sourceElement: HTMLElement): void {
        if (trigger.payload) return;
        const form = sourceElement.closest<HTMLFormElement>("form[data-sui='form']");
        if (form && form.id) {
            trigger.payload = form.id;
        }
    }

    private switchTab(tab: HTMLElement): void {
        const tabsBar = tab.closest<HTMLElement>(".sui-tabs");
        if (!tabsBar) return;
        const section = tabsBar.closest<HTMLElement>(".sui-section");
        const scope: HTMLElement = section ?? this.root;
        scope.querySelectorAll<HTMLElement>(":scope > .sui-panels > .sui-panel")
             .forEach(p => p.hidden = true);
        tabsBar.querySelectorAll<HTMLElement>(".sui-tab")
               .forEach(t => t.classList.remove("active"));
        tab.classList.add("active");
        const targetId = tab.dataset.target;
        if (targetId) {
            const panel = document.getElementById(targetId);
            if (panel) panel.hidden = false;
        }
    }

    private parseTrigger(el: HTMLElement): UiTrigger | null {
        const raw = el.dataset.trigger;
        if (!raw) return null;
        try {
            return JSON.parse(raw) as UiTrigger;
        } catch (err) {
            console.error("SuiEventBus: bad data-trigger JSON", err, raw);
            return null;
        }
    }

    /**
     * Collects editable field values from a {@code UiForm}-like node by id.
     *
     * <p>Walks every named {@code <input>}/{@code <select>}/{@code <textarea>}
     * inside the form and reads its value. The semantic type ({@code NUMBER},
     * {@code CURRENCY}, …) is taken from {@code data-sui-type} on the
     * control — both the SPA renderer and the SSR Handlebars template emit
     * it, so this one code path serves both modes.
     */
    private collectPayload(nodeId: string): Record<string, unknown> {
        const el = document.getElementById(nodeId);
        if (!el) return {};
        return harvestNamedControls(el);
    }

    // ── Built-in behaviours ───────────────────────────────────────────────

    private registerDefaultBehaviors(): void {
        this.registerBehavior("APPLY_RESPONSE", (ctx) => this.applyResponseBehavior(ctx));
        this.registerBehavior("STREAM",         (ctx) => this.streamBehavior(ctx));
        this.registerBehavior("DOWNLOAD",       (ctx) => this.downloadBehavior(ctx));
        this.registerBehavior("OPEN_IN_TAB",    (ctx) => this.openInTabBehavior(ctx));
        this.registerBehavior("INVOKE",         (ctx) => this.invokeBehavior(ctx));
        this.registerBehavior("PATCH",          (ctx) => this.inlinePatchBehavior(ctx));
        this.registerBehavior("UPLOAD",         (ctx) => this.uploadBehavior(ctx));
    }

    /**
     * Built-in {@code UPLOAD} behaviour: POSTs {@code ctx.files} to
     * {@code ctx.url} as {@code multipart/form-data} and applies the response
     * through the configured {@link ResponseHandler} — same as a normal
     * fetch, but with a file body. The multipart field name comes from the
     * source element's {@code data-sui-upload-name} (or its {@code name}),
     * falling back to {@code "files"}. Fired by a {@code UiUpload} drop zone
     * or a {@code FILE} field's change.
     */
    private async uploadBehavior(ctx: BehaviorContext): Promise<void> {
        const files = ctx.files ?? [];
        if (files.length === 0) return;
        const src = ctx.sourceElement;
        const name = src?.dataset?.suiUploadName
            || (src instanceof HTMLInputElement ? src.name : "")
            || "files";
        const form = new FormData();
        for (const file of files) form.append(name, file, file.name);
        // Don't set Content-Type — the browser adds the multipart boundary.
        const method = ctx.method === "GET" ? "POST" : ctx.method;
        const res = await this.safeFetch(ctx.url, { method, body: form }, ctx);
        if (!res) return; // network failure already reported
        if (await this.handleUnauthenticated(res, ctx)) return;
        if (await this.reportHttpError(res, ctx.url, ctx)) return;
        const ct = res.headers.get("content-type") ?? "";
        const body = ct.includes("application/json") ? await res.json() : null;
        await this.responseHandler(body, ctx.url, this);
    }

    /**
     * Built-in {@code PATCH} behaviour: applies the {@link UiPatch} carried
     * inline on the trigger ({@code trigger.patch}) — no server call, no JS
     * handler. The patch is baked into the trigger at render time, so this is
     * the leanest way to express static, known-ahead UI logic: a list row
     * that fills a detail panel, a button that opens a fixed dialog, a toggle
     * that reveals another field — all with zero round-trip.
     */
    private inlinePatchBehavior(ctx: BehaviorContext): void {
        const patch = ctx.trigger.patch;
        if (!patch) {
            console.warn("SuiEventBus: PATCH trigger has no inline patch", ctx.trigger);
            return;
        }
        this.applyPatch(patch);
    }

    /**
     * Built-in {@code INVOKE} behaviour: looks up the client handler named by
     * {@code trigger.handler} and runs it — no network. A returned
     * {@link UiPage} / {@link UiPatch} is applied through the configured
     * {@link ResponseHandler} (same path a fetched response takes), so a
     * handler can swap the page, open a dialog, or emit a partial patch. A
     * {@code void} return means the handler already applied its own changes.
     */
    private async invokeBehavior(ctx: BehaviorContext): Promise<void> {
        const name = ctx.trigger.handler;
        if (!name) {
            console.warn("SuiEventBus: INVOKE trigger has no handler name", ctx.trigger);
            return;
        }
        const handler = this.clientHandlers.get(name);
        if (!handler) {
            console.warn(`SuiEventBus: no client handler registered for "${name}"`);
            return;
        }
        const result = await handler(ctx);
        if (result == null) return; // handler applied its own changes
        await this.responseHandler(result, undefined, this);
    }

    private async applyResponseBehavior(ctx: BehaviorContext): Promise<void> {
        const init: RequestInit = { method: ctx.method };
        let url = ctx.url;
        if (ctx.payload != null) {
            // GET/HEAD can't carry a body — fold the payload into the query
            // string instead, matching native <form method="GET"> semantics.
            // For body-bearing verbs we send JSON; the server is expected to
            // accept it (or expose a sibling form-encoded endpoint).
            if (ctx.method === "GET" || ctx.method === "HEAD") {
                url = appendQuery(url, ctx.payload);
            } else {
                init.headers = { "Content-Type": "application/json" };
                init.body = JSON.stringify(ctx.payload);
            }
        }
        const res = await this.safeFetch(url, init, ctx);
        if (!res) return; // network failure already reported
        if (await this.handleUnauthenticated(res, ctx)) return;
        if (await this.reportHttpError(res, url, ctx)) return;
        const ct = res.headers.get("content-type") ?? "";
        const body = ct.includes("application/json") ? await res.json() : null;
        // Pass the resolved URL (with query) as the navigation fallback so
        // pushState lands on a reload-safe address bar value for GET forms.
        await this.responseHandler(body, url, this);
    }

    private async downloadBehavior(ctx: BehaviorContext): Promise<void> {
        const res = await this.safeFetch(ctx.url, { method: ctx.method }, ctx);
        if (!res) return; // network failure already reported
        if (await this.handleUnauthenticated(res, ctx)) return;
        if (await this.reportHttpError(res, ctx.url, ctx)) return;
        const disp = res.headers.get("content-disposition") ?? "";
        const m = disp.match(/filename\*?=(?:UTF-8'')?["']?([^"';]+)["']?/i);
        const fallback = ctx.url.split("/").pop() ?? "download";
        const filename = m?.[1] ?? fallback;
        const blob = await res.blob();
        const blobUrl = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = blobUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
    }

    private async openInTabBehavior(ctx: BehaviorContext): Promise<void> {
        const res = await this.safeFetch(ctx.url, { method: ctx.method }, ctx);
        if (!res) return; // network failure already reported
        if (await this.handleUnauthenticated(res, ctx)) return;
        if (await this.reportHttpError(res, ctx.url, ctx)) return;
        const blob = await res.blob();
        const blobUrl = URL.createObjectURL(blob);
        window.open(blobUrl, "_blank", "noopener");
        setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000);
    }

    private async streamBehavior(ctx: BehaviorContext): Promise<void> {
        const abortController = new AbortController();
        const init: RequestInit = {
            method: ctx.method === "GET" ? "POST" : ctx.method,
            headers: { "Accept": "text/event-stream" },
            signal: abortController.signal,
        };
        if (ctx.payload != null) {
            (init.headers as Record<string, string>)["Content-Type"] = "application/json";
            init.body = JSON.stringify(ctx.payload);
        }
        const res = await this.safeFetch(ctx.url, init, ctx);
        if (!res) return; // network failure already reported
        if (await this.handleUnauthenticated(res, ctx)) return;
        if (await this.reportHttpError(res, ctx.url, ctx)) return;
        if (!res.body) {
            console.warn("SuiEventBus: STREAM got no body", ctx.url);
            return;
        }
        // Register the stream so it survives DOM swaps. The channel id is
        // taken from a response header so the same id can be used by the
        // originating page to mark its re-mount target. Falls back to a
        // random id when the server doesn't expose one (e.g. third-party
        // streams not under our control).
        const channelId = res.headers.get("Sui-Stream-Channel")
            ?? `sse-${crypto.randomUUID()}`;
        const label = res.headers.get("Sui-Stream-Label") ?? "Agent";
        // The "return-to-stream-source" href the status toast links to.
        // Prefer an explicit header from the server; fall back to the
        // address bar (the user was on the page that started the stream).
        const returnHref = res.headers.get("Sui-Stream-Return-Href")
            ?? window.location.pathname + window.location.search;
        const handle: StreamHandle = {
            channelId,
            returnHref,
            label,
            state: "running",
            bufferedEvents: [],
            pageAttached: this.findStreamTarget(channelId) != null,
            lastSeq: 0,
            abort: () => abortController.abort(),
        };
        this.streams.set(channelId, handle);
        this.updateStatusToast();

        // Fire-and-forget the reader. We deliberately do NOT await it from
        // here — the dispatch caller (showLoading wrapper, click handler)
        // should return as soon as the request is in flight, so the user
        // can navigate away without blocking. The reader keeps running
        // until the server closes it or {@code handle.abort()} is called.
        void this.consumeSse(res.body, ctx, handle).catch(err => {
            console.warn(`SuiEventBus: stream ${channelId} aborted`, err);
            handle.state = "errored";
            this.updateStatusToast();
        });
    }

    private async consumeSse(body: ReadableStream<Uint8Array>,
                             ctx: BehaviorContext,
                             handle: StreamHandle): Promise<void> {
        const reader = body.getReader();
        const decoder = new TextDecoder();
        let buf = "";
        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                buf += decoder.decode(value, { stream: true });
                const events = buf.split(/\r?\n\r?\n/);
                buf = events.pop() ?? "";
                for (const block of events) {
                    if (!block.trim()) continue;
                    let eventName = "message";
                    let eventId: number | null = null;
                    const dataLines: string[] = [];
                    for (const raw of block.split(/\r?\n/)) {
                        if (raw.startsWith("event:")) eventName = raw.slice(6).trim();
                        else if (raw.startsWith("data:")) dataLines.push(raw.slice(5));
                        else if (raw.startsWith("id:")) {
                            const n = Number.parseInt(raw.slice(3).trim(), 10);
                            if (Number.isFinite(n)) eventId = n;
                        }
                    }
                    // Server publishes the channel's monotonic seq as the SSE
                    // id; track it so a reconnect can pass lastSeq and skip
                    // events we already saw.
                    if (eventId != null && eventId > handle.lastSeq) handle.lastSeq = eventId;
                    const data = dataLines.join("\n");
                    await this.dispatchStreamEvent(eventName, data, ctx, handle);
                }
            }
        } finally {
            // The reader closed (server done, network drop, or explicit abort).
            // Mark the stream completed if it wasn't already errored; status
            // toast picks this up on its next update.
            if (handle.state === "running") handle.state = "completed";
            this.updateStatusToast();
        }
    }

    /**
     * Routes one SSE event to its registered handler. If the owning page is
     * detached (the user navigated away mid-stream), the event is buffered
     * on the handle and will be replayed when the page is re-mounted. If
     * the handler runs but returns {@code "deferred"} the event is also
     * buffered (the handler determined it couldn't apply right now, e.g.
     * the target id wasn't in the live DOM despite the page being mounted).
     */
    private async dispatchStreamEvent(eventName: string, data: string,
                                      ctx: BehaviorContext,
                                      handle: StreamHandle): Promise<void> {
        // Re-check attachment on every event — cheap (one DOM lookup) and
        // catches the in-between-applyPage window where the user just
        // landed but reconcileStreamAttachments hasn't been called yet.
        handle.pageAttached = this.findStreamTarget(handle.channelId) != null;
        if (!handle.pageAttached) {
            handle.bufferedEvents.push({ event: eventName, data });
            return;
        }
        const handler = this.streamEventHandlers.get(eventName);
        if (!handler) return;
        try {
            const result = await handler(data, ctx);
            if (result === "deferred") {
                handle.bufferedEvents.push({ event: eventName, data });
            }
        } catch (err) {
            console.error(`SuiEventBus: stream handler "${eventName}" failed`, err);
        }
    }
}

/**
 * Default response handler. Applies one of three shapes:
 * <ul>
 *   <li>a {@link UiPage} — an object with {@code node} / {@code navigate}
 *       (full swap + pushState);</li>
 *   <li>a {@link UiPatch} — an object with a {@code patches} array
 *       (in-place);</li>
 *   <li>an <b>array</b> of {@code UiPatch} / {@code UiPage} — applied in
 *       order. This is how a server returns <em>multiple patches</em> from a
 *       single {@code APPLY_RESPONSE}: several envelopes, each free to carry
 *       its own toasts / dialog. (A single {@code UiPatch} is an object whose
 *       {@code patches} property is the array; the multi-patch case is a
 *       top-level array of such objects — so the two never collide.)</li>
 * </ul>
 * Unknown shapes log and no-op.
 */
const defaultResponseHandler: ResponseHandler = (body, fallbackHref, bus) => {
    if (body == null) return;
    if (Array.isArray(body)) {
        for (const item of body) applyResponseItem(item, fallbackHref, bus);
        return;
    }
    applyResponseItem(body, fallbackHref, bus);
};

/** Applies a single UiPage / UiPatch, branching on its shape. */
function applyResponseItem(body: unknown, fallbackHref: string | undefined, bus: SuiEventBus): void {
    if (body == null) return;
    const b = body as { patches?: unknown; node?: unknown; navigate?: unknown };
    if (Array.isArray(b.patches)) {
        bus.applyPatch(body as UiPatch);
    } else if (b.node || b.navigate) {
        bus.applyPage(body as UiPage, fallbackHref);
    } else {
        console.warn("SuiEventBus: response body matches neither UiPage nor UiPatch", body);
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

function readInputValue(
    input: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement,
    type?: string,
): unknown {
    if (input instanceof HTMLInputElement && input.type === "checkbox") {
        return input.checked;
    }
    if (input instanceof HTMLSelectElement && input.multiple) {
        return Array.from(input.selectedOptions, o => o.value);
    }
    const raw = (input as HTMLInputElement).value;
    if (raw === "") return null;
    switch (type) {
        case "NUMBER":
        case "CURRENCY":
        case "PERCENT":
            return Number(raw);
        default:
            return raw;
    }
}

/**
 * Minimal CSS attribute-selector escape. Native {@code CSS.escape} would do
 * a fuller job but the field ids we receive from the server are JSON-safe
 * identifiers — backslash-escaping double quotes is enough in practice and
 * keeps this file dependency-free.
 */
function cssEscape(value: string): string {
    return value.replace(/(["\\])/g, "\\$1");
}

/**
 * Folds a flat key/value payload into the given URL's query string. Used by
 * GET-bound actions (search forms, paging links) so the resolved URL is the
 * one the user sees in the address bar — reload-safe and bookmarkable.
 *
 * <ul>
 *   <li>{@code null}/{@code undefined} values are dropped (matches native
 *       form behaviour for empty optional fields).</li>
 *   <li>Arrays expand to repeated {@code key=value} pairs.</li>
 *   <li>Objects are JSON-stringified — uncommon for GET but keeps callers
 *       from silently losing data.</li>
 * </ul>
 */
function appendQuery(url: string, payload: Record<string, unknown>): string {
    const params = new URLSearchParams();
    for (const [k, v] of Object.entries(payload)) {
        if (v == null) continue;
        if (Array.isArray(v)) {
            for (const item of v) {
                if (item != null) params.append(k, String(item));
            }
        } else if (typeof v === "object") {
            params.append(k, JSON.stringify(v));
        } else {
            params.append(k, String(v));
        }
    }
    const qs = params.toString();
    if (!qs) return url;
    const sep = url.includes("?") ? "&" : "?";
    return url + sep + qs;
}

/**
 * Walks every named editable control inside the given root and returns
 * {@code name → value}. Mirrors native HTML-form semantics with one twist:
 * the semantic field type comes from {@code data-sui-type} on the control
 * (e.g. {@code NUMBER}, {@code CURRENCY}, {@code BOOLEAN}), so {@code <input
 * type="number">}-shaped controls keep the distinction between
 * {@code NUMBER} and {@code CURRENCY} even though they share an HTML type.
 *
 * <p>Skips:
 * <ul>
 *   <li>controls without {@code name}</li>
 *   <li>{@code name="_method"} — Spring's hidden-method override is for
 *       form-encoded bodies; the JSON path uses the real HTTP verb.</li>
 *   <li>unchecked radio buttons (only the selected one contributes)</li>
 * </ul>
 */
function harvestNamedControls(root: HTMLElement): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    const controls = Array.from(
        root.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(
            "input[name], select[name], textarea[name]"));
    for (const ctrl of controls) {
        const name = ctrl.name;
        if (!name || name === "_method") continue;
        if (ctrl instanceof HTMLInputElement && ctrl.type === "radio" && !ctrl.checked) {
            continue;
        }
        out[name] = readInputValue(ctrl, ctrl.dataset.suiType);
    }
    return out;
}

// ── Toasts ──────────────────────────────────────────────────────────────────

/**
 * Appends one or more toasts to the body-level toast container. Survives
 * page swaps because the container lives outside {@code #sui-root} (the SSR
 * converter renders it as a sibling). Auto-dismisses each toast after its
 * {@code durationMs}; a non-positive duration is treated as sticky and only
 * goes away via the close button.
 */
function showToasts(toasts: UiToast[]): void {
    if (!toasts || toasts.length === 0) return;
    const container = ensureToastContainer();
    for (const t of toasts) container.appendChild(renderToastElement(t));
}

function ensureToastContainer(): HTMLElement {
    let el = document.getElementById("sui-toast-container");
    if (el) return el;
    el = document.createElement("div");
    el.id = "sui-toast-container";
    el.className = "sui-toast-container";
    document.body.appendChild(el);
    return el;
}

/** One status glyph per level — a coloured icon reads as a status far better
 *  than a coloured edge, and matches how the rest of the UI signals state. */
const TOAST_ICON: Record<string, string> = {
    info:    "info",
    success: "success",
    warn:    "warning",
    error:   "error",
};

function renderToastElement(t: UiToast): HTMLElement {
    const level = (t.level ?? "INFO").toLowerCase();
    const el = document.createElement("div");
    el.className = `sui-toast sui-toast--${level}`;
    el.setAttribute("role", "status");

    const iconName = TOAST_ICON[level] ?? "info";
    const iconHtml = renderIcon(iconName);
    if (iconHtml) {
        const icon = document.createElement("span");
        icon.className = "sui-toast-icon";
        icon.setAttribute("aria-hidden", "true");
        icon.innerHTML = iconHtml;
        el.appendChild(icon);
    }

    // Title + message share a column so the icon aligns with the first line.
    const body = document.createElement("div");
    body.className = "sui-toast-body";
    if (t.title) {
        const title = document.createElement("div");
        title.className = "sui-toast-title";
        title.textContent = t.title;
        body.appendChild(title);
    }
    const msg = document.createElement("div");
    msg.className = "sui-toast-message";
    msg.textContent = t.message ?? "";
    body.appendChild(msg);
    el.appendChild(body);
    const close = document.createElement("button");
    close.type = "button";
    close.className = "sui-toast-close";
    close.setAttribute("aria-label", "Close");
    close.textContent = "×";
    close.addEventListener("click", () => el.remove());
    el.appendChild(close);

    const duration = t.durationMs;
    if (duration && duration > 0) {
        setTimeout(() => {
            el.classList.add("sui-toast--leaving");
            // CSS animation duration (200ms) before removing from DOM —
            // matches the keyframes in sui.css.
            setTimeout(() => el.remove(), 200);
        }, duration);
    }
    return el;
}
