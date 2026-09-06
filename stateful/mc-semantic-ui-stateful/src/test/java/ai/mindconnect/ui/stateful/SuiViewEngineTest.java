package ai.mindconnect.ui.stateful;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiColumn;
import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiToast;
import ai.mindconnect.ui.stateful.store.InMemoryViewStateStore;
import ai.mindconnect.ui.stateful.store.VersionConflictException;
import ai.mindconnect.ui.stateful.store.ViewInstance;
import ai.mindconnect.ui.stateful.store.ViewStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuiViewEngineTest {

    // ── views under test ───────────────────────────────────────────────────

    @SuiRoute("/counter")
    public static class CounterView extends SuiView<CounterView.State> {
        public static class State {
            int count;          // package-private on purpose: the natural way to write a state class
            String note = "";
            boolean dialogOpen;
        }

        @Override
        protected State initialState(RouteParams params) {
            State s = new State();
            s.count = params.queryInt("start", 0);
            return s;
        }

        @Override
        protected UiNode render(State s) {
            title("Counter " + s.count);
            if (s.dialogOpen) {
                UiDialog d = UiDialog.of("Dialog", null, UiText.of("dlg-body", "count is " + s.count));
                d.setId("dlg");
                dialog(d);
            }
            UiForm form = UiForm.of("note-form", null)
                    .field(UiField.text("note", "Note", s.note).asEditable())
                    .action(on(UiAction.primary("save-note", "Save")).click(e -> {
                        s.note = e.value("note", "");
                        toast("saved");
                    }));
            return UiStack.of("root")
                    .child(UiText.of("count", "Clicked " + s.count + " times"))
                    .child(on(UiAction.primary("inc", "+1")).click(e -> s.count++))
                    .child(on(UiAction.secondary("toggle", "Dialog")).click(e -> s.dialogOpen = !s.dialogOpen))
                    .child(on(UiAction.secondary("boom", "Fail")).click(e -> { s.count = 999; throw new IllegalStateException("kaboom"); }))
                    .child(on(UiAction.link("away", "Other")).click(e -> navigate(OtherView.class, Map.of("who", "bob"))))
                    .child(on(UiAction.link("ext", "Docs")).click(e -> navigate("https://example.org/docs")))
                    .child(form);
        }
    }

    @SuiRoute("/other")
    public static class OtherView extends SuiView<OtherView.State> {
        public static class State {
            public String who;
        }

        @Override
        protected State initialState(RouteParams params) {
            State s = new State();
            s.who = params.query("who", "nobody");
            return s;
        }

        @Override
        protected UiNode render(State s) {
            UiTable t = UiTable.of("people", "People").column(UiColumn.of("name", "Name"));
            t.row(Map.of("id", "p-" + s.who, "name", s.who));
            t.rowAction(onRow(UiAction.secondary("pick", "Pick")).click(e -> s.who = e.rowId()));
            return UiStack.of("other-root").child(UiText.of("hello", "Hello " + s.who)).child(t);
        }
    }

    // ── fixture ────────────────────────────────────────────────────────────

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final InMemoryViewStateStore store = new InMemoryViewStateStore();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final SuiViewEngine engine = engine(store);

    private SuiViewEngine engine(ViewStateStore store) {
        SuiViewRegistry registry = new SuiViewRegistry().register(CounterView.class).register(OtherView.class);
        // Threshold 1.0: the test pages are tiny, and a patch that is bigger
        // than 60 % of them would be the budget speaking, not the diff.
        var settings = new SuiViewEngine.Settings("/sui/s", "_v", Duration.ofMinutes(30), 50, 1.0);
        return new SuiViewEngine(mapper, registry, ViewFactory.reflective(), store, settings, clock);
    }

    private static String idOf(SuiViewEngine.PageResponse r) {
        return r.page().getNavigate().substring(r.page().getNavigate().indexOf("_v=") + 3);
    }

    private SuiViewEngine.Response click(String id, String node, String owner) {
        return engine.event(id, node, "click", null, Map.of(), List.of(), owner, SuiViewEngine.ToastDelivery.INLINE);
    }

    private static List<UiPatch.Operation> ops(SuiViewEngine.Response r) {
        return ((SuiViewEngine.PatchResponse) r).patch().getPatches();
    }

    // ── tests ──────────────────────────────────────────────────────────────

    @Test
    void openRendersAndAddressesTheInstance() {
        SuiViewEngine.PageResponse r = engine.open("/counter", RouteParams.of(Map.of("start", List.of("5"))), "alice");
        assertThat(r.page().getNavigate()).startsWith("/counter?_v=");
        assertThat(r.page().getTitle()).isEqualTo("Counter 5");
        assertThat(r.version()).isEqualTo(1);
        assertThat(store.load(idOf(r))).isPresent();
        assertThat(mapper.valueToTree(r.page()).at("/node/children/1/onClick/url").asText())
                .isEqualTo("/sui/s/" + idOf(r) + "/inc/click");
        assertThat(mapper.valueToTree(r.page()).at("/node/children/1/onClick/method").asText()).isEqualTo("POST");
    }

    @Test
    void anEventChangesStateAndAnswersWithTheDifference() {
        String id = idOf(engine.open("/counter", RouteParams.empty(), "alice"));
        SuiViewEngine.Response r = click(id, "inc", "alice");
        assertThat(r).isInstanceOf(SuiViewEngine.PatchResponse.class);
        assertThat(((SuiViewEngine.PatchResponse) r).version()).isEqualTo(2);
        assertThat(ops(r)).hasSize(1);
        assertThat(ops(r).get(0).getOp()).isEqualTo(UiPatch.Op.MERGE);
        assertThat(ops(r).get(0).getTargetId()).isEqualTo("count");
        assertThat(ops(r).get(0).getAttributes()).containsEntry("text", "Clicked 1 times");
        assertThat(store.load(id).orElseThrow().getStateJson()).contains("\"count\":1");
    }

    @Test
    void payloadValuesReachTheHandlerAndToastsRideAlong() {
        String id = idOf(engine.open("/counter", RouteParams.empty(), "alice"));
        SuiViewEngine.Response r = engine.event(id, "save-note", "click", null, Map.of("note", "hello"),
                List.of(), "alice", SuiViewEngine.ToastDelivery.INLINE);
        UiPatch patch = ((SuiViewEngine.PatchResponse) r).patch();
        assertThat(patch.getToasts()).extracting(UiToast::getMessage).containsExactly("saved");
        assertThat(patch.getPatches()).singleElement().satisfies(op -> {
            assertThat(op.getOp()).isEqualTo(UiPatch.Op.MERGE);
            assertThat(op.getTargetId()).isEqualTo("note");
            assertThat(op.getAttributes()).containsEntry("value", "hello");
        });
    }

    @Test
    void deferredToastsAreShownByTheNextShow() {
        String id = idOf(engine.open("/counter", RouteParams.empty(), "alice"));
        engine.event(id, "save-note", "click", null, Map.of("note", "x"), List.of(), "alice",
                SuiViewEngine.ToastDelivery.DEFERRED);
        SuiViewEngine.PageResponse shown = engine.show(id, "alice");
        assertThat(shown.page().getToasts()).extracting(UiToast::getMessage).containsExactly("saved");
        assertThat(engine.show(id, "alice").page().getToasts()).isNullOrEmpty();
    }

    @Test
    void dialogsOpenAndCloseFromState() {
        String id = idOf(engine.open("/counter", RouteParams.empty(), "alice"));
        List<UiPatch.Operation> open = ops(click(id, "toggle", "alice"));
        assertThat(open).singleElement().satisfies(op -> {
            assertThat(op.getOp()).isEqualTo(UiPatch.Op.APPEND);
            assertThat(op.getTargetId()).isEqualTo(TreeDiff.DIALOG_HOST_ID);
            assertThat(op.getNode()).isInstanceOf(UiDialog.class);
        });
        List<UiPatch.Operation> close = ops(click(id, "toggle", "alice"));
        assertThat(close).singleElement().satisfies(op -> {
            assertThat(op.getOp()).isEqualTo(UiPatch.Op.REMOVE);
            assertThat(op.getTargetId()).isEqualTo("dlg");
        });
    }

    @Test
    void unboundEventsAreRefused() {
        String id = idOf(engine.open("/counter", RouteParams.empty(), "alice"));
        assertThatThrownBy(() -> click(id, "nope", "alice")).isInstanceOf(StatefulException.UnboundEvent.class);
        assertThatThrownBy(() -> engine.event(id, "inc", "change", null, Map.of(), List.of(), "alice",
                SuiViewEngine.ToastDelivery.INLINE)).isInstanceOf(StatefulException.UnboundEvent.class);
    }

    @Test
    void anotherOwnerSeesNothing() {
        String id = idOf(engine.open("/counter", RouteParams.empty(), "alice"));
        assertThatThrownBy(() -> click(id, "inc", "mallory")).isInstanceOf(StatefulException.UnknownInstance.class);
        assertThatThrownBy(() -> engine.show(id, "mallory")).isInstanceOf(StatefulException.UnknownInstance.class);
    }

    @Test
    void aFailingHandlerLeavesTheStateAlone() {
        String id = idOf(engine.open("/counter", RouteParams.empty(), "alice"));
        assertThatThrownBy(() -> click(id, "boom", "alice"))
                .isInstanceOf(StatefulException.HandlerFailed.class)
                .hasRootCauseMessage("kaboom");
        ViewInstance stored = store.load(id).orElseThrow();
        assertThat(stored.getVersion()).isEqualTo(1);
        assertThat(stored.getStateJson()).contains("\"count\":0");
    }

    @Test
    void navigatingToAnotherViewOpensItInTheSameResponse() {
        String id = idOf(engine.open("/counter", RouteParams.empty(), "alice"));
        SuiViewEngine.Response r = click(id, "away", "alice");
        assertThat(r).isInstanceOf(SuiViewEngine.PageResponse.class);
        var page = ((SuiViewEngine.PageResponse) r).page();
        assertThat(page.getNavigate()).startsWith("/other?_v=");
        assertThat(mapper.valueToTree(page).at("/node/children/0/text").asText()).isEqualTo("Hello bob");
    }

    @Test
    void navigatingElsewhereIsARedirect() {
        String id = idOf(engine.open("/counter", RouteParams.empty(), "alice"));
        SuiViewEngine.Response r = click(id, "ext", "alice");
        assertThat(r).isInstanceOf(SuiViewEngine.RedirectResponse.class);
        assertThat(((SuiViewEngine.RedirectResponse) r).url()).isEqualTo("https://example.org/docs");
    }

    @Test
    void rowActionsCarryTheRowIdAndTableColumnsStayStable() {
        SuiViewEngine.PageResponse opened = engine.open("/other", RouteParams.empty(), "alice");
        String id = idOf(opened);
        var json = mapper.valueToTree(opened.page());
        assertThat(json.at("/node/children/1/rowActions/0/onClick/url").asText())
                .isEqualTo("/sui/s/" + id + "/pick/click?row={id}");
        assertThat(json.at("/node/children/1/columns/0/id").asText()).isEqualTo("people-col-name");

        SuiViewEngine.Response r = engine.event(id, "pick", "click", "p-nobody", Map.of(), List.of(), "alice",
                SuiViewEngine.ToastDelivery.INLINE);
        // The row id becomes the name; the greeting merges, the row is replaced (its id changed).
        assertThat(ops(r)).extracting(UiPatch.Operation::getOp)
                .containsExactlyInAnyOrder(UiPatch.Op.MERGE, UiPatch.Op.REPLACE);
    }

    @Test
    void aNoOpEventIsAnEmptyPatch() {
        String id = idOf(engine.open("/other", RouteParams.empty(), "alice"));
        // Picking the row that is already picked changes nothing — and the
        // factory-made column ids must not make the table look changed.
        SuiViewEngine.Response r = engine.event(id, "pick", "click", "nobody", Map.of(), List.of(), "alice",
                SuiViewEngine.ToastDelivery.INLINE);
        assertThat(ops(r)).isEmpty();
    }

    @Test
    void idleInstancesExpire() {
        String id = idOf(engine.open("/counter", RouteParams.empty(), "alice"));
        clock.advance(Duration.ofMinutes(31));
        assertThatThrownBy(() -> click(id, "inc", "alice")).isInstanceOf(StatefulException.UnknownInstance.class);
        assertThat(store.load(id)).isEmpty();
    }

    @Test
    void reapDropsWhatTheTimeoutSays() {
        engine.open("/counter", RouteParams.empty(), "alice");
        clock.advance(Duration.ofMinutes(10));
        engine.open("/counter", RouteParams.empty(), "alice");
        clock.advance(Duration.ofMinutes(25));
        assertThat(engine.reap()).isEqualTo(1);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void aLostRaceIsRetriedOnceAgainstTheFreshState() {
        var flaky = new ViewStateStore() {
            final AtomicBoolean failOnce = new AtomicBoolean(true);

            @Override public Optional<ViewInstance> load(String id) { return store.load(id); }
            @Override public void insert(ViewInstance instance) { store.insert(instance); }
            @Override public void update(ViewInstance instance, long expectedVersion) {
                if (failOnce.getAndSet(false)) {
                    // Somebody else got there first: bump the stored version under us.
                    ViewInstance other = store.load(instance.getId()).orElseThrow();
                    other.setVersion(other.getVersion() + 1);
                    store.update(other, expectedVersion);
                    throw new VersionConflictException(instance.getId(), expectedVersion, other.getVersion());
                }
                store.update(instance, expectedVersion);
            }
            @Override public void touch(String id, Instant at) { store.touch(id, at); }
            @Override public void delete(String id) { store.delete(id); }
            @Override public int expireIdle(Instant idleBefore) { return store.expireIdle(idleBefore); }
            @Override public int trimOwner(String owner, int keep) { return store.trimOwner(owner, keep); }
        };
        SuiViewEngine racy = engine(flaky);
        String id = idOf(racy.open("/counter", RouteParams.empty(), "alice"));
        SuiViewEngine.Response r = racy.event(id, "inc", "click", null, Map.of(), List.of(), "alice",
                SuiViewEngine.ToastDelivery.INLINE);
        assertThat(((SuiViewEngine.PatchResponse) r).version()).isEqualTo(3);
        assertThat(store.load(id).orElseThrow().getStateJson()).contains("\"count\":1");
    }

    @Test
    void ownerInstanceCountIsBounded() {
        SuiViewRegistry registry = new SuiViewRegistry().register(CounterView.class);
        var settings = new SuiViewEngine.Settings("/sui/s", "_v", Duration.ofMinutes(30), 2, 0.6);
        SuiViewEngine bounded = new SuiViewEngine(mapper, registry, ViewFactory.reflective(), store, settings, clock);
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofSeconds(1));
            bounded.open("/counter", RouteParams.empty(), "alice");
        }
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void unknownRouteIsRefused() {
        assertThatThrownBy(() -> engine.open("/nowhere", RouteParams.empty(), "alice"))
                .isInstanceOf(StatefulException.UnknownRoute.class);
    }

    /** A clock the tests can move. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
