package ai.mindconnect.ui.stateful;

import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiToast;
import ai.mindconnect.ui.stateful.store.VersionConflictException;
import ai.mindconnect.ui.stateful.store.ViewInstance;
import ai.mindconnect.ui.stateful.store.ViewStateStore;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Drives view instances: opens them, shows them again, applies events. Has
 * no idea about HTTP — the Spring controller translates requests into these
 * calls and the results into responses; a plain-Java host could do the same.
 *
 * <p>One event, start to finish: load the instance → check the owner →
 * instantiate the view class → deserialise the state → render once so the
 * listeners are bound → run the handler → render again → diff against the
 * page the client has → store state, page and a bumped version → answer with
 * the patch (or the page, when the diff says so or the handler navigated).
 *
 * <p>Events on one instance are serialised within this JVM by a lock; across
 * JVMs the store's version check catches the race, and the event is applied
 * once more against the fresh state before giving up.
 */
public final class SuiViewEngine {

    /** Tunables; see the Spring properties for their meaning and defaults. */
    public record Settings(String eventBasePath, String instanceParam, Duration idleTimeout,
                           int maxInstancesPerOwner, double fullPageThreshold) {

        public static Settings defaults() {
            return new Settings("/sui/s", "_v", Duration.ofMinutes(30), 50, 0.6);
        }
    }

    /** What the client gets back. */
    public sealed interface Response permits PageResponse, PatchResponse, RedirectResponse {
    }

    /** A whole page: a fresh instance, a reload, a diff too large, or a navigation to another view. */
    public record PageResponse(UiPage page, long version) implements Response {
    }

    /** The difference since the client's last page. */
    public record PatchResponse(UiPatch patch, long version) implements Response {
    }

    /** The handler navigated somewhere no view answers to; the web layer redirects. */
    public record RedirectResponse(String url) implements Response {
    }

    /** How the web layer wants toasts delivered on an event. */
    public enum ToastDelivery {
        /** In the response (a patch or page carrying them). */
        INLINE,
        /** Kept in the store and shown by the page the redirect lands on (the no-JS path). */
        DEFERRED
    }

    private static final int LOCKS = 64;

    private final ObjectMapper pageMapper;
    private final ObjectMapper stateMapper;
    private final SuiViewRegistry registry;
    private final ViewFactory factory;
    private final ViewStateStore store;
    private final Settings settings;
    private final TreeDiff diff;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final ReentrantLock[] locks = new ReentrantLock[LOCKS];

    public SuiViewEngine(ObjectMapper mapper, SuiViewRegistry registry, ViewFactory factory,
                         ViewStateStore store, Settings settings) {
        this(mapper, registry, factory, store, settings, Clock.systemUTC());
    }

    public SuiViewEngine(ObjectMapper mapper, SuiViewRegistry registry, ViewFactory factory,
                         ViewStateStore store, Settings settings, Clock clock) {
        this.pageMapper = mapper;
        // State classes are the application's plain POJOs: package-private
        // fields are the natural way to write them, and a state class that
        // gained a field since an instance was stored should still load.
        this.stateMapper = mapper.copy()
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.registry = registry;
        this.factory = factory;
        this.store = store;
        this.settings = settings;
        this.diff = new TreeDiff(mapper, settings.fullPageThreshold());
        this.clock = clock;
        for (int i = 0; i < LOCKS; i++) locks[i] = new ReentrantLock();
    }

    public Settings settings() {
        return settings;
    }

    public SuiViewRegistry registry() {
        return registry;
    }

    // ── open / show ────────────────────────────────────────────────────────

    /**
     * Creates an instance of the view at {@code route} for {@code owner} and
     * renders it. The page's {@code navigate} is the instance's own address,
     * so the SPA lands on it and the SSR path can redirect there.
     */
    public PageResponse open(String route, RouteParams params, String owner) {
        Objects.requireNonNull(owner, "owner");
        Class<? extends SuiView<?>> viewClass = registry.byRoute(route)
                .orElseThrow(() -> new StatefulException.UnknownRoute(route));
        String id = newInstanceId();
        Instant now = clock.instant();

        @SuppressWarnings("unchecked")
        SuiView<Object> view = (SuiView<Object>) factory.create(viewClass);
        ViewContext ctx = context(id, route, owner);
        view.attach(ctx);
        Object state = view.initialState(params == null ? RouteParams.empty() : params);
        SuiView.Rendered rendered = view.doRender(state);
        JsonNode pageJson = pageJson(rendered, ctx);

        var instance = new ViewInstance();
        instance.setId(id);
        instance.setRoute(route);
        instance.setViewClass(viewClass.getName());
        instance.setOwner(owner);
        instance.setVersion(1);
        instance.setStateJson(writeState(state));
        instance.setPageJson(write(pageJson));
        instance.setCreatedAt(now);
        instance.setTouchedAt(now);
        store.insert(instance);
        if (settings.maxInstancesPerOwner() > 0) {
            store.trimOwner(owner, settings.maxInstancesPerOwner());
        }

        UiPage page = toPage(pageJson);
        view.drainIntents().toasts().forEach(page::toast);
        return new PageResponse(page, 1);
    }

    /**
     * Renders an existing instance again — a reload, the back button, the
     * redirect after a no-JS event. Re-renders rather than replaying the
     * cached page, because the data behind the view may have moved on.
     */
    public PageResponse show(String instanceId, String owner) {
        ReentrantLock lock = lockFor(instanceId);
        lock.lock();
        try {
            ViewInstance instance = loadOwned(instanceId, owner);
            Loaded loaded = instantiate(instance);
            SuiView.Rendered rendered = loaded.view.doRender(loaded.state);
            JsonNode pageJson = pageJson(rendered, loaded.ctx);

            List<UiToast> pending = readToasts(instance.getPendingToastsJson());
            long next = instance.getVersion() + 1;
            instance.setVersion(next);
            instance.setPageJson(write(pageJson));
            instance.setPendingToastsJson(null);
            instance.setTouchedAt(clock.instant());
            store.update(instance, next - 1);

            UiPage page = toPage(pageJson);
            pending.forEach(page::toast);
            loaded.view.drainIntents().toasts().forEach(page::toast);
            return new PageResponse(page, next);
        } finally {
            lock.unlock();
        }
    }

    // ── events ─────────────────────────────────────────────────────────────

    /**
     * Applies one event and answers with what the client needs to catch up.
     *
     * @param rowId       the row a row action was fired on, else {@code null}
     * @param payload     the harvested form values
     * @param attachments files of an upload event
     * @param owner       who is asking; must match the instance's owner
     * @param toasts      whether toasts ride in the response or wait for the redirect
     */
    public Response event(String instanceId, String nodeId, String event, String rowId,
                          Map<String, Object> payload, List<UiEvent.Attachment> attachments,
                          String owner, ToastDelivery toasts) {
        ReentrantLock lock = lockFor(instanceId);
        lock.lock();
        try {
            try {
                return applyEvent(instanceId, nodeId, event, rowId, payload, attachments, owner, toasts);
            } catch (VersionConflictException first) {
                // Another node applied something in between: once more against
                // what is stored now. Handlers are HTTP handlers, so they are
                // expected to cope with being run on a state they did not see.
                try {
                    return applyEvent(instanceId, nodeId, event, rowId, payload, attachments, owner, toasts);
                } catch (VersionConflictException second) {
                    throw new StatefulException.Stale(instanceId);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private Response applyEvent(String instanceId, String nodeId, String event, String rowId,
                                Map<String, Object> payload, List<UiEvent.Attachment> attachments,
                                String owner, ToastDelivery toastDelivery) {
        ViewInstance instance = loadOwned(instanceId, owner);
        Loaded loaded = instantiate(instance);
        SuiView<Object> view = loaded.view;

        // Render #1 binds the listeners; the tree itself is thrown away.
        SuiView.Rendered before = view.doRender(loaded.state);
        UiEventHandler handler = before.handler(nodeId, event);
        if (handler == null) {
            throw new StatefulException.UnboundEvent(nodeId, event);
        }
        var uiEvent = new UiEvent(nodeId, event, rowId, payload, attachments, loaded.ctx.principal());
        try {
            handler.handle(uiEvent);
        } catch (Exception e) {
            view.drainIntents();
            throw new StatefulException.HandlerFailed(nodeId, event, e);
        }
        SuiView.Intents intents = view.drainIntents();

        // Render #2 is what the client should see now.
        SuiView.Rendered after = view.doRender(loaded.state);
        JsonNode newPage = pageJson(after, loaded.ctx);
        JsonNode oldPage = read(instance.getPageJson());

        long expected = instance.getVersion();
        long next = expected + 1;
        instance.setVersion(next);
        instance.setStateJson(writeState(loaded.state));
        instance.setPageJson(write(newPage));
        instance.setTouchedAt(clock.instant());
        if (toastDelivery == ToastDelivery.DEFERRED && !intents.toasts().isEmpty()) {
            instance.setPendingToastsJson(write(pageMapper.valueToTree(intents.toasts())));
        }
        store.update(instance, expected);

        if (intents.navigateTo() != null) {
            return navigation(intents.navigateTo(), owner, intents.toasts(), toastDelivery);
        }

        TreeDiff.Result result = diff.diff(oldPage, newPage);
        if (result.fullPage()) {
            UiPage page = toPage(newPage);
            if (toastDelivery == ToastDelivery.INLINE) intents.toasts().forEach(page::toast);
            return new PageResponse(page, next);
        }
        UiPatch patch = result.patch();
        if (toastDelivery == ToastDelivery.INLINE) intents.toasts().forEach(patch::toast);
        return new PatchResponse(patch, next);
    }

    /**
     * A handler asked to leave. A registered route is opened right here, so
     * the SPA receives the target page in the same response; anything else is
     * a redirect the web layer performs.
     */
    private Response navigation(String url, String owner, List<UiToast> toasts, ToastDelivery delivery) {
        URI uri = URI.create(url);
        String path = uri.getPath();
        if (uri.getHost() == null && path != null && registry.byRoute(path).isPresent()) {
            RouteParams params = QueryStrings.parse(uri.getRawQuery());
            PageResponse opened = open(path, params, owner);
            if (delivery == ToastDelivery.INLINE) {
                toasts.forEach(opened.page()::toast);
            } else if (!toasts.isEmpty()) {
                // Park them on the new instance, whose page the redirect shows.
                store.load(instanceIdOf(opened.page().getNavigate())).ifPresent(target -> {
                    target.setPendingToastsJson(write(pageMapper.valueToTree(toasts)));
                    store.update(target, target.getVersion());
                });
            }
            return opened;
        }
        return new RedirectResponse(url);
    }

    // ── housekeeping ───────────────────────────────────────────────────────

    /** Drops instances idle for longer than the configured timeout; returns how many. */
    public int reap() {
        return store.expireIdle(clock.instant().minus(settings.idleTimeout()));
    }

    /** Forgets an instance — e.g. on logout. */
    public void discard(String instanceId) {
        store.delete(instanceId);
    }

    /** The address of an instance, as {@link ViewContext#instanceUrl()} spells it. */
    public String instanceUrl(String route, String instanceId) {
        return route + "?" + settings.instanceParam() + "=" + instanceId;
    }

    /** The stored instance's own address, or {@code null} when it is gone. */
    public String addressOf(String instanceId) {
        return store.load(instanceId).map(i -> instanceUrl(i.getRoute(), i.getId())).orElse(null);
    }

    /** The instance id in an address produced by {@link #instanceUrl}, or {@code null}. */
    public String instanceIdOf(String url) {
        if (url == null) return null;
        RouteParams params = QueryStrings.parse(URI.create(url).getRawQuery());
        return params.query(settings.instanceParam());
    }

    // ── internals ──────────────────────────────────────────────────────────

    private record Loaded(SuiView<Object> view, Object state, ViewContext ctx) {
    }

    private ViewInstance loadOwned(String instanceId, String owner) {
        ViewInstance instance = store.load(instanceId)
                .orElseThrow(() -> new StatefulException.UnknownInstance(instanceId, null));
        if (!Objects.equals(instance.getOwner(), owner)) {
            // Somebody else's: indistinguishable from unknown, on purpose.
            throw new StatefulException.UnknownInstance(instanceId, null);
        }
        if (instance.getTouchedAt() != null
                && instance.getTouchedAt().plus(settings.idleTimeout()).isBefore(clock.instant())) {
            store.delete(instanceId);
            throw new StatefulException.UnknownInstance(instanceId, instance.getRoute());
        }
        return instance;
    }

    private Loaded instantiate(ViewInstance instance) {
        Class<? extends SuiView<?>> viewClass = registry.byName(instance.getViewClass())
                .orElseThrow(() -> new StatefulException.UnknownInstance(instance.getId(), instance.getRoute()));
        @SuppressWarnings("unchecked")
        SuiView<Object> view = (SuiView<Object>) factory.create(viewClass);
        ViewContext ctx = context(instance.getId(), instance.getRoute(), instance.getOwner());
        view.attach(ctx);
        Object state = readState(instance.getStateJson(), viewClass);
        return new Loaded(view, state, ctx);
    }

    private ViewContext context(String id, String route, String owner) {
        String principal = owner != null && owner.startsWith(Owners.SESSION_PREFIX) ? null : owner;
        return new ViewContext(id, route, settings.eventBasePath(), settings.instanceParam(), principal);
    }

    /** The page as JSON, with per-call ids made stable — the one shape everything else derives from. */
    private JsonNode pageJson(SuiView.Rendered rendered, ViewContext ctx) {
        UiPage page = UiPage.of(ctx.instanceUrl(), rendered.node());
        page.setTitle(rendered.title());
        if (!rendered.dialogs().isEmpty()) {
            page.setDialogs(new ArrayList<>(rendered.dialogs()));
        }
        return IdStabilizer.stabilize(pageMapper.valueToTree(page));
    }

    private UiPage toPage(JsonNode json) {
        try {
            return pageMapper.treeToValue(json, UiPage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("page JSON does not deserialise as UiPage", e);
        }
    }

    private String newInstanceId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ReentrantLock lockFor(String instanceId) {
        return locks[Math.floorMod(instanceId.hashCode(), LOCKS)];
    }

    private String writeState(Object state) {
        try {
            return stateMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("view state is not JSON-serialisable: " + state.getClass().getName(), e);
        }
    }

    private Object readState(String json, Class<?> viewClass) {
        JavaType type = stateMapper.constructType(StateTypes.resolve(viewClass));
        try {
            return stateMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored state does not load as " + type, e);
        }
    }

    private String write(JsonNode json) {
        try {
            return pageMapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode read(String json) {
        if (json == null) return null;
        try {
            return pageMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null; // a broken cache only costs a full page
        }
    }

    private List<UiToast> readToasts(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return pageMapper.readValue(json, pageMapper.getTypeFactory()
                    .constructCollectionType(List.class, UiToast.class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
